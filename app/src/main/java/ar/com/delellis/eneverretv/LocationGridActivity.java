package ar.com.delellis.eneverretv;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ar.com.delellis.eneverretv.api.model.Camera;

public class LocationGridActivity extends AppCompatActivity {
    private static final String TAG = "LocationGridActivity";

    public static final String RAW_CAMERAS_LIST_DATA = "RAW_CAMERA_LIST";
    public static final String LOCATION_NAME = "LOCATION_NAME";
    public static final String FOCUS_CAMERA_ID = "FOCUS_CAMERA_ID";

    private GridLayout gridLayout;
    private TextView locationLabel;
    private View focusedTile;

    private List<Camera> cameras = new ArrayList<>();
    private List<MediaPlayer> players = new ArrayList<>();
    private List<LibVLC> libvlcs = new ArrayList<>();
    private List<View> tiles = new ArrayList<>();

    private int focusedIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_grid);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        gridLayout = findViewById(R.id.grid_layout);
        locationLabel = findViewById(R.id.location_label);

        String name = getIntent().getStringExtra(LOCATION_NAME);
        if (name == null) name = "";
        locationLabel.setText(name);

        cameras = (List<Camera>) getIntent().getSerializableExtra(RAW_CAMERAS_LIST_DATA);
        if (cameras == null) cameras = new ArrayList<>();

        if (cameras.isEmpty()) {
            Toast.makeText(this, R.string.no_cameras_in_location, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        buildGrid();
    }

    private void buildGrid() {
        int n = cameras.size();
        int cols = computeColumns(n);
        int rows = (int) Math.ceil((double) n / cols);

        gridLayout.setColumnCount(cols);
        gridLayout.setRowCount(rows);

        for (int i = 0; i < n; i++) {
            int row = i / cols;
            int col = i % cols;
            View tile = createTile(cameras.get(i), i);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.rowSpec = GridLayout.spec(row, 1f);
            params.columnSpec = GridLayout.spec(col, 1f);
            params.width = 0;
            params.height = 0;
            tile.setLayoutParams(params);
            tile.setFocusable(true);
            tile.setFocusableInTouchMode(true);
            tile.setOnFocusChangeListener((v, hasFocus) -> {
                View indicator = v.findViewById(R.id.focus_indicator);
                if (indicator != null) {
                    indicator.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
                }
                if (hasFocus) {
                    focusedIndex = (int) v.getTag();
                    focusedTile = v;
                }
            });
            tile.setOnClickListener(v -> openFocusedFullscreen());
            gridLayout.addView(tile);
            tiles.add(tile);
        }

        if (!tiles.isEmpty()) {
            String focusId = getIntent().getStringExtra(FOCUS_CAMERA_ID);
            int focusIndex = 0;
            if (focusId != null) {
                for (int i = 0; i < cameras.size(); i++) {
                    if (String.valueOf(cameras.get(i).getId()).equals(focusId)) {
                        focusIndex = i;
                        break;
                    }
                }
            }
            tiles.get(focusIndex).requestFocus();
        }
    }

    private int computeColumns(int n) {
        if (n <= 1) return 1;
        if (n == 2) return 2;
        if (n <= 4) return 2;
        if (n <= 6) return 3;
        if (n <= 9) return 3;
        return 3;
    }

    private View createTile(Camera camera, int index) {
        View tile = getLayoutInflater().inflate(R.layout.item_grid_tile, gridLayout, false);
        tile.setTag(index);

        TextView nameView = tile.findViewById(R.id.tile_name);
        nameView.setText(camera.getName());

        FrameLayout videoContainer = tile.findViewById(R.id.tile_video_container);
        VLCVideoLayout videoLayout = new VLCVideoLayout(this);
        videoContainer.addView(videoLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ArrayList<String> options = new ArrayList<>();
        options.add("--quiet");
        options.add("--network-caching=150");
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");
        options.add("--rtsp-tcp");

        LibVLC libVLC = new LibVLC(this, options);
        MediaPlayer mediaPlayer = new MediaPlayer(libVLC);
        mediaPlayer.setEventListener(event -> {
            if (event.type == MediaPlayer.Event.Buffering) {
                View progress = tile.findViewById(R.id.tile_progress);
                if (progress != null) {
                    progress.setVisibility(event.getBuffering() == 100f ? View.GONE : View.VISIBLE);
                }
            }
        });
        mediaPlayer.attachViews(videoLayout, null, false, false);

        Media media = new Media(libVLC, Uri.parse(camera.getLive()));
        media.setHWDecoderEnabled(true, false);
        mediaPlayer.setMedia(media);
        media.release();
        mediaPlayer.play();

        libvlcs.add(libVLC);
        players.add(mediaPlayer);

        return tile;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (focusedTile != null) {
                    openFocusedFullscreen();
                    return true;
                }
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    private void openFocusedFullscreen() {
        if (focusedIndex < 0 || focusedIndex >= cameras.size()) return;
        Camera cam = cameras.get(focusedIndex);
        List<Camera> all = (List<Camera>) getIntent().getSerializableExtra(RAW_CAMERAS_LIST_DATA);
        if (all == null) all = cameras;

        Intent intent = new Intent(this, LiveActivity.class);
        intent.putExtra(LiveActivity.RAW_CAMERAS_LIST_DATA, (Serializable) all);
        intent.putExtra(LiveActivity.CURRENT_CAMERA_ID, cam.getId());
        startActivity(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayers();
    }

    @Override
    protected void onDestroy() {
        releasePlayers();
        super.onDestroy();
    }

    private void releasePlayers() {
        for (MediaPlayer mp : players) {
            try {
                mp.stop();
                mp.detachViews();
                mp.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing MediaPlayer: " + e.getMessage());
            }
        }
        for (LibVLC lib : libvlcs) {
            try {
                lib.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing LibVLC: " + e.getMessage());
            }
        }
        players.clear();
        libvlcs.clear();
    }
}

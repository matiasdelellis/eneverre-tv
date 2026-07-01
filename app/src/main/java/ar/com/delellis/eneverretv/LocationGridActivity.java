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

import com.alexvas.rtsp.codec.VideoDecodeThread;
import com.alexvas.rtsp.widget.RtspStatusListener;
import com.alexvas.rtsp.widget.RtspSurfaceView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ar.com.delellis.eneverretv.api.model.Camera;

public class LocationGridActivity extends AppCompatActivity {
    private static final String TAG = "LocationGridActivity";

    public static final String RAW_CAMERAS_LIST_DATA = "RAW_CAMERA_LIST";
    public static final String LOCATION_NAME = "LOCATION_NAME";
    public static final String FOCUS_CAMERA_ID = "FOCUS_CAMERA_ID";

    private static final int MAX_TILES = 4;

    private GridLayout gridLayout;
    private TextView locationLabel;
    private View focusedTile;

    private List<Camera> cameras = new ArrayList<>();
    private List<RtspSurfaceView> players = new ArrayList<>();
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

        // Limit the grid to a 2x2 window (up to 4 simultaneous streams) to stay within the
        // hardware decoder budget of typical Android TV devices. The window always includes
        // the focused camera; the full list is still passed through to fullscreen playback.
        cameras = selectGridWindow(cameras);

        buildGrid();
    }

    private List<Camera> selectGridWindow(List<Camera> all) {
        if (all.size() <= MAX_TILES) return all;

        String focusId = getIntent().getStringExtra(FOCUS_CAMERA_ID);
        int focusIndex = 0;
        if (focusId != null) {
            for (int i = 0; i < all.size(); i++) {
                if (String.valueOf(all.get(i).getId()).equals(focusId)) {
                    focusIndex = i;
                    break;
                }
            }
        }

        int start = focusIndex - MAX_TILES / 2;
        if (start < 0) start = 0;
        if (start > all.size() - MAX_TILES) start = all.size() - MAX_TILES;

        return new ArrayList<>(all.subList(start, start + MAX_TILES));
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
        return n <= 1 ? 1 : 2;
    }

    private View createTile(Camera camera, int index) {
        View tile = getLayoutInflater().inflate(R.layout.item_grid_tile, gridLayout, false);
        tile.setTag(index);

        TextView nameView = tile.findViewById(R.id.tile_name);
        nameView.setText(camera.getName());

        FrameLayout videoContainer = tile.findViewById(R.id.tile_video_container);
        RtspSurfaceView surfaceView = new RtspSurfaceView(this);
        videoContainer.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        surfaceView.setDebug(BuildConfig.DEBUG);
        // See LiveActivity: the emulator's hardware decoder never renders High-profile H264.
        if (DeviceUtils.isEmulator()) {
            surfaceView.setVideoDecoderType(VideoDecodeThread.DecoderType.SOFTWARE);
        }

        surfaceView.setStatusListener(new RtspStatusListener() {
            @Override
            public void onRtspStatusConnecting() {
                setTileProgress(tile, true);
            }

            @Override
            public void onRtspStatusConnected() { }

            @Override
            public void onRtspStatusDisconnecting() { }

            @Override
            public void onRtspStatusDisconnected() { }

            @Override
            public void onRtspStatusFailedUnauthorized() {
                setTileProgress(tile, false);
            }

            @Override
            public void onRtspStatusFailed(String message) {
                setTileProgress(tile, false);
            }

            @Override
            public void onRtspFirstFrameRendered() {
                // Invoked on the decoder thread.
                runOnUiThread(() -> setTileProgress(tile, false));
            }

            @Override
            public void onRtspFrameSizeChanged(int width, int height) { }
        });

        Uri raw = Uri.parse(camera.getLive());
        String username = null;
        String password = null;
        Uri uri = raw;

        String userInfo = raw.getUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int sep = userInfo.indexOf(':');
            if (sep >= 0) {
                username = userInfo.substring(0, sep);
                password = userInfo.substring(sep + 1);
            } else {
                username = userInfo;
            }
            String authority = raw.getHost();
            if (raw.getPort() != -1) authority += ":" + raw.getPort();
            uri = raw.buildUpon().encodedAuthority(authority).build();
        }

        surfaceView.init(uri, username, password, "EneverreTV", null);
        surfaceView.start(true, false, false);

        players.add(surfaceView);

        return tile;
    }

    private void setTileProgress(View tile, boolean visible) {
        View progress = tile.findViewById(R.id.tile_progress);
        if (progress != null) {
            progress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
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
        for (RtspSurfaceView sv : players) {
            try {
                if (sv.isStarted()) sv.stop();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping RtspSurfaceView: " + e.getMessage());
            }
        }
        players.clear();
    }
}

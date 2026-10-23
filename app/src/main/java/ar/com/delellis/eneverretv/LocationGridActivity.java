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

import ar.com.delellis.eneverretv.api.CameraRepository;
import ar.com.delellis.eneverretv.api.SessionManager;
import ar.com.delellis.eneverretv.api.model.Camera;
import ar.com.delellis.eneverretv.player.RtspUrl;

public class LocationGridActivity extends AppCompatActivity {
    private static final String TAG = "LocationGridActivity";

    public static final String RAW_CAMERAS_LIST_DATA = "RAW_CAMERA_LIST";
    public static final String LOCATION_NAME = "LOCATION_NAME";
    public static final String LOCATION_KEY = "LOCATION_KEY";
    public static final String FOCUS_CAMERA_ID = "FOCUS_CAMERA_ID";

    private static final int MAX_TILES = 4;
    private static final long CAMERA_LIST_MAX_AGE_MS = 5L * 60L * 60L * 1000L;

    private GridLayout gridLayout;
    private TextView locationLabel;
    private View focusedTile;

    // Every camera in the location (used to hand a complete list back to the
    // fullscreen view) and the 2x2 window currently streaming.
    private List<Camera> allCameras = new ArrayList<>();
    private List<Camera> cameras = new ArrayList<>();
    private List<RtspSurfaceView> players = new ArrayList<>();
    private List<View> tiles = new ArrayList<>();

    private String locationName = "";
    private String locationKey = null;
    private String focusedCameraId = null;

    private int focusedIndex = 0;

    private boolean gridRefreshAttempted = false;
    private boolean gridRefreshing = false;
    private boolean wasStopped = false;
    private boolean navigatingToLogin = false;

    // Incremented whenever the grid is torn down. Callbacks from a stopped
    // RtspSurfaceView belong to an older generation and must not trigger a
    // refresh (or clear the flags) for the tiles now on screen.
    private int gridGeneration = 0;

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

        locationName = getIntent().getStringExtra(LOCATION_NAME);
        if (locationName == null) locationName = "";
        locationLabel.setText(locationName);

        locationKey = getIntent().getStringExtra(LOCATION_KEY);
        focusedCameraId = getIntent().getStringExtra(FOCUS_CAMERA_ID);

        List<Camera> intentList = (List<Camera>) getIntent().getSerializableExtra(RAW_CAMERAS_LIST_DATA);
        if (intentList == null) intentList = new ArrayList<>();
        CameraRepository.seedIfEmpty(intentList);

        if (!intentList.isEmpty()) {
            allCameras = new ArrayList<>(intentList);
            if (locationKey == null && !allCameras.isEmpty()) {
                locationKey = locationKey(allCameras.get(0));
            }
        } else if (!CameraRepository.isEmpty()) {
            if (locationKey == null) {
                locationKey = locationName;
            }
            allCameras = filterByLocation(CameraRepository.getCameras(), locationKey);
        }

        if (allCameras.isEmpty()) {
            Toast.makeText(this, R.string.no_cameras_in_location, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Limit the grid to a 2x2 window (up to 4 simultaneous streams) to stay within the
        // hardware decoder budget of typical Android TV devices. The window always includes
        // the focused camera; the full list is still passed through to fullscreen playback.
        cameras = selectGridWindow(allCameras);
        buildGrid();
        refreshGridIfStale();
    }

    private List<Camera> filterByLocation(List<Camera> source, String key) {
        List<Camera> filtered = new ArrayList<>();
        for (Camera camera : source) {
            if (key != null && key.equals(locationKey(camera))) {
                filtered.add(camera);
            }
        }
        return filtered;
    }

    private static String locationKey(Camera camera) {
        if (camera == null) {
            return "";
        }
        String key = camera.getLocation();
        return (key == null || key.isEmpty()) ? "" : key;
    }

    private static Camera findCamera(List<Camera> source, String id) {
        if (id == null) {
            return null;
        }
        for (Camera camera : source) {
            if (id.equals(camera.getId())) {
                return camera;
            }
        }
        return null;
    }

    private static List<Camera> updateById(List<Camera> oldList, List<Camera> fresh) {
        List<Camera> updated = new ArrayList<>();
        for (Camera old : oldList) {
            Camera current = findCamera(fresh, old.getId());
            if (current != null) {
                updated.add(current);
            }
        }
        return updated;
    }

    private List<Camera> selectGridWindow(List<Camera> all) {
        if (all.size() <= MAX_TILES) return new ArrayList<>(all);

        String focusId = focusedCameraId;
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
        if (n == 0) {
            Toast.makeText(this, R.string.no_cameras_in_location, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int cols = computeColumns(n);
        int rows = (int) Math.ceil((double) n / cols);

        gridLayout.setColumnCount(cols);
        gridLayout.setRowCount(rows);

        final int generation = gridGeneration;
        for (int i = 0; i < n; i++) {
            int row = i / cols;
            int col = i % cols;
            View tile = createTile(cameras.get(i), i, generation);
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
                    if (focusedIndex >= 0 && focusedIndex < cameras.size()) {
                        focusedCameraId = cameras.get(focusedIndex).getId();
                    }
                }
            });
            tile.setOnClickListener(v -> openFocusedFullscreen());
            gridLayout.addView(tile);
            tiles.add(tile);
        }

        if (!tiles.isEmpty()) {
            int focusIndex = 0;
            if (focusedCameraId != null) {
                for (int i = 0; i < cameras.size(); i++) {
                    if (focusedCameraId.equals(cameras.get(i).getId())) {
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

    private View createTile(Camera camera, int index, int generation) {
        View tile = getLayoutInflater().inflate(R.layout.item_grid_tile, gridLayout, false);
        tile.setTag(index);

        TextView nameView = tile.findViewById(R.id.tile_name);
        nameView.setText(camera.getName());

        FrameLayout videoContainer = tile.findViewById(R.id.tile_video_container);
        RtspSurfaceView surfaceView = new RtspSurfaceView(this);
        videoContainer.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // See LiveActivity: the library debug mode logs the full RTSP request
        // line, including the signed path token.
        surfaceView.setDebug(false);
        // See LiveActivity: the emulator's hardware decoder never renders High-profile H264.
        if (DeviceUtils.isEmulator()) {
            surfaceView.setVideoDecoderType(VideoDecodeThread.DecoderType.SOFTWARE);
        }

        surfaceView.setStatusListener(new RtspStatusListener() {
            @Override
            public void onRtspStatusConnecting() {
                runOnUiThread(() -> {
                    if (generation != gridGeneration) {
                        return;
                    }
                    setTileProgress(tile, true);
                    setTileMessage(tile, 0);
                });
            }

            @Override
            public void onRtspStatusConnected() { }

            @Override
            public void onRtspStatusDisconnecting() { }

            @Override
            public void onRtspStatusDisconnected() { }

            @Override
            public void onRtspStatusFailedUnauthorized() {
                runOnUiThread(() -> {
                    if (generation != gridGeneration) {
                        return;
                    }
                    onCellError(tile, true);
                });
            }

            @Override
            public void onRtspStatusFailed(String message) {
                if (generation != gridGeneration) {
                    return;
                }
                Log.w(TAG, "RTSP failure: " + RtspUrl.redact(message));
                runOnUiThread(() -> {
                    if (generation == gridGeneration) {
                        onCellError(tile, false);
                    }
                });
            }

            @Override
            public void onRtspFirstFrameRendered() {
                // Invoked on the decoder thread.
                runOnUiThread(() -> {
                    if (generation != gridGeneration) {
                        return;
                    }
                    setTileProgress(tile, false);
                    setTileMessage(tile, 0);
                    // A working stream proves the current URLs are valid, so a
                    // later expiry may trigger one more refresh.
                    gridRefreshAttempted = false;
                });
            }

            @Override
            public void onRtspFrameSizeChanged(int width, int height) { }
        });

        String url = camera.getRtsp();
        if (url == null || url.isEmpty() || !camera.isEnabled()) {
            setTileProgress(tile, false);
            setTileMessage(tile, unavailableReason(camera));
        } else {
            // Opaque URL: no userinfo split, no Authorization header, no rebuild.
            Uri uri = Uri.parse(url);
            surfaceView.init(uri, null, null, "EneverreTV", null);
            surfaceView.start(true, false, false);
        }

        players.add(surfaceView);

        return tile;
    }

    private int unavailableReason(Camera camera) {
        if (!camera.isEnabled()) {
            return R.string.camera_unavailable;
        }
        if (camera.getPrivacy()) {
            return R.string.camera_paused;
        }
        if (camera.isScheduleOff()) {
            return R.string.camera_off_schedule;
        }
        return R.string.no_signal;
    }

    private void onCellError(View tile, boolean unauthorized) {
        setTileProgress(tile, false);
        if (navigatingToLogin) {
            return;
        }
        if (unauthorized) {
            if (!gridRefreshAttempted) {
                gridRefreshAttempted = true;
                refreshGrid();
                return;
            }
            if (gridRefreshing) {
                // Another cell already started the one refresh this grid gets.
                // Keep its spinner running instead of flashing an error.
                setTileProgress(tile, true);
                return;
            }
        }
        setTileMessage(tile, R.string.no_signal);
    }

    private void refreshGridIfStale() {
        if (gridRefreshAttempted) {
            return;
        }
        boolean stale = CameraRepository.isOlderThan(CAMERA_LIST_MAX_AGE_MS);
        if (!stale) {
            for (Camera camera : cameras) {
                String url = camera.getRtsp();
                if (url != null && !url.isEmpty()
                        && RtspUrl.shouldRefresh(url, RtspUrl.REFRESH_MARGIN_MS)) {
                    stale = true;
                    break;
                }
            }
        }
        if (!stale) {
            return;
        }

        // Start the (one) refresh without tearing down the grid: the current
        // tiles keep their spinner until fresh URLs arrive.
        gridRefreshAttempted = true;
        refreshGrid();
    }

    /**
     * Refetches GET /api/cameras once for the whole grid. CameraRepository
     * collapses concurrent calls, and both guards above ensure four failing
     * tiles never produce four requests.
     */
    private void refreshGrid() {
        if (gridRefreshing || navigatingToLogin) {
            return;
        }
        gridRefreshing = true;
        for (View tile : tiles) {
            setTileMessage(tile, 0);
            setTileProgress(tile, true);
        }

        CameraRepository.refresh(this, new CameraRepository.Callback() {
            @Override
            public void onCameras(List<Camera> fresh) {
                gridRefreshing = false;
                if (navigatingToLogin || isFinishing() || isDestroyed()) {
                    return;
                }

                List<Camera> updatedAll = locationKey == null
                        ? updateById(allCameras, fresh)
                        : filterByLocation(fresh, locationKey);
                if (updatedAll.isEmpty() && locationKey != null) {
                    // The location may have been renamed, or the extra may be
                    // missing on an old Intent: fall back to the current ids.
                    updatedAll = updateById(allCameras, fresh);
                }
                if (updatedAll.isEmpty()) {
                    Toast.makeText(LocationGridActivity.this,
                            R.string.no_cameras_in_location, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                allCameras = updatedAll;
                if (locationKey == null) {
                    locationKey = locationKey(allCameras.get(0));
                }
                cameras = selectGridWindow(allCameras);
                if (!wasStopped) {
                    rebuildGrid();
                }
            }

            @Override
            public void onError(int httpCode, String message) {
                gridRefreshing = false;
                if (navigatingToLogin || isFinishing() || isDestroyed()) {
                    return;
                }
                if (httpCode == 401) {
                    onSessionExpired();
                    return;
                }
                Log.w(TAG, "Could not refresh the grid: " + message);
                if (wasStopped) {
                    return;
                }
                for (View tile : tiles) {
                    setTileProgress(tile, false);
                    setTileMessage(tile, R.string.no_signal);
                }
            }
        });
    }

    private void rebuildGrid() {
        releasePlayers();
        gridLayout.removeAllViews();
        tiles.clear();
        players.clear();
        focusedTile = null;
        buildGrid();
    }

    private void setTileProgress(View tile, boolean visible) {
        View progress = tile.findViewById(R.id.tile_progress);
        if (progress != null) {
            progress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void setTileMessage(View tile, int messageRes) {
        TextView message = tile.findViewById(R.id.tile_message);
        if (message == null) {
            return;
        }
        if (messageRes == 0) {
            message.setVisibility(View.GONE);
        } else {
            message.setText(messageRes);
            message.setVisibility(View.VISIBLE);
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

        Intent intent = new Intent(this, LiveActivity.class);
        intent.putExtra(LiveActivity.RAW_CAMERAS_LIST_DATA, (Serializable) new ArrayList<>(allCameras));
        intent.putExtra(LiveActivity.CURRENT_CAMERA_ID, cam.getId());
        startActivity(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (wasStopped && !navigatingToLogin && !isFinishing()) {
            wasStopped = false;
            gridRefreshAttempted = false;
            if (CameraRepository.isOlderThan(CAMERA_LIST_MAX_AGE_MS)) {
                if (!gridRefreshing) {
                    gridRefreshAttempted = true;
                    refreshGrid();
                }
            } else {
                rebuildGrid();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayers();
        if (!navigatingToLogin && !isFinishing()) {
            wasStopped = true;
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayers();
        super.onDestroy();
    }

    private void releasePlayers() {
        // Invalidate callbacks from the surfaces being stopped before the new
        // grid is built.
        gridGeneration++;
        for (RtspSurfaceView sv : players) {
            try {
                if (sv.isStarted()) sv.stop();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping RtspSurfaceView: " + e.getMessage());
            }
        }
        players.clear();
    }

    private void onSessionExpired() {
        if (navigatingToLogin) {
            return;
        }
        navigatingToLogin = true;
        releasePlayers();
        SessionManager.clear(this);
        CameraRepository.clear();
        Toast.makeText(this, R.string.session_expired, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, QrLoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

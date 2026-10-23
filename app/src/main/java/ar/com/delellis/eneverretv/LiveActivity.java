package ar.com.delellis.eneverretv;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static java.lang.Math.clamp;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.alexvas.rtsp.codec.VideoDecodeThread;
import com.alexvas.rtsp.widget.RtspStatusListener;
import com.alexvas.rtsp.widget.RtspSurfaceView;

import java.util.ArrayList;
import java.util.List;

import ar.com.delellis.eneverretv.api.CameraRepository;
import ar.com.delellis.eneverretv.api.SessionManager;
import ar.com.delellis.eneverretv.api.model.Camera;
import ar.com.delellis.eneverretv.player.PtzController;
import ar.com.delellis.eneverretv.player.RtspUrl;

public class LiveActivity extends AppCompatActivity {
    private static final String TAG = "LiveActivity";

    public static final String RAW_CAMERAS_LIST_DATA = "RAW_CAMERA_LIST";
    public static final String CURRENT_CAMERA_ID = "CURRENT_CAMERA_ID";

    // Re-fetch the list after this long in the background/page. A relay token
    // lives 6 h, so 5 h leaves plenty of room while still avoiding a needless
    // request on a quick app switch.
    private static final long CAMERA_LIST_MAX_AGE_MS = 5L * 60L * 60L * 1000L;

    private RtspSurfaceView videoLayout;
    private TextView messageView;

    private boolean scaled = false;
    private boolean ptzMode = false;

    private long lastCenterPressTime = 0;
    private static final long DOUBLE_TAP_TIMEOUT_MS = 500;

    private boolean pendingSingleTap = false;

    private List<Camera> cameraList = new ArrayList<>();
    private int currentIndex = 0;
    private String requestedCameraId = null;

    private boolean needReconnect = false;
    private boolean lastFailureUnauthorized = false;

    // One proactive refresh per view/session (guards against a wrong device
    // clock), plus one error-driven refresh per attempt. Both reset on a
    // rendered frame, except the proactive one: that is per session.
    private boolean proactiveRefreshAttempted = false;
    private boolean errorRefreshAttempted = false;

    private boolean wasStopped = false;
    private boolean navigatingToLogin = false;

    // Incremented on every start/stop so callbacks from a stream that was
    // already torn down (camera switch, grid round-trip) cannot trigger a
    // refresh or clear the flags of the stream now on screen.
    private int streamGeneration = 0;

    // Invalidates an in-flight camera-list refresh when the user switches
    // cameras before it completes.
    private int playRequestId = 0;

    private PtzController ptzController;

    private final PtzController.Listener ptzListener = new PtzController.Listener() {
        @Override
        public void onMoveFailed() {
            Toast.makeText(LiveActivity.this, R.string.ptz_move_failed, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onSessionExpired() {
            onSessionExpired();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.live_player);

        videoLayout = findViewById(R.id.videoLayout);
        messageView = findViewById(R.id.live_message);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        initPlayer();

        findViewById(R.id.reconnect_button).setVisibility(GONE);
        findViewById(R.id.reconnect_button).setOnClickListener(v -> onReconnectClicked());

        View gridButton = findViewById(R.id.grid_button);
        gridButton.setOnClickListener(v -> openLocationGrid());

        List<Camera> intentList = (List<Camera>) getIntent().getSerializableExtra(RAW_CAMERAS_LIST_DATA);
        if (intentList == null) intentList = new ArrayList<>();
        CameraRepository.seedIfEmpty(intentList);

        List<Camera> source = CameraRepository.isEmpty() ? intentList : CameraRepository.getCameras();
        requestedCameraId = getIntent().getStringExtra(CURRENT_CAMERA_ID);

        Camera requested = findCamera(source, requestedCameraId);
        if (requested == null && source != intentList) {
            // The repository can be older than the Intent (e.g. a camera added
            // just before process restore). Prefer the requested camera if the
            // Intent has it.
            Camera fromIntent = findCamera(intentList, requestedCameraId);
            if (fromIntent != null) {
                source = intentList;
                requested = fromIntent;
            }
        }

        if (requested == null && requestedCameraId == null && !source.isEmpty()) {
            requested = source.get(0);
        }

        if (requested == null) {
            showCameraUnavailable(R.string.camera_removed);
            return;
        }

        buildCameraList(source, requested);
        playCurrentCamera(true);
    }

    private static Camera findCamera(List<Camera> cameras, String id) {
        if (id == null) {
            return null;
        }
        for (Camera camera : cameras) {
            if (id.equals(camera.getId())) {
                return camera;
            }
        }
        return null;
    }

    private void buildCameraList(List<Camera> source, Camera requested) {
        String locationKey = locationKey(requested);
        cameraList = new ArrayList<>();
        for (Camera camera : source) {
            if (locationKey(camera).equals(locationKey)) {
                cameraList.add(camera);
            }
        }

        currentIndex = cameraList.indexOf(requested);
        if (currentIndex < 0) {
            for (int i = 0; i < cameraList.size(); i++) {
                if (requested.getId() != null && requested.getId().equals(cameraList.get(i).getId())) {
                    currentIndex = i;
                    break;
                }
            }
        }
        if (currentIndex < 0 || currentIndex >= cameraList.size()) {
            currentIndex = 0;
        }

        View gridButton = findViewById(R.id.grid_button);
        gridButton.setVisibility(cameraList.size() > 1 ? VISIBLE : GONE);
    }

    private Camera currentCamera() {
        if (currentIndex < 0 || currentIndex >= cameraList.size()) {
            return null;
        }
        return cameraList.get(currentIndex);
    }

    private void openLocationGrid() {
        Camera current = currentCamera();
        if (current == null) return;
        String rawLocation = current.getLocation() == null ? "" : current.getLocation();
        String label = rawLocation.isEmpty() ? getString(R.string.location_unnamed) : rawLocation;
        Intent intent = new Intent(this, LocationGridActivity.class);
        intent.putExtra(LocationGridActivity.RAW_CAMERAS_LIST_DATA, (java.io.Serializable) cameraList);
        intent.putExtra(LocationGridActivity.LOCATION_NAME, label);
        intent.putExtra(LocationGridActivity.LOCATION_KEY, rawLocation);
        intent.putExtra(LocationGridActivity.FOCUS_CAMERA_ID, current.getId());
        startActivity(intent);
    }

    private static String locationKey(Camera camera) {
        String key = camera.getLocation();
        return (key == null || key.isEmpty()) ? "" : key;
    }

    private void initPlayer() {
        // Never enable the library debug mode: it logs the full RTSP request
        // line, including the signed path token (a bearer credential).
        videoLayout.setDebug(false);
        // The emulator's goldfish hardware H264 decoder accepts input but never emits frames for
        // High-profile streams (black screen), and the library only falls back to software when the
        // hardware decoder throws. Force software decoding on emulators; real devices use hardware.
        if (DeviceUtils.isEmulator()) {
            videoLayout.setVideoDecoderType(VideoDecodeThread.DecoderType.SOFTWARE);
        }
    }

    private RtspStatusListener createStatusListener(int generation) {
        return new RtspStatusListener() {
            @Override
            public void onRtspStatusConnecting() {
                runOnUiThread(() -> {
                    if (generation != streamGeneration) {
                        return;
                    }
                    findViewById(R.id.loading_progress).setVisibility(VISIBLE);
                    messageView.setVisibility(GONE);
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
                    if (generation != streamGeneration) {
                        return;
                    }
                    onPlaybackError(true);
                });
            }

            @Override
            public void onRtspStatusFailed(String message) {
                if (generation != streamGeneration) {
                    return;
                }
                // The library can include the URL in its error text; never log
                // or show it verbatim because the last segment is the token.
                Log.w(TAG, "RTSP failure: " + RtspUrl.redact(message));
                runOnUiThread(() -> {
                    if (generation == streamGeneration) {
                        onPlaybackError(false);
                    }
                });
            }

            @Override
            public void onRtspFirstFrameRendered() {
                // Invoked on the decoder thread.
                runOnUiThread(() -> {
                    if (generation != streamGeneration) {
                        return;
                    }
                    findViewById(R.id.loading_progress).setVisibility(GONE);
                    messageView.setVisibility(GONE);
                    findViewById(R.id.reconnect_button).setVisibility(GONE);
                    needReconnect = false;
                    errorRefreshAttempted = false;
                    lastFailureUnauthorized = false;
                });
            }

            @Override
            public void onRtspFrameSizeChanged(int width, int height) { }
        };
    }

    private void onPlaybackError(boolean unauthorized) {
        if (navigatingToLogin) {
            return;
        }
        findViewById(R.id.loading_progress).setVisibility(GONE);

        if (unauthorized) {
            lastFailureUnauthorized = true;
            Camera camera = currentCamera();
            if (!errorRefreshAttempted && camera != null) {
                // The signed path token may have expired (or belong to another
                // camera). Re-fetch once for a fresh URL before giving up.
                errorRefreshAttempted = true;
                refreshCamerasThenPlay(camera.getId(), false);
                return;
            }
        }

        showPlaybackError();
    }

    private void showPlaybackError() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Toast.makeText(this, R.string.error_playing, Toast.LENGTH_SHORT).show();
        needReconnect = true;
        messageView.setText(R.string.error_playing);
        messageView.setVisibility(VISIBLE);
        View reconnect = findViewById(R.id.reconnect_button);
        reconnect.setVisibility(VISIBLE);
        reconnect.requestFocus();
    }

    private void showCameraUnavailable(int messageRes) {
        stopPlayback();
        needReconnect = true;
        findViewById(R.id.loading_progress).setVisibility(GONE);
        messageView.setText(messageRes);
        messageView.setVisibility(VISIBLE);
        View reconnect = findViewById(R.id.reconnect_button);
        reconnect.setVisibility(VISIBLE);
        reconnect.requestFocus();
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    private void playCurrentCamera(boolean allowProactiveRefresh) {
        Camera camera = currentCamera();
        if (camera == null) {
            showCameraUnavailable(R.string.camera_removed);
            return;
        }
        requestPlay(camera, allowProactiveRefresh, true);
    }

    private void requestPlay(Camera camera, boolean allowProactiveRefresh, boolean allowFallback) {
        if (camera == null || navigatingToLogin) {
            return;
        }

        // A new play request supersedes any refresh started for the previous
        // camera.
        playRequestId++;

        String url = camera.getRtsp();
        boolean listStale = CameraRepository.isOlderThan(CAMERA_LIST_MAX_AGE_MS);
        boolean tokenNeedsRefresh = url != null && !url.isEmpty()
                && RtspUrl.shouldRefresh(url, RtspUrl.REFRESH_MARGIN_MS);
        boolean refreshFirst = lastFailureUnauthorized || listStale || tokenNeedsRefresh;

        if (allowProactiveRefresh && !proactiveRefreshAttempted && refreshFirst) {
            proactiveRefreshAttempted = true;
            boolean fallback = allowFallback && !lastFailureUnauthorized
                    && RtspUrl.isUsable(url, 0L);
            refreshCamerasThenPlay(camera.getId(), fallback);
            return;
        }

        if (!camera.isEnabled()) {
            showCameraUnavailable(R.string.camera_unavailable);
            return;
        }
        if (url == null || url.isEmpty()) {
            showCameraUnavailable(unavailableReason(camera));
            return;
        }

        startStream(camera);
    }

    private int unavailableReason(Camera camera) {
        if (camera.getPrivacy()) {
            return R.string.camera_paused;
        }
        if (camera.isScheduleOff()) {
            return R.string.camera_off_schedule;
        }
        return R.string.camera_unavailable;
    }

    /**
     * Refetches GET /api/cameras, updates every camera in this location by id
     * and starts playback with the fresh URL. CameraRepository collapses
     * concurrent refreshes (relevant for the grid, where several cells can
     * fail at the same time).
     */
    private void refreshCamerasThenPlay(String cameraId, boolean allowFallback) {
        if (navigatingToLogin) {
            return;
        }
        final int requestId = ++playRequestId;
        findViewById(R.id.loading_progress).setVisibility(VISIBLE);
        messageView.setVisibility(GONE);
        findViewById(R.id.reconnect_button).setVisibility(GONE);

        CameraRepository.refresh(this, new CameraRepository.Callback() {
            @Override
            public void onCameras(List<Camera> cameras) {
                if (requestId != playRequestId
                        || navigatingToLogin || isFinishing() || isDestroyed()) {
                    return;
                }
                Camera fresh = cameraId == null
                        ? (cameras.isEmpty() ? null : cameras.get(0))
                        : findCamera(cameras, cameraId);
                if (fresh == null) {
                    showCameraUnavailable(R.string.camera_removed);
                    return;
                }
                buildCameraList(cameras, fresh);
                if (!wasStopped) {
                    startStream(fresh);
                }
            }

            @Override
            public void onError(int httpCode, String message) {
                if (requestId != playRequestId
                        || navigatingToLogin || isFinishing() || isDestroyed()) {
                    return;
                }
                if (httpCode == 401) {
                    onSessionExpired();
                    return;
                }
                if (wasStopped) {
                    // onStart() will retry; do not flash an error on a screen
                    // that is currently in the background.
                    return;
                }

                Camera fallback = findCamera(cameraList, cameraId);
                if (allowFallback && fallback != null && RtspUrl.isUsable(fallback.getRtsp(), 0L)) {
                    // The list refresh failed (network, 5xx), but the URL we
                    // already had is still valid: play it rather than show an
                    // error.
                    if (!wasStopped) {
                        startStream(fallback);
                    }
                    return;
                }

                Log.w(TAG, "Could not refresh cameras: " + message);
                showPlaybackError();
            }
        });
    }

    private void onReconnectClicked() {
        if (navigatingToLogin) {
            return;
        }
        View reconnect = findViewById(R.id.reconnect_button);
        reconnect.setVisibility(GONE);
        messageView.setVisibility(GONE);
        needReconnect = false;

        Camera camera = currentCamera();
        if (camera == null) {
            refreshCamerasThenPlay(requestedCameraId, false);
            return;
        }

        String url = camera.getRtsp();
        boolean refreshFirst = lastFailureUnauthorized
                || CameraRepository.isOlderThan(CAMERA_LIST_MAX_AGE_MS)
                || RtspUrl.shouldRefresh(url, RtspUrl.REFRESH_MARGIN_MS);

        if (refreshFirst) {
            errorRefreshAttempted = true;
            boolean fallback = !lastFailureUnauthorized && RtspUrl.isUsable(url, 0L);
            refreshCamerasThenPlay(camera.getId(), fallback);
            return;
        }

        // A manual retry is a fresh attempt: if it gets a 401, allow the
        // automatic refresh once.
        errorRefreshAttempted = false;
        requestPlay(camera, false, true);
    }

    private void startStream(Camera camera) {
        if (camera == null || navigatingToLogin) {
            return;
        }
        String url = camera.getRtsp();
        if (!camera.isEnabled() || url == null || url.isEmpty()) {
            showCameraUnavailable(unavailableReason(camera));
            return;
        }

        if (videoLayout.isStarted()) {
            streamGeneration++;
            videoLayout.stop();
            clearSurface();
        }

        final int generation = ++streamGeneration;
        videoLayout.setStatusListener(createStatusListener(generation));

        findViewById(R.id.loading_progress).setVisibility(VISIBLE);
        messageView.setVisibility(GONE);
        findViewById(R.id.reconnect_button).setVisibility(GONE);
        needReconnect = false;

        Toast.makeText(this, camera.getName(), Toast.LENGTH_SHORT).show();

        ensurePtzController(camera);

        // The URL is opaque: no userinfo, no Authorization header, no
        // rebuilding. Uri.parse() preserves both path segments exactly.
        Uri uri = Uri.parse(url);
        videoLayout.init(uri, null, null, "EneverreTV", null);
        videoLayout.start(true, true, false);
    }

    private void stopPlayback() {
        // Invalidate callbacks from the stream being stopped.
        streamGeneration++;
        if (videoLayout != null && videoLayout.isStarted()) {
            videoLayout.stop();
            clearSurface();
        }
    }

    private void clearSurface() {
        SurfaceHolder holder = videoLayout.getHolder();
        if (holder == null) return;
        try {
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.BLACK);
                holder.unlockCanvasAndPost(canvas);
            }
        } catch (Exception ignored) {
            // Surface may be momentarily owned by the decoder; clearing is best-effort.
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        float moveStep = 0.1f * videoLayout.getHeight();

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (ptzMode) {
                    sendPtzCommand("right");
                } else if (!scaled) {
                    nextVideo();
                } else {
                    move(-moveStep, 0);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (ptzMode) {
                    sendPtzCommand("left");
                } else if (!scaled) {
                    previousVideo();
                } else {
                    move(moveStep, 0);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (ptzMode) {
                    sendPtzCommand("up");
                } else if (scaled) {
                    move(0, moveStep);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (ptzMode) {
                    sendPtzCommand("down");
                } else if (scaled) {
                    move(0, -moveStep);
                }
                return true;

            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_GUIDE:
                if (cameraList != null && cameraList.size() > 1) {
                    openLocationGrid();
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (needReconnect) {
                    findViewById(R.id.reconnect_button).performClick();
                    return true;
                }

                long currentTime = System.currentTimeMillis();
                if (lastCenterPressTime > 0 && currentTime - lastCenterPressTime < DOUBLE_TAP_TIMEOUT_MS) {
                    lastCenterPressTime = 0;
                    pendingSingleTap = false;
                    togglePtzMode();
                } else {
                    lastCenterPressTime = currentTime;
                    pendingSingleTap = true;

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (pendingSingleTap) {
                            pendingSingleTap = false;
                            lastCenterPressTime = 0;
                            if (!ptzMode) {
                                toggleZoom();
                            } else {
                                sendPtzCommand("center");
                            }
                        }
                    }, DOUBLE_TAP_TIMEOUT_MS);
                }
                return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private void nextVideo() {
        if (cameraList.size() <= 1) {
            return;
        }
        currentIndex = (currentIndex + 1) % cameraList.size();
        errorRefreshAttempted = false;
        requestPlay(cameraList.get(currentIndex), true, true);
    }

    private void previousVideo() {
        if (cameraList.size() <= 1) {
            return;
        }
        currentIndex = (currentIndex - 1 + cameraList.size()) % cameraList.size();
        errorRefreshAttempted = false;
        requestPlay(cameraList.get(currentIndex), true, true);
    }

    private void move(float dx, float dy) {
        float scale = videoLayout.getScaleX();

        float maxX = (scale - 1f) * videoLayout.getWidth() / 2f;
        float maxY = (scale - 1f) * videoLayout.getHeight() / 2f;

        float newX = videoLayout.getTranslationX() + dx;
        float newY = videoLayout.getTranslationY() + dy;

        newX = clamp(newX, -maxX, maxX);
        newY = clamp(newY, -maxY, maxY);

        videoLayout.animate()
                .translationX(newX)
                .translationY(newY)
                .setDuration(120)
                .start();
    }

    private boolean isZoomed() {
        return scaled;
    }

    private void toggleZoom() {
        if (!scaled) {
            videoLayout.animate()
                    .scaleX(2f)
                    .scaleY(2f)
                    .setDuration(200)
                    .start();
            scaled = true;
        } else {
            videoLayout.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationX(0)
                    .translationY(0)
                    .setDuration(200)
                    .start();
            scaled = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (wasStopped && !navigatingToLogin && !isFinishing()) {
            wasStopped = false;
            // Returning from the grid or from the background: the player was
            // stopped in onStop(), so restart it. Give the list a chance to be
            // refreshed if it is old or the previous attempt was rejected.
            if (CameraRepository.isOlderThan(CAMERA_LIST_MAX_AGE_MS)) {
                proactiveRefreshAttempted = false;
            }
            if (lastFailureUnauthorized) {
                // The previous attempt was rejected: force a refresh path even
                // if one was already tried in this session.
                proactiveRefreshAttempted = false;
            }
            errorRefreshAttempted = false;
            playCurrentCamera(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (ptzController != null) {
            ptzController.cancel();
            ptzController = null;
        }
        stopPlayback();
        if (!navigatingToLogin && !isFinishing()) {
            wasStopped = true;
        }
    }

    private void togglePtzMode() {
        Camera currentCamera = currentCamera();
        if (currentCamera == null) {
            return;
        }
        if (!currentCamera.getPtz()) {
            Toast.makeText(this, getString(R.string.the_camera_does_not_support_ptz) + currentCamera.getName(), Toast.LENGTH_SHORT).show();
            return;
        }

        ptzMode = !ptzMode;
        if (ptzMode) {
            Toast.makeText(this, R.string.move_the_camera_with_the_pad, Toast.LENGTH_SHORT).show();
            if (isZoomed()) {
                toggleZoom();
            }
        } else {
            Toast.makeText(this, R.string.exiting_ptz_mode, Toast.LENGTH_SHORT).show();
        }
        ensurePtzController(currentCamera);
    }

    private void ensurePtzController(Camera camera) {
        if (ptzController != null) {
            ptzController.cancel();
            ptzController = null;
        }
        if (ptzMode && camera != null && camera.getPtz()) {
            ptzController = new PtzController(this, camera, ptzListener);
        }
    }

    private void sendPtzCommand(String direction) {
        Camera camera = currentCamera();
        if (camera == null || !camera.getPtz()) {
            return;
        }
        if (ptzController == null) {
            ptzController = new PtzController(this, camera, ptzListener);
        }
        Log.d(TAG, "PTZ command: " + direction);
        if ("center".equals(direction)) {
            ptzController.home();
        } else {
            ptzController.move(direction);
        }
    }

    private void onSessionExpired() {
        if (navigatingToLogin) {
            return;
        }
        navigatingToLogin = true;
        stopPlayback();
        SessionManager.clear(this);
        CameraRepository.clear();
        Toast.makeText(this, R.string.session_expired, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, QrLoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.alexvas.rtsp.codec.VideoDecodeThread;
import com.alexvas.rtsp.widget.RtspStatusListener;
import com.alexvas.rtsp.widget.RtspSurfaceView;

import java.util.ArrayList;
import java.util.List;

import ar.com.delellis.eneverretv.api.model.Camera;

public class LiveActivity extends AppCompatActivity {
    private static final String TAG = "LiveActivity";

    public static final String RAW_CAMERAS_LIST_DATA = "RAW_CAMERA_LIST";
    public static final String CURRENT_CAMERA_ID = "CURRENT_CAMERA_ID";

    private RtspSurfaceView videoLayout;

    private boolean scaled = false;
    private boolean ptzMode = false;

    private long lastCenterPressTime = 0;
    private static final long DOUBLE_TAP_TIMEOUT_MS = 500;

    private boolean pendingSingleTap = false;

    private List<Camera> cameraList = null;
    private int currentIndex = 0;

    private boolean needReconect = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.live_player);

        videoLayout = findViewById(R.id.videoLayout);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        initPlayer();

        findViewById(R.id.reconnect_button).setVisibility(GONE);
        findViewById(R.id.reconnect_button).setOnClickListener(v -> {
            findViewById(R.id.reconnect_button).setVisibility(GONE);
            playVideo(cameraList.get(currentIndex));
            needReconect = false;
        });

        View gridButton = findViewById(R.id.grid_button);
        gridButton.setOnClickListener(v -> openLocationGrid());

        List<Camera> fullList = (List<Camera>) getIntent().getSerializableExtra(RAW_CAMERAS_LIST_DATA);
        if (fullList == null) fullList = new ArrayList<>();

        String cameraId = getIntent().getStringExtra(CURRENT_CAMERA_ID);
        Camera requested = null;
        if (cameraId != null) {
            for (Camera c : fullList) {
                if (String.valueOf(c.getId()).equals(cameraId)) {
                    requested = c;
                    break;
                }
            }
        }
        if (requested == null) {
            requested = fullList.isEmpty() ? null : fullList.get(0);
        }

        if (requested != null) {
            String locationKey = locationKey(requested);
            cameraList = new ArrayList<>();
            for (Camera c : fullList) {
                if (locationKey(c).equals(locationKey)) {
                    cameraList.add(c);
                }
            }
            currentIndex = cameraList.indexOf(requested);
            if (currentIndex < 0) currentIndex = 0;

            if (cameraList.size() > 1) {
                gridButton.setVisibility(VISIBLE);
            } else {
                gridButton.setVisibility(GONE);
            }

            playVideo(cameraList.get(currentIndex));
        }
    }

    private void openLocationGrid() {
        if (cameraList == null || cameraList.isEmpty()) return;
        Camera current = cameraList.get(currentIndex);
        Intent intent = new Intent(this, LocationGridActivity.class);
        intent.putExtra(LocationGridActivity.RAW_CAMERAS_LIST_DATA, (java.io.Serializable) cameraList);
        intent.putExtra(LocationGridActivity.LOCATION_NAME, locationKey(current));
        intent.putExtra(LocationGridActivity.FOCUS_CAMERA_ID, current.getId());
        startActivity(intent);
    }

    private static String locationKey(Camera camera) {
        String key = camera.getLocation();
        return (key == null || key.isEmpty()) ? "" : key;
    }

    private void initPlayer() {
        videoLayout.setDebug(BuildConfig.DEBUG);
        // The emulator's goldfish hardware H264 decoder accepts input but never emits frames for
        // High-profile streams (black screen), and the library only falls back to software when the
        // hardware decoder throws. Force software decoding on emulators; real devices use hardware.
        if (DeviceUtils.isEmulator()) {
            videoLayout.setVideoDecoderType(VideoDecodeThread.DecoderType.SOFTWARE);
        }
        videoLayout.setStatusListener(new RtspStatusListener() {
            @Override
            public void onRtspStatusConnecting() {
                findViewById(R.id.loading_progress).setVisibility(VISIBLE);
            }

            @Override
            public void onRtspStatusConnected() { }

            @Override
            public void onRtspStatusDisconnecting() { }

            @Override
            public void onRtspStatusDisconnected() { }

            @Override
            public void onRtspStatusFailedUnauthorized() {
                onPlaybackError();
            }

            @Override
            public void onRtspStatusFailed(String message) {
                onPlaybackError();
            }

            @Override
            public void onRtspFirstFrameRendered() {
                // Invoked on the decoder thread.
                runOnUiThread(() -> findViewById(R.id.loading_progress).setVisibility(GONE));
            }

            @Override
            public void onRtspFrameSizeChanged(int width, int height) { }
        });
    }

    private void onPlaybackError() {
        Toast.makeText(this, R.string.error_playing, Toast.LENGTH_SHORT).show();
        needReconect = true;

        findViewById(R.id.loading_progress).setVisibility(GONE);
        findViewById(R.id.reconnect_button).requestFocus();
        findViewById(R.id.reconnect_button).setVisibility(VISIBLE);
    }

    private void playVideo(Camera camera) {
        if (videoLayout.isStarted()) {
            videoLayout.stop();
            clearSurface();
        }

        findViewById(R.id.loading_progress).setVisibility(VISIBLE);
        Toast.makeText(this, camera.getName(), Toast.LENGTH_SHORT).show();

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

        videoLayout.init(uri, username, password, "EneverreTV", null);
        videoLayout.start(true, true, false);
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
                if (needReconect) {
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
        currentIndex = (currentIndex + 1) % cameraList.size();
        playVideo(cameraList.get(currentIndex));
    }

    private void previousVideo() {
        currentIndex = (currentIndex - 1 + cameraList.size()) % cameraList.size();
        playVideo(cameraList.get(currentIndex));
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
    protected void onStop() {
        super.onStop();

        if (videoLayout != null && videoLayout.isStarted()) {
            videoLayout.stop();
        }
    }

    private void togglePtzMode() {
        Camera currentCamera = cameraList.get(currentIndex);
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
    }

    private void sendPtzCommand(String direction) {
        Log.d(TAG, "PTZ command: " + direction);
        Toast.makeText(this, direction, Toast.LENGTH_SHORT).show();
    }
}
package ar.com.delellis.eneverretv;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static java.lang.Math.clamp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.List;

import ar.com.delellis.eneverretv.api.model.Camera;

public class LiveActivity extends AppCompatActivity {
    private static final String TAG = "LiveActivity";

    public static final String RAW_CAMERAS_LIST_DATA = "RAW_CAMERA_LIST";
    public static final String CURRENT_CAMERA_ID = "CURRENT_CAMERA_ID";

    private VLCVideoLayout videoLayout;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;

    private boolean scaled = false;
    private boolean ptzMode = false;

    private long lastCenterPressTime = 0;
    private static final long DOUBLE_TAP_TIMEOUT_MS = 500;

    private boolean pendingSingleTap = false;
    private boolean pendingZoomWasEnabled = false;

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

        cameraList = (List<Camera>) getIntent().getSerializableExtra(RAW_CAMERAS_LIST_DATA);
        String cameraId = getIntent().getStringExtra(CURRENT_CAMERA_ID);
        if (cameraId != null) {
            currentIndex = findCameraIndex(cameraId);
        }
        playVideo(cameraList.get(currentIndex));

        TvChannelManager.publish(this, cameraList);
    }

    private void initPlayer() {
        ArrayList<String> options = new ArrayList<>();

        options.add("--quiet");
        options.add("--network-caching=50");
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");
        options.add("--rtsp-tcp");

        libVLC = new LibVLC(this, options);

        mediaPlayer = new MediaPlayer(libVLC);
        mediaPlayer.setEventListener(event -> {
            if (event.type == MediaPlayer.Event.Buffering) {
                if (event.getBuffering() == 100f) {
                    findViewById(R.id.loading_progress).setVisibility(GONE);
                } else {
                    findViewById(R.id.loading_progress).setVisibility(VISIBLE);
                }
            } else if (event.type == MediaPlayer.Event.EncounteredError) {
                Toast.makeText(this, R.string.error_playing, Toast.LENGTH_SHORT).show();
                needReconect = true;

                findViewById(R.id.loading_progress).setVisibility(GONE);
                findViewById(R.id.reconnect_button).requestFocus();
                findViewById(R.id.reconnect_button).setVisibility(VISIBLE);
            }
        });

        mediaPlayer.attachViews(videoLayout, null, false, false);
    }

    private void playVideo(Camera camera) {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();

            SurfaceView surfaceView = findViewById(org.videolan.R.id.surface_video);
            if (surfaceView != null) {
                SurfaceHolder holder = surfaceView.getHolder();
                if (holder != null) {
                    Canvas canvas = holder.lockCanvas();
                    if (canvas != null) {
                        canvas.drawColor(Color.BLACK);
                    }
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }

        Toast.makeText(this, camera.getName(), Toast.LENGTH_SHORT).show();

        Media media = new Media(libVLC, Uri.parse(camera.getLive()));
        media.setHWDecoderEnabled(true, false);

        mediaPlayer.setMedia(media);
        media.release();

        mediaPlayer.play();
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
                    pendingZoomWasEnabled = scaled;

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

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.detachViews();
            mediaPlayer.release();
        }

        if (libVLC != null) {
            libVLC.release();
        }
    }

    private int findCameraIndex(String cameraId) {
        for (int i = 0; i < cameraList.size(); i++) {
            if (String.valueOf(cameraList.get(i).getId()).equals(cameraId)) {
                return i;
            }
        }
        return 0;
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
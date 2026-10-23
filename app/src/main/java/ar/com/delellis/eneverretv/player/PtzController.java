package ar.com.delellis.eneverretv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import ar.com.delellis.eneverretv.api.ApiClient;
import ar.com.delellis.eneverretv.api.SessionManager;
import ar.com.delellis.eneverretv.api.model.Camera;
import ar.com.delellis.eneverretv.api.model.PtzPosition;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Turns D-pad presses into relative PTZ moves, in degrees.
 *
 * <p>A held key repeats faster than a camera can physically move and faster
 * than the API should be called. Presses are accumulated and flushed at most
 * once every {@value #FLUSH_DELAY_MS} ms; while one request is in flight the
 * next batch waits, so the server never sees a burst of overlapping moves.</p>
 */
public final class PtzController {
    private static final String TAG = "PtzController";
    private static final long FLUSH_DELAY_MS = 120L;
    private static final long ERROR_THROTTLE_MS = 2000L;

    public interface Listener {
        void onMoveFailed();

        void onSessionExpired();
    }

    private final Context context;
    private final String cameraId;
    private final double panStep;
    private final double tiltStep;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable flushTask = this::flush;

    private boolean active = true;
    private boolean flushScheduled = false;
    private boolean inFlight = false;
    private double pendingPan = 0;
    private double pendingTilt = 0;
    private long lastErrorAtMs = 0L;

    public PtzController(Context context, Camera camera, Listener listener) {
        this.context = context.getApplicationContext();
        this.cameraId = camera == null ? null : camera.getId();
        this.listener = listener;

        Camera.PtzMetadata metadata = camera == null ? null : camera.getPtzMetadata();
        float fovH = metadata != null && metadata.getFovH() > 0 ? metadata.getFovH() : 60f;
        float fovV = metadata != null && metadata.getFovV() > 0 ? metadata.getFovV() : 34f;
        // One press moves roughly a tenth of the visible frame: small enough
        // to frame a subject, large enough not to feel like nothing happened.
        this.panStep = Math.max(1.0, fovH / 10.0);
        this.tiltStep = Math.max(1.0, fovV / 10.0);
    }

    public void move(String direction) {
        if (!active) {
            return;
        }
        switch (direction) {
            case "left":
                pendingPan -= panStep;
                break;
            case "right":
                pendingPan += panStep;
                break;
            case "up":
                pendingTilt -= tiltStep;
                break;
            case "down":
                pendingTilt += tiltStep;
                break;
            default:
                return;
        }
        scheduleFlush();
    }

    /** Moves to the configured home position (POST .../ptz/home). */
    public void home() {
        if (!active) {
            return;
        }
        pendingPan = 0;
        pendingTilt = 0;
        Log.d(TAG, "PTZ home");

        withToken((authorization, api) -> api.ptzHome(authorization, cameraId), false);
    }

    public void cancel() {
        active = false;
        handler.removeCallbacks(flushTask);
        flushScheduled = false;
        pendingPan = 0;
        pendingTilt = 0;
    }

    private void scheduleFlush() {
        if (flushScheduled || !active) {
            return;
        }
        flushScheduled = true;
        handler.postDelayed(flushTask, FLUSH_DELAY_MS);
    }

    private void flush() {
        flushScheduled = false;
        if (!active || (pendingPan == 0 && pendingTilt == 0)) {
            return;
        }
        if (inFlight) {
            scheduleFlush();
            return;
        }

        final double pan = pendingPan;
        final double tilt = pendingTilt;
        pendingPan = 0;
        pendingTilt = 0;
        inFlight = true;

        Log.d(TAG, "PTZ move pan=" + pan + " tilt=" + tilt);
        withToken((authorization, api) -> api.ptzMove(authorization, cameraId, pan, tilt), true);
    }

    private interface CallFactory {
        Call<PtzPosition> create(String authorization, ar.com.delellis.eneverretv.api.ApiService api);
    }

    private void withToken(CallFactory factory, boolean moveRequest) {
        SessionManager.withAccessToken(context, new SessionManager.TokenCallback() {
            @Override
            public void onToken(String accessToken) {
                Call<PtzPosition> call;
                try {
                    call = factory.create("Bearer " + accessToken, ApiClient.get().api());
                } catch (RuntimeException e) {
                    finishRequest(moveRequest);
                    fail();
                    return;
                }

                call.enqueue(new Callback<PtzPosition>() {
                    @Override
                    public void onResponse(Call<PtzPosition> call, Response<PtzPosition> response) {
                        finishRequest(moveRequest);
                        if (response.isSuccessful()) {
                            return;
                        }
                        if (response.code() == 401) {
                            if (active) {
                                listener.onSessionExpired();
                            }
                            return;
                        }
                        Log.w(TAG, "PTZ request failed: HTTP " + response.code());
                        fail();
                    }

                    @Override
                    public void onFailure(Call<PtzPosition> call, Throwable throwable) {
                        finishRequest(moveRequest);
                        Log.w(TAG, "PTZ network error: " + throwable.getMessage());
                        fail();
                    }
                });
            }

            @Override
            public void onFailure(boolean sessionExpired) {
                finishRequest(moveRequest);
                if (sessionExpired) {
                    if (active) {
                        listener.onSessionExpired();
                    }
                } else {
                    fail();
                }
            }
        });
    }

    private void finishRequest(boolean moveRequest) {
        if (moveRequest) {
            inFlight = false;
            if (active && (pendingPan != 0 || pendingTilt != 0)) {
                scheduleFlush();
            }
        }
    }

    private void fail() {
        long now = System.currentTimeMillis();
        if (now - lastErrorAtMs < ERROR_THROTTLE_MS) {
            return;
        }
        lastErrorAtMs = now;
        if (active) {
            listener.onMoveFailed();
        }
    }
}

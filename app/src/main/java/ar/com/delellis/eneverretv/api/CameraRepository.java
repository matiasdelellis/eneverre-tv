package ar.com.delellis.eneverretv.api;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ar.com.delellis.eneverretv.api.model.Camera;
import retrofit2.Call;
import retrofit2.Response;

/**
 * In-memory camera list shared by the Activities, plus the one place that
 * fetches it.
 *
 * <p>The list received with an Intent is only a cold-start/process-restore
 * fallback: every refresh replaces it here, so the fullscreen view and the
 * grid always pick up the current signed RTSP URLs. Concurrent refreshes are
 * collapsed into a single {@code GET /api/cameras}.</p>
 */
public final class CameraRepository {
    private static final String TAG = "CameraRepository";

    public interface Callback {
        void onCameras(List<Camera> cameras);

        /**
         * @param httpCode 401 when the session is gone and the user must sign
         *                 in again; 0 for a network failure; otherwise the
         *                 HTTP status.
         */
        void onError(int httpCode, String message);
    }

    private static final List<Camera> EMPTY = Collections.emptyList();

    private static List<Camera> cameras = EMPTY;
    private static long fetchedAtMs = 0L;
    private static boolean loading = false;
    private static int generation = 0;
    private static final List<Callback> pending = new ArrayList<>();

    private CameraRepository() {
    }

    public static List<Camera> getCameras() {
        return cameras;
    }

    public static boolean isEmpty() {
        return cameras.isEmpty();
    }

    public static boolean isOlderThan(long maxAgeMs) {
        return fetchedAtMs <= 0L || System.currentTimeMillis() - fetchedAtMs > maxAgeMs;
    }

    public static Camera findById(String id) {
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

    /**
     * Installs the list carried by an Intent when this process has no newer
     * copy. The fetch time stays unknown, so the first live view verifies the
     * URLs with the server instead of trusting an arbitrarily old token.
     */
    public static void seedIfEmpty(List<Camera> initial) {
        if (!cameras.isEmpty() || initial == null || initial.isEmpty()) {
            return;
        }
        cameras = Collections.unmodifiableList(new ArrayList<>(initial));
    }

    public static void clear() {
        generation++;
        cameras = EMPTY;
        fetchedAtMs = 0L;
        loading = false;
        pending.clear();
    }

    public static void refresh(Context context, Callback callback) {
        if (context == null) {
            if (callback != null) {
                callback.onError(0, "No context");
            }
            return;
        }

        if (loading) {
            if (callback != null) {
                pending.add(callback);
            }
            return;
        }

        loading = true;
        if (callback != null) {
            pending.add(callback);
        }

        final int requestGeneration = generation;
        final Context appContext = context.getApplicationContext();

        SessionManager.withAccessToken(appContext, new SessionManager.TokenCallback() {
            @Override
            public void onToken(String accessToken) {
                fetch(appContext, requestGeneration, accessToken, false);
            }

            @Override
            public void onFailure(boolean sessionExpired) {
                finishError(requestGeneration, sessionExpired ? 401 : 0,
                        sessionExpired ? "Session expired" : "Could not renew the session");
            }
        });
    }

    public static void refreshIfOlderThan(Context context, long maxAgeMs, Callback callback) {
        if (!cameras.isEmpty() && !isOlderThan(maxAgeMs)) {
            if (callback != null) {
                callback.onCameras(cameras);
            }
            return;
        }
        refresh(context, callback);
    }

    private static void fetch(Context appContext, int requestGeneration, String accessToken,
                              boolean afterRefresh) {
        Call<List<Camera>> call;
        try {
            call = ApiClient.get().api().cameras("Bearer " + accessToken);
        } catch (IllegalStateException e) {
            finishError(requestGeneration, 0, "ApiClient not initialized");
            return;
        }

        call.enqueue(new retrofit2.Callback<List<Camera>>() {
            @Override
            public void onResponse(Call<List<Camera>> call, Response<List<Camera>> response) {
                if (requestGeneration != generation) {
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    List<Camera> fresh = Collections.unmodifiableList(new ArrayList<>(response.body()));
                    cameras = fresh;
                    fetchedAtMs = System.currentTimeMillis();
                    Log.d(TAG, "Loaded " + fresh.size() + " cameras");
                    finishSuccess(requestGeneration, fresh);
                    return;
                }

                if (response.code() == 401 && !afterRefresh) {
                    // The stored access token can be rejected even when its
                    // local expiry looks valid (clock skew, server-side token
                    // revocation, an early rotation). A valid refresh token
                    // still recovers the session without a QR re-pair.
                    Log.w(TAG, "Access token rejected (HTTP 401). Trying the refresh token.");
                    SessionManager.refreshAccessToken(appContext, new SessionManager.TokenCallback() {
                        @Override
                        public void onToken(String refreshedToken) {
                            if (requestGeneration != generation) {
                                return;
                            }
                            fetch(appContext, requestGeneration, refreshedToken, true);
                        }

                        @Override
                        public void onFailure(boolean sessionExpired) {
                            if (requestGeneration != generation) {
                                return;
                            }
                            if (sessionExpired) {
                                SessionManager.clear(appContext);
                                finishError(requestGeneration, 401, "Session expired");
                            } else {
                                finishError(requestGeneration, 0, "Could not renew the session");
                            }
                        }
                    });
                    return;
                }

                if (response.code() == 401 || response.code() == 403) {
                    Log.w(TAG, "Camera list rejected (HTTP " + response.code() + "). Session is no longer valid.");
                    SessionManager.clear(appContext);
                    finishError(requestGeneration, 401, "Session expired");
                    return;
                }

                Log.e(TAG, "Camera list request failed: HTTP " + response.code());
                finishError(requestGeneration, response.code(), "HTTP " + response.code());
            }

            @Override
            public void onFailure(Call<List<Camera>> call, Throwable throwable) {
                if (requestGeneration != generation) {
                    return;
                }
                String message = throwable.getMessage() == null ? "Network error" : throwable.getMessage();
                Log.e(TAG, "Camera list network error: " + message);
                finishError(requestGeneration, 0, message);
            }
        });
    }

    private static void finishSuccess(int requestGeneration, List<Camera> fresh) {
        if (requestGeneration != generation) {
            return;
        }
        loading = false;
        List<Callback> callbacks = new ArrayList<>(pending);
        pending.clear();
        for (Callback callback : callbacks) {
            try {
                callback.onCameras(fresh);
            } catch (RuntimeException e) {
                Log.w(TAG, "Camera callback failed: " + e.getMessage());
            }
        }
    }

    private static void finishError(int requestGeneration, int httpCode, String message) {
        if (requestGeneration != generation) {
            return;
        }
        loading = false;
        List<Callback> callbacks = new ArrayList<>(pending);
        pending.clear();
        for (Callback callback : callbacks) {
            try {
                callback.onError(httpCode, message);
            } catch (RuntimeException e) {
                Log.w(TAG, "Camera callback failed: " + e.getMessage());
            }
        }
    }
}

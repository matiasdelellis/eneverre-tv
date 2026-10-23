package ar.com.delellis.eneverretv.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ar.com.delellis.eneverretv.api.model.AuthDeviceToken;
import ar.com.delellis.eneverretv.api.model.RefreshRequest;
import ar.com.delellis.eneverretv.api.model.RefreshResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Owns the device session stored in SharedPreferences.
 *
 * <p>The server issues a short-lived access token plus a long-lived refresh
 * token. The TV used to force a new QR pairing whenever the access token
 * expired; instead, this class renews it in place and rotates both secrets,
 * exactly like the phone app. Only when the refresh token is also gone does a
 * re-pairing become necessary.</p>
 */
public final class SessionManager {
    private static final String TAG = "SessionManager";

    private static final String PREFS = "auth";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EXPIRES_AT = "expire_at";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_REFRESH_EXPIRES_AT = "refresh_expires_at";

    // Renew slightly before the server would reject the access token, so a
    // request never races an expiry that happens between the check and the
    // call.
    private static final long EXPIRY_SKEW_SECONDS = 60L;

    private static final Object LOCK = new Object();
    private static boolean refreshing = false;
    private static int sessionGeneration = 0;
    private static final List<TokenCallback> waiting = new ArrayList<>();

    public interface TokenCallback {
        void onToken(String accessToken);

        /**
         * @param sessionExpired true when the session cannot be renewed and
         *                       the user must sign in again; false for a
         *                       transient network/server failure that left the
         *                       stored pair intact.
         */
        void onFailure(boolean sessionExpired);
    }

    private SessionManager() {
    }

    public static void save(Context context, AuthDeviceToken session) {
        if (session == null || session.getToken() == null || session.getToken().isEmpty()) {
            return;
        }
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(KEY_TOKEN, session.getToken())
                .putLong(KEY_EXPIRES_AT, session.getExpiresAt());
        if (session.getRefreshToken() != null && !session.getRefreshToken().isEmpty()) {
            editor.putString(KEY_REFRESH_TOKEN, session.getRefreshToken())
                    .putLong(KEY_REFRESH_EXPIRES_AT, session.getRefreshExpiresAt());
        } else {
            // Newer servers always send a refresh token. An older one that
            // does not means the old stored refresh belongs to another
            // session; keeping it would renew the wrong session.
            editor.remove(KEY_REFRESH_TOKEN).remove(KEY_REFRESH_EXPIRES_AT);
        }
        editor.apply();
    }

    private static void save(Context context, RefreshResponse session) {
        prefs(context).edit()
                .putString(KEY_TOKEN, session.getToken())
                .putLong(KEY_EXPIRES_AT, session.getExpiresAt())
                .putString(KEY_REFRESH_TOKEN, session.getRefreshToken())
                .putLong(KEY_REFRESH_EXPIRES_AT, session.getRefreshExpiresAt())
                .apply();
    }

    public static void clear(Context context) {
        clearPrefs(context);
        // Tell anything waiting on an in-flight refresh that the session is
        // gone instead of leaving its UI stuck on a spinner.
        deliver(null, true);
    }

    private static void clearPrefs(Context context) {
        prefs(context).edit().clear().apply();
        synchronized (LOCK) {
            sessionGeneration++;
        }
    }

    /** True when an access token is still valid or a refresh token can renew it. */
    public static boolean hasStoredSession(Context context) {
        SharedPreferences prefs = prefs(context);
        long now = nowSeconds();

        String access = prefs.getString(KEY_TOKEN, null);
        long accessExpiresAt = getLong(prefs, KEY_EXPIRES_AT, 0L);
        if (access != null && !access.isEmpty() && accessExpiresAt > now) {
            return true;
        }

        String refresh = prefs.getString(KEY_REFRESH_TOKEN, null);
        long refreshExpiresAt = getLong(prefs, KEY_REFRESH_EXPIRES_AT, 0L);
        return refresh != null && !refresh.isEmpty() && refreshExpiresAt > now;
    }

    /**
     * Good/bad access token for an API call. Refreshes proactively when the
     * access token is about to expire; the callback runs on the thread that
     * called this method (the main thread in every current caller).
     */
    public static void withAccessToken(Context context, TokenCallback callback) {
        if (callback == null) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        long now = nowSeconds();

        String access = prefs.getString(KEY_TOKEN, null);
        long accessExpiresAt = getLong(prefs, KEY_EXPIRES_AT, 0L);
        if (access != null && !access.isEmpty() && accessExpiresAt > now + EXPIRY_SKEW_SECONDS) {
            callback.onToken(access);
            return;
        }

        String refresh = prefs.getString(KEY_REFRESH_TOKEN, null);
        long refreshExpiresAt = getLong(prefs, KEY_REFRESH_EXPIRES_AT, 0L);
        if (refresh == null || refresh.isEmpty() || refreshExpiresAt <= now) {
            callback.onFailure(true);
            return;
        }

        refreshAccessToken(context, callback);
    }

    /**
     * Exchanges the stored refresh token for a new access/refresh pair. Calls
     * made while a refresh is in flight are collapsed into that one request.
     */
    public static void refreshAccessToken(Context context, TokenCallback callback) {
        if (callback == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        SharedPreferences prefs = prefs(appContext);
        long now = nowSeconds();

        final String refresh = prefs.getString(KEY_REFRESH_TOKEN, null);
        long refreshExpiresAt = getLong(prefs, KEY_REFRESH_EXPIRES_AT, 0L);
        if (refresh == null || refresh.isEmpty() || refreshExpiresAt <= now) {
            clearPrefs(appContext);
            callback.onFailure(true);
            return;
        }

        synchronized (LOCK) {
            if (refreshing) {
                waiting.add(callback);
                return;
            }
            refreshing = true;
            waiting.add(callback);
        }

        final int requestGeneration;
        synchronized (LOCK) {
            requestGeneration = sessionGeneration;
        }

        Log.d(TAG, "Refreshing the access token");
        Call<RefreshResponse> call;
        try {
            call = ApiClient.get().api().refresh(new RefreshRequest(refresh));
        } catch (IllegalStateException e) {
            // ApiClient not initialized yet: treat as a transient failure, not
            // a dead session.
            deliver(null, false);
            return;
        }

        call.enqueue(new Callback<RefreshResponse>() {
            @Override
            public void onResponse(Call<RefreshResponse> call, Response<RefreshResponse> response) {
                if (isStaleGeneration(requestGeneration)) {
                    return;
                }
                RefreshResponse body = response.body();
                if (response.isSuccessful()
                        && body != null
                        && body.getToken() != null
                        && !body.getToken().isEmpty()
                        && body.getRefreshToken() != null
                        && !body.getRefreshToken().isEmpty()) {
                    save(appContext, body);
                    deliver(body.getToken(), false);
                    return;
                }

                if (response.code() == 401 || response.code() == 403) {
                    Log.w(TAG, "Refresh token rejected (HTTP " + response.code() + "). Clearing the session.");
                    // Only clear the stored pair here; deliver() owns the
                    // waiting list and must still notify every caller that was
                    // collapsed into this refresh.
                    clearPrefs(appContext);
                    deliver(null, true);
                    return;
                }

                Log.w(TAG, "Token refresh failed: HTTP " + response.code());
                deliver(null, false);
            }

            @Override
            public void onFailure(Call<RefreshResponse> call, Throwable throwable) {
                if (isStaleGeneration(requestGeneration)) {
                    return;
                }
                Log.w(TAG, "Token refresh network error: " + throwable.getMessage());
                deliver(null, false);
            }
        });
    }

    private static boolean isStaleGeneration(int requestGeneration) {
        synchronized (LOCK) {
            return requestGeneration != sessionGeneration;
        }
    }

    private static void deliver(String accessToken, boolean sessionExpired) {
        List<TokenCallback> callbacks;
        synchronized (LOCK) {
            refreshing = false;
            callbacks = new ArrayList<>(waiting);
            waiting.clear();
        }

        for (TokenCallback callback : callbacks) {
            try {
                if (accessToken != null) {
                    callback.onToken(accessToken);
                } else {
                    callback.onFailure(sessionExpired);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Session callback failed: " + e.getMessage());
            }
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Reads a long without assuming the stored type: older builds wrote
     * expire_at with putInt, and SharedPreferences throws on getLong for those.
     */
    private static long getLong(SharedPreferences prefs, String key, long fallback) {
        Object value = prefs.getAll().get(key);
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}

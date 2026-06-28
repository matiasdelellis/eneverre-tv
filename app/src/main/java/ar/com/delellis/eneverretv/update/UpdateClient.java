package ar.com.delellis.eneverretv.update;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

import ar.com.delellis.eneverretv.BuildConfig;
import ar.com.delellis.eneverretv.api.ApiClient;
import ar.com.delellis.eneverretv.api.model.UpdateManifest;
import retrofit2.Call;
import retrofit2.Response;

public class UpdateClient {
    private static final String TAG = "UpdateClient";

    private static final String PREFS = "updates";
    private static final String KEY_SKIPPED_VERSION = "skipped_version";

    private static final AtomicBoolean CHECK_IN_FLIGHT = new AtomicBoolean(false);

    public interface Callback {
        void onUpdateAvailable(UpdateManifest manifest);
        void onUpToDate();
    }

    public static void checkOnce(@NonNull Context context, @NonNull Callback callback) {
        if (!CHECK_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        check(context, callback);
    }

    private static void check(@NonNull Context context, @NonNull Callback callback) {
        Call<UpdateManifest> call = ApiClient.get().api().tvUpdate();
        call.enqueue(new retrofit2.Callback<UpdateManifest>() {
            @Override
            public void onResponse(@NonNull Call<UpdateManifest> call,
                                   @NonNull Response<UpdateManifest> response) {
                CHECK_IN_FLIGHT.set(false);

                int code = response.code();
                if (code == 204) {
                    Log.d(TAG, "No update available (204).");
                    callback.onUpToDate();
                    return;
                }
                if (code == 503) {
                    Log.d(TAG, "Update feature disabled on server (503). Skipping silently.");
                    callback.onUpToDate();
                    return;
                }
                if (code < 200 || code >= 300 || response.body() == null) {
                    Log.w(TAG, "Update check returned HTTP " + code + ". Treating as up-to-date.");
                    callback.onUpToDate();
                    return;
                }

                UpdateManifest manifest = response.body();
                if (manifest.getVersionCode() <= BuildConfig.VERSION_CODE) {
                    Log.d(TAG, "Manifest versionCode " + manifest.getVersionCode()
                            + " is not greater than installed " + BuildConfig.VERSION_CODE + ".");
                    callback.onUpToDate();
                    return;
                }

                if (isSkipped(context, manifest)) {
                    Log.d(TAG, "Version " + manifest.getVersionName() + " is in the skip list.");
                    callback.onUpToDate();
                    return;
                }

                Log.d(TAG, "Update available: " + manifest.getVersionName()
                        + " (code " + manifest.getVersionCode() + ").");
                callback.onUpdateAvailable(manifest);
            }

            @Override
            public void onFailure(@NonNull Call<UpdateManifest> call, @NonNull Throwable t) {
                CHECK_IN_FLIGHT.set(false);
                Log.w(TAG, "Update check failed: " + t.getMessage());
                callback.onUpToDate();
            }
        });
    }

    public static void skip(@NonNull Context context, @NonNull UpdateManifest manifest) {
        if (manifest.getVersionName() == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SKIPPED_VERSION, manifest.getVersionName())
                .apply();
    }

    public static void clearSkip(@NonNull Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SKIPPED_VERSION)
                .apply();
    }

    public static void install(@NonNull Context context, @NonNull UpdateManifest manifest) {
        UpdateDownloadWorker.enqueue(context, manifest);
    }

    private static boolean isSkipped(@NonNull Context context, @NonNull UpdateManifest manifest) {
        String skipped = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SKIPPED_VERSION, null);
        if (skipped == null || manifest.getVersionName() == null) {
            return false;
        }
        return skipped.equals(manifest.getVersionName());
    }
}

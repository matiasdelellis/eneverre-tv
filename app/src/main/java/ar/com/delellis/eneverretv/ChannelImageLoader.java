package ar.com.delellis.eneverretv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import androidx.collection.LruCache;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ar.com.delellis.eneverretv.api.SessionManager;
import ar.com.delellis.eneverretv.api.model.Camera;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChannelImageLoader {
    private static final String TAG = "ChannelImageLoader";

    public static final String BANNER_CAMERA = bannerBaseUrl() + "img/camera-banner.png";
    public static final String BANNER_GRID = bannerBaseUrl() + "img/grid-banner.png";

    public static String bannerBaseUrl() {
        String host = BuildConfig.ENEVERRE_HOST;
        return host.endsWith("/") ? host : host + "/";
    }

    private static final OkHttpClient client = new OkHttpClient();
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> cache = new LruCache<>(8 * 1024 * 1024);

    public interface Callback {
        void onLoaded(Drawable drawable);
    }

    public static Drawable getDrawable(Context context, String url) {
        Bitmap bitmap = cache.get(url);
        if (bitmap != null) {
            return new BitmapDrawable(context.getResources(), bitmap);
        }
        return null;
    }

    public static void loadInto(Context context, String url, ImageView target, Callback callback) {
        Bitmap cached = cache.get(url);
        if (cached != null) {
            applyBitmap(context, target, url, cached, callback);
            return;
        }

        target.setTag(url);
        executor.execute(() -> {
            try {
                Request request = new Request.Builder().url(url).build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        Log.w(TAG, "Banner HTTP " + response.code() + " for " + url);
                        return;
                    }
                    byte[] bytes = response.body().bytes();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap == null) {
                        Log.w(TAG, "Could not decode " + url);
                        return;
                    }
                    cache.put(url, bitmap);
                    mainHandler.post(() -> applyBitmap(context, target, url, bitmap, callback));
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to load " + url + ": " + e.getMessage());
            }
        });
    }

    /**
     * Loads the camera's current still from GET camera/{id}/thumbnail. The
     * endpoint authenticates with the device's Bearer token (and can answer
     * 404/409/502 when the camera has no snapshot or is paused), so the static
     * card banner stays as the fallback.
     */
    public static void loadThumbnail(Context context, Camera camera, ImageView target, Callback callback) {
        if (camera == null || camera.getId() == null) {
            return;
        }
        final String url = thumbnailUrl(camera);
        Bitmap cached = cache.get(url);
        if (cached != null) {
            applyBitmap(context, target, url, cached, callback);
            return;
        }

        target.setTag(url);
        SessionManager.withAccessToken(context.getApplicationContext(), new SessionManager.TokenCallback() {
            @Override
            public void onToken(String accessToken) {
                executor.execute(() -> {
                    try {
                        Request request = new Request.Builder()
                                .url(url)
                                .header("Authorization", "Bearer " + accessToken)
                                .build();
                        try (Response response = client.newCall(request).execute()) {
                            if (!response.isSuccessful() || response.body() == null) {
                                Log.d(TAG, "Thumbnail HTTP " + response.code() + " for " + camera.getId());
                                return;
                            }
                            byte[] bytes = response.body().bytes();
                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bitmap == null) {
                                Log.w(TAG, "Could not decode thumbnail for " + camera.getId());
                                return;
                            }
                            cache.put(url, bitmap);
                            mainHandler.post(() -> applyBitmap(context, target, url, bitmap, callback));
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to load thumbnail for " + camera.getId() + ": " + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(boolean sessionExpired) {
                // Keep the banner placeholder; the screen itself handles an
                // expired session on its next API call.
            }
        });
    }

    private static String thumbnailUrl(Camera camera) {
        return bannerBaseUrl() + "api/camera/" + Uri.encode(camera.getId()) + "/thumbnail";
    }

    private static void applyBitmap(Context context, ImageView target, String url,
                                    Bitmap bitmap, Callback callback) {
        if (!url.equals(target.getTag())) {
            return;
        }
        Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        target.setImageDrawable(drawable);
        if (callback != null) {
            callback.onLoaded(drawable);
        }
    }
}

package ar.com.delellis.eneverretv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import androidx.collection.LruCache;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
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

    private static void applyBitmap(Context context, ImageView target, String url,
                                    Bitmap bitmap, Callback callback) {
        Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        if (url.equals(target.getTag())) {
            target.setImageDrawable(drawable);
        }
        if (callback != null) {
            callback.onLoaded(drawable);
        }
    }
}

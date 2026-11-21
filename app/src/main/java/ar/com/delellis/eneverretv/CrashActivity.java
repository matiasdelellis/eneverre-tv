package ar.com.delellis.eneverretv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Separate-process screen shown after an uncaught exception. Uploads the
 * report to this deployment's own server (unlisted, random id, 24h TTL) and
 * shows a QR code so the phone can open it without ADB or a public pastebin.
 */
public class CrashActivity extends Activity {
    public static final String EXTRA_REPORT_FILE = "report_file";
    private static final String TAG = "CrashActivity";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private TextView status;
    private TextView urlView;
    private ImageView qrView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        status = findViewById(R.id.crash_status);
        urlView = findViewById(R.id.crash_url);
        qrView = findViewById(R.id.crash_qr);
        TextView reportView = findViewById(R.id.crash_report);

        String path = getIntent().getStringExtra(EXTRA_REPORT_FILE);
        String report = readReport(path);
        reportView.setText(report);
        upload(report);
    }

    private String readReport(String path) {
        if (path == null) {
            return "No se pudo leer el reporte.";
        }
        File file = new File(path);
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(data, 0, read, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Could not read crash report", e);
            return "No se pudo leer el reporte.";
        }
    }

    private void upload(String report) {
        String base = BuildConfig.ENEVERRE_HOST;
        if (!base.endsWith("/")) {
            base += "/";
        }
        final String baseUrl = base;

        String body;
        try {
            JSONObject json = new JSONObject();
            json.put("report", report);
            json.put("version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
            json.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            json.put("android", Build.VERSION.RELEASE);
            body = json.toString();
        } catch (Exception e) {
            showFailure();
            return;
        }

        Request request = new Request.Builder()
                .url(baseUrl + "api/crash")
                .post(RequestBody.create(body, JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Crash upload failed: " + e.getMessage());
                runOnUiThread(CrashActivity.this::showFailure);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        runOnUiThread(CrashActivity.this::showFailure);
                        return;
                    }
                    JSONObject json = new JSONObject(response.body().string());
                    String id = json.optString("id", "");
                    if (id.isEmpty()) {
                        runOnUiThread(CrashActivity.this::showFailure);
                        return;
                    }
                    String url = baseUrl + "api/crash/" + id;
                    runOnUiThread(() -> showReportUrl(url));
                } catch (Exception e) {
                    Log.w(TAG, "Could not parse crash upload response", e);
                    runOnUiThread(CrashActivity.this::showFailure);
                }
            }
        });
    }

    private void showReportUrl(String url) {
        status.setText(R.string.crash_sent);
        urlView.setText(url);
        urlView.setVisibility(TextView.VISIBLE);

        Bitmap bitmap = qrBitmap(url);
        if (bitmap != null) {
            qrView.setImageBitmap(bitmap);
            qrView.setVisibility(ImageView.VISIBLE);
        }
    }

    private void showFailure() {
        status.setText(R.string.crash_failed);
    }

    private Bitmap qrBitmap(String content) {
        try {
            final int size = 560;
            BitMatrix matrix = new MultiFormatWriter().encode(
                    content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                int row = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[row + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Could not generate crash QR", e);
            return null;
        }
    }
}

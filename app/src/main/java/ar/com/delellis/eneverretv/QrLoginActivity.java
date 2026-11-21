package ar.com.delellis.eneverretv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import ar.com.delellis.eneverretv.api.ApiClient;
import ar.com.delellis.eneverretv.api.PollingManager;
import ar.com.delellis.eneverretv.api.SessionManager;
import ar.com.delellis.eneverretv.api.model.AuthDevice;
import ar.com.delellis.eneverretv.api.model.AuthDeviceToken;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QrLoginActivity extends AppCompatActivity {
    private static final String TAG = "QrLoginActivity";

    private ImageView qrImage;
    private TextView txtUserCode;
    private View qrProgress;
    private View qrError;
    private View qrRetry;
    private View qrCodeContainer;

    private PollingManager pollingManager = new PollingManager();

    private AuthDevice authDevice = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qr_login);

        ApiClient.init(BuildConfig.ENEVERRE_HOST + "api/");

        qrImage = findViewById(R.id.qrImage);
        txtUserCode = findViewById(R.id.txtUserCode);
        qrProgress = findViewById(R.id.qrProgress);
        qrError = findViewById(R.id.qrError);
        qrRetry = findViewById(R.id.qrRetry);
        qrCodeContainer = findViewById(R.id.qr_code_container);
        qrRetry.setOnClickListener(v -> requestCode());

        showQrLoading();
        requestCode();
    }

    private void requestCode() {
        showQrLoading();
        Call<AuthDevice> authDeviceCall = ApiClient.get().api().authDevice(DeviceInfo.getDeviceName());
        authDeviceCall.enqueue(new Callback<AuthDevice>() {
            @Override
            public void onResponse(Call<AuthDevice> call, Response<AuthDevice> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "requestCode unsuccessful: HTTP " + response.code());
                    showQrError();
                    return;
                }
                authDevice = response.body();
                Log.d(TAG, "requestCode got user_code=" + authDevice.getUserCode());
                if (updateQR(authDevice.getUserCode())) {
                    showQrReady();
                    initPolling();
                } else {
                    showQrError();
                }
            }
            @Override
            public void onFailure(Call<AuthDevice> call, Throwable throwable) {
                Log.e(TAG, "requestCode error: " + throwable.getMessage());
                showQrError();
            }
        });
    }

    private boolean updateQR(String user_code) {
        txtUserCode.setText(user_code);

        // Keep the full configured base URL (including any reverse-proxy
        // subpath); rebuilding it from scheme+authority dropped "/nvr/" and
        // sent the phone to the wrong page.
        String base = BuildConfig.ENEVERRE_HOST;
        if (!base.endsWith("/")) {
            base += "/";
        }
        String qr_code = base + "?usercode=" + Uri.encode(user_code);
        try {
            final int size = 400;
            BitMatrix matrix = new MultiFormatWriter().encode(
                    qr_code,
                    BarcodeFormat.QR_CODE,
                    size,
                    size
            );

            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                int row = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[row + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            bmp.setPixels(pixels, 0, size, 0, 0, size, size);
            qrImage.setImageBitmap(bmp);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Could not generate the pairing QR", e);
            return false;
        }
    }

    private void showQrLoading() {
        if (qrProgress == null) {
            return;
        }
        qrProgress.setVisibility(View.VISIBLE);
        qrError.setVisibility(View.GONE);
        qrRetry.setVisibility(View.GONE);
        qrImage.setVisibility(View.GONE);
        qrCodeContainer.setVisibility(View.GONE);
    }

    private void showQrReady() {
        qrProgress.setVisibility(View.GONE);
        qrError.setVisibility(View.GONE);
        qrRetry.setVisibility(View.GONE);
        qrImage.setVisibility(View.VISIBLE);
        qrCodeContainer.setVisibility(View.VISIBLE);
    }

    private void showQrError() {
        qrProgress.setVisibility(View.GONE);
        qrImage.setVisibility(View.GONE);
        qrError.setVisibility(View.VISIBLE);
        qrRetry.setVisibility(View.VISIBLE);
        qrCodeContainer.setVisibility(View.GONE);
        qrRetry.requestFocus();
    }

    private void initPolling() {
        pollingManager.start(callback -> {
            loginVerify(authDevice.getDeviceCode(), new PollingManager.Callback() {
                @Override
                public void onSuccess(boolean shouldContinue) {
                    callback.onSuccess(shouldContinue);
                }
                @Override
                public void onError() {
                    callback.onError();
                }
            });
        });
    }

    private void loginVerify(String device_code, PollingManager.Callback pollingCallback) {
        Log.d(TAG, "Checking code status");

        Call<AuthDeviceToken> call = ApiClient.get().api().authDeviceToken(device_code);
        call.enqueue(new Callback<AuthDeviceToken>() {
            @Override
            public void onResponse(Call<AuthDeviceToken> call, Response<AuthDeviceToken> response) {
                Log.d(TAG, "loginVerify response: HTTP " + response.code() + " (successful=" + response.isSuccessful() + ")");

                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "loginVerify unsuccessful or empty body. code=" + response.code() + ", body=" + (response.body() != null));
                    if (response.code() == 404) {
                        // The device code vanished server-side (expired row
                        // cleanup, server restart with a fresh DB, ...): a new
                        // code is the only way forward.
                        Log.d(TAG, "Device code unknown. Requesting a new one.");
                        pollingManager.stop();
                        requestCode();
                        return;
                    }
                    if (response.code() == 403) {
                        Log.w(TAG, "Device login refused (HTTP 403). Stopping polling.");
                        pollingManager.stop();
                        Toast.makeText(QrLoginActivity.this,
                                R.string.error_connecting_to_the_api, Toast.LENGTH_LONG).show();
                        return;
                    }
                    pollingCallback.onError();
                    return;
                }

                AuthDeviceToken authDeviceToken = response.body();

                String status = authDeviceToken.getStatus();
                Log.d(TAG, "loginVerify status: " + status);
                if ("approved".equals(status)) {
                    Log.d(TAG, "loginVerify approved. token=" + (authDeviceToken.getToken() != null ? "present" : "null") + ", expire_at=" + authDeviceToken.getExpiresAt());
                    SessionManager.save(QrLoginActivity.this, authDeviceToken);

                    onLoginSuccess();
                    pollingCallback.onSuccess(false);
                } else if ("expired".equals(status)) {
                    Log.d(TAG, "loginVerify expired, requesting a new code.");
                    pollingCallback.onSuccess(false);
                    pollingManager.stop();
                    requestCode();
                } else {
                    Log.d(TAG, "loginVerify pending (status=" + status + "), continue polling.");
                    pollingCallback.onSuccess(true);
                }
            }

            @Override
            public void onFailure(Call<AuthDeviceToken> call, Throwable throwable) {
                Log.e(TAG, "Polling error: " + throwable.getMessage(), throwable);
                pollingCallback.onError();
            }
        });
    }

    private void onLoginSuccess() {
        startActivity(new Intent(this, LaunchActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        pollingManager.stop();
        super.onDestroy();
    }
}
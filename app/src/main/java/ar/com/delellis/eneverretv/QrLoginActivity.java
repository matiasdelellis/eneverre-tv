package ar.com.delellis.eneverretv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import ar.com.delellis.eneverretv.api.ApiClient;
import ar.com.delellis.eneverretv.api.PollingManager;
import ar.com.delellis.eneverretv.api.model.AuthDevice;
import ar.com.delellis.eneverretv.api.model.AuthDeviceToken;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QrLoginActivity extends AppCompatActivity {
    private static final String TAG = "QrLoginActivity";

    private ImageView qrImage;
    private TextView txtUserCode;

    private PollingManager pollingManager = new PollingManager();

    private AuthDevice authDevice = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qr_login);

        qrImage = findViewById(R.id.qrImage);
        txtUserCode = findViewById(R.id.txtUserCode);

        requestCode();
    }

    private void requestCode() {
        Call<AuthDevice> authDeviceCall = ApiClient.get().api().authDevice(DeviceInfo.getDeviceName());
        authDeviceCall.enqueue(new Callback<AuthDevice>() {
            @Override
            public void onResponse(Call<AuthDevice> call, Response<AuthDevice> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "requestCode unsuccessful: HTTP " + response.code());
                    Toast.makeText(QrLoginActivity.this, R.string.error_connecting_to_the_api, Toast.LENGTH_LONG).show();
                    return;
                }
                authDevice = response.body();
                Log.d(TAG, "requestCode got user_code=" + authDevice.getUserCode());
                updateQR(authDevice.getUserCode());

                initPolling();
            }
            @Override
            public void onFailure(Call<AuthDevice> call, Throwable throwable) {
                Log.e(TAG, "requestCode error: " + throwable.getMessage());
                Toast.makeText(QrLoginActivity.this, R.string.error_connecting_to_the_api, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateQR(String user_code) {
        txtUserCode.setText(user_code);

        Uri apiHost = Uri.parse(BuildConfig.API_HOST);
        String qr_code = apiHost.getScheme() + "://" + apiHost.getAuthority()
                + "/?usercode=" + user_code;
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    qr_code,
                    BarcodeFormat.QR_CODE,
                    400,
                    400
            );

            Bitmap bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.RGB_565);

            for (int x = 0; x < 400; x++) {
                for (int y = 0; y < 400; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            qrImage.setImageBitmap(bmp);

        } catch (Exception e) {
            e.printStackTrace();
        }
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
        Log.d(TAG, "Checking code status: " + device_code);

        Call<AuthDeviceToken> call = ApiClient.get().api().authDeviceToken(device_code);
        call.enqueue(new Callback<AuthDeviceToken>() {
            @Override
            public void onResponse(Call<AuthDeviceToken> call, Response<AuthDeviceToken> response) {
                Log.d(TAG, "loginVerify response: HTTP " + response.code() + " (successful=" + response.isSuccessful() + ")");

                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "loginVerify unsuccessful or empty body. code=" + response.code() + ", body=" + (response.body() != null));
                    pollingCallback.onError();
                    return;
                }

                AuthDeviceToken authDeviceToken = response.body();

                String status = authDeviceToken.getStatus();
                Log.d(TAG, "loginVerify status: " + status);
                if ("approved".equals(status)) {
                    Log.d(TAG, "loginVerify approved. token=" + (authDeviceToken.getToken() != null ? "present" : "null") + ", expire_at=" + authDeviceToken.getExpiresAt());
                    SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                    prefs.edit()
                            .putString("token", authDeviceToken.getToken())
                            .putInt("expire_at", authDeviceToken.getExpiresAt())
                            .apply();

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
                Log.e(TAG, "Polling error for device_code=" + device_code + ": " + throwable.getMessage(), throwable);
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
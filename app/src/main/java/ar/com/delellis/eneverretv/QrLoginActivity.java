package ar.com.delellis.eneverretv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import ar.com.delellis.eneverretv.api.ApiClient;
import ar.com.delellis.eneverretv.api.model.AuthDevice;
import ar.com.delellis.eneverretv.api.model.AuthDeviceToken;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QrLoginActivity extends AppCompatActivity {
    private static final String TAG = "QrLoginActivity";

    private ImageView qrImage;
    private TextView txtUserCode;

    private Handler handler = new Handler();

    private boolean authorized = false;

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
        Call<AuthDevice> authDeviceCall = ApiClient.get().api().authDevice();
        authDeviceCall.enqueue(new Callback<AuthDevice>() {
            @Override
            public void onResponse(Call<AuthDevice> call, Response<AuthDevice> response) {
                // TODO: Is that always a valid answer?
                authDevice = response.body();
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

        String qr_code = "eneverre://login?code=" + user_code;
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
        handler.postDelayed(pollRunnable, 3000);
    }

    private Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!authorized) {
                loginVerify(authDevice.getDeviceCode());
                handler.postDelayed(this, 3000);
            }
        }
    };

    private void loginVerify(String device_code) {
        Log.d(TAG, "Checking code status: " + device_code);

        Call<AuthDeviceToken> authDeviceTokenCall = ApiClient.get().api().authDeviceToken(device_code);
        authDeviceTokenCall.enqueue(new Callback<AuthDeviceToken>() {
            @Override
            public void onResponse(Call<AuthDeviceToken> call, Response<AuthDeviceToken> response) {
                AuthDeviceToken authDeviceToken = response.body();

                String status = authDeviceToken.getStatus();
                if ("approved".equals(status)) {
                    SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                    prefs.edit()
                            .putString("token", authDeviceToken.getToken())
                            .putInt("expire_at", authDeviceToken.getExpiresAt())
                            .apply();

                    onLoginSuccess();
                } else if ("expired".equals(status)) {
                    requestCode();
                }
            }
            @Override
            public void onFailure(Call<AuthDeviceToken> call, Throwable throwable) {
                Toast.makeText(QrLoginActivity.this, R.string.error_connecting_to_the_api, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onLoginSuccess() {
        authorized = true;
        startActivity(new Intent(this, LaunchActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(pollRunnable);
        super.onDestroy();
    }
}
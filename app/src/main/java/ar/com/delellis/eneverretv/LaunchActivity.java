package ar.com.delellis.eneverretv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import java.io.Serializable;
import java.util.List;

import ar.com.delellis.eneverretv.api.ApiClient;
import ar.com.delellis.eneverretv.api.model.Camera;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class LaunchActivity extends AppCompatActivity {
    private static final String TAG = "LaunchActivity";

    private boolean ready = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setKeepOnScreenCondition(() -> !ready);

        super.onCreate(savedInstanceState);
        setContentView(new FrameLayout(this));

        ApiClient.init(BuildConfig.API_HOST);

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        String token = prefs.getString("token", null);
        int expire_at = prefs.getInt("expire_at", -1);

        // TODO: check expire_at
        if (token != null && expire_at > 0) {
            Log.d(TAG, "Already logged in. Searching for cameras");
            loadCameras(token);
        } else {
            Log.d(TAG, "Without logging in. Going to the login screen.");
            goToLogin();
        }
    }

    private void loadCameras(String token) {
        Call<List<Camera>> camerasCall = ApiClient.get().api().cameras("Bearer " + token);
        camerasCall.enqueue(new Callback<List<Camera>>() {
            @Override
            public void onResponse(Call<List<Camera>> call, Response<List<Camera>> response) {
                List<Camera> cameras = response.body();
                Log.d(TAG, "We found the " + cameras.size() + " cameras. Going to view them.");
                ready = true;
                goToLive(cameras);
            }
            @Override
            public void onFailure(Call<List<Camera>> call, Throwable throwable) {
                Log.d(TAG, "loadCameras failure: " + throwable.getMessage());
                Toast.makeText(LaunchActivity.this, R.string.error_connecting_to_the_api, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goToLive(List<Camera> cameras) {
        Intent intent = new Intent(LaunchActivity.this, LiveActivity.class);
        intent.putExtra(LiveActivity.RAW_CAMERAS_LIST_DATA, (Serializable) cameras);

        String requestedCameraId = getIntent().getStringExtra("camera_id");
        if (requestedCameraId != null) {
            intent.putExtra(LiveActivity.CURRENT_CAMERA_ID, requestedCameraId);
        }

        startActivity(intent);
        finish();
    }

    private void goToLogin() {
        Intent intent = new Intent(LaunchActivity.this, QrLoginActivity.class);
        startActivity(intent);
        finish();
    }
}
package ar.com.delellis.eneverretv;

import android.content.Intent;
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

        ApiClient.init(BuildConfig.API_HOST, BuildConfig.API_USER, BuildConfig.API_PASS);

        loadCameras();
    }

    private void loadCameras() {
        Call<List<Camera>> camerasCall = ApiClient.get().api().cameras();
        camerasCall.enqueue(new Callback<List<Camera>>() {
            @Override
            public void onResponse(Call<List<Camera>> call, Response<List<Camera>> response) {
                List<Camera> cameras = response.body();
                Log.e(TAG, "onResponse " + cameras.size());
                ready = true;

                goToLive(cameras);
            }
            @Override
            public void onFailure(Call<List<Camera>> call, Throwable throwable) {
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
}
package ar.com.delellis.eneverretv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import java.io.Serializable;
import java.util.List;

import ar.com.delellis.eneverretv.api.ApiClient;
import ar.com.delellis.eneverretv.api.model.Camera;
import ar.com.delellis.eneverretv.api.model.UpdateManifest;
import ar.com.delellis.eneverretv.update.UpdateClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class LaunchActivity extends AppCompatActivity {
    private static final String TAG = "LaunchActivity";

    private boolean ready = false;
    private boolean updateDialogShown = false;
    private AlertDialog updateDialog = null;

    private String cachedToken = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setKeepOnScreenCondition(() -> !ready);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        Button retryButton = findViewById(R.id.launch_retry);
        retryButton.setOnClickListener(v -> {
            if (cachedToken != null) {
                ready = false;
                showError(false);
                loadCameras(cachedToken);
            } else {
                goToLogin();
            }
        });
        Button reloginButton = findViewById(R.id.launch_relogin);
        reloginButton.setOnClickListener(v -> {
            clearSession();
            goToLogin();
        });

        showError(false);

        ApiClient.init(BuildConfig.ENEVERRE_HOST + "api/");

        UpdateClient.checkOnce(this, new UpdateClient.Callback() {
            @Override
            public void onUpdateAvailable(UpdateManifest manifest, UpdateManifest.Build build) {
                if (!isFinishing() && !isDestroyed()) {
                    showUpdateDialog(manifest, build);
                }
            }

            @Override
            public void onUpToDate() {
            }
        });

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        String token = prefs.getString("token", null);
        int expire_at = prefs.getInt("expire_at", -1);

        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (token != null && expire_at > nowSeconds) {
            Log.d(TAG, "Already logged in. Searching for cameras");
            cachedToken = token;
            loadCameras(token);
        } else {
            if (token != null) {
                Log.d(TAG, "Token expired (expire_at=" + expire_at + ", now=" + nowSeconds + "). Clearing session.");
                clearSession();
            } else {
                Log.d(TAG, "Without logging in. Going to the login screen.");
            }
            goToLogin();
        }
    }

    private void showError(boolean show) {
        findViewById(R.id.launch_message).setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(R.id.launch_retry).setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(R.id.launch_relogin).setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showUpdateDialog(UpdateManifest manifest, UpdateManifest.Build build) {
        if (updateDialogShown) {
            return;
        }
        updateDialogShown = true;

        String title = getString(R.string.update_dialog_title, manifest.getVersionName());
        String body = manifest.getReleaseNotes();
        boolean mandatory = manifest.isMandatory();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setCancelable(!mandatory)
                .setPositiveButton(R.string.update_action_install, (d, w) -> {
                    UpdateClient.install(LaunchActivity.this, build);
                });

        if (body != null && !body.isEmpty()) {
            builder.setMessage(body);
        }

        if (!mandatory) {
            builder.setNegativeButton(R.string.update_action_later, (d, w) -> d.dismiss());
            builder.setNeutralButton(R.string.update_action_skip, (d, w) -> {
                UpdateClient.skip(LaunchActivity.this, manifest);
            });
        }

        updateDialog = builder.create();
        updateDialog.setOnDismissListener(d -> {
            updateDialogShown = false;
            updateDialog = null;
        });
        updateDialog.show();
    }

    @Override
    protected void onDestroy() {
        if (updateDialog != null && updateDialog.isShowing()) {
            updateDialog.dismiss();
            updateDialog = null;
        }
        super.onDestroy();
    }

    private void clearSession() {
        getSharedPreferences("auth", MODE_PRIVATE).edit().clear().apply();
    }

    private void loadCameras(String token) {
        Call<List<Camera>> camerasCall = ApiClient.get().api().cameras("Bearer " + token);
        camerasCall.enqueue(new Callback<List<Camera>>() {
            @Override
            public void onResponse(Call<List<Camera>> call, Response<List<Camera>> response) {
                if (response.code() == 401) {
                    Log.w(TAG, "Token rejected (HTTP 401). Clearing session and going to login.");
                    ready = true;
                    clearSession();
                    goToLogin();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "loadCameras unsuccessful: HTTP " + response.code());
                    ready = true;
                    Toast.makeText(LaunchActivity.this, R.string.error_connecting_to_the_api, Toast.LENGTH_LONG).show();
                    return;
                }
                List<Camera> cameras = response.body();
                Log.d(TAG, "We found the " + cameras.size() + " cameras. Going to view them.");
                ready = true;
                goToLive(cameras);
            }
            @Override
            public void onFailure(Call<List<Camera>> call, Throwable throwable) {
                Log.d(TAG, "loadCameras failure: " + throwable.getMessage());
                ready = true;
                if (!isFinishing() && !isDestroyed()) {
                    TextView message = findViewById(R.id.launch_message);
                    if (message != null) {
                        message.setText(getString(R.string.error_connecting_to_the_api)
                                + "\n(" + throwable.getMessage() + ")");
                    }
                    showError(true);
                }
            }
        });
    }

    private void goToLive(List<Camera> cameras) {
        TvChannelManager.publish(LaunchActivity.this, cameras);

        String requestedCameraId = getIntent().getStringExtra("camera_id");
        if (requestedCameraId != null) {
            Intent intent = new Intent(LaunchActivity.this, LiveActivity.class);
            intent.putExtra(LiveActivity.RAW_CAMERAS_LIST_DATA, (Serializable) cameras);
            intent.putExtra(LiveActivity.CURRENT_CAMERA_ID, requestedCameraId);
            startActivity(intent);
        } else {
            Intent intent = new Intent(LaunchActivity.this, LocationsActivity.class);
            intent.putExtra(LocationsActivity.RAW_CAMERAS_LIST_DATA, (Serializable) cameras);
            startActivity(intent);
        }
        finish();
    }

    private void goToLogin() {
        Intent intent = new Intent(LaunchActivity.this, QrLoginActivity.class);
        startActivity(intent);
        finish();
    }
}
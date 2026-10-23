package ar.com.delellis.eneverretv;

import android.content.Intent;
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
import ar.com.delellis.eneverretv.api.CameraRepository;
import ar.com.delellis.eneverretv.api.SessionManager;
import ar.com.delellis.eneverretv.api.model.Camera;
import ar.com.delellis.eneverretv.api.model.UpdateManifest;
import ar.com.delellis.eneverretv.update.UpdateClient;


public class LaunchActivity extends AppCompatActivity {
    private static final String TAG = "LaunchActivity";

    private boolean ready = false;
    private boolean updateDialogShown = false;
    private AlertDialog updateDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setKeepOnScreenCondition(() -> !ready);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        Button retryButton = findViewById(R.id.launch_retry);
        retryButton.setOnClickListener(v -> {
            if (SessionManager.hasStoredSession(this)) {
                loadCameras();
            } else {
                goToLogin();
            }
        });
        Button reloginButton = findViewById(R.id.launch_relogin);
        reloginButton.setOnClickListener(v -> {
            signOut();
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

        if (SessionManager.hasStoredSession(this)) {
            Log.d(TAG, "Stored session found. Loading cameras.");
            loadCameras();
        } else {
            Log.d(TAG, "No usable session. Going to the login screen.");
            ready = true;
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

    private void signOut() {
        SessionManager.clear(this);
        CameraRepository.clear();
    }

    private void loadCameras() {
        ready = false;
        showError(false);

        CameraRepository.refresh(this, new CameraRepository.Callback() {
            @Override
            public void onCameras(List<Camera> cameras) {
                ready = true;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Log.d(TAG, "We found the " + cameras.size() + " cameras. Going to view them.");
                goToLive(cameras);
            }

            @Override
            public void onError(int httpCode, String message) {
                ready = true;
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (httpCode == 401) {
                    Log.w(TAG, "Session rejected. Clearing it and going to login.");
                    signOut();
                    goToLogin();
                    return;
                }

                Log.e(TAG, "loadCameras failed (HTTP " + httpCode + "): " + message);
                Toast.makeText(LaunchActivity.this, R.string.error_connecting_to_the_api, Toast.LENGTH_LONG).show();
                TextView messageView = findViewById(R.id.launch_message);
                if (messageView != null) {
                    String detail = message == null || message.isEmpty() ? "" : "\n(" + message + ")";
                    messageView.setText(getString(R.string.error_connecting_to_the_api) + detail);
                }
                showError(true);
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
        ready = true;
        Intent intent = new Intent(LaunchActivity.this, QrLoginActivity.class);
        startActivity(intent);
        finish();
    }
}

package ar.com.delellis.eneverretv.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import ar.com.delellis.eneverretv.R;
import ar.com.delellis.eneverretv.api.model.UpdateManifest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class UpdateDownloadWorker extends Worker {
    public static final String KEY_MANIFEST = "manifest";
    public static final String KEY_ERROR = "error";
    public static final String ERROR_INTEGRITY = "integrity";
    public static final String ERROR_DOWNLOAD = "download";
    public static final String ERROR_IO = "io";

    private static final String CHANNEL_ID = "updates";
    private static final int NOTIFICATION_ID = 0xA9D7;

    public UpdateDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context, UpdateManifest manifest) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UpdateDownloadWorker.class)
                .setConstraints(constraints)
                .setInputData(new Data.Builder()
                        .putString(KEY_MANIFEST, manifest.toJson())
                        .build())
                .addTag("update-download")
                .build();

        WorkManager.getInstance(context).enqueue(request);
    }

    @NonNull
    @Override
    public Result doWork() {
        String manifestJson = getInputData().getString(KEY_MANIFEST);
        UpdateManifest manifest = UpdateManifest.fromJson(manifestJson);
        if (manifest == null || manifest.getUrl() == null || manifest.getSha256() == null
                || manifest.getApkFilename() == null) {
            return Result.failure(new Data.Builder()
                    .putString(KEY_ERROR, ERROR_DOWNLOAD)
                    .build());
        }

        File outDir = new File(getApplicationContext().getCacheDir(), "updates");
        if (!outDir.exists() && !outDir.mkdirs()) {
            return Result.failure(new Data.Builder()
                    .putString(KEY_ERROR, ERROR_IO)
                    .build());
        }

        File outFile = new File(outDir, manifest.getApkFilename());

        setForegroundAsync(buildForegroundInfo(getApplicationContext().getString(R.string.update_downloading)));

        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        Request request = new Request.Builder()
                .url(manifest.getUrl())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return Result.failure(new Data.Builder()
                        .putString(KEY_ERROR, ERROR_DOWNLOAD)
                        .build());
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (ResponseBody body = response.body();
                 InputStream in = body.byteStream();
                 OutputStream out = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (isStopped()) {
                        outFile.delete();
                        return Result.failure();
                    }
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
                out.flush();
            }

            String actual = toHex(digest);
            String expected = manifest.getSha256().toLowerCase(Locale.ROOT);
            if (!expected.equals(actual)) {
                outFile.delete();
                return Result.failure(new Data.Builder()
                        .putString(KEY_ERROR, ERROR_INTEGRITY)
                        .build());
            }

            install(getApplicationContext(), outFile, manifest);
            return Result.success();

        } catch (IOException e) {
            if (outFile.exists()) {
                outFile.delete();
            }
            return Result.failure(new Data.Builder()
                    .putString(KEY_ERROR, ERROR_IO)
                    .build());
        } catch (NoSuchAlgorithmException e) {
            return Result.failure(new Data.Builder()
                    .putString(KEY_ERROR, ERROR_INTEGRITY)
                    .build());
        }
    }

    private void install(Context context, File file, UpdateManifest manifest) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);

        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(uri);
        intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooser = Intent.createChooser(intent, context.getString(R.string.update_install_chooser));
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }

    private ForegroundInfo buildForegroundInfo(String message) {
        Context context = getApplicationContext();
        createChannel(context);

        PendingIntent cancelPending = WorkManager.getInstance(context).createCancelPendingIntent(getId());

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        context.getString(R.string.update_action_cancel),
                        cancelPending)
                .build();

        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.update_channel_name),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(context.getString(R.string.update_channel_description));
                nm.createNotificationChannel(channel);
            }
        }
    }

    private static String toHex(MessageDigest digest) {
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}

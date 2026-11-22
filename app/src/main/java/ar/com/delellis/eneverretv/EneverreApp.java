package ar.com.delellis.eneverretv;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Captures uncaught exceptions, writes the stack trace to a file and starts
 * {@link CrashActivity} in a separate process. The activity uploads the report
 * and shows a QR code, so a crash can be diagnosed without ADB.
 */
public class EneverreApp extends Application {
    private static final String TAG = "EneverreApp";
    private static final String CRASH_DIR = "crash";
    private static final String CRASH_FILE = "last-crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        // The crash reporter runs in the :crash process too. Do not install a
        // second handler there: a failure in CrashActivity must not restart it.
        if (isCrashProcess()) {
            return;
        }

        // A previous crash may have happened before the crash screen could be
        // brought up. Show the saved report on the next launch instead.
        File previousCrash = crashFile();
        if (previousCrash.exists()) {
            launchCrashActivity(previousCrash);
        }

        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            File reportFile = writeReport(throwable);
            if (reportFile != null) {
                launchCrashActivity(reportFile);
                try {
                    // Give the system time to bring up the separate :crash
                    // process before the crashing process is torn down.
                    Thread.sleep(700);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                Process.killProcess(Process.myPid());
            }
        });
    }

    private void launchCrashActivity(File reportFile) {
        try {
            Intent intent = new Intent(this, CrashActivity.class);
            intent.putExtra(CrashActivity.EXTRA_REPORT_FILE, reportFile.getAbsolutePath());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } catch (Throwable e) {
            Log.e(TAG, "Could not start the crash reporter", e);
        }
    }

    private File crashFile() {
        return new File(new File(getFilesDir(), CRASH_DIR), CRASH_FILE);
    }

    private File writeReport(Throwable throwable) {
        try {
            String report = Log.getStackTraceString(throwable);
            // The library can put the RTSP URL (and therefore the signed path
            // token) in an exception message. Never upload that.
            report = report.replaceAll("(?i)rtsps?://\\S+", "rtsp://<redacted>");
            if (report.length() > 100_000) {
                report = report.substring(0, 100_000) + "\n… (truncated)";
            }

            File dir = new File(getFilesDir(), CRASH_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File file = new File(dir, CRASH_FILE);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(report.getBytes(StandardCharsets.UTF_8));
            }
            return file;
        } catch (Throwable e) {
            Log.e(TAG, "Could not write the crash report", e);
            return null;
        }
    }

    private boolean isCrashProcess() {
        String crashName = getPackageName() + ":crash";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return crashName.equals(getProcessName());
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null) {
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process.pid == Process.myPid()) {
                        return process.processName.endsWith(":crash");
                    }
                }
            }
        }
        return false;
    }
}

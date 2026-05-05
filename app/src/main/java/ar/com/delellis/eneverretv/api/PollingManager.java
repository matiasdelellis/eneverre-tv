package ar.com.delellis.eneverretv.api;

import android.os.Handler;
import android.os.Looper;

public class PollingManager {

    public interface Task {
        void execute(Callback callback);
    }

    public interface Callback {
        void onSuccess(boolean shouldContinue);
        void onError();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean running = false;
    private boolean inFlight = false;

    private int baseDelay = 3000;
    private int currentDelay = 3000;
    private int maxDelay = 15000;

    public void start(Task task) {
        if (running) return;

        running = true;
        currentDelay = baseDelay;

        schedule(task);
    }

    private void schedule(Task task) {
        handler.postDelayed(() -> run(task), currentDelay);
    }

    private void run(Task task) {
        if (!running || inFlight) return;

        inFlight = true;

        task.execute(new Callback() {
            @Override
            public void onSuccess(boolean shouldContinue) {
                inFlight = false;

                currentDelay = baseDelay;

                if (running && shouldContinue) {
                    schedule(task);
                }
            }

            @Override
            public void onError() {
                inFlight = false;

                currentDelay = Math.min(currentDelay * 2, maxDelay);

                if (running) {
                    schedule(task);
                }
            }
        });
    }

    public void stop() {
        running = false;
        inFlight = false;
        handler.removeCallbacksAndMessages(null);
    }
}
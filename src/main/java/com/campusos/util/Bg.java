package com.campusos.util;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Runs database work on a background thread so the JavaFX Application
 * Thread never freezes. Results and errors are delivered on the FX thread.
 */
public final class Bg {

    private Bg() { }

    /**
     * Executes work off the UI thread; onSuccess receives the result.
     * Any exception is shown to the user as an error dialog.
     */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, null);
    }

    /** Variant with a custom error handler (e.g. inline banners). */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess,
                               Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            if (onError != null) {
                onError.accept(task.getException());
            } else {
                Alerts.error("Operation failed", task.getException());
            }
        });
        Thread worker = new Thread(task, "campusos-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Fire-and-forget variant for actions that only need error handling. */
    public static void run(Callable<Void> work) {
        run(work, result -> { });
    }
}

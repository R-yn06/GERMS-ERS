package com.gudo.germs.utils;

import javax.swing.SwingWorker;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

// Runs blocking work off the Event Dispatch Thread and delivers the result back on it. 
public final class BackgroundTask {
    private BackgroundTask() { }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }

            @Override protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    onError.accept(e.getCause() != null ? e.getCause() : e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    onError.accept(e);
                }
            }
        }.execute();
    }
}

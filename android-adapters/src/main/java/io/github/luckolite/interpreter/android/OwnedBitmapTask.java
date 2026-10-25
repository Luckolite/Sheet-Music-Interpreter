// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter.android;

import android.graphics.Bitmap;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/** An asynchronous recognizer owns its private pixels until its task actually finishes. */
public final class OwnedBitmapTask {
    private OwnedBitmapTask() {}

    public static <T> Task<T> process(Bitmap original, Function<Bitmap, Task<T>> processor) {
        Bitmap owned = original.copy(Bitmap.Config.ARGB_8888, false);
        if (owned == null) throw new IllegalStateException("Could not copy text-recognition input");
        Task<T> task;
        try {
            task = Objects.requireNonNull(processor.apply(owned));
        } catch (RuntimeException | Error failure) {
            owned.recycle();
            throw failure;
        }
        // Timeout/interruption stops only the waiter, not ML Kit's pixel reads.
        task.addOnCompleteListener(Runnable::run, completed -> owned.recycle());
        return task;
    }

    public static <T> T await(
            Bitmap original, Function<Bitmap, Task<T>> processor, int timeoutSeconds)
            throws ExecutionException, InterruptedException, TimeoutException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        return await(process(original, processor), timeoutSeconds);
    }

    public static <T> T await(Task<T> task, int timeoutSeconds)
            throws ExecutionException, InterruptedException, TimeoutException {
        try {
            return Tasks.await(task, timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }
}

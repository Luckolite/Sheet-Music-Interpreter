// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter.android;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Real Bitmap/Tasks, deterministic completion; no text recognizer, model, PDF or network. */
public final class OwnedBitmapTaskInstrumentedTest {
    private static Bitmap bitmap() {
        Bitmap result = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        result.eraseColor(Color.BLUE);
        return result;
    }

    @Test
    public void terminalSuccessFailureAndCancellationReleaseOnlyTheirPrivateCopy()
            throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            Bitmap original = bitmap();
            var cancellation = new CancellationTokenSource();
            var completion = new TaskCompletionSource<Void>(cancellation.getToken());
            var owned = new AtomicReference<Bitmap>();
            var task =
                    OwnedBitmapTask.process(
                            original,
                            copy -> {
                                owned.set(copy);
                                return completion.getTask();
                            });
            var terminal = new CountDownLatch(1);
            task.addOnCompleteListener(Runnable::run, ignored -> terminal.countDown());
            try {
                assertNotSame(original, owned.get());
                original.recycle();
                assertFalse(owned.get().isRecycled());
                assertEquals(Color.BLUE, owned.get().getPixel(0, 0));
                if (mode == 0) completion.setResult(null);
                else if (mode == 1) completion.setException(new IllegalStateException("fixture"));
                else cancellation.cancel();
                assertTrue(terminal.await(5, TimeUnit.SECONDS));
                assertTrue(task.isComplete());
                assertTrue(owned.get().isRecycled());
                assertFalse(completion.trySetResult(null));
            } finally {
                completion.trySetResult(null);
                if (!original.isRecycled()) original.recycle();
            }
        }
    }

    @Test
    public void synchronousSubmissionFailureAndImmediateSuccessLeaveOriginalOwnedByCaller() {
        Bitmap original = bitmap();
        try {
            for (int mode = 0; mode < 3; mode++) {
                var owned = new AtomicReference<Bitmap>();
                final int selected = mode;
                Class<? extends Throwable> expected =
                        selected == 1 ? AssertionError.class : RuntimeException.class;
                Throwable failure =
                        assertThrows(
                                expected,
                                () ->
                                        OwnedBitmapTask.process(
                                                original,
                                                copy -> {
                                                    owned.set(copy);
                                                    if (selected == 0)
                                                        throw new IllegalArgumentException(
                                                                "fixture");
                                                    if (selected == 1)
                                                        throw new AssertionError("fixture");
                                                    return null;
                                                }));
                assertNotNull(failure);
                assertTrue(owned.get().isRecycled());
                assertFalse(original.isRecycled());
            }
            var immediateCopy = new AtomicReference<Bitmap>();
            var task =
                    OwnedBitmapTask.process(
                            original,
                            copy -> {
                                immediateCopy.set(copy);
                                return Tasks.forResult("complete");
                            });
            assertTrue(task.isSuccessful());
            assertTrue(immediateCopy.get().isRecycled());
            assertFalse(original.isRecycled());
            assertEquals(Color.BLUE, original.getPixel(0, 0));
        } finally {
            original.recycle();
        }
    }

    @Test
    public void timeoutDoesNotReleasePixelsStillOwnedByTheTask() throws Exception {
        Bitmap original = bitmap();
        var completion = new TaskCompletionSource<Void>();
        var owned = new AtomicReference<Bitmap>();
        var task =
                OwnedBitmapTask.process(
                        original,
                        copy -> {
                            owned.set(copy);
                            return completion.getTask();
                        });
        var failure = new AtomicReference<Throwable>();
        Thread waiter =
                new Thread(
                        () -> {
                            try {
                                OwnedBitmapTask.await(task, 0);
                            } catch (Throwable error) {
                                failure.set(error);
                            }
                        });
        try {
            waiter.start();
            waiter.join(5000);
            assertFalse(waiter.isAlive());
            assertTrue(failure.get() instanceof TimeoutException);
            original.recycle();
            assertFalse(owned.get().isRecycled());
            assertEquals(Color.BLUE, owned.get().getPixel(0, 0));
            completion.setResult(null);
            assertTrue(owned.get().isRecycled());
        } finally {
            completion.trySetResult(null);
            waiter.interrupt();
            waiter.join(5000);
            if (!original.isRecycled()) original.recycle();
        }
    }

    @Test
    public void interruptedWaitRestoresStatusWithoutReleasingPendingPixels() throws Exception {
        Bitmap original = bitmap();
        var completion = new TaskCompletionSource<Void>();
        var owned = new AtomicReference<Bitmap>();
        var task =
                OwnedBitmapTask.process(
                        original,
                        copy -> {
                            owned.set(copy);
                            return completion.getTask();
                        });
        var started = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var restored = new AtomicBoolean();
        Thread waiter =
                new Thread(
                        () -> {
                            started.countDown();
                            try {
                                OwnedBitmapTask.await(task, 30);
                            } catch (Throwable error) {
                                failure.set(error);
                                restored.set(Thread.currentThread().isInterrupted());
                            }
                        });
        try {
            waiter.start();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            waiter.interrupt();
            waiter.join(5000);
            assertFalse(waiter.isAlive());
            assertTrue(failure.get() instanceof InterruptedException);
            assertTrue(restored.get());
            original.recycle();
            assertFalse(owned.get().isRecycled());
            assertEquals(Color.BLUE, owned.get().getPixel(0, 0));
            completion.setException(new IllegalStateException("late fixture failure"));
            assertTrue(owned.get().isRecycled());
        } finally {
            completion.trySetResult(null);
            waiter.interrupt();
            waiter.join(5000);
            if (!original.isRecycled()) original.recycle();
        }
    }

    @Test
    public void alreadyInterruptedCallDoesNotSubmitRecognition() throws Exception {
        Bitmap original = bitmap();
        var submitted = new AtomicBoolean();
        var restored = new AtomicBoolean();
        var failure = new AtomicReference<Throwable>();
        Thread caller =
                new Thread(
                        () -> {
                            Thread.currentThread().interrupt();
                            try {
                                OwnedBitmapTask.await(
                                        original,
                                        copy -> {
                                            submitted.set(true);
                                            return Tasks.forResult("unexpected");
                                        },
                                        1);
                            } catch (Throwable error) {
                                failure.set(error);
                                restored.set(Thread.currentThread().isInterrupted());
                            }
                        });
        try {
            caller.start();
            caller.join(5000);
            assertFalse(caller.isAlive());
            assertTrue(failure.get() instanceof InterruptedException);
            assertTrue(restored.get());
            assertFalse(submitted.get());
            assertFalse(original.isRecycled());
        } finally {
            caller.interrupt();
            caller.join(5000);
            original.recycle();
        }
    }
}

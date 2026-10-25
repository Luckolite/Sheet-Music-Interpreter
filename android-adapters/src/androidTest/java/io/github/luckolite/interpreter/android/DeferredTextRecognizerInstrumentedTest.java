// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter.android;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.Image;
import com.google.android.gms.common.Feature;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.android.odml.image.MlImage;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Models SDK close cancellation with real Tasks/Bitmaps and deterministic synthetic processing. */
public final class DeferredTextRecognizerInstrumentedTest {
    static TextRecognizer delegate(InvocationHandler handler) {
        return (TextRecognizer)
                Proxy.newProxyInstance(
                        TextRecognizer.class.getClassLoader(),
                        new Class<?>[] {TextRecognizer.class},
                        handler);
    }

    @Test
    public void allSdkOverloadsAndMetadataForwardAndClosedClientRejectsNewWork() {
        List<String> calls = new ArrayList<>();
        Feature[] features = {new Feature("fixture", 1L)};
        AtomicInteger closed = new AtomicInteger();
        TextRecognizer client =
                new DeferredTextRecognizer(
                        delegate(
                                (p, method, args) -> {
                                    if (method.getName().equals("process")) {
                                        calls.add(Arrays.toString(method.getParameterTypes()));
                                        return Tasks.<Text>forResult(null);
                                    }
                                    if (method.getName().equals("getOptionalFeatures"))
                                        return features;
                                    if (method.getName().equals("getDetectorType")) return 42;
                                    if (method.getName().equals("close")) {
                                        closed.incrementAndGet();
                                        return null;
                                    }
                                    throw new AssertionError(method);
                                }));
        assertTrue(client.process((InputImage) null).isSuccessful());
        assertTrue(client.process((MlImage) null).isSuccessful());
        assertTrue(client.process((Bitmap) null, 90).isSuccessful());
        assertTrue(client.process((Image) null, 180).isSuccessful());
        assertTrue(client.process((Image) null, 270, (Matrix) null).isSuccessful());
        assertTrue(client.process((ByteBuffer) null, 1, 2, 3, 4).isSuccessful());
        assertEquals(6, calls.size());
        assertEquals(6, new HashSet<>(calls).size());
        assertEquals(42, client.getDetectorType());
        assertSame(features, client.getOptionalFeatures());
        client.close();
        client.close();
        assertEquals(1, closed.get());
        Task<Text> rejected = client.process((InputImage) null);
        assertTrue(rejected.isComplete());
        assertFalse(rejected.isSuccessful());
        assertTrue(rejected.getException() instanceof IllegalStateException);
        assertEquals(6, calls.size());
    }

    @Test
    public void closeCannotCancelPendingSdkTasksOrReleaseTheirOwnedPixelsEarly() {
        var token = new CancellationTokenSource();
        var first = new TaskCompletionSource<Text>(token.getToken());
        var second = new TaskCompletionSource<Text>(token.getToken());
        var queue = new ArrayDeque<>(List.of(first.getTask(), second.getTask()));
        AtomicInteger closed = new AtomicInteger();
        TextRecognizer client =
                new DeferredTextRecognizer(
                        delegate(
                                (p, method, args) -> {
                                    if (method.getName().equals("process"))
                                        return queue.removeFirst();
                                    if (method.getName().equals("close")) {
                                        closed.incrementAndGet();
                                        token.cancel();
                                        return null;
                                    }
                                    throw new AssertionError(method);
                                }));
        Bitmap a = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Bitmap b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        a.eraseColor(Color.BLUE);
        b.eraseColor(Color.RED);
        var ownedA = new AtomicReference<Bitmap>();
        var ownedB = new AtomicReference<Bitmap>();
        try {
            var taskA =
                    OwnedBitmapTask.process(
                            a,
                            copy -> {
                                ownedA.set(copy);
                                return client.process(InputImage.fromBitmap(copy, 0));
                            });
            var taskB =
                    OwnedBitmapTask.process(
                            b,
                            copy -> {
                                ownedB.set(copy);
                                return client.process(InputImage.fromBitmap(copy, 0));
                            });
            client.close();
            client.close();
            a.recycle();
            b.recycle();
            assertEquals(0, closed.get());
            assertFalse(taskA.isComplete());
            assertFalse(taskB.isComplete());
            assertEquals(Color.BLUE, ownedA.get().getPixel(0, 0));
            assertEquals(Color.RED, ownedB.get().getPixel(0, 0));
            first.setResult(null);
            assertEquals(0, closed.get());
            assertTrue(ownedA.get().isRecycled());
            assertFalse(ownedB.get().isRecycled());
            second.setException(new IllegalStateException("natural fixture failure"));
            assertEquals(1, closed.get());
            assertTrue(ownedB.get().isRecycled());
            assertFalse(taskA.isCanceled());
            assertFalse(taskB.isCanceled());
            client.close();
            assertEquals(1, closed.get());
        } finally {
            first.trySetResult(null);
            second.trySetResult(null);
            client.close();
            if (!a.isRecycled()) a.recycle();
            if (!b.isRecycled()) b.recycle();
        }
    }

    @Test
    public void reservePrecedesDelegateSubmissionAndCloseDoesNotBlockOnIt() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pending = new TaskCompletionSource<Text>();
        var closed = new AtomicInteger();
        TextRecognizer client =
                new DeferredTextRecognizer(
                        delegate(
                                (p, method, args) -> {
                                    if (method.getName().equals("process")) {
                                        entered.countDown();
                                        assertTrue(release.await(5, TimeUnit.SECONDS));
                                        return pending.getTask();
                                    }
                                    if (method.getName().equals("close")) {
                                        closed.incrementAndGet();
                                        return null;
                                    }
                                    throw new AssertionError(method);
                                }));
        var failure = new AtomicReference<Throwable>();
        Thread submit =
                new Thread(
                        () -> {
                            try {
                                client.process((InputImage) null);
                            } catch (Throwable e) {
                                failure.set(e);
                            }
                        });
        Thread closer = new Thread(client::close);
        try {
            submit.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            closer.start();
            closer.join(1000);
            assertFalse(closer.isAlive());
            assertEquals(0, closed.get());
            release.countDown();
            submit.join(5000);
            assertFalse(submit.isAlive());
            assertNull(failure.get());
            assertEquals(0, closed.get());
            pending.setResult(null);
            assertEquals(1, closed.get());
        } finally {
            release.countDown();
            pending.trySetResult(null);
            submit.join(5000);
            closer.join(5000);
            client.close();
        }
    }

    @Test
    public void reentrantCloseAndSynchronousFailuresReleaseExactlyOneReservation() {
        for (int mode = 0; mode < 4; mode++) {
            final int selected = mode;
            var closed = new AtomicInteger();
            var clientRef = new AtomicReference<TextRecognizer>();
            var processing = new java.util.concurrent.atomic.AtomicBoolean();
            TextRecognizer client =
                    new DeferredTextRecognizer(
                            delegate(
                                    (p, method, args) -> {
                                        if (method.getName().equals("process")) {
                                            processing.set(true);
                                            clientRef.get().close();
                                            assertEquals(0, closed.get());
                                            try {
                                                if (selected == 0)
                                                    throw new IllegalArgumentException("fixture");
                                                if (selected == 1)
                                                    throw new AssertionError("fixture");
                                                if (selected == 2) return null;
                                                return Tasks.<Text>forResult(null);
                                            } finally {
                                                processing.set(false);
                                            }
                                        }
                                        if (method.getName().equals("close")) {
                                            assertFalse(processing.get());
                                            closed.incrementAndGet();
                                            return null;
                                        }
                                        throw new AssertionError(method);
                                    }));
            clientRef.set(client);
            if (mode == 0)
                assertThrows(
                        IllegalArgumentException.class, () -> client.process((InputImage) null));
            else if (mode == 1)
                assertThrows(AssertionError.class, () -> client.process((InputImage) null));
            else if (mode == 2)
                assertThrows(NullPointerException.class, () -> client.process((InputImage) null));
            else assertTrue(client.process((InputImage) null).isSuccessful());
            assertEquals(1, closed.get());
            client.close();
            assertEquals(1, closed.get());
        }
    }
}

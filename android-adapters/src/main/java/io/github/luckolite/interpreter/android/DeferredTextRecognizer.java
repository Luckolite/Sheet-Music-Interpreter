// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter.android;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import com.google.android.gms.common.Feature;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.odml.image.MlImage;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Function;

/** Closing a client must not cancel its task while native code still owns submitted pixels. */
public final class DeferredTextRecognizer implements TextRecognizer {
    private final TextRecognizer delegate;
    private int pending;
    private boolean closing;
    private boolean released;

    public DeferredTextRecognizer(TextRecognizer delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    private Task<Text> process(Function<TextRecognizer, Task<Text>> operation) {
        synchronized (this) {
            if (closing)
                return Tasks.forException(new IllegalStateException("Text recognizer is closed"));
            pending++;
        }
        Task<Text> task;
        try {
            task = Objects.requireNonNull(operation.apply(delegate));
        } catch (RuntimeException | Error failure) {
            finished();
            throw failure;
        }
        task.addOnCompleteListener(Runnable::run, completed -> finished());
        return task;
    }

    private void finished() {
        boolean close;
        synchronized (this) {
            pending--;
            close = closing && pending == 0 && !released;
            if (close) released = true;
        }
        if (close) delegate.close();
    }

    @Override
    public void close() {
        boolean close;
        synchronized (this) {
            closing = true;
            close = pending == 0 && !released;
            if (close) released = true;
        }
        if (close) delegate.close();
    }

    @Override
    public Task<Text> process(InputImage image) {
        return process(r -> r.process(image));
    }

    @Override
    public Task<Text> process(MlImage image) {
        return process(r -> r.process(image));
    }

    @Override
    public Task<Text> process(Bitmap bitmap, int rotation) {
        return process(r -> r.process(bitmap, rotation));
    }

    @Override
    public Task<Text> process(Image image, int rotation) {
        return process(r -> r.process(image, rotation));
    }

    @Override
    public Task<Text> process(Image image, int rotation, Matrix matrix) {
        return process(r -> r.process(image, rotation, matrix));
    }

    @Override
    public Task<Text> process(ByteBuffer buffer, int width, int height, int rotation, int format) {
        return process(r -> r.process(buffer, width, height, rotation, format));
    }

    @Override
    public int getDetectorType() {
        return delegate.getDetectorType();
    }

    @Override
    public Feature[] getOptionalFeatures() {
        return delegate.getOptionalFeatures();
    }
}

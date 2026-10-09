// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import ai.onnxruntime.*;
import java.nio.FloatBuffer;
import java.util.*;

/** Optional runtime binding shared by the desktop and Android validation builds. */
public final class OnnxOcrInference implements PortableOcr.Inference, AutoCloseable {
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession detector, recognizer;
    private final List<String> dictionary;

    public OnnxOcrInference(String detectorPath, String recognizerPath) throws Exception {
        this(detectorPath, recognizerPath, false);
    }

    /** Own only the recognizer when text crops have already been located. */
    public static OnnxOcrInference recognizerOnly(String recognizerPath) throws Exception {
        return new OnnxOcrInference(null, recognizerPath, true);
    }

    private OnnxOcrInference(String detectorPath, String recognizerPath, boolean recognizerOnly)
            throws Exception {
        if (!recognizerOnly)
            verify(
                    detectorPath,
                    Set.of(
                            "d2a7720d45a54257208b1e13e36a8479894cb74155a5efe29462512d42f49da9",
                            "4d97c44a20d30a81aad087d6a396b08f786c4635742afc391f6621f5c6ae78ae"));
        verify(
                recognizerPath,
                Set.of("b20bd37c168a570f583afbc8cd7925603890efbcdc000a59e22c269d160b5f5a"));
        verify(
                recognizerPath + ".dictionary",
                Set.of("1169fb297871f7a14d6a0f20c14af56de789c48b170e59dfb66950448e31c062"));
        OrtSession openedDetector = null, openedRecognizer = null;
        try {
            try (var options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(2);
                options.setInterOpNumThreads(1);
                // Keep spinning during a run, then release idle workers between runs.
                options.addConfigEntry("session.force_spinning_stop", "1");
                if (!recognizerOnly)
                    openedDetector = environment.createSession(detectorPath, options);
                options.setIntraOpNumThreads(4);
                openedRecognizer = environment.createSession(recognizerPath, options);
            }
            dictionary =
                    List.copyOf(
                            java.nio.file.Files.readAllLines(
                                    new java.io.File(recognizerPath + ".dictionary").toPath(),
                                    java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception | Error failure) {
            closeAfterFailure(openedDetector, failure);
            closeAfterFailure(openedRecognizer, failure);
            throw failure;
        }
        detector = openedDetector;
        recognizer = openedRecognizer;
    }

    private static void closeAfterFailure(OrtSession session, Throwable failure) {
        if (session != null)
            try {
                session.close();
            } catch (Exception | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
    }

    private static void verify(String path, Set<String> allowed) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = new java.io.FileInputStream(path)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        var hex = new StringBuilder();
        for (byte b : digest.digest())
            hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        if (!allowed.contains(hex.toString()))
            throw new java.io.IOException("Unrecognized OCR artifact checksum");
    }

    public List<String> dictionary() {
        return dictionary;
    }

    public float[][] detect(float[] input, int width, int height) throws Exception {
        if (detector == null)
            throw new IllegalStateException("OCR detector is unavailable in recognizer-only mode");
        try (var tensor =
                        OnnxTensor.createTensor(
                                environment,
                                FloatBuffer.wrap(input),
                                new long[] {1, 3, height, width});
                var result =
                        detector.run(Map.of(detector.getInputNames().iterator().next(), tensor))) {
            return ((float[][][][]) result.get(0).getValue())[0][0];
        }
    }

    public float[][] recognize(float[] input, int width, int height) throws Exception {
        try (var tensor =
                        OnnxTensor.createTensor(
                                environment,
                                FloatBuffer.wrap(input),
                                new long[] {1, 3, height, width});
                var result =
                        recognizer.run(
                                Map.of(recognizer.getInputNames().iterator().next(), tensor))) {
            var output = ((float[][][]) result.get(0).getValue())[0];
            if (output.length > 0 && output[0].length != dictionary.size())
                throw new IllegalArgumentException(
                        "Model vocabulary="
                                + output[0].length
                                + " dictionary="
                                + dictionary.size());
            return output;
        }
    }

    public void close() throws OrtException {
        try {
            if (detector != null) detector.close();
        } catch (OrtException | RuntimeException | Error failure) {
            closeAfterFailure(recognizer, failure);
            throw failure;
        }
        recognizer.close();
    }
}

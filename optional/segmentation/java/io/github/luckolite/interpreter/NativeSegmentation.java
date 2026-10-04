// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Windows segmentation candidate using the app model converted to ONNX. */
public final class NativeSegmentation implements AutoCloseable {
    private static final int WINDOW = 320;
    private static final int STEP = 192;
    public static final String MODEL_SHA256 =
            "e436efe12ddc598add9540378d6772622a2ad9d7bdb1f9d4c0ab87e3144402a7";
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final String inputName;

    public NativeSegmentation(Path model) throws OrtException, IOException {
        if (!Files.isRegularFile(model)) throw new IOException("Segmentation model is missing");
        try (var input = Files.newInputStream(model)) {
            if (!MODEL_SHA256.equals(
                    java.util.HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(input.readAllBytes()))))
                throw new IOException("Segmentation model checksum mismatch");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
        try (var options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(
                    Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
            session = environment.createSession(model.toString(), options);
        }
        inputName = session.getInputNames().iterator().next();
    }

    public byte[] predict(byte[] gray, int width, int height) throws OrtException {
        long size = (long) width * height;
        if (width < 1 || height < 1 || size > 20_000_000 || gray.length != size)
            throw new IllegalArgumentException("Invalid grayscale page");
        var labels = new byte[gray.length];
        var confidence = new byte[gray.length];
        var input = new float[3 * WINDOW * WINDOW];
        int plane = WINDOW * WINDOW;
        long[] whitePrediction = null;
        for (int top : tileStarts(height))
            for (int left : tileStarts(width)) {
                java.util.Arrays.fill(input, 0, plane, 255f);
                int rows = Math.min(WINDOW, height - top), columns = Math.min(WINDOW, width - left);
                for (int y = 0; y < rows; y++)
                    for (int x = 0; x < columns; x++)
                        input[y * WINDOW + x] = gray[(top + y) * width + left + x] & 255;
                System.arraycopy(input, 0, input, plane, plane);
                System.arraycopy(input, 0, input, plane * 2, plane);
                boolean exactWhite = ExactWhiteTileInput.matchesReplicatedFirstPlane(input, plane);
                if (exactWhite && whitePrediction != null) {
                    for (int y = 0; y < rows; y++)
                        for (int x = 0; x < columns; x++) {
                            int destination = (top + y) * width + left + x;
                            int edge =
                                    Math.min(
                                                    Math.min(x, WINDOW - 1 - x),
                                                    Math.min(y, WINDOW - 1 - y))
                                            + 1;
                            if (edge >= (confidence[destination] & 255)) {
                                long value = whitePrediction[y * WINDOW + x];
                                if (value < 0 || value > 5)
                                    throw new OrtException("Invalid segmentation class");
                                labels[destination] = (byte) value;
                                confidence[destination] = (byte) edge;
                            }
                        }
                    continue;
                }
                try (var tensor =
                                OnnxTensor.createTensor(
                                        environment,
                                        FloatBuffer.wrap(input),
                                        new long[] {1, 3, WINDOW, WINDOW});
                        var output = session.run(Map.of(inputName, tensor))) {
                    LongBuffer prediction = ((OnnxTensor) output.get(0)).getLongBuffer();
                    if (prediction.remaining() != plane)
                        throw new OrtException("Unexpected segmentation output");
                    if (exactWhite) {
                        // Keep the actual model's output, never an assumed background class.
                        whitePrediction = new long[plane];
                        prediction.duplicate().get(whitePrediction);
                    }
                    for (int y = 0; y < rows; y++)
                        for (int x = 0; x < columns; x++) {
                            int destination = (top + y) * width + left + x;
                            int edge =
                                    Math.min(
                                                    Math.min(x, WINDOW - 1 - x),
                                                    Math.min(y, WINDOW - 1 - y))
                                            + 1;
                            if (edge >= (confidence[destination] & 255)) {
                                long value = prediction.get(y * WINDOW + x);
                                if (value < 0 || value > 5)
                                    throw new OrtException("Invalid segmentation class");
                                labels[destination] = (byte) value;
                                confidence[destination] = (byte) edge;
                            }
                        }
                }
            }
        return labels;
    }

    private static List<Integer> tileStarts(int size) {
        var starts = new ArrayList<Integer>();
        int finalStart = Math.max(0, size - WINDOW);
        for (int value = 0; value < size; value += STEP) {
            int start = Math.min(value, finalStart);
            if (starts.isEmpty() || starts.get(starts.size() - 1) != start) starts.add(start);
            if (start == finalStart) break;
        }
        if (starts.isEmpty()) starts.add(0);
        return starts;
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException(
                    "Usage: NativeSegmentation model.onnx gray.raw width height labels.raw");
        byte[] gray = Files.readAllBytes(Path.of(args[1]));
        int width = Integer.parseInt(args[2]), height = Integer.parseInt(args[3]);
        try (var segmenter = new NativeSegmentation(Path.of(args[0]))) {
            Files.write(Path.of(args[4]), segmenter.predict(gray, width, height));
        }
    }
}

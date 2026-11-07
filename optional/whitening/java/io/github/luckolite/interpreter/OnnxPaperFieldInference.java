// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.FileInputStream;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.Map;

/** ONNX binding for the pinned original-training paper model. */
public final class OnnxPaperFieldInference
        implements PaperBackgroundWhitening.Inference, AutoCloseable {
    private final OrtEnvironment environment;
    private final OrtSession session;

    public OnnxPaperFieldInference(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value & 255) >>> 4, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        if (!PaperBackgroundWhitening.MODEL_SHA256.equals(hex.toString())) {
            throw new IllegalArgumentException("Unrecognized paper-field model checksum");
        }
        environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            options.addConfigEntry("session.force_spinning_stop", "1");
            session = environment.createSession(path, options);
        }
    }

    @Override
    public PaperBackgroundWhitening.Fields run(float[] features, int width, int height)
            throws OrtException {
        try (OnnxTensor tensor =
                        OnnxTensor.createTensor(
                                environment,
                                FloatBuffer.wrap(features),
                                new long[] {1, 2, height, width});
                OrtSession.Result result = session.run(Map.of("features", tensor))) {
            float[][] paper = ((float[][][][]) result.get("paper").orElseThrow().getValue())[0][0];
            float[][] contrast =
                    ((float[][][][]) result.get("contrast").orElseThrow().getValue())[0][0];
            return new PaperBackgroundWhitening.Fields(paper, contrast);
        }
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}

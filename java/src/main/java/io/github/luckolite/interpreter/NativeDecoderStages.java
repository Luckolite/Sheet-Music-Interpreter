// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0

package io.github.luckolite.interpreter;

import java.util.List;

/** The same pure geometry stage runs locally or on the matching desktop build. */
public final class NativeDecoderStages {
    public record Geometry(byte[] labels, List<MeasureRegion> measures) {}

    private NativeDecoderStages() {}

    public static Geometry geometry(byte[] labels, byte[] gray, int width, int height) {
        return geometry(labels, gray, width, height, null);
    }

    public static Geometry geometry(
            byte[] labels, byte[] gray, int width, int height, SelectedMeasureOrigins origins) {
        var raw = OmrMeasurePostProcessor.process(labels, gray, width, height, labels, origins);
        byte[] prepared =
                OmrScoreInterpreter.normalizeHeaderSymbols(labels, gray, width, height, raw);
        if (prepared != labels)
            raw =
                    OmrMeasurePostProcessor.process(
                            OmrScoreInterpreter.normalizeTextGeometry(
                                    labels, gray, width, height, raw),
                            gray,
                            width,
                            height,
                            prepared,
                            origins);
        if (origins != null) origins.prepared(prepared);
        return new Geometry(prepared, raw);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Checks the exact model tensor, including its padded pixels and all channels. */
final class ExactWhiteTileInput {
    private ExactWhiteTileInput() {}

    static boolean matches(float[] input) {
        for (float pixel : input) if (pixel != 255f) return false;
        return true;
    }

    /**
     * Checks one plane of an owned tensor whose three planes were just replicated by arraycopy.
     * The caller must not write or expose the input between those copies and this check. Padding
     * remains part of the first plane; unrelated tensors must use matches(float[]) instead.
     */
    static boolean matchesReplicatedFirstPlane(float[] input, int planeSize) {
        if (planeSize < 0 || input.length != 3L * planeSize)
            throw new IllegalArgumentException("Expected exactly three replicated channel planes");
        for (int i = 0; i < planeSize; i++) if (input[i] != 255f) return false;
        return true;
    }

    /**
     * Proves that the ordinary packing loop overwrites every first-plane cell.
     * A complete, nonoverflowing raster and full in-bounds tile are required.
     * Otherwise callers retain the original white fill before any pixel access.
     */
    static boolean fullTileOverwritesPlane(
            byte[] gray, int width, int height, int left, int top, int window) {
        return window > 0
                && width >= window
                && height >= window
                && left >= 0
                && top >= 0
                && width - left >= window
                && height - top >= window
                && gray != null
                && (long) width * height == gray.length;
    }

    /** Validates a complete actual model output for reuse without narrowing class IDs. */
    static boolean cacheablePrediction(long[] prediction, int planeSize) {
        if (prediction == null || prediction.length != planeSize) return false;
        for (long value : prediction) if (value < 0 || value > 5) return false;
        return true;
    }
}

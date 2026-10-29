// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Complete common/cut-time shape evidence for a bounded registered header crop. */
public final class CommonTimeHeaderGlyph {
    private CommonTimeHeaderGlyph() {}

    /** Returns 4 for C, 2 for cut C, and zero when the complete glyph is not proved. */
    public static int read(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int firstLine,
            float gap,
            float slope) {
        if (width < 1
                || height < 1
                || gray == null
                || (long) width * height != gray.length
                || !Float.isFinite(gap)
                || !Float.isFinite(slope)
                || gap < 6
                || gap > height
                || firstLine < 0
                || firstLine >= height) return 0;
        return CommonTimeMeter.read(gray, width, height, left, right, firstLine, gap, slope);
    }
}

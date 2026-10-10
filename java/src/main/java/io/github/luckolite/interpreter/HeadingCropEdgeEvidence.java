// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Original source ink outside the ordinary OCR crop; no text or names are guessed. */
public final class HeadingCropEdgeEvidence {
    private HeadingCropEdgeEvidence() {}

    public static boolean hasOuterInk(
            int[] argb,
            int width,
            int height,
            int leftExtra,
            int rightExtra,
            int textTop,
            int textBottom) {
        if (width < 1
                || height < 1
                || (long) width * height > 20_000_000
                || argb == null
                || argb.length != (long) width * height
                || leftExtra < 0
                || rightExtra < 0
                || leftExtra + rightExtra >= width
                || textTop < 0
                || textBottom > height
                || textBottom <= textTop) return false;
        return side(argb, width, leftExtra, 0, textTop, textBottom)
                || side(argb, width, rightExtra, width - rightExtra, textTop, textBottom);
    }

    private static boolean side(int[] pixels, int width, int size, int start, int top, int bottom) {
        if (size < 2) return false;
        int rows = 0, count = 0;
        for (int y = top; y < bottom; y++) {
            boolean hit = false;
            for (int x = start; x < start + size; x++) {
                int rgb = pixels[y * width + x];
                if (((rgb >>> 16) & 255) <= 160
                        && ((rgb >>> 8) & 255) <= 160
                        && (rgb & 255) <= 160) {
                    count++;
                    hit = true;
                }
            }
            if (hit) rows++;
        }
        int contentHeight = bottom - top;
        return rows >= Math.max(3, (int) Math.ceil(contentHeight * .25)) && count >= contentHeight;
    }
}

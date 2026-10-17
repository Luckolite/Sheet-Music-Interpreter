// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Blank OCR context around an isolated meter digit; source pixels and scale stay unchanged. */
public final class MeterDigitPadding {
    private MeterDigitPadding() {}

    public record Raster(int[] pixels, int width, int height, int border) {}

    /** Measured at the consumer's input scale, before any further OCR enlargement. */
    public static int border(int digitHeight) {
        if (digitHeight < 1) throw new IllegalArgumentException("digit height");
        return Math.max(2, Math.round(digitHeight * .05f));
    }

    /** Copies only the selected half-open row band, with no resampling or ink cleanup. */
    public static Raster pad(int[] pixels, int width, int height, int top, int bottom) {
        if (pixels == null
                || width < 1
                || height < 1
                || pixels.length != (long) width * height
                || top < 0
                || bottom > height
                || top >= bottom) throw new IllegalArgumentException("digit raster");
        int border = border(bottom - top);
        int outWidth = Math.addExact(width, Math.multiplyExact(2, border));
        int outHeight = Math.addExact(bottom - top, Math.multiplyExact(2, border));
        int[] padded = new int[Math.multiplyExact(outWidth, outHeight)];
        Arrays.fill(padded, 0xffffffff);
        for (int y = top; y < bottom; y++) {
            System.arraycopy(
                    pixels, y * width, padded, (y - top + border) * outWidth + border, width);
        }
        return new Raster(padded, outWidth, outHeight, border);
    }
}

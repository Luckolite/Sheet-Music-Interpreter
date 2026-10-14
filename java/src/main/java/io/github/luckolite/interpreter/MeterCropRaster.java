// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** In-place ARGB equivalent of meter-crop cleanup, without per-pixel native bitmap calls. */
public final class MeterCropRaster {
    private MeterCropRaster() {}

    public static void prepare(
            int[] pixels,
            int width,
            int height,
            int sourceTop,
            float firstLine,
            float gap,
            boolean maskInk) {
        if (width < 1 || height < 1 || (long) width * height != pixels.length)
            throw new IllegalArgumentException("raster size");
        if (maskInk)
            for (int i = 0; i < pixels.length; i++)
                pixels[i] = MeterOcrEvidence.ink(luminance(pixels[i])) ? 0xff000000 : 0xffffffff;
        int radius = Math.max(1, Math.round(gap * .06f));
        for (int line = 0; line < 5; line++) {
            int y = Math.round(firstLine + line * gap) - sourceTop;
            if (y + radius < 0 || y - radius >= height) continue;
            for (int x = 0; x < width; x++) {
                boolean bridge =
                        darkColumn(
                                        pixels,
                                        width,
                                        x,
                                        Math.max(0, y - radius - 2),
                                        Math.max(0, y - radius - 1))
                                && darkColumn(
                                        pixels,
                                        width,
                                        x,
                                        Math.min(height - 1, y + radius + 1),
                                        Math.min(height - 1, y + radius + 2));
                for (int yy = Math.max(0, y - radius); yy <= Math.min(height - 1, y + radius); yy++)
                    pixels[yy * width + x] = bridge ? 0xff000000 : 0xffffffff;
            }
        }
        // Keep identical edge limits and iteration order: earlier columns can support later ones.
        for (int x = 0; x < width; x++) {
            if (x >= width * .15f && x < width * .90f) continue;
            int dark = 0;
            for (int y = 0; y < height; y++) if (luminance(pixels[y * width + x]) < 135) dark++;
            if (dark > height * .85f)
                for (int y = 0; y < height; y++) pixels[y * width + x] = 0xffffffff;
        }
    }

    private static boolean darkColumn(int[] pixels, int width, int centerX, int top, int bottom) {
        for (int x = Math.max(0, centerX - 1); x <= Math.min(width - 1, centerX + 1); x++)
            for (int y = top; y <= bottom; y++)
                if (luminance(pixels[y * width + x]) < 135) return true;
        return false;
    }

    private static int luminance(int color) {
        return (((color >>> 16) & 255) * 30 + ((color >>> 8) & 255) * 59 + (color & 255) * 11)
                / 100;
    }
}

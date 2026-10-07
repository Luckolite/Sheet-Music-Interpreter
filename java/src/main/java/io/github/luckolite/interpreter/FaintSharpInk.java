// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Local-background evidence for existing sharp-shape verification, never a classifier alone. */
final class FaintSharpInk {
    static byte[] crop(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap) {
        if (gray == null
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || gap < 4
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || right < left
                || bottom < top) return null;
        int side = Math.max(2, Math.round(gap * .25f)), probe = Math.max(2, Math.round(gap * .2f));
        if (left < side || right + side >= width || top < probe + 1 || bottom + probe + 1 >= height)
            return null;
        int w = right - left + 1, h = bottom - top + 1, reach = Math.max(3, Math.round(gap * .65f));
        byte[] mask = new byte[w * h];
        for (int y = top; y <= bottom; y++) {
            int outside = 0, dark = 0;
            for (int x = Math.max(0, left - reach); x <= Math.min(width - 1, right + reach); x++)
                if (x < left || x > right) {
                    outside++;
                    if ((gray[y * width + x] & 255) <= 180) dark++;
                }
            boolean rule = outside > 0 && dark >= outside * .8;
            for (int x = left; x <= right; x++) {
                if (!ink(gray, width, x, y, side)) continue;
                if (rule
                        && (!ink(gray, width, x, y - probe, side)
                                || !ink(gray, width, x, y + probe, side))) continue;
                mask[(y - top) * w + x - left] = OmrMeasurePostProcessor.CLEF_OR_KEY;
            }
        }
        byte[] closed = mask;
        int limit = Math.max(1, Math.round(gap * .2f));
        for (int y = 1; y < h - 2; y++)
            for (int x = 0; x < w; x++)
                if (mask[y * w + x] != 0 && mask[(y - 1) * w + x] != 0)
                    for (int skip = 1; skip <= limit && y + skip + 2 < h; skip++) {
                        int radius = Math.max(1, Math.round((skip + 1) * .3f));
                        for (int end = Math.max(0, x - radius);
                                end <= Math.min(w - 1, x + radius);
                                end++)
                            if (mask[(y + skip + 1) * w + end] != 0
                                    && mask[(y + skip + 2) * w + end] != 0) {
                                if (closed == mask) closed = mask.clone();
                                // Follow the bounded shaft lean across a short missing
                                // stripe; both ends retain two consecutive raw ink pixels.
                                for (int dy = 1; dy <= skip; dy++) {
                                    int xx = x + Math.round((end - x) * dy / (float) (skip + 1));
                                    closed[(y + dy) * w + xx] = OmrMeasurePostProcessor.CLEF_OR_KEY;
                                }
                            }
                    }
        return closed;
    }

    private static boolean ink(byte[] gray, int width, int x, int y, int side) {
        int value = gray[y * width + x] & 255;
        if (value <= 180) return true;
        if (value > 245) return false;
        if (Math.min(gray[y * width + x - side] & 255, gray[y * width + x + side] & 255) <= value)
            return false;
        int center = 0, left = 0, right = 0;
        for (int dy = -1; dy <= 1; dy++) {
            center += gray[(y + dy) * width + x] & 255;
            left += gray[(y + dy) * width + x - side] & 255;
            right += gray[(y + dy) * width + x + side] & 255;
        }
        return Math.min(left, right) - center >= 3;
    }
}

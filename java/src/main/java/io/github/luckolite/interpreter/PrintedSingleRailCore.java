// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Positive two-dimensional filled area; used only with a complete owned rail. */
final class PrintedSingleRailCore {
    private PrintedSingleRailCore() {}

    static boolean proved(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float centerX,
            float centerY,
            float gap) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || gap < 6
                || left < 0
                || top < 0
                || right < left
                || bottom < top
                || right >= width
                || bottom >= height
                || !Float.isFinite(centerX)
                || !Float.isFinite(centerY)
                || centerX < left
                || centerX > right
                || centerY < top
                || centerY > bottom) return false;
        float w = right - left + 1, h = bottom - top + 1;
        if (w < gap * 1.05f || h < gap * .75f || w > gap * 1.9f || h > gap * 1.4f) return false;
        int[] shades = new int[256];
        int total = 0, margin = Math.round(gap);
        for (int y = Math.max(0, top - margin); y <= Math.min(height - 1, bottom + margin); y++)
            for (int x = Math.max(0, left - margin);
                    x <= Math.min(width - 1, right + margin);
                    x++) {
                shades[gray[y * width + x] & 255]++;
                total++;
            }
        int paper = 255, seen = 0;
        for (int value = 0; value < 256; value++)
            if ((seen += shades[value]) >= total * .8) {
                paper = value;
                break;
            }
        if (paper < 210) return false;
        int count = 0, filled = 0;
        for (int y = Math.max(0, Math.round(centerY - h * .25f));
                y <= Math.min(height - 1, Math.round(centerY + h * .25f));
                y++)
            for (int x = Math.max(0, Math.round(centerX - w * .2f));
                    x <= Math.min(width - 1, Math.round(centerX + w * .2f));
                    x++) {
                int value = gray[y * width + x] & 255;
                count++;
                if (value <= paper - 25) filled++;
            }
        // Shaft and staff crossings cannot fill nearly all of this two-dimensional area.
        return count >= 12 && filled >= count * .95f;
    }
}

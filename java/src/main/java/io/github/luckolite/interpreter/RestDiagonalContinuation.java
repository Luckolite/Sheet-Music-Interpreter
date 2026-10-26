// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A cropped glissando is not a rest if its oblique stroke continues at both ends. */
final class RestDiagonalContinuation {
    private RestDiagonalContinuation() {}

    static boolean crosses(
            byte[] gray, int width, int left, int right, int minY, int maxY, float gap) {
        return crosses(gray, width, left, right, minY, maxY, gap, null);
    }

    static boolean crosses(
            byte[] gray,
            int width,
            int left,
            int right,
            int minY,
            int maxY,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (gray == null || width < 1 || gray.length % width != 0 || gap < 4 || maxY - minY < gap)
            return false;
        int height = gray.length / width;
        if (left < 0 || right >= width || minY < 0 || maxY >= height) return false;
        // A diagonal drawn through a legitimate hook is insufficient: the whole
        // oblique path, including well beyond each glyph end, must be present.
        int radius = Math.max(1, Math.round(gap * .10f));
        int span = Math.max(4, Math.round(gap * .8f)), skip = Math.max(1, Math.round(gap * .2f));
        int centerY = (minY + maxY) / 2, shift = Math.round(gap * .2f);
        float centerX = (left + right) * .5f;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int offset = -shift; offset <= shift; offset++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int step = 4; step <= 20; step++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        float slope = -step * .1f, x = centerX + offset;
                        if (supported(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        centerY,
                                        slope,
                                        minY - centerY,
                                        maxY - centerY,
                                        radius,
                                        quarterBudget)
                                && supported(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        centerY,
                                        slope,
                                        minY - centerY - span,
                                        minY - centerY - skip,
                                        radius,
                                        quarterBudget)
                                && supported(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        centerY,
                                        slope,
                                        maxY - centerY + skip,
                                        maxY - centerY + span,
                                        radius,
                                        quarterBudget)) return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean supported(
            byte[] gray,
            int width,
            int height,
            float x,
            int y,
            float slope,
            int first,
            int last,
            int radius) {
        return supported(gray, width, height, x, y, slope, first, last, radius, null);
    }

    private static boolean supported(
            byte[] gray,
            int width,
            int height,
            float x,
            int y,
            float slope,
            int first,
            int last,
            int radius,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int dark = 0, total = last - first + 1;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int dy = first; dy <= last; dy++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                int yy = y + dy, xx = Math.round(x + slope * dy);
                if (yy < 0 || yy >= height || xx - radius < 0 || xx + radius >= width) return false;
                boolean ink = false;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if ((gray[yy * width + xx + dx] & 255) < 170) {
                            ink = true;
                            break;
                        }
                    }
                }

                if (ink) dark++;
            }
        }

        return dark >= total * .8f;
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.function.LongConsumer;

/** Literal foreground evidence, never a replacement raster or an ownership certificate. */
final class PrintedBeamRawContrast {
    private PrintedBeamRawContrast() {}

    private static void check(
            byte[] raw, int width, int height, int x, int y, double gap, LongConsumer charge) {
        if (raw == null
                || width < 3
                || height < 3
                || (long) width * height != raw.length
                || x < 0
                || x >= width
                || y < 0
                || y >= height
                || !Double.isFinite(gap)
                || gap < 3
                || gap > 64
                || charge == null) throw new IllegalArgumentException("Invalid raw contrast query");
    }

    /** A short crossing needs a proved orthogonal ink band and eligible approaches
     * on BOTH sides. Gray paper alone is not intervening ink. */
    static boolean crossing(
            byte[] raw, int width, int height, int x, int y, double gap, LongConsumer charge) {
        check(raw, width, height, x, y, gap, charge);
        int value = raw[y * width + x] & 255;
        if (value < 165 || value > 229) return false;
        int distance = Math.max(1, (int) Math.ceil(gap * .3));
        int radius = Math.max(2, (int) Math.ceil(gap * .75));
        if (y - distance < 0 || y + distance >= height || x - radius < 0 || x + radius >= width)
            return false;
        charge.accept(2L * (8L * radius + 16) + 2L * radius + 1 + 2L * distance + 16);
        int left = raw[y * width + x - radius] & 255, right = raw[y * width + x + radius] & 255;
        if (left > 229
                || right > 229
                || !section(raw, width, height, x - radius, y, distance, gap, false, false)
                || !section(raw, width, height, x + radius, y, distance, gap, false, false))
            return false;
        int ceiling = Math.max(left, right);
        for (int col = x - radius; col <= x + radius; col++)
            if ((raw[y * width + col] & 255) > ceiling) return false;
        int above = 0, below = 0;
        for (int d = 1; d <= distance && above == 0; d++) {
            int v = raw[(y - d) * width + x] & 255;
            if (v > 229) return false;
            if (v < 165 || faint(raw, width, height, x, y - d, gap, true, charge)) above = d;
        }
        if (above == 0) return false;
        for (int d = 1; d <= distance && below == 0; d++) {
            int v = raw[(y + d) * width + x] & 255;
            if (v > 229) return false;
            if (v < 165 || faint(raw, width, height, x, y + d, gap, true, charge)) below = d;
        }
        return below != 0 && above + below - 1 <= distance;
    }

    /** Candidate-only head neck support. The caller must still authenticate the
     * strong body and the final literal head-to-shaft connected component. */
    static boolean neck(
            byte[] raw, int width, int height, int x, int y, double gap, LongConsumer charge) {
        check(raw, width, height, x, y, gap, charge);
        int value = raw[y * width + x] & 255;
        if (value < 165 || value > 229) return false;
        int radius = Math.max(2, (int) Math.ceil(gap * 1.25));
        charge.accept(8L * radius + 16);
        if (x - radius < 0 || x + radius >= width) return false;
        int base = y * width + x;
        int paper = Math.min(raw[base - radius] & 255, raw[base + radius] & 255);
        if (paper < 210 || paper - value < 30) return false;
        int threshold = paper - 30, left = 0, right = 0;
        while (left > -radius && (raw[base + left - 1] & 255) <= threshold) left--;
        while (right < radius && (raw[base + right + 1] & 255) <= threshold) right++;
        return left > -radius && right < radius && right - left + 1 <= Math.ceil(gap * 1.8);
    }

    static boolean faint(
            byte[] raw,
            int width,
            int height,
            int x,
            int y,
            double gap,
            boolean shaft,
            LongConsumer charge) {
        check(raw, width, height, x, y, gap, charge);
        int value = raw[y * width + x] & 255;
        if (value < 165 || value > 229) return false;
        int radius = Math.max(2, (int) Math.ceil(gap * .75));
        // Fixed bounded prepayment covers both complete cross sections before any reads.
        // Shaft evidence uses only the horizontal normal: a staff rule is not a stem.
        charge.accept((shaft ? 1L : 2L) * (8L * radius + 16));
        return section(raw, width, height, x, y, radius, gap, true, shaft)
                || !shaft && section(raw, width, height, x, y, radius, gap, false, false);
    }

    private static boolean section(
            byte[] raw,
            int width,
            int height,
            int x,
            int y,
            int radius,
            double gap,
            boolean horizontal,
            boolean shaft) {
        int center = horizontal ? x : y, limit = horizontal ? width : height;
        if (center - radius < 0 || center + radius >= limit) return false;
        int stride = horizontal ? 1 : width, base = y * width + x;
        int paper = Math.min(raw[base - radius * stride] & 255, raw[base + radius * stride] & 255);
        if (paper < 210) return false;
        int minimum = 255;
        for (int d = -radius + 1; d < radius; d++)
            minimum = Math.min(minimum, raw[base + d * stride] & 255);
        if (minimum > 229 || paper - minimum < 30) return false;
        int doubledThreshold = paper + minimum;
        if (2 * (raw[base] & 255) >= doubledThreshold) return false;
        int left = 0, right = 0;
        while (left > -radius && 2 * (raw[base + (left - 1) * stride] & 255) < doubledThreshold)
            left--;
        while (right < radius && 2 * (raw[base + (right + 1) * stride] & 255) < doubledThreshold)
            right++;
        if (left == -radius
                || right == radius
                || right - left + 1 > Math.max(3, Math.ceil(gap * (shaft ? .35 : 1)))) return false;
        // This is a literal component, not uniqueness or ownership. Other transverse
        // components remain visible to the complete shaft/strip rival-family checks.
        // In particular, a nearby authenticated head is not a competing stem merely
        // because its ink appears in this normal cross section.
        return true;
    }
}

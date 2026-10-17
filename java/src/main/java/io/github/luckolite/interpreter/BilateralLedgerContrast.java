// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Measure a pale inner ledger independently from the darker oval above or below it. */
final class BilateralLedgerContrast {
    static boolean proved(
            byte[] gray, int width, int height, int left, int right, float expectedY, float gap) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || !Float.isFinite(expectedY)
                || !Float.isFinite(gap)
                || gap < 6
                || gap > Math.min(width, height)
                || left < 0
                || right >= width
                || right < left) return false;
        int flank = Math.max(2, Math.round(gap * .32f));
        int reach = Math.max(3, Math.round(gap * .55f));
        int minimum = Math.max(2, Math.round(gap * .18f));
        for (int y = Math.max(flank + 1, Math.round(expectedY - gap * .25f));
                y <= Math.min(height - flank - 2, Math.round(expectedY + gap * .25f));
                y++) {
            int a = flankInk(gray, width, left, -1, y, reach, minimum, flank);
            int b = flankInk(gray, width, right, 1, y, reach, minimum, flank);
            if (a < 0 || b < 0) continue;
            int limit = Math.min(245, Math.max(a & 255, b & 255) + 6);
            boolean connected = true;
            int crossing = 0, maximumCrossing = Math.max(1, Math.round(gap * .18f));
            for (int x = left - (a >>> 8); x <= right + (b >>> 8); x++) {
                int ink = core(gray, width, x, y);
                boolean thin =
                        (gray[(y - flank) * width + x] & 255) >= ink + 8
                                && (gray[(y + flank) * width + x] & 255) >= ink + 8;
                crossing = thin ? 0 : crossing + 1;
                if (ink > limit || crossing > maximumCrossing) {
                    connected = false;
                    break;
                }
            }
            if (connected) return true;
        }
        return false;
    }

    private static int flankInk(
            byte[] gray,
            int width,
            int edge,
            int direction,
            int y,
            int reach,
            int minimum,
            int flank) {
        int run = 0, sum = 0;
        for (int d = 1; d <= reach; d++) {
            int x = edge + direction * d;
            if (x < 0 || x >= width) break;
            int ink = core(gray, width, x, y);
            boolean thin =
                    ink <= 240
                            && (gray[(y - flank) * width + x] & 255) >= ink + 12
                            && (gray[(y + flank) * width + x] & 255) >= ink + 12;
            if (!thin) {
                run = 0;
                sum = 0;
            } else {
                run++;
                sum += ink;
            }
            // Pack the sampled ink and the last supported flank column together.
            if (run >= minimum) return (d << 8) | Math.round(sum / (float) run);
        }
        return -1;
    }

    private static int core(byte[] gray, int width, int x, int y) {
        return Math.min(
                gray[y * width + x] & 255,
                Math.min(gray[(y - 1) * width + x] & 255, gray[(y + 1) * width + x] & 255));
    }
}

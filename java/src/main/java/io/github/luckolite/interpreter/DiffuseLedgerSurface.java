// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A continuously shaded body and surround cannot supply a thin protruding ledger rule. */
final class DiffuseLedgerSurface {
    static boolean matches(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float centerY,
            float gap) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || gap < 6
                || !Float.isFinite(centerY)
                || right - left < 5
                || bottom - top < 5) return false;
        int margin = Math.max(2, Math.min(right - left + 1, bottom - top + 1) / 2);
        if (left <= margin
                || top <= margin
                || right + margin + 1 >= width
                || bottom + margin + 1 >= height) return false;
        // Keep the established shading thresholds; require continuity through every side of the
        // body.
        if (!SmoothLedgerBackground.matches(
                gray, width, height, left - margin, top - margin, right + margin, bottom + margin))
            return false;
        // A pale filled or hollow oval can own itself when a printed ledger is occluded.
        if (boundedBody(gray, width, left, top, right, bottom, margin)) return false;
        return !thinLedger(gray, width, height, left, right, centerY, gap);
    }

    private static boolean boundedBody(
            byte[] gray, int width, int left, int top, int right, int bottom, int margin) {
        int[] body = new int[256];
        int count = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                body[gray[y * width + x] & 255]++;
                count++;
            }
        int ink = percentile(body, count, 25);
        int[] sides = new int[4];
        int proved = 0;
        for (int side = 0; side < 4; side++) {
            int[] outside = new int[256];
            int samples = 0;
            for (int d = -1; d <= 1; d++) {
                if (side < 2) {
                    int x = (side == 0 ? left - margin : right + margin) + d;
                    for (int y = top; y <= bottom; y++) {
                        outside[gray[y * width + x] & 255]++;
                        samples++;
                    }
                } else {
                    int y = (side == 2 ? top - margin : bottom + margin) + d;
                    for (int x = left; x <= right; x++) {
                        outside[gray[y * width + x] & 255]++;
                        samples++;
                    }
                }
            }
            sides[side] = percentile(outside, samples, 50);
            if (sides[side] >= ink + 12) proved++;
        }
        if (proved == 4) return true;
        if (proved != 3) return false;
        for (int side = 0; side < 4; side++) {
            if (sides[side] > ink) continue;
            int far =
                    side == 0
                            ? left - 3 * margin
                            : side == 1
                                    ? right + 3 * margin
                                    : side == 2 ? top - 3 * margin : bottom + 3 * margin;
            int limit = side < 2 ? width : gray.length / width;
            // Three owned sides cannot support rejection when the fourth witness is off-page.
            if (far - 1 < 0 || far + 1 >= limit) return true;
            if (boundedNeighbor(gray, width, left, top, right, bottom, margin, side, sides[side]))
                return true;
        }
        return false;
    }

    /** A separate dark glyph can obscure one side of an otherwise bounded pale oval. */
    private static boolean boundedNeighbor(
            byte[] gray,
            int width,
            int left,
            int top,
            int right,
            int bottom,
            int margin,
            int side,
            int near) {
        int height = gray.length / width;
        int x = side == 0 ? left - margin : right + margin;
        int y = side == 2 ? top - margin : bottom + margin;
        if (side < 2) {
            int far = side == 0 ? left - 3 * margin : right + 3 * margin;
            return median(gray, width, height, far - 1, top, far + 1, bottom) >= near + 12
                    && median(gray, width, height, x - 1, top - margin - 1, x + 1, top - margin + 1)
                            >= near + 12
                    && median(
                                    gray,
                                    width,
                                    height,
                                    x - 1,
                                    bottom + margin - 1,
                                    x + 1,
                                    bottom + margin + 1)
                            >= near + 12;
        }
        int far = side == 2 ? top - 3 * margin : bottom + 3 * margin;
        return median(gray, width, height, left, far - 1, right, far + 1) >= near + 12
                && median(gray, width, height, left - margin - 1, y - 1, left - margin + 1, y + 1)
                        >= near + 12
                && median(gray, width, height, right + margin - 1, y - 1, right + margin + 1, y + 1)
                        >= near + 12;
    }

    private static int median(
            byte[] gray, int width, int height, int left, int top, int right, int bottom) {
        if (left < 0 || top < 0 || right >= width || bottom >= height) return -1;
        int[] values = new int[256];
        int total = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                values[gray[y * width + x] & 255]++;
                total++;
            }
        return percentile(values, total, 50);
    }

    private static int percentile(int[] values, int total, int percent) {
        int target = Math.max(1, (total * percent + 99) / 100), count = 0;
        for (int value = 0; value < 256; value++) {
            count += values[value];
            if (count >= target) return value;
        }
        return 255;
    }

    private static boolean thinLedger(
            byte[] gray, int width, int height, int left, int right, float centerY, float gap) {
        int flank = Math.max(2, Math.round(gap * .35f));
        int reach = Math.max(3, Math.round(gap * .65f));
        int needed = Math.max(2, Math.round(gap * .18f));
        int maximumThickness = Math.max(2, Math.round(gap * .25f));
        for (int y = Math.max(flank, Math.round(centerY - gap * .9f));
                y <= Math.min(height - flank - 1, Math.round(centerY + gap * .9f));
                y++) {
            int a = 0, b = 0;
            for (int distance = 1; distance <= reach; distance++) {
                if (left - distance >= 0
                        && thin(gray, width, left - distance, y, flank, maximumThickness)) a++;
                if (right + distance < width
                        && thin(gray, width, right + distance, y, flank, maximumThickness)) b++;
            }
            if (a >= needed && b >= needed) return true;
        }
        return false;
    }

    private static boolean thin(byte[] gray, int width, int x, int y, int flank, int maximum) {
        int ink = gray[y * width + x] & 255;
        int edge =
                Math.min(gray[(y - flank) * width + x] & 255, gray[(y + flank) * width + x] & 255);
        if (ink > 245 || edge < ink + 8) return false;
        int cut = (ink + edge) / 2, lo = y, hi = y;
        while (lo > y - flank && (gray[(lo - 1) * width + x] & 255) <= cut) lo--;
        while (hi < y + flank && (gray[(hi + 1) * width + x] & 255) <= cut) hi++;
        return hi - lo + 1 <= maximum;
    }
}

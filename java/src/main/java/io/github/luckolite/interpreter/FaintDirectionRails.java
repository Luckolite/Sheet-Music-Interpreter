// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Five raw rules need bilateral contrast and consistent phase, independent of absolute exposure. */
final class FaintDirectionRails {
    static boolean valid(byte[] gray, int width, int height, float x, float hint, float gap) {
        return gray != null
                && width > 0
                && height > 0
                && gray.length == (long) width * height
                && Float.isFinite(x)
                && x >= 0
                && x < width
                && Float.isFinite(hint)
                && hint >= 0
                && hint < height
                && Float.isFinite(gap)
                && gap >= 6
                && gap <= Math.min(width, height) / 8f;
    }

    static float[] read(
            byte[] gray, int width, int height, float x, float hint, float gap, float radiusGaps) {
        if (!valid(gray, width, height, x, hint, gap)
                || !Float.isFinite(radiusGaps)
                || radiusGaps < 3.5f
                || radiusGaps > 5.5f) return null;
        int radius = Math.round(gap * radiusGaps), center = Math.round(x);
        int left = center - radius, right = center + radius;
        int top = (int) Math.floor(hint - gap * 5.5f), bottom = (int) Math.ceil(hint + gap * 1.5f);
        if (left < 0 || right >= width || top < 0 || bottom >= height) return null;
        int w = right - left + 1,
                h = bottom - top + 1,
                exclusion = Math.max(2, Math.round(gap * .5f));
        float[] accepted = null;
        for (float slope : new float[] {0, .04f, -.04f, .08f, -.08f, .12f, -.12f, .16f, -.16f}) {
            byte[] crop = new byte[w * h];
            java.util.Arrays.fill(crop, (byte) 255);
            boolean valid = true;
            for (int xx = left; xx <= right; xx++) {
                int dy = Math.round((xx - x) * slope);
                if (top + dy < 0 || bottom + dy >= height) {
                    valid = false;
                    break;
                }
                for (int y = top; y <= bottom; y++)
                    crop[(y - top) * w + xx - left] = gray[(y + dy) * width + xx];
            }
            if (!valid) continue;
            // Written-head floors inherit a small staff-gap rounding error at remote steps.
            // Every allowed reference offset must still converge on the same complete raw rails.
            for (float shift : new float[] {0, -gap * .2f, gap * .2f}) {
                var found = broadStraightPitch(crop, w, h, hint - top + shift, gap, false);
                if (found == null) continue;
                int flank = Math.max(2, Math.round(gap * .3f)),
                        band = Math.max(1, Math.round(gap * .2f));
                boolean bilateral = true;
                for (int line = 0; line < 5 && bilateral; line++)
                    for (int side : new int[] {-1, 1}) {
                        int samples = 0, hits = 0;
                        for (int dx = exclusion + 1; dx <= radius; dx++) {
                            int xx = center + side * dx - left;
                            samples++;
                            boolean ink = false;
                            int cy = Math.round(found[0] - line * found[1]);
                            for (int y = cy - band; y <= cy + band; y++) {
                                if (y < flank || y + flank >= h) continue;
                                int tone = crop[y * w + xx] & 255;
                                if ((crop[(y - flank) * w + xx] & 255) > tone + 12
                                        && (crop[(y + flank) * w + xx] & 255) > tone + 12) {
                                    ink = true;
                                    break;
                                }
                            }
                            if (ink) hits++;
                        }
                        if (samples < 8 || hits < samples * .6f) {
                            bilateral = false;
                            break;
                        }
                    }
                if (!bilateral) continue;
                found[0] += top;
                if (accepted != null
                        && (Math.abs(accepted[0] - found[0]) > gap * .2f
                                || Math.abs(accepted[1] - found[1]) > gap * .08f)) return null;
                accepted = found;
            }
        }
        return accepted;
    }

    static float[] broadStraightPitch(
            byte[] gray, int width, int height, float bottom, float gap, boolean correctionOnly) {
        if (gray == null || gap < 8) return null;
        float[] rows = new float[5];
        int flank = Math.max(2, Math.round(gap * .3f));
        int radius = Math.max(2, Math.round(gap * .35f));
        for (int line = 0; line < 5; line++) {
            int center = Math.round(bottom - line * gap),
                    first = center - radius,
                    last = center + radius;
            if (first < flank || last >= height - flank) return null;
            int[] support = new int[last - first + 1];
            int peak = 0, at = 0;
            for (int y = first; y <= last; y++) {
                for (int x = 0; x < width; x++) {
                    int ink = gray[y * width + x] & 255;
                    if ((gray[(y - flank) * width + x] & 255) > ink + 12
                            && (gray[(y + flank) * width + x] & 255) > ink + 12)
                        support[y - first]++;
                }
                if (support[y - first] > peak) {
                    peak = support[y - first];
                    at = y - first;
                }
            }
            if (peak < width * .55f) return null;
            int lo = at, hi = at;
            while (lo > 0 && support[lo - 1] >= peak * .85f) lo--;
            while (hi + 1 < support.length && support[hi + 1] >= peak * .85f) hi++;
            if (hi - lo > Math.max(2, gap * .25f)) return null;
            double weight = 0, total = 0;
            for (int i = lo; i <= hi; i++) {
                weight += support[i];
                total += (first + i) * (double) support[i];
            }
            rows[line] = (float) (total / weight);
        }
        float spacing = (rows[0] - rows[4]) / 4;
        if (correctionOnly && Math.abs(spacing - gap) < gap * .035f
                || Math.abs(spacing - gap) > gap * .08f
                || Math.abs(rows[0] - bottom) > gap * .3f) return null;
        for (int i = 1; i < 5; i++)
            if (Math.abs(rows[i] - (rows[0] - spacing * i)) > gap * .12f) return null;
        return new float[] {rows[0], spacing};
    }
}

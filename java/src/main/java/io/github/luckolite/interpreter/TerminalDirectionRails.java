// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Complete strict rail window inside a proved terminal written domain. */
final class TerminalDirectionRails {
    private TerminalDirectionRails() {}

    static float[] read(
            byte[] gray,
            int width,
            int height,
            float x,
            float hint,
            float gap,
            float regionLeft,
            float regionRight) {
        if (!FaintDirectionRails.valid(gray, width, height, x, hint, gap)
                || !Float.isFinite(regionLeft)
                || !Float.isFinite(regionRight)
                || regionLeft < 0
                || regionRight > 1
                || regionLeft >= regionRight
                || x < regionLeft * width
                || x > regionRight * width) return null;
        int radius = Math.round(gap * 3.5f);
        int right = (int) Math.floor(regionRight * width);
        int left = right - 2 * radius, center = left + radius;
        int query = Math.round(x);
        if (left < Math.ceil(regionLeft * width) || query < center || query > right) return null;
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
                var found =
                        StaffPitchTrack.broadStraightPitch(
                                crop, w, h, hint - top + shift, gap, false);
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
                                if (tone < 170
                                        && (crop[(y - flank) * w + xx] & 255) > tone + 20
                                        && (crop[(y + flank) * w + xx] & 255) > tone + 20) {
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
                // The shifted slab cannot lend a remote staff to an unprinted query.
                // Require each of its five measured rails at the actual glyph column,
                // and unchanged 60% contrast support across the complete intervening link.
                boolean linked = true;
                for (int line = 0; line < 5 && linked; line++) {
                    int samples = 0, hits = 0;
                    boolean queryInk = false;
                    int cy = Math.round(found[0] - line * found[1]);
                    for (int xx = center; xx <= query; xx++) {
                        samples++;
                        boolean ink = false;
                        for (int y = cy - band; y <= cy + band; y++) {
                            if (y < flank || y + flank >= h) continue;
                            int tone = crop[y * w + xx - left] & 255;
                            if (tone < 170
                                    && (crop[(y - flank) * w + xx - left] & 255) > tone + 20
                                    && (crop[(y + flank) * w + xx - left] & 255) > tone + 20) {
                                ink = true;
                                break;
                            }
                        }
                        if (ink) hits++;
                        if (xx == query) queryInk = ink;
                    }
                    if (!queryInk || samples < 8 || hits < samples * .6f) linked = false;
                }
                if (!linked) continue;
                found[0] += top;
                if (accepted != null
                        && (Math.abs(accepted[0] - found[0]) > gap * .2f
                                || Math.abs(accepted[1] - found[1]) > gap * .08f)) return null;
                accepted = found;
            }
        }
        return accepted;
    }
}

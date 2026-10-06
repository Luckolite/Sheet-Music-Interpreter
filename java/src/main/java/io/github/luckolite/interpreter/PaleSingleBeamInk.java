// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Thick near-straight beam evidence, independent from the faint shaft that reaches it. */
final class PaleSingleBeamInk {
    static boolean supports(byte[] g, int w, int h, int[] stem, float gap) {
        if (g == null
                || w <= 0
                || h <= 0
                || g.length != (long) w * h
                || stem == null
                || stem.length < 3
                || gap < 8
                || !Float.isFinite(gap)
                || stem[0] < 0
                || stem[0] >= w
                || stem[1] < 0
                || stem[1] >= h
                || Math.abs(stem[2]) != 1) return false;
        int t = Math.max(1, Math.round(stem[1] - gap * (stem[2] < 0 ? .35f : 1.85f))),
                b = Math.min(h - 2, Math.round(stem[1] + gap * (stem[2] < 0 ? 1.85f : .35f)));
        int thick = Math.max(4, (int) Math.ceil(gap * .28f)),
                near = Math.max(2, Math.round(gap * .25f)),
                far = Math.round(gap * 1.75f);
        float[] slopes = null;
        for (int side : new int[] {-1, 1})
            for (int y = t; y <= b; y++) {
                if (slopes == null) slopes = new float[] {-.5f, -.35f, -.2f, 0, .2f, .35f, .5f};
                for (float slope : slopes) {
                    int hits = 0, total = 0;
                    for (int d = near; d <= far; d++) {
                        int x = stem[0] + side * d, yy = Math.round(y + slope * d);
                        if (x < 0 || x >= w || yy < 0 || yy + thick >= h) {
                            total = 100000;
                            break;
                        }
                        total++;
                        boolean ink = true;
                        for (int dy = 0; dy < thick; dy++)
                            if ((g[(yy + dy) * w + x] & 255) >= 165) {
                                ink = false;
                                break;
                            }
                        if (ink) hits++;
                    }
                    if (total > 0 && hits >= total * .9f) return true;
                }
            }
        return false;
    }
}

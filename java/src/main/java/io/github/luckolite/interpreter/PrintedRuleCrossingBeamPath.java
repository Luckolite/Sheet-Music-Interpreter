// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independently sampled printed rules bound the ink budget at a rail crossing. */
final class PrintedRuleCrossingBeamPath {
    static boolean connects(
            byte[] labels,
            byte[] g,
            int w,
            int h,
            int[] a,
            int[] b,
            float gap,
            float staffTop,
            StaffPitchTrack track) {
        if (labels == null
                || g == null
                || labels.length != g.length
                || g.length != (long) w * h
                || w < 3
                || h < 3
                || a == null
                || b == null
                || a.length != 3
                || b.length != 3
                || Math.abs(a[2]) != 1
                || a[2] != b[2]
                || !Float.isFinite(gap)
                || gap < 8
                || !Float.isFinite(staffTop)) return false;
        if (a[0] > b[0]) {
            int[] t = a;
            a = b;
            b = t;
        }
        int span = b[0] - a[0];
        if (span < gap * 2 || span > gap * 5 || Math.abs(a[1] - b[1]) > gap * .75f) return false;
        int margin = Math.max(3, Math.round(gap * .3f)),
                radius = Math.round(gap),
                reach = Math.max(2, Math.round(gap * .5f));
        boolean[] prev = new boolean[h];
        boolean first = true, crossed = false;
        for (int x = a[0] + margin; x <= b[0] - margin; x++) {
            int e = Math.round(a[1] + (b[1] - a[1]) * (x - a[0]) / (float) span);
            if (x < 1 || x >= w - 1 || e - radius - reach < 0 || e + radius + reach >= h)
                return false;
            int[] values = new int[radius * 2 + 1];
            for (int d = -radius; d <= radius; d++) values[d + radius] = g[(e + d) * w + x] & 255;
            Arrays.sort(values);
            int paper = values[values.length * 4 / 5];
            if (paper < 225) return false;
            int limit = Math.min(205, paper - 30);
            float[] f = track == null ? new float[] {staffTop + 4 * gap, gap} : track.at(x);
            if (f == null
                    || !Float.isFinite(f[0])
                    || !Float.isFinite(f[1])
                    || f[1] < gap * .8f
                    || f[1] > gap * 1.2f) return false;
            float top = f[0] - 4 * f[1];
            boolean[] cur = new boolean[h];
            boolean any = false;
            for (int y = e - reach; y <= e + reach; y++) {
                if ((g[(y - 1) * w + x] & 255) >= limit
                        || (g[y * w + x] & 255) >= limit
                        || (g[(y + 1) * w + x] & 255) >= limit) continue;
                int t = y, bot = y;
                while (t > y - radius && (g[(t - 1) * w + x] & 255) < limit) t--;
                while (bot < y + radius && (g[(bot + 1) * w + x] & 255) < limit) bot++;
                int thick = bot - t + 1, body = thick;
                if (thick > gap * 1.2f) continue;
                if (thick > gap * .8f) {
                    int line = Math.round(((t + bot) * .5f - top) / f[1]);
                    if (line < 0 || line > 4) continue;
                    float[] p = new float[4], ww = new float[4];
                    int n = 0;
                    for (int j = 0; j < 5; j++)
                        if (j != line) {
                            float[] r = rule(g, w, h, x, top + j * f[1], f[1], limit);
                            if (r != null) {
                                p[n] = r[0] - (top + j * f[1]);
                                ww[n++] = r[1];
                            }
                        }
                    if (n < 3) continue;
                    float[] sorted = Arrays.copyOf(p, n);
                    Arrays.sort(sorted);
                    float phase = sorted[n / 2];
                    int n2 = 0;
                    for (int i = 0; i < n; i++)
                        if (Math.abs(p[i] - phase) <= Math.max(1, gap * .1f)) ww[n2++] = ww[i];
                    if (n2 < 3) continue;
                    Arrays.sort(ww, 0, n2);
                    float width = ww[n2 / 2], center = top + line * f[1] + phase;
                    int lo = (int) Math.ceil(center - width * .5f),
                            hi = (int) Math.floor(center + width * .5f);
                    if (lo > bot || hi < t) continue;
                    body = thick - Math.max(0, Math.min(bot, hi) - Math.max(t, lo) + 1);
                }
                boolean otherSemanticRail = false;
                int beamTop = Math.max(0, e - Math.round(gap * (a[2] < 0 ? .2f : 2.2f)));
                int beamBottom = Math.min(h - 1, e + Math.round(gap * (a[2] < 0 ? 2.2f : .2f)));
                for (int yy = beamTop; yy <= beamBottom; yy++)
                    if ((yy < t || yy > bot)
                            && labels[yy * w + x] == OmrMeasurePostProcessor.SYMBOL)
                        otherSemanticRail = true;
                if (otherSemanticRail) continue;
                if (body < Math.max(4, Math.ceil(gap * .25f)) || body > gap * .8f) continue;
                if (first || prev[y - 1] || prev[y] || prev[y + 1]) {
                    cur[y] = true;
                    any = true;
                    if (thick > gap * .8f) crossed = true;
                }
            }
            if (!any) return false;
            prev = cur;
            first = false;
        }
        return !first && crossed;
    }

    private static float[] rule(byte[] g, int w, int h, int x, float seed, float gap, int limit) {
        int lo = Math.max(1, Math.round(seed - gap * .6f)),
                hi = Math.min(h - 2, Math.round(seed + gap * .6f)),
                start = -1;
        float best = Float.POSITIVE_INFINITY;
        float[] found = null;
        for (int y = lo; y <= hi + 1; y++) {
            boolean ink = y <= hi && (g[y * w + x] & 255) < limit;
            if (ink && start < 0) start = y;
            if (!ink && start >= 0) {
                float cy = (start + y - 1) * .5f;
                int dark = 255;
                for (int yy = start; yy < y; yy++) dark = Math.min(dark, g[yy * w + x] & 255);
                if (start > lo + 1
                        && y < hi
                        && y - start <= Math.ceil(gap * .4f)
                        && Math.abs(cy - seed) <= gap * .3f
                        && Math.abs(cy - seed) < best
                        && dark <= 145
                        && (g[(start - 2) * w + x] & 255) >= 225
                        && (g[(y + 1) * w + x] & 255) >= 225) {
                    best = Math.abs(cy - seed);
                    found = new float[] {cy, y - start};
                }
                start = -1;
            }
        }
        return found;
    }
}

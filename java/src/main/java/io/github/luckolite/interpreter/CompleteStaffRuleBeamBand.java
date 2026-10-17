// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Isolated complete staff frames corroborate a narrow rail at a cluttered beam probe. */
final class CompleteStaffRuleBeamBand {
    private CompleteStaffRuleBeamBand() {}

    private record Sample(float center, float mass, int length) {}

    static boolean matches(
            byte[] g,
            int w,
            int h,
            int x,
            float top,
            float gap,
            int threshold,
            int first,
            int last) {
        if (g == null
                || g.length != (long) w * h
                || w <= 0
                || h <= 0
                || gap < 8
                || !Float.isFinite(gap)
                || !Float.isFinite(top)
                || first < 1
                || last >= h - 1
                || last < first
                || last - first + 1 > Math.ceil(.35 * gap)) return false;
        float center = (first + last) * .5f;
        int line = Math.round((center - top) / gap);
        if (line < 0 || line > 4 || Math.abs(center - top - line * gap) > .25 * gap) return false;
        Sample near = sample(g, w, h, x, center, gap, threshold);
        if (near == null || Math.abs(near.center - center) > 1 || near.length != last - first + 1)
            return false;
        var masses = new ArrayList<Float>();
        var phases = new ArrayList<Float>();
        int left = 0, right = 0;
        for (int side : new int[] {-1, 1})
            for (int distance = 3; distance <= 18; distance++) {
                int xx = x + Math.round(side * distance * gap);
                Sample[] rails = new Sample[5];
                boolean complete = true;
                for (int k = 0; k < 5; k++) {
                    rails[k] = sample(g, w, h, xx, top + k * gap, gap, threshold);
                    if (rails[k] == null) {
                        complete = false;
                        break;
                    }
                }
                if (!complete) continue;
                float phase = 0;
                for (int k = 0; k < 5; k++) phase += rails[k].center - top - k * gap;
                phase /= 5;
                for (int k = 0; k < 5; k++)
                    if (Math.abs(rails[k].center - top - k * gap - phase) > .1 * gap)
                        complete = false;
                if (!complete || Math.abs(center - top - line * gap - phase) > .15 * gap) continue;
                int seen = 0, total = 0;
                for (int dx = Math.max(2, Math.round(gap * .5f));
                        dx < Math.abs(xx - x);
                        dx += Math.max(2, Math.round(gap * .5f))) {
                    total++;
                    Sample continued =
                            sample(
                                    g,
                                    w,
                                    h,
                                    x + side * dx,
                                    top + line * gap + phase,
                                    gap,
                                    threshold);
                    if (continued != null
                            && Math.abs(continued.center - top - line * gap - phase) <= .1 * gap)
                        seen++;
                }
                if (total < 4 || seen < total * .8f) continue;
                if (side < 0) left++;
                else right++;
                phases.add(phase);
                masses.add(rails[line].mass);
            }
        if (left < 2 || right < 2) return false;
        Collections.sort(masses);
        Collections.sort(phases);
        float phase = phases.get(phases.size() / 2);
        for (float v : phases) if (Math.abs(v - phase) > .1 * gap) return false;
        return near.mass <= masses.get(masses.size() / 2) * 1.25f
                && !finiteRail(g, w, h, x, center, gap, threshold, near);
    }

    /** A finite added rail can share the rule's phase and ink. Independent ends preserve it. */
    private static boolean finiteRail(
            byte[] g, int w, int h, int x, float center, float gap, int threshold, Sample near) {
        int witness = Math.max(4, Math.round(gap * .75f)),
                minimumBody = Math.max(2, Math.round(gap * .15f)),
                totalBody = 0,
                provedEnds = 0;
        boolean longEnd = false;
        for (int side : new int[] {-1, 1}) {
            int body = 0, missing = 0, tail = 0;
            float bodyMass = 0, tailMass = 0;
            boolean ended = false, proved = false;
            for (int dx = 1; dx <= Math.round(gap * 18); dx++) {
                Sample s = sample(g, w, h, x + side * dx, center, gap, threshold);
                if (s == null) {
                    if (++missing > Math.max(1, Math.round(gap * .35f))) break;
                    continue;
                }
                missing = 0;
                boolean sameBody = s.length >= near.length && s.mass >= near.mass * .9f;
                if (!ended && sameBody) {
                    body++;
                    bodyMass += s.mass;
                    continue;
                }
                if (!ended) {
                    if (body < minimumBody || s.length >= near.length || s.mass >= near.mass * .9f)
                        break;
                    ended = true;
                }
                if (sameBody || s.length >= near.length) break;
                tail++;
                tailMass += s.mass;
                if (tail >= witness) {
                    proved = bodyMass / Math.max(1, body) > tailMass / tail * 1.10f;
                    break;
                }
            }
            if (proved) {
                provedEnds++;
                totalBody += body;
                longEnd |= body >= Math.round(gap * .85f);
            }
        }
        return longEnd || provedEnds == 2 && totalBody >= Math.round(gap * .85f);
    }

    private static Sample sample(
            byte[] g, int w, int h, int x, float seed, float gap, int threshold) {
        int lo = Math.round(seed - .48f * gap), hi = Math.round(seed + .48f * gap);
        if (x < 1 || x >= w - 1 || lo < 1 || hi >= h - 1) return null;
        int[] p = new int[hi - lo + 1];
        for (int y = lo; y <= hi; y++) p[y - lo] = g[y * w + x] & 255;
        Arrays.sort(p);
        int paper = p[(p.length - 1) * 85 / 100];
        if (paper < 60) return null;
        int limit = Math.min(threshold, paper - 24), start = -1;
        Sample best = null;
        for (int y = lo; y <= hi + 1; y++) {
            boolean ink = y <= hi && (g[y * w + x] & 255) < limit;
            if (ink && start < 0) start = y;
            if (!ink && start >= 0) {
                int n = y - start;
                float c = (start + y - 1) * .5f;
                int fringe = Math.max(1, Math.round(gap * .12f));
                int above = start - fringe, below = y - 1 + fringe;
                if (start > lo
                        && y <= hi
                        && n >= 2
                        && n <= Math.ceil(.35f * gap)
                        && Math.abs(c - seed) <= .25f * gap
                        && above >= 0
                        && below < h
                        && (g[above * w + x] & 255) >= limit + 8
                        && (g[below * w + x] & 255) >= limit + 8) {
                    float mass = 0;
                    for (int yy = start; yy < y; yy++)
                        mass += Math.max(0, paper - (g[yy * w + x] & 255));
                    if (best == null || Math.abs(c - seed) < Math.abs(best.center - seed))
                        best = new Sample(c, mass, n);
                }
                start = -1;
            }
        }
        return best;
    }
}

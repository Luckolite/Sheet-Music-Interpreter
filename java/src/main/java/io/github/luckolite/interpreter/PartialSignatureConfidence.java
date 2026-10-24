// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/**
 * Source-bound partial-accidental confidence. No complete sign, ink ownership, or note admission.
 */
final class PartialSignatureConfidence {
    enum State {
        PARTIAL_POSSIBLE_SHARP,
        NO_OBSERVED_PAIR,
        UNKNOWN
    }

    record Read(int index, byte gray, byte label) {}

    static final class Observation {
        final State state;
        final byte[] gray, labels;
        final int w, h;
        final List<Read> reads;
        final String reason;

        Observation(State s, byte[] g, byte[] a, int w, int h, List<Read> r, String why) {
            state = s;
            gray = g;
            labels = a;
            this.w = w;
            this.h = h;
            reads = List.copyOf(r);
            reason = why;
        }

        boolean sameSource(byte[] g, byte[] a, int width, int height) {
            if (g != gray || a != labels || w != width || h != height) return false;
            for (var r : reads) if (g[r.index] != r.gray || a[r.index] != r.label) return false;
            return true;
        }

        boolean refusesDecrease(
                int inherited,
                int proposed,
                boolean verifiedHeader,
                boolean cancellation,
                byte[] g,
                byte[] a,
                int width,
                int height) {
            return inherited > proposed
                    && proposed > 0
                    && verifiedHeader
                    && !cancellation
                    && state == State.PARTIAL_POSSIBLE_SHARP
                    && sameSource(g, a, width, height);
        }
    }

    static final class Limit extends RuntimeException {}

    static final class Work {
        long left;

        Work(long n) {
            left = n;
        }

        void use() {
            if (left-- <= 0) throw new Limit();
        }
    }

    static Observation inspect(
            byte[] g,
            byte[] a,
            int w,
            int h,
            float lastX,
            float lastPitch,
            float gap,
            float firstHead) {
        return inspect(g, a, w, h, lastX, lastPitch, gap, firstHead, 2000000);
    }

    static Observation inspect(
            byte[] g,
            byte[] a,
            int w,
            int h,
            float lastX,
            float lastPitch,
            float gap,
            float firstHead,
            long work) {
        try {
            return probe(g, a, w, h, lastX, lastPitch, gap, firstHead, new Work(work));
        } catch (Limit limit) {
            return new Observation(State.UNKNOWN, g, a, w, h, List.of(), "WORK_UNKNOWN");
        }
    }

    private static Observation probe(
            byte[] g,
            byte[] a,
            int w,
            int h,
            float lastX,
            float lastPitch,
            float gap,
            float firstHead,
            Work work) {
        if (w <= 0
                || h <= 0
                || g == null
                || a == null
                || g.length != (long) w * h
                || a.length != g.length
                || !Float.isFinite(lastX + lastPitch + gap + firstHead)
                || gap < 6
                || gap > 64)
            return new Observation(State.UNKNOWN, g, a, w, h, List.of(), "source/frame");
        var reads = new ArrayList<Read>();
        // Existing consecutive-sharp slot and pitch alternation, rather than a new header search.
        int l = Math.round(lastX + gap * .65f), r = Math.round(lastX + gap * 1.85f);
        if (firstHead - r < gap * 1.35f)
            return new Observation(State.UNKNOWN, g, a, w, h, reads, "head overlaps slot");
        for (float expected : new float[] {lastPitch + gap * 1.5f, lastPitch - gap * 2}) {
            int t = Math.round(expected - gap * 1.65f), b = Math.round(expected + gap * 1.65f);
            int reach = Math.max(1, Math.round(gap * .25f)), left = l - reach, right = r + reach;
            if (left < 0 || right >= w || t < 0 || b >= h)
                return new Observation(State.UNKNOWN, g, a, w, h, reads, "clipped slot");
            int cells = (right - left + 1) * (b - t + 1);
            if (cells > 30000)
                return new Observation(State.UNKNOWN, g, a, w, h, reads, "bounded local work");
            boolean[][] ink = new boolean[b - t + 1][right - left + 1];
            for (int y = t; y <= b; y++)
                for (int x = left; x <= right; x++) {
                    work.use();
                    int p = y * w + x;
                    reads.add(new Read(p, g[p], a[p]));
                    ink[y - t][x - left] = (g[p] & 255) < 205;
                }
            var spines = new ArrayList<Integer>();
            for (int x = l; x <= r; x++) {
                work.use();
                int count = 0, above = 0, below = 0;
                for (int y = Math.round(expected - gap * 1.2f);
                        y <= Math.round(expected + gap * 1.2f);
                        y++) {
                    work.use();
                    if (ink[y - t][x - left]) {
                        count++;
                        if (y < expected - gap * .5f) above++;
                        if (y > expected + gap * .5f) below++;
                    }
                }
                if (count >= Math.ceil(gap * 1.2f)
                        && above >= Math.ceil(gap * .2f)
                        && below >= Math.ceil(gap * .2f)) spines.add(x);
            }
            for (int x : spines)
                for (int z : spines) {
                    work.use();
                    if (z - x < gap * .35f || z - x > gap * .95f) continue;
                    var bars = new ArrayList<Integer>();
                    for (int y = Math.round(expected - gap * .9f);
                            y <= Math.round(expected + gap * .9f);
                            y++) {
                        work.use();
                        int start = x, end = z;
                        while (start > left && ink[y - t][start - left - 1]) {
                            work.use();
                            start--;
                        }
                        while (end < right && ink[y - t][end - left + 1]) {
                            work.use();
                            end++;
                        }
                        boolean bridge = true;
                        for (int xx = x; xx <= z; xx++) {
                            work.use();
                            bridge &= ink[y - t][xx - left];
                        }
                        // Literal finite bars must extend on both sides of the pair.
                        if (!bridge
                                || x - start < Math.max(1, Math.round(gap * .1f))
                                || end - z < Math.max(1, Math.round(gap * .1f))) continue;
                        if (start == left || end == right)
                            continue; // full staff/foreign continuation is not a finite bar.
                        if (end - start + 1 < gap * .65f || end - start + 1 > gap * 1.8f) continue;
                        bars.add(y);
                    }
                    for (int y : bars)
                        for (int q : bars) {
                            work.use();
                            if (q - y >= gap * .7f
                                    && q - y <= gap * 1.4f
                                    && Math.abs((y + q) * .5f - expected) <= gap * .3f) {
                                int upper = 0, middle = 0, lower = 0;
                                for (int yy = t; yy <= b; yy++) {
                                    work.use();
                                    if (!ink[yy - t][x - left] || !ink[yy - t][z - left]) continue;
                                    boolean clearGap = false;
                                    for (int xx = x + 1; xx < z; xx++) {
                                        work.use();
                                        if (!ink[yy - t][xx - left]) clearGap = true;
                                    }
                                    if (!clearGap) continue;
                                    if (yy < y - gap * .2f) upper++;
                                    else if (yy > q + gap * .2f) lower++;
                                    else if (yy > y + gap * .2f && yy < q - gap * .2f) middle++;
                                }
                                int required = (int) Math.ceil(gap * .2f);
                                if (upper < required || middle < required || lower < required)
                                    continue;
                                int anchors = 0;
                                for (int yy : new int[] {y, q})
                                    for (int xx = x; xx <= z; xx++)
                                        if (a[yy * w + xx] != 0) anchors++;
                                if (anchors < 4) continue;
                                return new Observation(
                                        State.PARTIAL_POSSIBLE_SHARP,
                                        g,
                                        a,
                                        w,
                                        h,
                                        reads,
                                        "separated partial spines in upper/middle/lower bands +paired finite bars in ordered next-sharp slot; semantic anchors="
                                                + anchors
                                                + " axes="
                                                + x
                                                + ","
                                                + z
                                                + " bars="
                                                + y
                                                + ","
                                                + q);
                            }
                        }
                }
        }
        return new Observation(
                State.NO_OBSERVED_PAIR,
                g,
                a,
                w,
                h,
                reads,
                "no positive partial-pair witness; not absence of a sharp");
    }
}

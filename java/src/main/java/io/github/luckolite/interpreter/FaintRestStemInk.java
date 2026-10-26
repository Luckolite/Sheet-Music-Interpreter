// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** A local note-fragment veto, not a Source/Current or pitch certificate. */
final class FaintRestStemInk {
    private FaintRestStemInk() {}

    static boolean owns(
            byte[] view,
            int w,
            int h,
            List<ScoreNoteEvent> notes,
            ScoreNoteEvent note,
            int left,
            int right,
            int minY,
            float gap,
            float noteX,
            float noteY,
            FaintQuarterRestInk.Budget budget) {
        pay(budget, 32);
        if (view == null
                || w < 1
                || h < 1
                || w > 16384
                || h > 16384
                || (long) w * h != view.length
                || notes == null
                || note == null
                || !Float.isFinite(gap + noteX + noteY)
                || gap < 6
                || gap > 64
                || left < 0
                || right >= w
                || left > right
                || minY < 0
                || minY >= h) return false;
        int first =
                Math.max(0, Math.max(left - Math.round(gap * .25f), Math.round(noteX - gap * .8f)));
        int last = Math.min(w - 1, Math.min(right, Math.round(noteX + gap * .65f)));
        if (first > last) return false;
        var origin = RestFigureOrigins.origin(notes, note, view, w, h);
        if (origin == null
                || origin.originalGray == null
                || origin.originalGray.length != view.length) return false;
        byte[] raw = origin.originalGray;
        int hl = Math.max(0, Math.round(noteX - gap * .9f)),
                hr = Math.min(w - 1, Math.round(noteX + gap * .9f));
        int ht = Math.max(0, Math.round(noteY - gap * .65f)),
                hb = Math.min(h - 1, Math.round(noteY + gap * .65f));
        int required = Math.max(3, (int) Math.ceil(gap * .25f)), headRows = 0, lastMember = -1;
        for (int y = ht; y <= hb; y++) {
            pay(budget, 1);
            int hits = 0;
            for (int x = hl; x <= hr; x++) {
                pay(budget, 1);
                if (!origin.contains(x, y, budget)) continue;
                int v = pixel(raw, w, x, y, budget);
                if (v < 225) lastMember = Math.max(lastMember, y);
                if (v < 165) hits++;
            }
            if (hits >= required) headRows++;
        }
        if (headRows < required
                || lastMember < 0
                || minY - noteY < gap * .65f
                || minY - noteY > gap * 4.5f) return false;
        int from = lastMember + 1, to = Math.min(h - 1, Math.round(minY + gap * .4f));
        if (to - from + 1 < gap) return false;
        int flank = Math.max(2, Math.round(gap * .32f)),
                maxBlank = Math.max(1, Math.round(gap * .15f));
        for (int x = first; x <= last; x++) {
            pay(budget, 1);
            int good = 0, blank = 0, longest = 0;
            boolean linked = false;
            for (int y = from; y <= to; y++) {
                pay(budget, 1);
                long profile = profile(raw, w, h, x, y, flank, gap, budget);
                if (profile < 0) {
                    longest = Math.max(longest, ++blank);
                    continue;
                }
                if (good == 0) {
                    int a = (int) (profile >>> 32), b = (int) profile;
                    for (int yy = Math.max(ht, y - maxBlank - 1); yy < y; yy++) {
                        pay(budget, 1);
                        for (int xx = Math.max(hl, a - 1); xx <= Math.min(hr, b + 1); xx++) {
                            pay(budget, 1);
                            if (origin.contains(xx, yy, budget)
                                    && pixel(raw, w, xx, yy, budget) < 225) linked = true;
                        }
                    }
                }
                good++;
                blank = 0;
            }
            if (linked && good >= (to - from + 1) * .85f && longest <= maxBlank) return true;
        }
        return false;
    }

    private static long profile(
            byte[] g,
            int w,
            int h,
            int x,
            int y,
            int flank,
            float gap,
            FaintQuarterRestInk.Budget q) {
        pay(q, 8);
        if (x < flank || x + flank >= w) return -1;
        int ink = pixel(g, w, x, y, q),
                paper = Math.min(pixel(g, w, x - flank, y, q), pixel(g, w, x + flank, y, q));
        if (ink >= 225 || paper < ink + 12) return -1;
        double threshold = paper - (paper - ink) * .5;
        int a = x, b = x;
        while (a > x - flank) {
            pay(q, 1);
            if (pixel(g, w, a - 1, y, q) >= threshold) break;
            a--;
        }
        while (b < x + flank) {
            pay(q, 1);
            if (pixel(g, w, b + 1, y, q) >= threshold) break;
            b++;
        }
        if (a <= x - flank || b >= x + flank || b - a + 1 > Math.ceil(gap * .3f)) return -1;
        return ((long) a << 32) | b;
    }

    private static int pixel(byte[] g, int w, int x, int y, FaintQuarterRestInk.Budget q) {
        pay(q, 1);
        return g[y * w + x] & 255;
    }

    private static void pay(FaintQuarterRestInk.Budget q, long n) {
        FaintQuarterRestInk.charge(q, n, 0);
    }
}

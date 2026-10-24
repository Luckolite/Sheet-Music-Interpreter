// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete unoccluded barline profiles supply a proposal role; junction branches stay intact. */
final class FiniteBarlineSlideInk {
    private FiniteBarlineSlideInk() {}

    static final class Body {
        private final int[] pixels;
        final int left, right, top, bottom;

        private Body(int l, int r, int t, int b, int[] p) {
            left = l;
            right = r;
            top = t;
            bottom = b;
            pixels = p;
        }

        int[] pixels() {
            return pixels.clone();
        }

        int size() {
            return pixels.length;
        }

        boolean contains(int pixel) {
            return Arrays.binarySearch(pixels, pixel) >= 0;
        }
    }

    private static final class Limit extends RuntimeException {}

    private static final class Work {
        long left;

        Work(long left) {
            this.left = Math.min(16_000_000L, Math.max(0, left));
        }

        void take(long n) {
            if (n < 0 || n > left) throw new Limit();
            left -= n;
        }
    }

    static Body find(
            byte[] raw,
            int w,
            int h,
            StaffPitchTrack track,
            int core,
            List<NoteSlideDetector.Head> heads,
            long workLimit) {
        try {
            return find(raw, w, h, track, core, heads, new Work(workLimit));
        } catch (Limit limit) {
            return null;
        }
    }

    private static Body find(
            byte[] raw,
            int w,
            int h,
            StaffPitchTrack track,
            int core,
            List<NoteSlideDetector.Head> heads,
            Work work) {
        work.take(1);
        if (raw == null
                || w < 1
                || h < 1
                || raw.length != (long) w * h
                || track == null
                || heads == null
                || core < 0
                || core >= w
                || !track.verifiedAt(core)) return null;
        float[] frame = track.at(core);
        float gap = frame[1], top = frame[0] - 4 * gap, bottom = frame[0];
        if (!Float.isFinite(top + bottom + gap) || gap < 6 || gap > 64 || top < 0 || bottom >= h)
            return null;
        int first = Math.round(top), last = Math.round(bottom), radius = (int) Math.ceil(gap * .7f);
        int coreHits = 0;
        for (int y = first; y <= last; y++) {
            work.take(1);
            if ((raw[y * w + core] & 255) < 150) coreHits++;
        }
        if (coreHits < (last - first + 1) * .95f) return null;
        int lo = w, hi = -1, rows = 0;
        int[] perGap = new int[4];
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int y = first; y <= last; y++) {
            work.take(6);
            boolean rule = false;
            for (int k = 0; k < 5; k++)
                if (Math.abs(y - (top + k * gap)) <= Math.max(2, gap * .3f)) rule = true;
            if (rule) continue;
            if ((raw[y * w + core] & 255) >= 150) return null;
            int l = core, r = core;
            boolean junction = false;
            while (l > 0 && (raw[y * w + l - 1] & 255) < 150) {
                work.take(1);
                if (core - l >= radius) {
                    junction = true;
                    break;
                }
                l--;
            }
            while (!junction && r + 1 < w && (raw[y * w + r + 1] & 255) < 150) {
                work.take(1);
                if (r - core >= radius) {
                    junction = true;
                    break;
                }
                r++;
            }
            // Such a row does not calibrate the bar width. Its whole outside branch remains raw
            // contrary evidence for the later line verifier; this producer never labels it paper.
            if (junction || l == 0 || r == w - 1 || r - l + 1 > Math.max(3, Math.ceil(gap * .45f)))
                continue;
            int band = Math.min(3, Math.max(0, (int) ((y - top) / gap)));
            perGap[band]++;
            lo = Math.min(lo, l);
            hi = Math.max(hi, r);
            rows++;
            double center = (l + r) * .5;
            sx += y;
            sy += center;
            sxx += (double) y * y;
            sxy += y * center;
        }
        if (rows < Math.max(4, Math.ceil(gap * .5f))
                || hi < lo
                || hi - lo + 1 > Math.max(3, Math.ceil(gap * .5f))) return null;
        int supported = 0;
        for (int count : perGap) if (count > 0) supported++;
        if (supported < 3) return null;
        double vx = sxx / rows - (sx / rows) * (sx / rows);
        if (vx <= 0) return null;
        double slope = (sxy / rows - (sx / rows) * (sy / rows)) / vx;
        if (Math.abs(slope) > .08) return null;
        // A physical head/shaft competitor cannot be hidden by calling its column a barline.
        for (var head : heads) {
            work.take(1);
            if (head == null || !Float.isFinite(head.x() + head.y() + head.gap())) return null;
            if (head.x() >= lo - gap * .8f
                    && head.x() <= hi + gap * .8f
                    && head.y() >= top - 4 * gap
                    && head.y() <= bottom + 4 * gap) return null;
        }
        // Keep the full observed finite cap; extending into another symbol refuses the body.
        int maxCap = (int) Math.ceil(gap * .75f), t = first, b = last;
        for (int side = 0; side < 2; side++) {
            int step = side == 0 ? -1 : 1, y = side == 0 ? first - 1 : last + 1, count = 0;
            for (; y >= 0 && y < h; y += step) {
                boolean ink = false;
                for (int x = lo; x <= hi; x++) {
                    work.take(1);
                    if ((raw[y * w + x] & 255) < 150) ink = true;
                }
                if (!ink) break;
                if (++count > maxCap) return null;
                if (side == 0) t = y;
                else b = y;
            }
            if (y < 0 || y >= h) return null;
        }
        int[] mask = new int[(b - t + 1) * (hi - lo + 1)];
        int n = 0;
        for (int y = t; y <= b; y++)
            for (int x = lo; x <= hi; x++) {
                work.take(1);
                if ((raw[y * w + x] & 255) < 150) mask[n++] = y * w + x;
            }
        work.take(n);
        return n == 0 ? null : new Body(lo, hi, t, b, Arrays.copyOf(mask, n));
    }
}

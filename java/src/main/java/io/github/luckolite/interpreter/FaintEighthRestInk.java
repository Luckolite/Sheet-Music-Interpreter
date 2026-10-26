// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Independent literal endpoint/anatomy material. Never source/frame/owner authority. */
final class FaintEighthRestInk {
    private FaintEighthRestInk() {}

    static final class Proof {
        final byte[] raw, pixels;
        final int width, height, left, first, w, h, l, r, t, b;
        final SixteenthRestDetector.Staff frame;
        final boolean[] carrier;
        final FaintQuarterRestInk.Budget meter;

        private Proof(
                byte[] raw,
                int width,
                int height,
                SixteenthRestDetector.Staff frame,
                int left,
                int first,
                int w,
                int h,
                byte[] pixels,
                boolean[] carrier,
                int l,
                int r,
                int t,
                int b,
                FaintQuarterRestInk.Budget meter) {
            this.raw = raw;
            this.width = width;
            this.height = height;
            this.frame = frame;
            this.left = left;
            this.first = first;
            this.w = w;
            this.h = h;
            this.pixels = pixels;
            this.carrier = carrier;
            this.l = l;
            this.r = r;
            this.t = t;
            this.b = b;
            this.meter = meter;
        }

        boolean binds(
                byte[] actual,
                int aw,
                int ah,
                SixteenthRestDetector.Staff af,
                int al,
                int ar,
                FaintQuarterRestInk.Budget quarterBudget) {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);

            return meter == quarterBudget
                    && raw == actual
                    && width == aw
                    && height == ah
                    && frame == af
                    && l == al
                    && r == ar;
        }

        boolean same(Proof other, FaintQuarterRestInk.Budget quarterBudget) {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);

            if (other == null
                    || quarterBudget == null
                    || meter != quarterBudget
                    || other.meter != quarterBudget
                    || width != other.width
                    || height != other.height
                    || raw != other.raw
                    || frame != other.frame
                    || left != other.left
                    || first != other.first
                    || w != other.w
                    || h != other.h
                    || l != other.l
                    || r != other.r
                    || t != other.t
                    || b != other.b) return false;
            {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                for (int i = 0; i < pixels.length; i++) {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    if (pixels[i] != other.pixels[i]) return false;
                }
            }
            return true;
        }
    }

    private static byte[] bytes(int n, FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        FaintQuarterRestInk.charge(quarterBudget, n + 1L, 32L + 8L * n);
        return new byte[n];
    }

    private static int[] ints(int n, FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        FaintQuarterRestInk.charge(quarterBudget, n + 1L, 32L + 8L * n);
        return new int[n];
    }

    private static boolean[] bools(int n, FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        FaintQuarterRestInk.charge(quarterBudget, n + 1L, 32L + 8L * n);
        return new boolean[n];
    }

    private static void sort(int[] a, FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 1; i < a.length; i++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                int value = a[i], j = i - 1;
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    while (j >= 0 && a[j] > value) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        a[j + 1] = a[j];
                        j--;
                    }
                }
                a[j + 1] = value;
            }
        }
    }

    private static boolean foreground(
            byte[] raw,
            int width,
            int height,
            int x,
            int y,
            int radius,
            FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int value = raw[y * width + x] & 255;
        if (value < 170) return true;
        if (value >= 230 || x < radius || x + radius >= width || y < radius || y + radius >= height)
            return false;
        int bright = 0;
        if ((raw[y * width + x - radius] & 255) >= value + 20) bright++;
        if ((raw[y * width + x + radius] & 255) >= value + 20) bright++;
        if ((raw[(y - radius) * width + x] & 255) >= value + 20) bright++;
        if ((raw[(y + radius) * width + x] & 255) >= value + 20) bright++;
        return bright >= 2;
    }

    private static int rowRuns(
            byte[] points,
            int w,
            int row,
            int[] lo,
            int[] hi,
            FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int runs = 0;
        boolean active = false;
        lo[row] = -1;
        hi[row] = -1;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = 0; x < w; x++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                boolean ink = points[row * w + x] != 0;
                if (ink) {
                    if (!active) runs++;
                    if (lo[row] < 0) lo[row] = x;
                    hi[row] = x;
                }
                active = ink;
            }
        }
        return runs;
    }

    private static int flood(
            byte[] points, int w, int h, int[] queue, FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int all = 0, first = -1;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < points.length; i++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (points[i] != 0) {
                    all++;
                    if (first < 0) first = i;
                }
            }
        }
        if (first < 0) return 0;
        int take = 0, size = 1;
        queue[0] = first;
        points[first] = 2;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            while (take < size) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                int at = queue[take++], x = at % w, y = at / w;
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int dy = -1; dy <= 1; dy++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        {
                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int dx = -1; dx <= 1; dx++) {
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                int xx = x + dx, yy = y + dy;
                                if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                                int next = yy * w + xx;
                                if (points[next] == 1) {
                                    points[next] = 2;
                                    queue[size++] = next;
                                }
                            }
                        }
                    }
                }
            }
        }
        return size == all ? size : -1;
    }

    private static boolean completeBand(
            byte[] raw,
            int width,
            int left,
            int first,
            int w,
            int a,
            int c,
            int l0,
            int r0,
            int l1,
            int r1,
            byte[] points,
            byte[] scratch,
            int[] queue,
            FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        // Both-direction reachability is restricted to this SAME carrier band.
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = a; y <= c; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < w; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        scratch[y * w + x] = 0;
                    }
                }
            }
        }
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = l0; x <= r0; x++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                scratch[a * w + x] = 1;
            }
        }
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = l1; x <= r1; x++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                scratch[c * w + x] = 1;
            }
        }
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = a + 1; y < c; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                double f = (double) (y - a) / (c - a);
                int lo = (int) Math.floor(l0 * (1 - f) + l1 * f) - 1,
                        hi = (int) Math.ceil(r0 * (1 - f) + r1 * f) + 1;
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = Math.max(0, lo); x <= Math.min(w - 1, hi); x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if ((raw[(first + y) * width + left + x] & 255) < 230)
                            scratch[y * w + x] = 1;
                    }
                }
            }
        }
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int direction = 0; direction < 2; direction++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                int take = 0, size = 0, row = direction == 0 ? a : c;
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < w; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (scratch[row * w + x] != 0) {
                            queue[size++] = row * w + x;
                            scratch[row * w + x] = 2;
                        }
                    }
                }
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    while (take < size) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        int at = queue[take++], x = at % w, y = at / w;
                        {
                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int dy = -1; dy <= 1; dy++) {
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                {
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                    for (int dx = -1; dx <= 1; dx++) {
                                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                        int xx = x + dx, yy = y + dy;
                                        if (xx < 0 || xx >= w || yy < a || yy > c) continue;
                                        int n = yy * w + xx;
                                        if (scratch[n] == 1) {
                                            scratch[n] = 2;
                                            queue[size++] = n;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int y = a + 1; y < c; y++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        {
                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int x = 0; x < w; x++) {
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                if (scratch[y * w + x] == 1) return false;
                            }
                        }
                    }
                }
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int y = a; y <= c; y++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        {
                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int x = 0; x < w; x++) {
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                if (scratch[y * w + x] == 2) scratch[y * w + x] = 1;
                            }
                        }
                    }
                }
            }
        }
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = a + 1; y < c; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < w; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (scratch[y * w + x] != 0) points[y * w + x] = 1;
                    }
                }
            }
        }
        return true;
    }

    static Proof prove(
            byte[] raw,
            int width,
            int height,
            SixteenthRestDetector.Staff frame,
            int seedLeft,
            int seedRight,
            int bandTop,
            boolean[] narrow,
            boolean[] rules,
            FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (quarterBudget == null
                || raw == null
                || width <= 0
                || width > 8192
                || height <= 0
                || raw.length != (long) width * height
                || (long) width * height > 20_000_000
                || frame == null
                || !Float.isFinite(frame.top())
                || !Float.isFinite(frame.bottom())
                || !Float.isFinite(frame.gap())
                || frame.top() < 0
                || frame.bottom() >= height
                || frame.top() >= frame.bottom()
                || frame.gap() < 4
                || frame.gap() > 64
                || seedLeft < 0
                || seedRight >= width
                || seedLeft > seedRight
                || rules == null
                || rules.length != height
                || narrow == null
                || bandTop < 0
                || bandTop + narrow.length > height) return null;
        double gap = frame.gap();
        int left = Math.max(0, seedLeft - (int) Math.ceil(.65 * gap)),
                right = Math.min(width - 1, seedRight + (int) Math.ceil(.65 * gap));
        int first = Math.max(0, (int) Math.floor(frame.top() + .1 * gap)),
                last = Math.min(height - 1, (int) Math.ceil(frame.bottom() - .05 * gap));
        int w = right - left + 1, h = last - first + 1;
        if (w <= 0 || h <= 0 || (long) w * h > 200_000) return null;
        byte[] points = bytes(w * h, quarterBudget), scratch = bytes(w * h, quarterBudget);
        int[] queue = ints(w * h, quarterBudget),
                lo = ints(h, quarterBudget),
                hi = ints(h, quarterBudget),
                runs = ints(h, quarterBudget);
        int radius = Math.max(3, (int) Math.floor(gap * .6 + .5));
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < h; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (!rules[first + y]) {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < w; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (foreground(
                                raw, width, height, left + x, first + y, radius, quarterBudget))
                            points[y * w + x] = 1;
                    }
                }
            }
        }
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < h; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                runs[y] = rowRuns(points, w, y, lo, hi, quarterBudget);
            }
        }
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < h; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (rules[first + y]) {
                    int start = y;
                    {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        while (y + 1 < h && rules[first + y + 1]) {
                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            y++;
                        }
                    }
                    int a = start - 1, c = y + 1;
                    if (a < 0 || c >= h || runs[a] != 1 || runs[c] != 1) continue;
                    if (Math.max(Math.abs(lo[a] - lo[c]), Math.abs(hi[a] - hi[c]))
                            > c - a + gap * .2) continue;
                    // Reference discovery only; final profile carrier gets stricter both-path
                    // check.
                    {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        for (int row = start; row < c; row++) {
                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                            double f = (double) (row - a) / (c - a);
                            int l = (int) Math.floor(lo[a] * (1 - f) + lo[c] * f) - 1,
                                    r = (int) Math.ceil(hi[a] * (1 - f) + hi[c] * f) + 1;
                            {
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                for (int x = Math.max(0, l); x <= Math.min(w - 1, r); x++) {
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                    if ((raw[(first + row) * width + left + x] & 255) < 230)
                                        points[row * w + x] = 1;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (flood(points, w, h, queue, quarterBudget) <= 0) return null;
        int l = w, r = -1;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < h; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < w; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (points[y * w + x] != 0) {
                            if (x == 0 || x == w - 1 || y == 0 || y == h - 1) return null;
                            l = Math.min(l, x);
                            r = Math.max(r, x);
                        }
                    }
                }
            }
        }
        int inner = Math.max(2, (int) Math.floor(.4 * gap + .5)),
                outer = Math.max(inner + 2, (int) Math.floor(gap + .5));
        int la = left + l - outer,
                lb = left + l - inner,
                ra = left + r + inner,
                rb = left + r + outer;
        if (la < 0 || rb >= width) return null;
        int[] aa = ints(lb - la + 1, quarterBudget), bb = ints(rb - ra + 1, quarterBudget);
        boolean[] carrier = bools(h, quarterBudget), near = bools(h, quarterBudget);
        FaintQuarterRestInk.fill(quarterBudget, points, (byte) 0);
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < h; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < aa.length; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        aa[x] = raw[(first + y) * width + la + x] & 255;
                        bb[x] = raw[(first + y) * width + ra + x] & 255;
                    }
                }
                sort(aa, quarterBudget);
                sort(bb, quarterBudget);
                int n = aa.length;
                int aq = aa[n / 4], bq = bb[n / 4], au = aa[3 * n / 4], bu = bb[3 * n / 4];
                if (Math.abs(aq - bq) > 16 || au - aq > 16 || bu - bq > 16) return null;
                int base = Math.min(aq, bq);
                double phase =
                        frame.top() + Math.floor(((first + y) - frame.top()) / gap + .5) * gap;
                near[y] = Math.abs((first + y) - phase) <= gap * .35;
                if (base < 230) {
                    if (!near[y]) return null;
                    carrier[y] = true;
                }
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < w; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        int v = raw[(first + y) * width + left + x] & 255;
                        if (v < 230 && v <= base - 20) points[y * w + x] = 1;
                    }
                }
            }
        }
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < h; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                runs[y] = rowRuns(points, w, y, lo, hi, quarterBudget);
            }
        }
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < h; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (carrier[y]) {
                    int start = y;
                    {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        while (y + 1 < h && carrier[y + 1]) {
                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            y++;
                        }
                    }
                    int a = start - 1, c = y + 1;
                    if (a < 0 || c >= h || runs[a] != 1 || runs[c] != 1) continue;
                    if (Math.max(Math.abs(lo[a] - lo[c]), Math.abs(hi[a] - hi[c]))
                            > c - a + gap * .2) return null;
                    if (!completeBand(
                            raw,
                            width,
                            left,
                            first,
                            w,
                            a,
                            c,
                            lo[a],
                            hi[a],
                            lo[c],
                            hi[c],
                            points,
                            scratch,
                            queue,
                            quarterBudget)) return null;
                }
            }
        }
        if (flood(points, w, h, queue, quarterBudget) <= 0) return null;
        int t = h, b = -1;
        l = w;
        r = -1;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < h; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < w; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (points[y * w + x] != 0) {
                            if (x == 0 || x == w - 1 || y == 0 || y == h - 1) return null;
                            l = Math.min(l, x);
                            r = Math.max(r, x);
                            t = Math.min(t, y);
                            b = Math.max(b, y);
                        }
                    }
                }
            }
        }
        int peak = 0, peakCount = 0, peakY = -1;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = t; y <= b; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                runs[y] = rowRuns(points, w, y, lo, hi, quarterBudget);
                if (runs[y] == 0) return null;
                int widthRow = hi[y] - lo[y] + 1;
                if (widthRow > peak) {
                    peak = widthRow;
                    peakCount = 1;
                } else if (widthRow == peak) peakCount++;
            }
        }
        int seen = 0;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = t; y <= b; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (hi[y] - lo[y] + 1 == peak) {
                    if (seen++ == peakCount / 2) {
                        peakY = y;
                        break;
                    }
                }
            }
        }
        double coreX = 0;
        int coreCount = 0;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = t; y <= b; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (first + y <= first + peakY + Math.max(1, gap * .2)) {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = l; x <= r; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (points[y * w + x] != 0
                                && left + x >= seedLeft
                                && left + x <= seedRight
                                && (raw[(first + y) * width + left + x] & 255) < 155) {
                            coreX += left + x;
                            coreCount++;
                        }
                    }
                }
            }
        }
        double bulbCenter =
                coreCount == 0 ? left + (lo[peakY] + hi[peakY]) * .5 : coreX / coreCount;
        if (hi[t] - lo[t] + 1 > peak * .6 || peakY - t < Math.max(1, gap * .15)) return null;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = Math.max(t, b - (int) Math.floor(gap * .3)); y < b; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (hi[y + 1] - lo[y + 1] > hi[y] - lo[y]) return null;
            }
        }
        int[] suffixMax = ints(h + 1, quarterBudget),
                badShape = ints(h + 1, quarterBudget),
                badAxis = ints(h + 1, quarterBudget);
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = b; y >= t; y--) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                int widthRow = hi[y] - lo[y] + 1;
                suffixMax[y] = Math.max(widthRow, suffixMax[y + 1]);
                badShape[y] = badShape[y + 1] + (runs[y] != 1 || widthRow > peak * .55 ? 1 : 0);
                double center = (lo[y] + hi[y]) * .5;
                badAxis[y] =
                        badAxis[y + 1]
                                + (y < b
                                                && (lo[y + 1] + hi[y + 1]) * .5
                                                        > center + Math.max(1, gap * .1)
                                        ? 1
                                        : 0);
            }
        }
        int motifBottom = Integer.MAX_VALUE;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = t; y < b; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                int yy = first + y;
                if (yy < bandTop
                        || yy + 1 >= bandTop + narrow.length
                        || narrow[yy - bandTop]
                        || narrow[yy + 1 - bandTop]) continue;
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = Math.max(l, seedLeft - left);
                            x < Math.min(r, seedRight - left);
                            x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (points[y * w + x] != 0
                                && points[y * w + x + 1] != 0
                                && points[(y + 1) * w + x] != 0
                                && points[(y + 1) * w + x + 1] != 0
                                && (raw[yy * width + left + x] & 255) < 155
                                && (raw[yy * width + left + x + 1] & 255) < 155
                                && (raw[(yy + 1) * width + left + x] & 255) < 155
                                && (raw[(yy + 1) * width + left + x + 1] & 255) < 155)
                            motifBottom = Math.min(motifBottom, y + 1);
                    }
                }
            }
        }
        boolean tail = false;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int split = peakY + 1; split <= b; split++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (b - split < gap * .8
                        || badShape[split] != 0
                        || badAxis[split] != 0
                        || hi[b] - lo[b] + 1 >= suffixMax[split]
                        || split <= motifBottom) continue;
                double center = left + (lo[split] + hi[split]) * .5,
                        end = left + (lo[b] + hi[b]) * .5;
                if (center < bulbCenter + gap * .15) continue;
                if (center - end < gap * .15 || center - end > gap * 1.3) continue;
                tail = true;
                break;
            }
        }
        if (!tail
                || b - t < gap * 1.3
                || b - t > gap * 2.2
                || Math.abs((first + b) - (frame.bottom() - gap)) > gap * .4
                || first + t < Math.floor(frame.top() + gap * .85 + .5)
                || first + t > frame.top() + gap * 1.55) return null;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = Math.max(0, t - 1); y <= Math.min(h - 1, b + 1); y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = Math.max(0, l - 1); x <= Math.min(w - 1, r + 1); x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (points[y * w + x] == 0
                                && (raw[(first + y) * width + left + x] & 255) < 230
                                && !near[y]) return null;
                    }
                }
            }
        }
        FaintQuarterRestInk.charge(quarterBudget, 1, 256);
        return new Proof(
                raw,
                width,
                height,
                frame,
                left,
                first,
                w,
                h,
                points,
                carrier,
                left + l,
                left + r,
                first + t,
                first + b,
                quarterBudget);
    }

    static int classify(
            Proof proof,
            byte[] target,
            int[] indices,
            byte[] oldValues,
            boolean[] line,
            FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (proof.meter != quarterBudget
                || target == proof.raw
                || target.length != proof.raw.length
                || indices.length < proof.w * proof.h
                || oldValues.length < proof.w * proof.h
                || line.length != proof.h) throw new FaintQuarterRestInk.Refusal();
        int changed = 0;
        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < proof.h; y++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                boolean bodyRow = false;
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < proof.w; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (proof.pixels[y * proof.w + x] != 0) {
                            bodyRow = true;
                            break;
                        }
                    }
                }
                line[y] = proof.carrier[y] && !bodyRow;
                if (!bodyRow) continue;
                {
                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = proof.l - proof.left; x <= proof.r - proof.left; x++) {
                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        int at = (proof.first + y) * proof.width + proof.left + x,
                                old = proof.raw[at] & 255,
                                value = old;
                        if (proof.pixels[y * proof.w + x] != 0) {
                            if (old >= 170) value = 169;
                        } else if (proof.carrier[y] && old < 170) value = 255;
                        if (value != old) {
                            indices[changed] = at;
                            oldValues[changed] = (byte) old;
                            changed++;
                            target[at] = (byte) value;
                        }
                    }
                }
            }
        }
        return changed;
    }

    static void restore(
            byte[] target,
            int[] indices,
            byte[] values,
            int count,
            FaintQuarterRestInk.Budget quarterBudget) {
        FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        {
            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < count; i++) {
                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                target[indices[i]] = values[i];
            }
        }
    }
}

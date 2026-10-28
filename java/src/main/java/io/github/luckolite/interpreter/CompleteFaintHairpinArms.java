// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Optional complete literal arms grown from an existing raw component, never replacement gray. */
final class CompleteFaintHairpinArms {
    record Shape(
            PlayingTechniqueDetector.Staff staff,
            int left,
            int right,
            int top,
            int bottom,
            int direction,
            boolean requiresPrintedLevel) {}

    private record Captured(Shape shape, int[] pixels) {}

    private static final class Refusal extends RuntimeException {
        Refusal() {
            super(null, null, false, false);
        }
    }

    private static final Refusal REFUSAL = new Refusal();

    static final class Page {
        final byte[] raw;
        final int width, height;
        final Thread thread = Thread.currentThread();
        final long workLimit;
        long work, bytes = 1_000_000;
        boolean failed;
        int[] stamp, queue;
        byte[] state;
        int generation;
        final int capacity;
        final List<Captured> captured;

        Page(byte[] raw, int w, int h, List<PlayingTechniqueDetector.Staff> staffs) {
            this.raw = raw;
            width = w;
            height = h;
            int size = 0;
            List<Captured> out = List.of();
            // This new optional feature has its own input-linear allowance; no shared limit
            // changes.
            workLimit =
                    raw != null && raw.length <= 20_000_000
                            ? Math.max(2_000_000, 2L * raw.length)
                            : 2_000_000;
            work = workLimit;
            try {
                take(32, 128);
                if (raw == null
                        || w < 3
                        || h < 3
                        || raw.length > 20_000_000
                        || raw.length != (long) w * h
                        || staffs == null) throw REFUSAL;
                float gap = 0;
                for (var s : staffs) {
                    take(4, 0);
                    if (s == null || !Float.isFinite(s.gap()) || s.gap() < 3 || s.gap() > 64)
                        throw REFUSAL;
                    gap = Math.max(gap, s.gap());
                }
                if (gap < 3) throw REFUSAL;
                int ww = Math.min(w, (int) Math.ceil(36 * gap + 4)),
                        hh = Math.min(h, (int) Math.ceil(9 * gap + 4));
                size = (int) Math.min(Integer.MAX_VALUE, (long) ww * hh);
                take(0, 128);
                out = new ArrayList<>();
            } catch (Refusal e) {
                failed = true;
            }
            capacity = size;
            captured = out;
        }

        void take(long n, long b) {
            if (n < 0 || b < 0 || failed || n > work || b > bytes) {
                failed = true;
                throw REFUSAL;
            }
            work -= n;
            bytes -= b;
        }

        List<Shape> finish() {
            if (failed || thread != Thread.currentThread()) return List.of();
            try {
                take(captured.size() * 4L + 32, 192L + 24L * captured.size());
                var out = new ArrayList<Shape>();
                for (int i = 0; i < captured.size(); i++) out.add(captured.get(i).shape);
                return List.copyOf(out);
            } catch (Refusal e) {
                return List.of();
            }
        }

        void candidate(
                PlayingTechniqueDetector.Staff staff,
                int[] seed,
                int count,
                int l,
                int r,
                int t,
                int b) {
            candidate(staff, seed, count, l, r, t, b, false);
        }

        void compactCandidate(
                PlayingTechniqueDetector.Staff staff,
                int[] seed,
                int count,
                int l,
                int r,
                int t,
                int b) {
            candidate(staff, seed, count, l, r, t, b, true);
        }

        private void candidate(
                PlayingTechniqueDetector.Staff staff,
                int[] seed,
                int count,
                int l,
                int r,
                int t,
                int b,
                boolean requiresPrintedLevel) {
            if (failed) return;
            try {
                if (thread != Thread.currentThread()) throw REFUSAL;
                if (staff == null || seed == null || count < 1 || count > seed.length) return;
                float gap = staff.gap();
                int sw = r - l + 1, sh = b - t + 1;
                // Existing straight-core prefilters; the complete body supplies later size proof.
                if (requiresPrintedLevel
                        ? sw < gap * 3 || sw > gap * 6 || sh > gap * 1.75f
                        : sh > gap || sw < sh * 4 || sw > gap * 12) return;
                if (count > sw * Math.max(8, gap)) return;
                take(64, 0);
                for (int k = 0; k < captured.size(); k++) {
                    var c = captured.get(k);
                    take(12, 0);
                    if (c.shape.requiresPrintedLevel() != requiresPrintedLevel
                            || !c.shape.staff().equals(staff)) continue;
                    take(
                            count
                                            * (Math.max(
                                                    1,
                                                    32
                                                            - Integer.numberOfLeadingZeros(
                                                                    c.pixels.length)))
                                    + 1L,
                            0);
                    boolean all = true;
                    for (int i = 0; i < count && all; i++)
                        all = Arrays.binarySearch(c.pixels, seed[i]) >= 0;
                    if (all) return;
                }
                if (stamp == null) {
                    take(3L * capacity + 64, 96L + 9L * capacity);
                    stamp = new int[capacity];
                    queue = new int[capacity];
                    state = new byte[capacity];
                }
                if (++generation <= 0) throw REFUSAL;
                int padX = (int) Math.ceil(gap * 12), padY = (int) Math.ceil(gap * 3);
                int left = Math.max(0, l - padX),
                        right = Math.min(width - 1, r + padX),
                        top = Math.max(0, t - padY),
                        bottom = Math.min(height - 1, b + padY),
                        rw = right - left + 1,
                        rh = bottom - top + 1;
                if ((long) rw * rh > capacity) throw REFUSAL;
                int first = seed[0];
                if (first % width < left
                        || first % width > right
                        || first / width < top
                        || first / width > bottom) return;
                int start = 0, n = 1, a = first % width, z = a, c = first / width, d = c;
                queue[0] = first;
                int at = (c - top) * rw + a - left;
                stamp[at] = generation;
                state[at] = 3;
                while (start < n) {
                    take(12, 0);
                    int p = queue[start++], x = p % width, y = p / width;
                    a = Math.min(a, x);
                    z = Math.max(z, x);
                    c = Math.min(c, y);
                    d = Math.max(d, y);
                    if (z - a + 1 > gap * 12
                            || d - c + 1 > gap * 3
                            || x == left
                            || x == right
                            || y == top
                            || y == bottom) return;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            take(4, 0);
                            int xx = x + dx, yy = y + dy;
                            if (xx < left || xx > right || yy < top || yy > bottom) continue;
                            int k = (yy - top) * rw + xx - left;
                            if (stamp[k] != generation) {
                                stamp[k] = generation;
                                state[k] = (byte) (foreground(xx, yy, gap) ? 2 : 1);
                            }
                            if (state[k] == 2) {
                                take(2, 0);
                                state[k] = 3;
                                queue[n++] = yy * width + xx;
                            }
                        }
                }
                // The whole original seed must remain present; a separated fragment is never
                // discarded.
                take(count * 4L, 0);
                for (int i = 0; i < count; i++) {
                    int x = seed[i] % width, y = seed[i] / width;
                    if (x < left || x > right || y < top || y > bottom) return;
                    int k = (y - top) * rw + x - left;
                    if (stamp[k] != generation || state[k] != 3) return;
                }
                int bw = z - a + 1, bh = d - c + 1;
                boolean sizePass =
                        requiresPrintedLevel
                                ? bw >= gap * 3 && bw <= gap * 6 && bh <= gap * 1.75f
                                : bw >= gap * 4 && bw >= bh * 4 && bh <= gap * 3;
                if (!sizePass || n > bw * Math.max(8, gap)) return;
                take(n + bw * bh + 2L * bw + 64, 96L + bw * bh + 16L * bw);
                boolean[] body = new boolean[bw * bh];
                int[] upper = new int[bw], lower = new int[bw];
                Arrays.fill(upper, Integer.MAX_VALUE);
                Arrays.fill(lower, Integer.MIN_VALUE);
                for (int i = 0; i < n; i++) {
                    int x = queue[i] % width - a, y = queue[i] / width;
                    body[(y - c) * bw + x] = true;
                    upper[x] = Math.min(upper[x], y);
                    lower[x] = Math.max(lower[x], y);
                }
                take(16L * bw + 64, 160);
                int direction = ScoreDynamicsDetector.hairpinDirection(upper, lower, gap);
                if (direction == 0 || !complete(body, a, z, c, d, gap, direction)) return;
                // Include the copied support, conservative primitive-sort scratch, records,
                // and collection growth before any of them can allocate.
                take(
                        n * (Math.max(1, 32 - Integer.numberOfLeadingZeros(n)) + 1L) + 32,
                        480L + 8L * n);
                int[] pixels = Arrays.copyOf(queue, n);
                Arrays.sort(pixels);
                captured.add(
                        new Captured(
                                new Shape(staff, a, z, c, d, direction, requiresPrintedLevel),
                                pixels));
            } catch (Refusal e) {
                failed = true;
            }
        }

        boolean foreground(int x, int y, float gap) {
            take(1, 0);
            int base = y * width + x, value = raw[base] & 255;
            if (value < 145) return true;
            if (value > 225) return false;
            int radius = Math.max(2, (int) Math.ceil(gap * .75));
            take(12L * radius + 48, 0);
            if (y - radius < 0 || y + radius >= height) return false;
            // Literal vertical enclosing-section law, identical to PrintedBeamRawContrast's
            // primitive.
            int required = Math.max(210, value + 30), a = 0, b = 0;
            if (required > 255) return false;
            for (int k = 1; k <= radius; k++)
                if ((raw[base - k * width] & 255) >= required) {
                    a = -k;
                    break;
                }
            for (int k = 1; k <= radius; k++)
                if ((raw[base + k * width] & 255) >= required) {
                    b = k;
                    break;
                }
            if (a == 0 || b == 0) return false;
            int paper = Math.min(raw[base + a * width] & 255, raw[base + b * width] & 255),
                    minimum = 255;
            for (int k = a + 1; k < b; k++)
                minimum = Math.min(minimum, raw[base + k * width] & 255);
            if (minimum > 229 || paper - minimum < 30 || 2 * value >= paper + minimum) return false;
            int lo = 0, hi = 0;
            while (lo > a && 2 * (raw[base + (lo - 1) * width] & 255) < paper + minimum) lo--;
            while (hi < b && 2 * (raw[base + (hi + 1) * width] & 255) < paper + minimum) hi++;
            return lo > a && hi < b && hi - lo + 1 <= Math.max(3, Math.ceil(gap));
        }

        boolean complete(
                boolean[] body,
                int left,
                int right,
                int top,
                int bottom,
                float gap,
                int direction) {
            int w = right - left + 1, h = bottom - top + 1;
            take(32L * w + 64, 96L + 120L * w);
            int[][] lo = new int[w][2], hi = new int[w][2];
            int[] bands = new int[w];
            int upperMax = 0, lowerMax = 0, mergedMax = 0, opened = 0, paperOpen = 0;
            boolean sawOpen = false;
            for (int step = 0; step < w; step++) {
                int x = direction < 0 ? w - 1 - step : step, count = 0;
                take(4L * h + 32, 0);
                for (int y = 0; y < h; ) {
                    if (!body[y * w + x]) {
                        y++;
                        continue;
                    }
                    int a = y;
                    while (y + 1 < h && body[(y + 1) * w + x]) y++;
                    if (count < 2) {
                        lo[x][count] = a + top;
                        hi[x][count] = y + top;
                    }
                    count++;
                    y++;
                }
                bands[x] = count;
                if (count < 1 || count > 2) return false;
                if (count == 1) {
                    mergedMax = Math.max(mergedMax, hi[x][0] - lo[x][0] + 1);
                    if (sawOpen) return false;
                } else {
                    opened++;
                    sawOpen = true;
                    upperMax = Math.max(upperMax, hi[x][0] - lo[x][0] + 1);
                    lowerMax = Math.max(lowerMax, hi[x][1] - lo[x][1] + 1);
                    int mid = (hi[x][0] + lo[x][1]) / 2,
                            flank = Math.max(4, Math.round(gap * .5f)),
                            above = lo[x][0] - flank,
                            below = hi[x][1] + flank;
                    if (above < 0 || below >= height) return false;
                    int pa = raw[above * width + left + x] & 255,
                            pb = raw[below * width + left + x] & 255;
                    if (pa < 225 || pb < 225 || Math.abs(pa - pb) > 12) return false;
                    if ((raw[mid * width + left + x] & 255) >= Math.max(pa, pb) - 12) paperOpen++;
                }
            }
            int capWidth = Math.max(3, Math.round(gap * .3f)), tipColumns = 0;
            for (int k = 0; k < Math.min(capWidth, w); k++) {
                take(2, 0);
                if (bands[direction < 0 ? w - 1 - k : k] == 1) tipColumns++;
            }
            if (bands[direction < 0 ? w - 1 : 0] != 1
                    || tipColumns < capWidth * .8
                    || opened < w * .25
                    || paperOpen < opened * .8
                    || upperMax > Math.ceil(gap * .5 + 1)
                    || lowerMax > Math.ceil(gap * .5 + 1)
                    || mergedMax > upperMax + lowerMax) return false;
            take(2L * h + 4, 96L + 2L * h);
            boolean[][] prev = new boolean[2][h];
            for (int step = 0; step < w; step++) {
                int x = direction < 0 ? w - 1 - step : step;
                take(8L * h + 16, 96L + 2L * h);
                boolean[][] next = new boolean[2][h];
                for (int arm = 0; arm < 2; arm++) {
                    int band = bands[x] == 1 ? 0 : arm;
                    boolean any = false;
                    for (int y = lo[x][band] - top; y <= hi[x][band] - top; y++) {
                        boolean reached = step == 0;
                        for (int py = Math.max(0, y - 1);
                                !reached && py <= Math.min(h - 1, y + 1);
                                py++) reached |= prev[arm][py];
                        next[arm][y] = reached;
                        any |= reached;
                    }
                    if (!any) return false;
                }
                prev = next;
            }
            return true;
        }
    }
}

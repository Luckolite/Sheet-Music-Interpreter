// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;

/** Pure optional complete-counter proof. No caller, funding default, or recognition adapter. */
final class ClosedCounterRestInk {
    interface Payer {
        void take(long work, long bytes);
    }

    static final class Refusal extends RuntimeException {
        private Refusal() {
            super(null, null, false, false);
        }
    }

    static final Refusal REFUSAL = new Refusal();

    static final class Budget implements Payer {
        final long workLimit, byteLimit;
        long workUsed, bytesUsed;
        boolean failed;

        Budget(long work, long bytes) {
            if (work < 0 || bytes < 0) throw new IllegalArgumentException();
            workLimit = work;
            byteLimit = bytes;
        }

        public void take(long work, long bytes) {
            if (failed
                    || work < 0
                    || bytes < 0
                    || work > workLimit - workUsed
                    || bytes > byteLimit - bytesUsed) {
                failed = true;
                throw REFUSAL;
            }
            workUsed += work;
            bytesUsed += bytes;
        }
    }

    static boolean owns(
            RestFigureOrigins.OriginalFrame frame,
            List<ScoreNoteEvent> notes,
            byte[] view,
            int width,
            int height,
            RestFigureOrigins.ProposalOrigin proposal,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            Payer payer) {
        if (payer == null) return false;
        try {
            payer.take(64, 0);
            if (frame == null
                    || !frame.binds(notes, view, width, height, payer)
                    || proposal == null
                    || !proposal.valid(view, width, height, left, right, top, bottom)
                    || !Float.isFinite(gap)
                    || gap <= 0
                    || gap > Math.max(width, height)) return false;
            byte[] source = frame.source;
            int pad = Math.max(1, Math.round(gap));
            int L = (int) Math.max(0L, (long) left - pad),
                    R = (int) Math.min(width - 1L, (long) right + pad);
            int T = (int) Math.max(0L, (long) top - pad),
                    B = (int) Math.min(height - 1L, (long) bottom + pad);
            long columns = (long) R - L + 1, rows = (long) B - T + 1, cells = columns * rows;
            long area = ((long) right - left + 1) * ((long) bottom - top + 1);
            if (columns < 3 || rows < 3 || cells > Integer.MAX_VALUE || area < 1) return false;
            // Fixed conservative reservations cover every finite raster pass, queue write,
            // neighbor check, row scan, DP clear and primitive allocation below.
            long work, bytes;
            try {
                work =
                        Math.addExact(
                                512,
                                Math.addExact(
                                        Math.multiplyExact(384, cells),
                                        Math.multiplyExact(16, area)));
                bytes =
                        Math.addExact(
                                320,
                                Math.addExact(
                                        Math.multiplyExact(7, cells),
                                        Math.addExact(
                                                Math.multiplyExact(16, rows),
                                                Math.multiplyExact(8, columns))));
            } catch (ArithmeticException overflow) {
                return false;
            }
            payer.take(work, bytes);
            int w = (int) columns, h = (int) rows, n = (int) cells;
            byte[] state = new byte[n], paper = new byte[n];
            int[] queue = new int[n];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if ((source[(T + y) * width + L + x] & 255) < 205) state[y * w + x] = 1;
            int seed = -1;
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++)
                    if ((view[y * width + x] & 255) < 170) {
                        int k = (y - T) * w + x - L;
                        if (state[k] == 0) return false;
                        if (seed < 0) seed = k;
                    }
            if (seed < 0) return false;
            int read = 0, size = 1, a = w, z = -1, c = h, d = -1;
            queue[0] = seed;
            state[seed] |= 2;
            while (read < size) {
                int k = queue[read++], x = k % w, y = k / w;
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) return false;
                a = Math.min(a, x);
                z = Math.max(z, x);
                c = Math.min(c, y);
                d = Math.max(d, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int q = (y + dy) * w + x + dx;
                        if ((state[q] & 1) != 0 && (state[q] & 2) == 0) {
                            state[q] |= 2;
                            queue[size++] = q;
                        }
                    }
            }
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++)
                    if ((view[y * width + x] & 255) < 170 && (state[(y - T) * w + x - L] & 2) == 0)
                        return false;
            // Unowned source ink inside the complete body cannot become counter paper.
            for (int y = c; y <= d; y++)
                for (int x = a; x <= z; x++) if (state[y * w + x] == 1) return false;
            if (frame.intersectsHead(state, 2, L, T, w, h, payer)) return false;
            // Outside paper is eight-connected. Other components cannot close this counter.
            read = 0;
            size = 1;
            int outside = (c - 1) * w + a - 1;
            queue[0] = outside;
            paper[outside] = 1;
            while (read < size) {
                int k = queue[read++], x = k % w, y = k / w;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < a - 1 || xx > z + 1 || yy < c - 1 || yy > d + 1) continue;
                        int q = yy * w + xx;
                        if ((state[q] & 2) == 0 && paper[q] == 0) {
                            paper[q] = 1;
                            queue[size++] = q;
                        }
                    }
            }
            int holes = 0, hl = w, hr = -1, ht = h, hb = -1;
            boolean bright = false;
            for (int y = c; y <= d; y++)
                for (int x = a; x <= z; x++) {
                    int k = y * w + x;
                    if ((state[k] & 2) != 0 || paper[k] != 0) continue;
                    if (++holes > 1) return false;
                    read = 0;
                    size = 1;
                    queue[0] = k;
                    paper[k] = 2;
                    while (read < size) {
                        int p = queue[read++], xx = p % w, yy = p / w;
                        hl = Math.min(hl, xx);
                        hr = Math.max(hr, xx);
                        ht = Math.min(ht, yy);
                        hb = Math.max(hb, yy);
                        bright |= (source[(T + yy) * width + L + xx] & 255) >= 225;
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                int nx = xx + dx, ny = yy + dy;
                                if (nx < a || nx > z || ny < c || ny > d) continue;
                                int q = ny * w + nx;
                                if ((state[q] & 2) == 0 && paper[q] == 0) {
                                    paper[q] = 2;
                                    queue[size++] = q;
                                }
                            }
                    }
                }
            if (holes != 1 || !bright) return false;
            int[] lo0 = new int[h], hi0 = new int[h], lo1 = new int[h], hi1 = new int[h];
            for (int y = c; y <= d; y++) {
                int count = 0, phl = -1, phr = -1, pc = 0;
                for (int x = a; x <= z; x++)
                    if (paper[y * w + x] == 2) {
                        if (phl < 0) phl = x;
                        phr = x;
                        pc++;
                    }
                for (int x = a; x <= z; ) {
                    if ((state[y * w + x] & 2) == 0) {
                        x++;
                        continue;
                    }
                    int start = x;
                    while (x < z && (state[y * w + x + 1] & 2) != 0) x++;
                    if (count == 0) {
                        lo0[y] = start;
                        hi0[y] = x;
                    } else if (count == 1) {
                        lo1[y] = start;
                        hi1[y] = x;
                    } else return false;
                    count++;
                    x++;
                }
                if (y >= ht && y <= hb) {
                    if (count != 2
                            || pc == 0
                            || pc != phr - phl + 1
                            || hi0[y] + 1 != phl
                            || lo1[y] - 1 != phr) return false;
                    if (y > ht
                            && (lo0[y] > hi0[y - 1] + 1
                                    || hi0[y] < lo0[y - 1] - 1
                                    || lo1[y] > hi1[y - 1] + 1
                                    || hi1[y] < lo1[y - 1] - 1)) return false;
                } else if (count != 1) return false;
            }
            boolean[] previous = new boolean[w], next = new boolean[w];
            for (int x = a; x < hl; x++) previous[x] = (state[ht * w + x] & 2) != 0;
            for (int y = ht + 1; y <= d; y++) {
                Arrays.fill(next, false);
                for (int x = a; x < hl; x++)
                    next[x] =
                            (state[y * w + x] & 2) != 0
                                    && (previous[x]
                                            || (x > 0 && previous[x - 1])
                                            || (x + 1 < w && previous[x + 1]));
                boolean[] swap = previous;
                previous = next;
                next = swap;
            }
            boolean spine = false;
            for (boolean v : previous) spine |= v;
            if (!spine) return false;
            int lastRight = ht;
            for (int y = c; y <= d; y++)
                for (int x = hl; x <= z; x++)
                    if ((state[y * w + x] & 2) != 0) lastRight = Math.max(lastRight, y);
            if (lastRight >= d) return false;
            // Reuse the existing positive bilateral ledger law, with its full finite reads paid.
            long ledgerRows = Math.min(height, (long) Math.ceil(gap * 1.8) + 5),
                    reach = (long) Math.ceil(gap * .65) + 1;
            long ledgerWork;
            try {
                ledgerWork =
                        Math.addExact(
                                128, Math.multiplyExact(64, Math.multiplyExact(ledgerRows, reach)));
            } catch (ArithmeticException overflow) {
                return false;
            }
            payer.take(ledgerWork, 0);
            if (LedgerRuleContrast.proved(
                    source, width, height, L + a, L + z, T + (ht + hb) * .5f, gap)) return false;
            return frame.binds(notes, view, width, height, payer);
        } catch (Refusal failure) {
            return false;
        }
    }
}

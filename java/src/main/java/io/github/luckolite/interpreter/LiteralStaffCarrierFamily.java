// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independently observed finite five-rail frames. Every matching frame is retained.
 * A rail is nonexclusive literal membership, never permission to erase a pixel. */
final class LiteralStaffCarrierFamily {
    private record Frame(int left, int right, int[] edges) {
        boolean contains(int x, int y) {
            if (x < left || x > right) return false;
            for (int i = 0; i < 10; i += 2) if (y >= edges[i] && y <= edges[i + 1]) return true;
            return false;
        }
    }

    private final byte[] source;
    private final int width;
    private final List<Frame> frames;

    private LiteralStaffCarrierFamily(byte[] g, int w, List<Frame> f) {
        source = g;
        width = w;
        frames = List.copyOf(f);
    }

    int completeFrames() {
        return frames.size();
    }

    boolean observed(int p) {
        if (p < 0 || p >= source.length || (source[p] & 255) >= 165) return false;
        for (Frame f : frames) if (!f.contains(p % width, p / width)) return false;
        return true;
    }

    static LiteralStaffCarrierFamily observe(
            byte[] gray,
            int width,
            int height,
            float nativeTop,
            float gap,
            SlurEvidenceBudget budget) {
        if (gray == null
                || (long) width * height != gray.length
                || !Float.isFinite(nativeTop)
                || !Float.isFinite(gap)
                || gap < 6) throw new SlurEvidenceBudget.Unknown("STAFF_SOURCE_UNKNOWN");
        int top = (int) Math.floor(nativeTop - 3 * gap),
                bottom = (int) Math.ceil(nativeTop + 7 * gap);
        if (top < 0 || bottom >= height)
            throw new SlurEvidenceBudget.Unknown("STAFF_SCOPE_CLIPPED");
        int rows = bottom - top + 1,
                thickness = (int) Math.ceil(.35 * gap),
                coreTop = Math.round(nativeTop),
                coreBottom = Math.round(nativeTop + 4 * gap);
        long n = (long) width * rows;
        if (n > Integer.MAX_VALUE) throw new SlurEvidenceBudget.Unknown("STAFF_STORAGE_UNKNOWN");
        budget.use(8 * n, 4 * n + 64L * width + 8192);
        int[] lastPaper = new int[(int) n];
        var bars = new ArrayList<Integer>();
        for (int y = top; y <= bottom; y++) {
            int last = -1;
            for (int x = 0; x < width; x++) {
                if ((gray[y * width + x] & 255) >= 165) last = x;
                lastPaper[(y - top) * width + x] = last;
            }
        }
        for (int x = 0; x < width; x++) {
            boolean full = true;
            for (int y = coreTop; y <= coreBottom; y++)
                if ((gray[y * width + x] & 255) >= 165) {
                    full = false;
                    break;
                }
            if (full) bars.add(x);
        }
        var frames = new ArrayList<Frame>();
        for (int left : bars)
            for (int right : bars) {
                budget.use(1, 0);
                if (right - left < 4 * gap) continue;
                budget.use(20L * rows + 4L * width + 64, 256);
                int[] edges = new int[10];
                boolean good = true;
                for (int r = 0; r < 5; r++) {
                    int lo = (int) Math.floor(nativeTop + r * gap - gap * .5) + 1,
                            hi = (int) Math.ceil(nativeTop + r * gap + gap * .5) - 1,
                            first = -1,
                            last = -1,
                            runs = 0;
                    boolean before = false;
                    if (lo < top || hi > bottom) {
                        good = false;
                        break;
                    }
                    for (int y = lo; y <= hi; y++) {
                        boolean literal = lastPaper[(y - top) * width + right] < left;
                        if (literal && !before) {
                            runs++;
                            first = y;
                        }
                        if (literal) last = y;
                        before = literal;
                    }
                    if (runs != 1
                            || first < 0
                            || last - first + 1 > thickness
                            || Math.abs((first + last) * .5 - nativeTop - r * gap) > .25 * gap) {
                        good = false;
                        break;
                    }
                    edges[2 * r] = first;
                    edges[2 * r + 1] = last;
                }
                if (!good) continue;
                int cap = left;
                for (int r = 0; r < 5; r++)
                    for (int y = edges[2 * r]; y <= edges[2 * r + 1]; y++) {
                        int start = lastPaper[(y - top) * width + left] + 1;
                        if (start < left - thickness + 1 || start > left) {
                            good = false;
                            break;
                        }
                        cap = Math.min(cap, start);
                    }
                if (!good || cap == 0) continue;
                for (int y = edges[0]; y <= edges[9]; y++)
                    if ((gray[y * width + cap - 1] & 255) < 165
                            || (gray[y * width + left] & 255) >= 165
                            || (gray[y * width + right] & 255) >= 165) {
                        good = false;
                        break;
                    }
                for (int r = 1; r < 5; r++) {
                    double observed =
                            (edges[2 * r] + edges[2 * r + 1] - edges[2 * r - 2] - edges[2 * r - 1])
                                    * .5;
                    if (edges[2 * r] <= edges[2 * r - 1] || Math.abs(observed - gap) > .15 * gap)
                        good = false;
                }
                for (int sign : new int[] {-1, 5}) {
                    int center = Math.round(nativeTop + sign * gap);
                    for (int y = Math.max(top, center - thickness);
                            y <= Math.min(bottom, center + thickness);
                            y++) if (lastPaper[(y - top) * width + right] < left) good = false;
                }
                if (!good) continue;
                if (frames.size() == 4096)
                    throw new SlurEvidenceBudget.Unknown("STAFF_FRAME_FAMILY_UNKNOWN");
                frames.add(new Frame(cap, right, edges));
            }
        if (frames.isEmpty())
            throw new SlurEvidenceBudget.Unknown("NO_COMPLETE_FINITE_STAFF_FRAME");
        return new LiteralStaffCarrierFamily(gray, width, frames);
    }
}

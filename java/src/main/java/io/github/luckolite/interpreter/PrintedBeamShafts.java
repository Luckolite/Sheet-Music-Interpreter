// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Bounded raw shaft candidates; note-body ownership and rivals remain caller obligations. */
final class PrintedBeamShafts {
    static final class Budget {
        private long remaining;
        private boolean refused;

        Budget(long pixels) {
            if (pixels < 0 || pixels > 8_000_000)
                throw new IllegalArgumentException("Invalid shaft budget");
            remaining = pixels;
        }

        boolean take(long pixels) {
            if (refused || pixels < 0 || pixels > remaining) {
                refused = true;
                return false;
            }
            remaining -= pixels;
            return true;
        }

        boolean refused() {
            return refused;
        }
    }

    record Column(int x, int endY) {}

    record Shaft(int left, int right, int startY, int endY, int direction, List<Column> columns) {
        Shaft {
            columns = List.copyOf(columns);
        }
    }

    private PrintedBeamShafts() {}

    static List<Shaft> at(
            byte[] raw, int width, int height, float headX, float headY, float gap, Budget budget) {
        if (raw == null
                || width < 3
                || height < 3
                || raw.length != (long) width * height
                || (long) width * height > 32_000_000
                || !Float.isFinite(headX + headY + gap)
                || headX < 0
                || headX >= width
                || headY < 0
                || headY >= height
                || gap < 3
                || gap > 64
                || budget == null)
            throw new IllegalArgumentException("Invalid raw beam shaft query");
        var result = new ArrayList<Shaft>(2);
        for (int direction : new int[] {-1, 1}) {
            int cy = direction > 0 ? Math.round(headY) : height - 1 - Math.round(headY);
            int edge = Math.round(headX - direction * .6f * gap);
            int first = Math.max(1, Math.round(edge - gap * .2f));
            int last = Math.min(width - 2, (int) Math.floor(edge + gap * .2f));
            int start = cy + Math.round(gap * .2f);
            int limit = Math.min(height - 2, start + (int) Math.ceil(gap * 12));
            if (start < 1 || start >= limit || first > last) continue;
            // A uniqueness decision requires BOTH possible stem directions.
            // Never return the first direction after refusing its rival scan.
            if (!budget.take((long) (last - first + 1) * (limit - start + 1) * 2)) return List.of();
            var cols = new ArrayList<Column>();
            int prior = -2, remote = -1;
            boolean ambiguous = false;
            for (int x = first; x <= last; x++) {
                int hits = 0, total = 0, blank = 0, end = start;
                for (int y = start; y <= limit; y++) {
                    if (dark(raw, width, height, x, y, direction)) {
                        hits++;
                        blank = 0;
                        end = y;
                    } else if (++blank >= 2) break;
                    total++;
                }
                if (end - cy < gap * 1.7f || hits < total * .9f || end >= limit - 1) continue;
                if (!cols.isEmpty() && x != prior + 1) {
                    ambiguous = true;
                    break;
                }
                cols.add(new Column(x, direction > 0 ? end : height - 1 - end));
                prior = x;
                remote = Math.max(remote, end);
            }
            if (ambiguous || cols.isEmpty() || cols.size() > Math.max(3, gap * .35f)) continue;
            int left = cols.get(0).x(), right = cols.get(cols.size() - 1).x();
            // At least one literal 8-connected path survives every row from
            // the authenticated head's edge to the most remote observed cap.
            // A shorter adjacent column never supplies a fictitious blank pixel.
            boolean[] previous = new boolean[right - left + 1];
            boolean connected = true;
            for (int y = start; y <= remote; y++) {
                boolean[] next = new boolean[previous.length];
                boolean any = false;
                for (int i = 0; i < next.length; i++)
                    if (dark(raw, width, height, left + i, y, direction)) {
                        next[i] =
                                y == start
                                        || previous[i]
                                        || i > 0 && previous[i - 1]
                                        || i + 1 < previous.length && previous[i + 1];
                        any |= next[i];
                    }
                if (!any) {
                    connected = false;
                    break;
                }
                previous = next;
            }
            if (connected)
                result.add(
                        new Shaft(
                                left,
                                right,
                                direction > 0 ? start : height - 1 - start,
                                direction > 0 ? remote : height - 1 - remote,
                                direction,
                                cols));
        }
        return List.copyOf(result);
    }

    private static boolean dark(byte[] raw, int width, int height, int x, int y, int direction) {
        int actual = direction > 0 ? y : height - 1 - y;
        return (raw[actual * width + x] & 255) < 165;
    }
}

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

    record Shaft(
            int left,
            int right,
            int startY,
            int endY,
            int direction,
            List<Column> columns,
            PrintedBeamShaftPixels.Scan pixels) {
        Shaft(int left, int right, int startY, int endY, int direction, List<Column> columns) {
            this(left, right, startY, endY, direction, columns, null);
        }

        Shaft {
            columns = List.copyOf(columns);
        }
    }

    private PrintedBeamShafts() {}

    static List<Shaft> at(
            byte[] raw, int width, int height, float headX, float headY, float gap, Budget budget) {
        return at(raw, width, height, headX, headY, gap, budget, null);
    }

    static List<Shaft> at(
            byte[] raw,
            int width,
            int height,
            float headX,
            float headY,
            float gap,
            Budget budget,
            PrintedBeamShaftPixels.Frame frame) {
        try {
            return scan(
                    raw,
                    width,
                    height,
                    headX,
                    headY,
                    gap,
                    budget,
                    frame,
                    false,
                    frame != null && frame.contrast);
        } catch (ContrastRefused refused) {
            return List.of();
        }
    }

    static List<Shaft> atAll(
            byte[] raw,
            int width,
            int height,
            float headX,
            float headY,
            float gap,
            Budget budget,
            PrintedBeamShaftPixels.Frame frame) {
        if (frame == null) throw new IllegalArgumentException("Missing source frame");
        try {
            return scan(raw, width, height, headX, headY, gap, budget, frame, true, frame.contrast);
        } catch (ContrastRefused refused) {
            return List.of();
        }
    }

    static List<Shaft> atStrict(
            byte[] raw,
            int width,
            int height,
            float headX,
            float headY,
            float gap,
            Budget budget,
            PrintedBeamShaftPixels.Frame frame) {
        if (frame == null) throw new IllegalArgumentException("Missing source frame");
        return scan(raw, width, height, headX, headY, gap, budget, frame, false, false);
    }

    private static final class ContrastRefused extends RuntimeException {}

    private static List<Shaft> scan(
            byte[] raw,
            int width,
            int height,
            float headX,
            float headY,
            float gap,
            Budget budget,
            PrintedBeamShaftPixels.Frame frame,
            boolean all,
            boolean contrast) {
        if (frame != null) frame.check(raw, width, height);
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
            boolean cacheContrast = all && contrast && frame != null;
            long cells = (long) (last - first + 1) * (limit - start + 1);
            // Four prepaid cell slots cover first lookup/read/store and repeated lookup.
            if (!budget.take(cells * (cacheContrast ? 4 : 2))) return List.of();
            ContrastMemo memo = null;
            if (cacheContrast) {
                frame.take(128 + 4 * cells);
                memo =
                        new ContrastMemo(
                                raw, width, height, first, last, start, limit, direction, gap,
                                budget, frame, headY);
            }
            var cols = new ArrayList<Column>();
            int prior = -2, remote = -1;
            boolean ambiguous = false;
            for (int x = first; x <= last; x++) {
                int hits = 0, total = 0, blank = 0, end = start;
                for (int y = start; y <= limit; y++) {
                    if (memo == null
                            ? dark(
                                    raw, width, height, x, y, direction, gap, budget, frame, headY,
                                    contrast)
                            : memo.at(x, y)) {
                        hits++;
                        blank = 0;
                        end = y;
                    } else if (++blank >= 2) break;
                    total++;
                }
                if (end - cy < gap * 1.7f || hits < total * .9f || end >= limit - 1) continue;
                if (!all && !cols.isEmpty() && x != prior + 1) {
                    ambiguous = true;
                    break;
                }
                cols.add(new Column(x, direction > 0 ? end : height - 1 - end));
                prior = x;
                remote = Math.max(remote, end);
            }
            if (ambiguous || cols.isEmpty()) continue;
            if (!all) {
                if (cols.size() > Math.max(3, gap * .35f)) continue;
                var shaft =
                        materialize(
                                raw, width, height, headY, gap, budget, frame, direction, start,
                                remote, cols, contrast);
                if (shaft != null) result.add(shaft);
            } else {
                for (int from = 0; from < cols.size(); ) {
                    int to = from + 1;
                    while (to < cols.size() && cols.get(to).x() == cols.get(to - 1).x() + 1) to++;
                    if (to - from <= Math.max(3, gap * .35f)) {
                        frame.take(to - from + 64L);
                        var run = new ArrayList<>(cols.subList(from, to));
                        int end = -1;
                        for (var col : run)
                            end =
                                    Math.max(
                                            end,
                                            direction > 0 ? col.endY() : height - 1 - col.endY());
                        var shaft =
                                materialize(
                                        raw, width, height, headY, gap, budget, frame, direction,
                                        start, end, run, contrast, memo);
                        if (shaft != null) result.add(shaft);
                    }
                    from = to;
                }
            }
        }
        return List.copyOf(result);
    }

    private static Shaft materialize(
            byte[] raw,
            int width,
            int height,
            float headY,
            float gap,
            Budget budget,
            PrintedBeamShaftPixels.Frame frame,
            int direction,
            int start,
            int remote,
            List<Column> cols,
            boolean contrast) {
        return materialize(
                raw, width, height, headY, gap, budget, frame, direction, start, remote, cols,
                contrast, null);
    }

    private static Shaft materialize(
            byte[] raw,
            int width,
            int height,
            float headY,
            float gap,
            Budget budget,
            PrintedBeamShaftPixels.Frame frame,
            int direction,
            int start,
            int remote,
            List<Column> cols,
            boolean contrast,
            ContrastMemo memo) {
        int left = cols.get(0).x(), right = cols.get(cols.size() - 1).x();
        // At least one literal 8-connected path survives every row from
        // the authenticated head's edge to the most remote observed cap.
        // A shorter adjacent column never supplies a fictitious blank pixel.
        int[] savedRows = null;
        if (frame != null) {
            frame.take(remote - start + 1L + 48);
            savedRows = new int[remote - start + 1];
        }
        boolean[] previous = new boolean[right - left + 1];
        boolean connected = true;
        for (int y = start; y <= remote; y++) {
            boolean[] next = new boolean[previous.length];
            boolean any = false;
            for (int i = 0; i < next.length; i++) {
                boolean actualDark =
                        memo == null
                                ? dark(
                                        raw, width, height, left + i, y, direction, gap, budget,
                                        frame, headY, contrast)
                                : memo.at(left + i, y);
                if (savedRows != null
                        && actualDark
                        && y
                                <= (direction > 0
                                        ? cols.get(i).endY()
                                        : height - 1 - cols.get(i).endY()))
                    savedRows[y - start] |= 1 << i;
                if (actualDark) {
                    next[i] =
                            y == start
                                    || previous[i]
                                    || i > 0 && previous[i - 1]
                                    || i + 1 < previous.length && previous[i + 1];
                    any |= next[i];
                }
            }
            if (!any) {
                connected = false;
                break;
            }
            previous = next;
        }
        if (connected)
            return new Shaft(
                    left,
                    right,
                    direction > 0 ? start : height - 1 - start,
                    direction > 0 ? remote : height - 1 - remote,
                    direction,
                    cols,
                    savedRows == null
                            ? null
                            : new PrintedBeamShaftPixels.Scan(
                                    frame,
                                    left,
                                    right,
                                    direction > 0 ? start : height - 1 - start,
                                    direction > 0 ? remote : height - 1 - remote,
                                    direction,
                                    cols,
                                    savedRows,
                                    contrast));
        return null;
    }

    /** Invocation/direction-local answers only; raw input remains immutable during the scan. */
    private static final class ContrastMemo {
        final byte[] raw, values;
        final int width, height, left, right, start, limit, direction, stride;
        final float gap, headY;
        final Budget budget;
        final PrintedBeamShaftPixels.Frame frame;

        ContrastMemo(
                byte[] raw,
                int width,
                int height,
                int left,
                int right,
                int start,
                int limit,
                int direction,
                float gap,
                Budget budget,
                PrintedBeamShaftPixels.Frame frame,
                float headY) {
            this.raw = raw;
            this.width = width;
            this.height = height;
            this.left = left;
            this.right = right;
            this.start = start;
            this.limit = limit;
            this.direction = direction;
            this.gap = gap;
            this.budget = budget;
            this.frame = frame;
            this.headY = headY;
            stride = right - left + 1;
            values = new byte[stride * (limit - start + 1)];
        }

        boolean at(int x, int y) {
            if (x < left || x > right || y < start || y > limit)
                throw new IllegalArgumentException("Foreign memo cell");
            int index = (y - start) * stride + x - left;
            int prior = values[index];
            if (prior != 0) return prior == 2;
            boolean answer =
                    dark(raw, width, height, x, y, direction, gap, budget, frame, headY, true);
            values[index] = (byte) (answer ? 2 : 1);
            return answer;
        }
    }

    private static boolean dark(
            byte[] raw,
            int width,
            int height,
            int x,
            int y,
            int direction,
            double gap,
            Budget budget,
            PrintedBeamShaftPixels.Frame frame,
            float headY,
            boolean contrast) {
        int actual = direction > 0 ? y : height - 1 - y;
        if ((raw[actual * width + x] & 255) < 165) return true;
        if (frame == null || !contrast) return false;
        frame.take(32); // callback allocation before construction
        if (Math.abs(actual - headY) <= gap * .65
                && PrintedBeamRawContrast.neck(
                        raw,
                        width,
                        height,
                        x,
                        actual,
                        gap,
                        n -> {
                            if (!budget.take(n)) throw new ContrastRefused();
                            frame.take(n);
                        })) return true;
        frame.take(32);
        java.util.function.LongConsumer charge =
                n -> {
                    if (!budget.take(n)) throw new ContrastRefused();
                    frame.take(n);
                };
        return PrintedBeamRawContrast.faint(raw, width, height, x, actual, gap, true, charge)
                || PrintedBeamRawContrast.shaftWithEnclosingPaper(
                        raw, width, height, x, actual, gap, charge)
                || PrintedBeamRawContrast.crossingWithEnclosingPaper(
                        raw, width, height, x, actual, gap, charge);
    }
}

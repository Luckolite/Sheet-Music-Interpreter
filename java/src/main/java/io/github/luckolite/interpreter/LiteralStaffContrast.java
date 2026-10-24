// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Exact two-sided five-rule tone references. Matching carrier cells remain co-owned/unknown. */
final class LiteralStaffContrast {
    enum Kind {
        EXTRA_CORE,
        EXTRA_FRINGE,
        MATCHED_CARRIER,
        OBSERVED_WHITE,
        CONTRADICTION
    }

    record Cell(int observed, int reference, Kind kind) {}

    record Window(int left, int right, float measuredGap) {}

    static final class Budget {
        private long remaining;
        private boolean exhausted;

        Budget(long allowance) {
            if (allowance < 0 || allowance > 8_000_000L) throw new IllegalArgumentException();
            remaining = allowance;
        }

        boolean take(long n) {
            if (exhausted || n < 0 || n > remaining) {
                exhausted = true;
                return false;
            }
            remaining -= n;
            return true;
        }

        boolean exhausted() {
            return exhausted;
        }

        long remaining() {
            return remaining;
        }
    }

    static final class Field {
        private final int left, right, firstY, lastY;
        private final byte[] reference, observed;

        private Field(
                int left, int right, int firstY, int lastY, byte[] reference, byte[] observed) {
            this.left = left;
            this.right = right;
            this.firstY = firstY;
            this.lastY = lastY;
            this.reference = reference.clone();
            this.observed = observed.clone();
        }

        int left() {
            return left;
        }

        int right() {
            return right;
        }

        int firstY() {
            return firstY;
        }

        int lastY() {
            return lastY;
        }

        Cell cell(int x, int y) {
            if (x < left || x > right || y < firstY || y > lastY)
                throw new IllegalArgumentException("Outside calibrated field");
            int r = reference[y - firstY] & 255,
                    v = observed[(y - firstY) * (right - left + 1) + x - left] & 255;
            Kind kind =
                    v > r
                            ? Kind.CONTRADICTION
                            : v == r
                                    ? (r == 255 ? Kind.OBSERVED_WHITE : Kind.MATCHED_CARRIER)
                                    : v * 255 <= 205 * r ? Kind.EXTRA_CORE : Kind.EXTRA_FRINGE;
            return new Cell(v, r, kind);
        }
    }

    record Result(
            Optional<Field> field,
            String refusal,
            List<Window> leftWindows,
            List<Window> rightWindows,
            long workRemaining) {
        Result {
            leftWindows = List.copyOf(leftWindows);
            rightWindows = List.copyOf(rightWindows);
        }
    }

    private record Frame(byte[] values, float gap) {}

    private record Reference(byte[] values, Window window) {}

    private LiteralStaffContrast() {}

    static Result observe(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float top,
            float gap,
            Budget budget) {
        if (budget == null) throw new IllegalArgumentException("Missing budget");
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || left < 1
                || right >= width - 1
                || left >= right
                || !Float.isFinite(top)
                || !Float.isFinite(gap)
                || gap < 8
                || gap > 64) return no("DOMAIN", budget);
        int first = Math.round(top - gap * .48f),
                last = Math.round(top + gap * 4.48f),
                rows = last - first + 1;
        if (first < 1 || last >= height - 1 || rows < 1) return no("FRAME_SCOPE", budget);
        long area = (long) width * rows;
        if (area > 250_000 || !budget.take(area * 12 + width * 64L))
            return no("FRAME_BUDGET", budget);
        var leftRefs = new ArrayList<Reference>();
        var rightRefs = new ArrayList<Reference>();
        Frame previous = null;
        int start = -1, previousSide = -2;
        for (int x = 1; x < width; x++) {
            int side = x < left - 1 ? 0 : x > right + 1 && x < width - 1 ? 1 : -1;
            Frame current = side < 0 ? null : frame(gray, width, x, first, last, top, gap);
            if (current != null
                    && previous != null
                    && previousSide == side
                    && Arrays.equals(current.values(), previous.values())) continue;
            if (previous != null && x - start >= Math.ceil(2 * previous.gap())) {
                var target = previousSide == 0 ? leftRefs : rightRefs;
                if (target.size() == 64) return no("WINDOW_LIMIT", budget);
                target.add(
                        new Reference(previous.values(), new Window(start, x - 1, previous.gap())));
            }
            previous = current;
            start = x;
            previousSide = side;
        }
        if (!budget.take((long) leftRefs.size() * rightRefs.size() * rows + 4096))
            return no("MATCH_BUDGET", budget);
        byte[] chosen = null;
        for (var a : leftRefs)
            for (var b : rightRefs)
                if (Arrays.equals(a.values(), b.values())) {
                    if (chosen != null && !Arrays.equals(chosen, a.values()))
                        return no("MULTIPLE_LITERAL_FRAMES", budget);
                    chosen = a.values();
                }
        if (chosen == null) return no("NO_TWO_SIDED_LITERAL_FRAME", budget);
        final byte[] profile = chosen;
        var leftWindows =
                leftRefs.stream()
                        .filter(a -> Arrays.equals(a.values(), profile))
                        .map(Reference::window)
                        .toList();
        var rightWindows =
                rightRefs.stream()
                        .filter(a -> Arrays.equals(a.values(), profile))
                        .map(Reference::window)
                        .toList();
        long count = (long) (right - left + 1) * rows;
        if (count > 250_000 || !budget.take(count * 8)) return no("FIELD_BUDGET", budget);
        byte[] observed = new byte[(int) count];
        for (int y = first; y <= last; y++)
            for (int x = left; x <= right; x++) {
                int v = gray[y * width + x] & 255;
                if (v > (profile[y - first] & 255))
                    return no("LITERAL_REFERENCE_CONTRADICTION", budget);
                observed[(y - first) * (right - left + 1) + x - left] = (byte) v;
            }
        return new Result(
                Optional.of(new Field(left, right, first, last, profile, observed)),
                "",
                leftWindows,
                rightWindows,
                budget.remaining);
    }

    /** Same complete five-rule literal-domain predicate as the independent staff-body proof. */
    private static Frame frame(
            byte[] gray, int width, int x, int first, int last, float top, float gap) {
        byte[] values = new byte[last - first + 1];
        for (int y = first; y <= last; y++) values[y - first] = gray[y * width + x];
        double[] minima = new double[5];
        for (int k = 0; k < 5; k++) {
            int low = Math.round(top + k * gap - gap * .48f),
                    high = Math.round(top + k * gap + gap * .48f);
            if (low < first
                    || high > last
                    || (gray[low * width + x] & 255) != 255
                    || (gray[high * width + x] & 255) != 255) return null;
            int minimum = 255, start = -1, end = -1, runs = 0, n = 0;
            long sum = 0;
            for (int y = low; y <= high; y++) {
                int v = gray[y * width + x] & 255;
                if (v < 245) {
                    if (y == low || (gray[(y - 1) * width + x] & 255) >= 245) runs++;
                    if (start < 0) start = y;
                    end = y;
                }
                if (v < minimum) {
                    minimum = v;
                    sum = y;
                    n = 1;
                } else if (v == minimum) {
                    sum += y;
                    n++;
                }
            }
            if (minimum >= 165 || runs != 1 || end - start + 1 > Math.ceil(.35 * gap)) return null;
            minima[k] = sum / (double) n;
        }
        double measured = (minima[4] - minima[0]) / 4;
        if (measured < gap * .9 || measured > gap * 1.1) return null;
        for (int k = 0; k < 5; k++)
            if (Math.abs(minima[k] - minima[0] - k * measured) > .1 * gap) return null;
        return new Frame(values, (float) measured);
    }

    private static Result no(String refusal, Budget budget) {
        return new Result(Optional.empty(), refusal, List.of(), List.of(), budget.remaining);
    }

    /** A separately bounded whole-page catalog route; original eight-million APIs stay exact. */
    static final class CatalogBudget {
        private long remaining;
        private boolean refused;

        CatalogBudget(long allowance) {
            if (allowance < 0 || allowance > 128_000_000L) throw new IllegalArgumentException();
            remaining = allowance;
        }

        boolean take(long n) {
            if (refused || n < 0 || n > remaining) {
                refused = true;
                return false;
            }
            remaining -= n;
            return true;
        }

        boolean refused() {
            return refused;
        }

        long remaining() {
            return remaining;
        }
    }

    /** Immutable complete-row snapshot and exact complete five-rule windows, never an eraser. */
    static final class Catalog {
        private final int width, first, last;
        private final byte[] snapshot;
        private final List<Reference> references;

        private Catalog(int width, int first, int last, byte[] snapshot, List<Reference> refs) {
            this.width = width;
            this.first = first;
            this.last = last;
            this.snapshot = snapshot;
            this.references = List.copyOf(refs);
        }

        Optional<Field> field(int left, int right, CatalogBudget budget) {
            if (left < 1 || right >= width - 1 || left >= right) return Optional.empty();
            int rows = last - first + 1;
            if (!budget.take(references.size() * 4L)) return Optional.empty();
            var ls = new ArrayList<Reference>();
            var rs = new ArrayList<Reference>();
            for (var ref : references) {
                var win = ref.window();
                int leftEnd = Math.min(win.right(), left - 2),
                        rightStart = Math.max(win.left(), right + 2);
                if (leftEnd - win.left() + 1 >= Math.ceil(2 * win.measuredGap()))
                    ls.add(
                            new Reference(
                                    ref.values(),
                                    new Window(win.left(), leftEnd, win.measuredGap())));
                if (win.right() - rightStart + 1 >= Math.ceil(2 * win.measuredGap()))
                    rs.add(
                            new Reference(
                                    ref.values(),
                                    new Window(rightStart, win.right(), win.measuredGap())));
            }
            if (ls.size() > 64 || rs.size() > 64) {
                budget.take(Long.MAX_VALUE);
                return Optional.empty();
            }
            if (!budget.take((long) ls.size() * rs.size() * rows + 4096)) return Optional.empty();
            byte[] chosen = null;
            for (var l : ls)
                for (var r : rs)
                    if (Arrays.equals(l.values(), r.values())) {
                        if (chosen != null && !Arrays.equals(chosen, l.values()))
                            return Optional.empty();
                        chosen = l.values();
                    }
            if (chosen == null) return Optional.empty();
            long area = (long) (right - left + 1) * rows;
            if (area > 250000 || !budget.take(area * 8)) return Optional.empty();
            byte[] observed = new byte[(int) area];
            for (int y = first; y <= last; y++)
                for (int x = left; x <= right; x++) {
                    int v = snapshot[(y - first) * width + x] & 255;
                    if (v > (chosen[y - first] & 255)) return Optional.empty();
                    observed[(y - first) * (right - left + 1) + x - left] = (byte) v;
                }
            return Optional.of(new Field(left, right, first, last, chosen, observed));
        }
    }

    /** Reference discovery is once per physical frame; every candidate still checks both sides,
     * all common profiles and every target cell before exposing an immutable contrast field. */
    static Optional<Catalog> catalog(
            byte[] gray, int width, int height, float top, float gap, CatalogBudget budget) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || !Float.isFinite(top)
                || !Float.isFinite(gap)
                || gap < 8
                || gap > 64) return Optional.empty();
        int first = Math.round(top - gap * .48f),
                last = Math.round(top + gap * 4.48f),
                rows = last - first + 1;
        if (first < 1 || last >= height - 1 || rows < 1) return Optional.empty();
        long area = (long) width * rows;
        if (area > 250000 || !budget.take(area * 12 + width * 64L)) return Optional.empty();
        var refs = new ArrayList<Reference>();
        Frame previous = null;
        int start = -1;
        for (int x = 1; x < width; x++) {
            Frame current = x == width - 1 ? null : frame(gray, width, x, first, last, top, gap);
            if (current != null
                    && previous != null
                    && Arrays.equals(current.values(), previous.values())) continue;
            if (previous != null && x - start >= Math.ceil(2 * previous.gap())) {
                if (refs.size() == 128) {
                    budget.take(Long.MAX_VALUE);
                    return Optional.empty();
                }
                refs.add(
                        new Reference(previous.values(), new Window(start, x - 1, previous.gap())));
            }
            previous = current;
            start = x;
        }
        return Optional.of(
                new Catalog(
                        width,
                        first,
                        last,
                        Arrays.copyOfRange(gray, first * width, (last + 1) * width),
                        refs));
    }
}

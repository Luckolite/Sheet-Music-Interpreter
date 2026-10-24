// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Immutable original-index column lookup only; no geometric or ownership authority. */
final class CoreColumnIndex {
    private final int[] starts, rows;
    private final int width, height;
    private final ObservedFringeContinuation.Charge work;

    private CoreColumnIndex(
            int[] starts,
            int[] rows,
            int width,
            int height,
            ObservedFringeContinuation.Charge work) {
        this.starts = starts;
        this.rows = rows;
        this.width = width;
        this.height = height;
        this.work = work;
    }

    private static void pay(ObservedFringeContinuation.Charge work, int n) {
        for (int i = 0; i < n; i++) work.tick();
    }

    static CoreColumnIndex create(
            int[] x,
            int[] y,
            int count,
            int width,
            int height,
            ObservedFringeContinuation.Charge work,
            Runnable capacity) {
        if (x == null
                || y == null
                || work == null
                || capacity == null
                || width < 1
                || height < 1
                || width > 128
                || height > 128
                || count < 0
                || count > x.length
                || count > y.length
                || count > (long) width * height)
            throw new IllegalArgumentException("core index domain");
        // Two arrays, including all zero initialization, reserved before allocation.
        capacity.run();
        capacity.run();
        pay(work, count + width + 7);
        int[] rows = new int[count], starts = new int[width + 1];
        int lastX = -1, lastY = -1;
        for (int q = 0; q < count; q++) {
            pay(work, 8);
            int xx = x[q], yy = y[q];
            if (xx < 0
                    || xx >= width
                    || yy < 0
                    || yy >= height
                    || xx < lastX
                    || xx == lastX && yy <= lastY)
                throw new IllegalArgumentException("original x-major unique core");
            rows[q] = yy;
            lastX = xx;
            lastY = yy;
        }
        int q = 0;
        for (int xx = 0; xx <= width; xx++) {
            pay(work, 2);
            starts[xx] = q;
            while (q < count && x[q] == xx) {
                pay(work, 3);
                q++;
            }
        }
        capacity.run();
        pay(work, 9);
        return new CoreColumnIndex(starts, rows, width, height, work);
    }

    /** Original q indices, half-open. Caller supplies conservative WHOLE-pixel row interval. */
    long range(int column, long interval) {
        pay(work, 8);
        if (column < 0 || column >= width) throw new IllegalArgumentException("core column");
        if (interval == CapsuleStripBounds.NONE) return CapsuleStripBounds.NONE;
        int lo = CapsuleStripBounds.first(interval), hi = CapsuleStripBounds.last(interval);
        if (lo < 0 || hi >= height || lo > hi)
            throw new IllegalArgumentException("core row interval");
        int first = lower(starts[column], starts[column + 1], lo);
        int end = lower(first, starts[column + 1], hi + 1);
        return first == end ? CapsuleStripBounds.NONE : ((long) first << 32) | (end & 0xffffffffL);
    }

    int size() {
        work.tick();
        return rows.length;
    }

    /** First original q with 4*x>=quarterBoundary; equality is retained, never sealed. */
    int sealedIndex(int quarterBoundary) {
        pay(work, 8);
        long column = -Math.floorDiv(-(long) quarterBoundary, 4);
        column = Math.max(0, Math.min(width, column));
        return starts[(int) column];
    }

    private int lower(int first, int end, int row) {
        while (first < end) {
            pay(work, 4);
            int mid = (first + end) >>> 1;
            if (rows[mid] < row) first = mid + 1;
            else end = mid;
        }
        return first;
    }
}

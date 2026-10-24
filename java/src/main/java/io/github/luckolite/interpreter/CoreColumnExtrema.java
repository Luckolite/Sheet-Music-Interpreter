// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Coverage observations only; every original core sample remains a candidate centre vertex. */
final class CoreColumnExtrema {
    final int[] x, y;
    final int count;

    private CoreColumnExtrema(int[] x, int[] y, int count) {
        this.x = x;
        this.y = y;
        this.count = count;
    }

    static CoreColumnExtrema create(
            int[] sx,
            int[] sy,
            int n,
            int width,
            int height,
            ObservedFringeContinuation.Charge work,
            Runnable capacity) {
        if (sx == null
                || sy == null
                || work == null
                || capacity == null
                || n < 1
                || n > 16384
                || n > sx.length
                || n > sy.length
                || width < 1
                || width > 128
                || height < 1
                || height > 128) throw new IllegalArgumentException("extrema domain");
        capacity.run();
        for (int i = 0; i < 4 * width + 12; i++) work.tick();
        int[] x = new int[2 * width], y = new int[2 * width];
        int out = 0, at = 0, previousX = -1;
        while (at < n) {
            work.tick();
            int column = sx[at], first = sy[at], last = first;
            if (column <= previousX
                    || column < 0
                    || column >= width
                    || first < 0
                    || first >= height) throw new IllegalArgumentException("ordered core columns");
            at++;
            while (at < n && sx[at] == column) {
                for (int i = 0; i < 3; i++) work.tick();
                if (sy[at] <= last || sy[at] >= height)
                    throw new IllegalArgumentException("ordered core rows");
                last = sy[at++];
            }
            for (int i = 0; i < 5; i++) work.tick();
            x[out] = column;
            y[out++] = first;
            if (last != first) {
                for (int i = 0; i < 3; i++) work.tick();
                x[out] = column;
                y[out++] = last;
            }
            previousX = column;
        }
        return new CoreColumnExtrema(x, y, out);
    }
}

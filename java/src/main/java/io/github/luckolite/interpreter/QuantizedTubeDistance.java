// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Exact local quarter-grid geometry. No raster/model/ownership authority. */
final class QuantizedTubeDistance {
    static final int MAX_SIDE = 128;
    // Source-local coordinates include one surrounding pixel of measured paper.
    private static final int LOW = -4, HIGH = 4 * MAX_SIDE + 4;
    private static final long DELTA = HIGH - LOW;
    private static final long MAX_DEN = 2 * DELTA * DELTA;
    private static final long MAX_NUM = MAX_DEN * MAX_DEN;

    record Ratio(long numerator, long denominator) implements Comparable<Ratio> {
        Ratio {
            if (numerator < 0 || numerator > MAX_NUM || denominator < 1 || denominator > MAX_DEN)
                throw new IllegalArgumentException("bounded local squared distance");
        }

        public int compareTo(Ratio other) {
            // MAX_NUM*MAX_DEN=158164877312000000 < Long.MAX_VALUE.
            return Long.compare(numerator * other.denominator, other.numerator * denominator);
        }
    }

    private QuantizedTubeDistance() {}

    private static boolean coordinate(int q) {
        return q >= LOW && q <= HIGH;
    }

    /** All coordinates are in quarter pixels, translated before this API. Invalid domain abstains. */
    static Ratio squared(
            int px,
            int py,
            int ax,
            int ay,
            int bx,
            int by,
            ObservedFringeContinuation.Charge work) {
        if (work == null
                || !coordinate(px)
                || !coordinate(py)
                || !coordinate(ax)
                || !coordinate(ay)
                || !coordinate(bx)
                || !coordinate(by)) return null;
        work.tick();
        long dx = (long) bx - ax, dy = (long) by - ay, x = (long) px - ax, y = (long) py - ay;
        long den = dx * dx + dy * dy, dot = x * dx + y * dy;
        work.tick();
        if (den == 0 || dot <= 0) {
            work.tick();
            return new Ratio(x * x + y * y, 1);
        }
        if (dot >= den) {
            work.tick();
            x = (long) px - bx;
            y = (long) py - by;
            return new Ratio(x * x + y * y, 1);
        }
        long cross = x * dy - y * dx;
        work.tick();
        return new Ratio(cross * cross, den);
    }

    static Ratio min(Ratio a, Ratio b, ObservedFringeContinuation.Charge work) {
        work.tick();
        return a.compareTo(b) <= 0 ? a : b;
    }

    static Ratio max(Ratio a, Ratio b, ObservedFringeContinuation.Charge work) {
        work.tick();
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Dark centres lie within the common tube, actual paper centres strictly outside it. */
    static boolean nonemptyRadiusInterval(
            Ratio allDarkMinimum, Ratio nearestPaper, ObservedFringeContinuation.Charge work) {
        if (allDarkMinimum == null || nearestPaper == null || work == null) return false;
        work.tick();
        return allDarkMinimum.compareTo(nearestPaper) < 0;
    }
}

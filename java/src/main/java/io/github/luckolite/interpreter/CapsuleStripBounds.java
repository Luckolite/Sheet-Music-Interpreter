// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Conservative enumeration only. Original exact finite-segment distance remains authority. */
final class CapsuleStripBounds {
    static final long NONE = Long.MIN_VALUE;
    private final ObservedFringeContinuation.Charge work;
    private boolean valid;
    private long ax, ay, dx, dy, den, root, limit;
    private int radius;

    private CapsuleStripBounds(ObservedFringeContinuation.Charge work) {
        this.work = work;
    }

    static CapsuleStripBounds create(ObservedFringeContinuation.Charge work, Runnable capacity) {
        if (work == null || capacity == null) throw new IllegalArgumentException("strip budget");
        capacity.run();
        for (int i = 0; i < 14; i++) work.tick();
        return new CapsuleStripBounds(work);
    }

    private void pay(int n) {
        for (int i = 0; i < n; i++) work.tick();
    }

    private static boolean coordinate(int q) {
        return q >= -4 && q <= 4 * QuantizedTubeDistance.MAX_SIDE + 4;
    }

    /** Endpoint/radius arguments are QUARTER pixels; no floating rounding or caller phase hint. */
    boolean prepare(int xa, int ya, int xb, int yb, int r) {
        valid = false;
        pay(24);
        if (!coordinate(xa)
                || !coordinate(ya)
                || !coordinate(xb)
                || !coordinate(yb)
                || r < 1
                || r > 520) return false;
        if (xa > xb) {
            int q = xa;
            xa = xb;
            xb = q;
            q = ya;
            ya = yb;
            yb = q;
        }
        long vx = (long) xb - xa, vy = (long) yb - ya, norm = vx * vx + vy * vy;
        long square = (long) r * r * norm;
        long low = 0, high = (long) r * (Math.abs(vx) + Math.abs(vy));
        while (low < high) {
            pay(4);
            long mid = (low + high) >>> 1;
            if (mid * mid < square) low = mid + 1;
            else high = mid;
        }
        pay(9);
        ax = xa;
        ay = ya;
        dx = vx;
        dy = vy;
        den = norm;
        root = low;
        limit = square;
        radius = r;
        valid = true;
        return true;
    }

    /** qx is QUARTER pixels; returned/clipped rows are WHOLE pixel indices (qy=4*y). */
    long rowsAtColumn(int qx, int oldFirst, int oldLast) {
        pay(14);
        if (!valid) return NONE;
        if (!coordinate(qx) || oldFirst > oldLast || oldFirst < -128 || oldLast > 129)
            throw new IllegalArgumentException("strip enumeration domain");
        if (dx == 0) return Math.abs((long) qx - ax) > radius ? NONE : pack(oldFirst, oldLast);
        long centre = dx * ay + dy * ((long) qx - ax), divisor = 4 * dx;
        long first = -Math.floorDiv(-(centre - root), divisor),
                last = Math.floorDiv(centre + root, divisor);
        first = Math.max(first, oldFirst);
        last = Math.min(last, oldLast);
        return first > last ? NONE : pack((int) first, (int) last);
    }

    /** Necessary only: false proves distance>radius; true STILL requires original exact distance. */
    boolean possiblyWithin(int qx, int qy) {
        pay(9);
        if (!valid) throw new IllegalStateException("unprepared strip");
        // Invalid points are never silently filtered; defer to the original distance-domain guard.
        if (!coordinate(qx) || !coordinate(qy)) return true;
        long x = (long) qx - ax, y = (long) qy - ay;
        if (den == 0) return Math.abs(x) <= radius && Math.abs(y) <= radius;
        long cross = dy * x - dx * y;
        return cross * cross <= limit;
    }

    private static long pack(int first, int last) {
        return ((long) first << 32) | (last & 0xffffffffL);
    }

    static int first(long rows) {
        return (int) (rows >> 32);
    }

    static int last(long rows) {
        return (int) rows;
    }

    long ceilNormal() {
        return root;
    }

    long squaredLimit() {
        return limit;
    }
}

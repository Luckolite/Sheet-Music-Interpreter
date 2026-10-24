// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Exact necessary infinite-line prerequisite for one real history's first newly sealed obligation. */
final class FirstMissingEndpointBound {
    private final int ax, ay, px, radius;
    private final long ux, uy, rr;
    private final ObservedFringeContinuation.Charge work;

    private FirstMissingEndpointBound(
            int px, int py, int ax, int ay, int radius, ObservedFringeContinuation.Charge work) {
        this.ax = ax;
        this.ay = ay;
        this.px = px;
        this.radius = radius;
        ux = (long) px - ax;
        uy = (long) py - ay;
        rr = (long) radius * radius;
        this.work = work;
    }

    private static void pay(ObservedFringeContinuation.Charge work, int n) {
        for (int i = 0; i < n; i++) work.tick();
    }

    static FirstMissingEndpointBound create(
            int px,
            int py,
            int ax,
            int ay,
            int radius,
            ObservedFringeContinuation.Charge work,
            Runnable capacity) {
        if (work == null || capacity == null)
            throw new IllegalArgumentException("endpoint bound callbacks");
        pay(work, 14);
        if (px < -4
                || px > 516
                || py < -4
                || py > 516
                || ax < -4
                || ax > 516
                || ay < -4
                || ay > 516
                || radius < 1
                || radius > 520
                || px < ax - radius)
            throw new IllegalArgumentException("endpoint bound new seal domain");
        capacity.run();
        pay(work, 18);
        return new FirstMissingEndpointBound(px, py, ax, ay, radius, work);
    }

    boolean allows(int bx, int by) {
        pay(work, 6);
        if (bx <= ax || bx > 516 || bx < -4 || by < -4 || by > 516)
            throw new IllegalArgumentException("endpoint bound increasing domain");
        pay(work, 3);
        if (px >= bx - radius) return true; // Strict end: Q at B.x-R may belong a future capsule.
        // |dx|,|dy|,|ux|,|uy|<=520; cross^2<=292464640000, rr*norm<=146232320000.
        pay(work, 12);
        long dx = (long) bx - ax, dy = (long) by - ay, cross = dx * uy - dy * ux;
        return cross * cross <= rr * (dx * dx + dy * dy);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.math.BigInteger;

/** Exact feasible parallel edges; observations retain the established one-pixel bound. */
final class FiniteParallelBeamBand {
    private record Q(BigInteger n, BigInteger d) implements Comparable<Q> {
        Q {
            if (d.signum() <= 0) throw new IllegalArgumentException();
            BigInteger k = n.gcd(d);
            n = n.divide(k);
            d = d.divide(k);
        }

        static Q of(long n) {
            return new Q(BigInteger.valueOf(n), BigInteger.ONE);
        }

        static Q ratio(long n, long d) {
            return new Q(BigInteger.valueOf(n), BigInteger.valueOf(d));
        }

        Q add(Q q) {
            return new Q(n.multiply(q.d).add(q.n.multiply(d)), d.multiply(q.d));
        }

        Q sub(Q q) {
            return new Q(n.multiply(q.d).subtract(q.n.multiply(d)), d.multiply(q.d));
        }

        Q times(long k) {
            return new Q(n.multiply(BigInteger.valueOf(k)), d);
        }

        public int compareTo(Q q) {
            return n.multiply(q.d).compareTo(q.n.multiply(d));
        }

        int floor() {
            BigInteger[] z = n.divideAndRemainder(d);
            return z[0].subtract(
                            n.signum() < 0 && z[1].signum() != 0 ? BigInteger.ONE : BigInteger.ZERO)
                    .intValueExact();
        }
    }

    private final int[] lo, hi;
    private final Q lowSlope, highSlope;

    private FiniteParallelBeamBand(int[] lo, int[] hi, Q sl, Q sh) {
        this.lo = lo;
        this.hi = hi;
        this.lowSlope = sl;
        this.highSlope = sh;
    }

    static FiniteParallelBeamBand prove(int[] top, int[] bottom, int thickness) {
        if (top == null
                || bottom == null
                || top.length != bottom.length
                || top.length < 2
                || thickness < 1
                || top.length > 512) return null; // At most 130816 exact pair constraints.
        int n = top.length;
        int[] l = new int[n], u = new int[n];
        Q sl = null, sh = null;
        for (int i = 0; i < n; i++) {
            if (top[i] < 0 || bottom[i] < top[i]) return null;
            long a = Math.max((long) bottom[i] - 1, (long) top[i] + thickness - 2);
            long b = Math.min((long) bottom[i] + 1, (long) top[i] + thickness);
            if (a > b || a < Integer.MIN_VALUE || b > Integer.MAX_VALUE) return null;
            l[i] = (int) a;
            u[i] = (int) b;
        }
        // Pairwise interval differences are necessary AND sufficient for a common intercept.
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                Q a = Q.ratio((long) l[j] - u[i], j - i), b = Q.ratio((long) u[j] - l[i], j - i);
                if (sl == null || a.compareTo(sl) > 0) sl = a;
                if (sh == null || b.compareTo(sh) < 0) sh = b;
            }
        return sl.compareTo(sh) > 0 ? null : new FiniteParallelBeamBand(l, u, sl, sh);
    }

    private Q intercept(Q s, boolean minimum) {
        Q v = null;
        for (int i = 0; i < lo.length; i++) {
            Q a = Q.of(minimum ? lo[i] : hi[i]).sub(s.times(i));
            if (v == null || (minimum ? a.compareTo(v) > 0 : a.compareTo(v) < 0)) v = a;
        }
        return v;
    }

    // Only exterior x is used: every (x-i) has one sign, so extrema occur at slope endpoints.
    private Q edge(int x, boolean minimum) {
        if (x >= 0 && x < lo.length) throw new IllegalArgumentException("exterior only");
        Q s = (x < 0) == minimum ? highSlope : lowSlope;
        return intercept(s, minimum).add(s.times(x));
    }

    boolean supportsEnd(int left, int right, int end) {
        if (left > right
                || left >= 0 && left < lo.length
                || right >= 0 && right < lo.length
                || left < 0 && right >= lo.length) return false;
        boolean positive = lowSlope.compareTo(Q.of(0)) >= 0;
        boolean negative = highSlope.compareTo(Q.of(0)) <= 0;
        if (!positive && !negative) return false; // No favorable slope selection.
        int minX = positive ? left : right, maxX = positive ? right : left;
        return Q.of(end).compareTo(edge(minX, false).sub(Q.of(1))) >= 0
                && Q.of(end).compareTo(edge(maxX, true).add(Q.of(1))) <= 0;
    }

    int terminalCeiling(int exteriorX) {
        return edge(exteriorX, true).add(Q.of(1)).floor();
    }
}

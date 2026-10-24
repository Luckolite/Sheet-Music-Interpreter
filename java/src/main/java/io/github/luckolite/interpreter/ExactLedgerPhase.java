// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.math.BigInteger;
import java.util.*;

/** Exact bounded two-variable phase family, retaining every admitted model. */
final class ExactLedgerPhase {
    record R(BigInteger n, BigInteger d) implements Comparable<R> {
        R {
            if (d.signum() == 0) throw new ArithmeticException();
            if (d.signum() < 0) {
                n = n.negate();
                d = d.negate();
            }
            BigInteger g = n.gcd(d);
            n = n.divide(g);
            d = d.divide(g);
            if (n.bitLength() > 256 || d.bitLength() > 256)
                throw new ArithmeticException("phase arithmetic bound");
        }

        static R of(long n) {
            return new R(BigInteger.valueOf(n), BigInteger.ONE);
        }

        static R ratio(long n, long d) {
            return new R(BigInteger.valueOf(n), BigInteger.valueOf(d));
        }

        static R of(float value) {
            if (!Float.isFinite(value)) throw new ArithmeticException();
            int bits = Float.floatToRawIntBits(value), e = (bits >>> 23) & 255;
            long m = bits & 0x7fffff;
            if (e != 0) m |= 1L << 23;
            int power = (e == 0 ? -126 : e - 127) - 23;
            if (bits < 0) m = -m;
            return power >= 0
                    ? new R(BigInteger.valueOf(m).shiftLeft(power), BigInteger.ONE)
                    : new R(BigInteger.valueOf(m), BigInteger.ONE.shiftLeft(-power));
        }

        R add(R x) {
            return new R(n.multiply(x.d).add(x.n.multiply(d)), d.multiply(x.d));
        }

        R sub(R x) {
            return add(x.neg());
        }

        R neg() {
            return new R(n.negate(), d);
        }

        R mul(R x) {
            return new R(n.multiply(x.n), d.multiply(x.d));
        }

        R mul(long x) {
            return new R(n.multiply(BigInteger.valueOf(x)), d);
        }

        R div(R x) {
            return new R(n.multiply(x.d), d.multiply(x.n));
        }

        R div(long x) {
            return new R(n, d.multiply(BigInteger.valueOf(x)));
        }

        R abs() {
            return n.signum() < 0 ? neg() : this;
        }

        double decimal() {
            return n.doubleValue() / d.doubleValue();
        }

        int floor() {
            BigInteger[] q = n.divideAndRemainder(d);
            return q[0].subtract(
                            n.signum() < 0 && q[1].signum() != 0 ? BigInteger.ONE : BigInteger.ZERO)
                    .intValueExact();
        }

        int ceil() {
            return neg().floor() * -1;
        }

        public int compareTo(R x) {
            return n.multiply(x.d).compareTo(x.n.multiply(d));
        }
    }

    record Point(R phase, R gap) {}

    record Range(R low, R high) {
        double lowValue() {
            return low.decimal();
        }

        double highValue() {
            return high.decimal();
        }
    }

    private record Band(R low, R high) {}

    private record Line(int a, int b, R value) {}

    private final TreeMap<Integer, Band> bands;
    private final R minimumGap, maximumGap;
    private final List<Point> vertices;

    private ExactLedgerPhase(TreeMap<Integer, Band> b, R low, R high) {
        bands = b;
        minimumGap = low;
        maximumGap = high;
        vertices = vertices();
    }

    static ExactLedgerPhase initial(R expected, float gap) {
        R g = R.of(gap);
        var b = new TreeMap<Integer, Band>();
        b.put(0, new Band(expected.sub(g.div(2)), expected.add(g.div(2))));
        return new ExactLedgerPhase(b, g.mul(R.ratio(17, 20)), g.mul(R.ratio(23, 20)));
    }

    ExactLedgerPhase band(int k, R low, R high) {
        var prior = bands.get(k);
        if (prior != null) {
            if (prior.low.compareTo(low) > 0) low = prior.low;
            if (prior.high.compareTo(high) < 0) high = prior.high;
            if (low.equals(prior.low) && high.equals(prior.high)) return this;
        }
        if (low.compareTo(high) > 0) return null;
        var next = new TreeMap<>(bands);
        next.put(k, new Band(low, high));
        if (next.size() > 12) throw new ArithmeticException("phase state bound");
        var result = new ExactLedgerPhase(next, minimumGap, maximumGap);
        return result.vertices.isEmpty() ? null : result;
    }

    private List<Point> vertices() {
        var lines = new ArrayList<Line>();
        for (var e : bands.entrySet()) {
            lines.add(new Line(1, e.getKey(), e.getValue().low));
            lines.add(new Line(1, e.getKey(), e.getValue().high));
        }
        lines.add(new Line(0, 1, minimumGap));
        lines.add(new Line(0, 1, maximumGap));
        var out = new LinkedHashSet<Point>();
        for (int i = 0; i < lines.size(); i++)
            for (int j = i + 1; j < lines.size(); j++) {
                Line x = lines.get(i), y = lines.get(j);
                int determinant = x.a * y.b - y.a * x.b;
                if (determinant == 0) continue;
                R phase = x.value.mul(y.b).sub(y.value.mul(x.b)).div(determinant),
                        gap = y.value.mul(x.a).sub(x.value.mul(y.a)).div(determinant);
                if (gap.compareTo(minimumGap) < 0 || gap.compareTo(maximumGap) > 0) continue;
                boolean valid = true;
                for (var e : bands.entrySet()) {
                    R row = phase.add(gap.mul(e.getKey()));
                    if (row.compareTo(e.getValue().low) < 0
                            || row.compareTo(e.getValue().high) > 0) {
                        valid = false;
                        break;
                    }
                }
                if (valid) out.add(new Point(phase, gap));
            }
        return List.copyOf(out);
    }

    boolean headRegistered(float ny, int direction, int halfStep) {
        R head = R.of(ny).mul(direction), wanted = R.ratio(halfStep, 2), tolerance = R.ratio(1, 8);
        for (Point p : vertices)
            if (p.phase.sub(head).div(p.gap).sub(wanted).abs().compareTo(tolerance) > 0)
                return false;
        return !vertices.isEmpty();
    }

    Range range(int k, int direction) {
        R low = null, high = null;
        for (Point p : vertices) {
            R y = p.phase.add(p.gap.mul(k)).mul(direction);
            if (low == null || y.compareTo(low) < 0) low = y;
            if (high == null || y.compareTo(high) > 0) high = y;
        }
        if (low == null) throw new ArithmeticException("empty phase family");
        return new Range(low, high);
    }

    static boolean covers(Range range, int y, int maximumRows, int shade) {
        R radius = R.ratio(maximumRows + 1, 2),
                bottom = R.ratio(2L * y + 1, 2),
                top = R.ratio(2L * y - 1, 2),
                required = R.ratio(255 - shade, 255);
        for (R center : List.of(range.low, range.high)) {
            R high = center.add(radius), low = center.sub(radius);
            if (high.compareTo(bottom) > 0) high = bottom;
            if (low.compareTo(top) < 0) low = top;
            R overlap = high.sub(low);
            if (overlap.compareTo(R.of(0)) < 0) overlap = R.of(0);
            if (overlap.compareTo(required) < 0) return false;
        }
        return true;
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Fits one spacing to the actual five observed pixel bands at both witness columns. */
final class PrintedBeamRuleFrame {
    record Pair(double leftBottom, double rightBottom, double gap) {}

    private record Range(double low, double high) {}

    private PrintedBeamRuleFrame() {}

    static Pair fit(
            PrintedBeamRuleWitness.Witness left,
            PrintedBeamRuleWitness.Witness right,
            double preferredGap) {
        if (left == null
                || right == null
                || !Double.isFinite(preferredGap)
                || preferredGap < 3
                || preferredGap > 64) return null;
        var l = gaps(left);
        var r = gaps(right);
        if (l == null || r == null) return null;
        double low = Math.max(Math.max(l.low, r.low), Math.max(3, preferredGap * .88));
        double high = Math.min(Math.min(l.high, r.high), Math.min(64, preferredGap * 1.12));
        if (low > high) return null;
        double gap = Math.max(low, Math.min(high, preferredGap));
        var lp = phase(left, gap);
        var rp = phase(right, gap);
        if (lp == null || rp == null) return null;
        return new Pair((lp.low + lp.high) * .5, (rp.low + rp.high) * .5, gap);
    }

    private static Range gaps(PrintedBeamRuleWitness.Witness witness) {
        if (witness.rules().size() != 5) return null;
        double low = 3, high = 64;
        int previous = Integer.MIN_VALUE;
        for (var band : witness.rules()) {
            if (band.top() > band.bottom() || band.top() <= previous) return null;
            previous = band.bottom();
        }
        // Every lower phase bound must lie below every upper phase bound.
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++) {
                double difference = j - i;
                double limit =
                        (double) witness.rules().get(j).bottom() - witness.rules().get(i).top() + 1;
                if (difference > 0) high = Math.min(high, limit / difference);
                else if (difference < 0) low = Math.max(low, limit / difference);
                else if (limit < 0) return null;
            }
        return low <= high ? new Range(low, high) : null;
    }

    private static Range phase(PrintedBeamRuleWitness.Witness witness, double gap) {
        double low = Double.NEGATIVE_INFINITY, high = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 5; i++) {
            var band = witness.rules().get(i);
            double offset = (4 - i) * gap;
            low = Math.max(low, band.top() - .5 + offset);
            high = Math.min(high, band.bottom() + .5 + offset);
        }
        return low <= high ? new Range(low, high) : null;
    }
}

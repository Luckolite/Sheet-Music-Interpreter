// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.BitSet;

/** History-specific necessary veto only. Never global edge safety or positive coverage. */
final class SealedTransitionGuard {
    private SealedTransitionGuard() {}

    private static void pay(ObservedFringeContinuation.Charge work, int n) {
        for (int i = 0; i < n; i++) work.tick();
    }

    static boolean allows(
            CoreColumnIndex index,
            int[] x,
            int[] y,
            int count,
            BitSet covered,
            int ax,
            int ay,
            int bx,
            int by,
            int radius,
            ObservedFringeContinuation.Charge work) {
        if (index == null
                || x == null
                || y == null
                || covered == null
                || work == null
                || count < 0
                || count > 16384
                || count > x.length
                || count > y.length) throw new IllegalArgumentException("sealed transition input");
        pay(work, (count + 63) / 64 + 24);
        if (bx <= ax
                || ax < -4
                || bx > 516
                || ay < -4
                || ay > 516
                || by < -4
                || by > 516
                || radius < 1
                || radius > 520
                || covered.length() > count
                || index.size() != count)
            throw new IllegalArgumentException("strict increasing bounded transition");
        int first = index.sealedIndex(ax - radius), end = index.sealedIndex(bx - radius);
        long radiusSquared = (long) radius * radius;
        for (int q = first; q < end; q++) {
            pay(work, 3);
            if (covered.get(q)) continue;
            // Transient exact Ratio and call payload prepaid; no new retained search state.
            pay(work, 5);
            QuantizedTubeDistance.Ratio distance =
                    QuantizedTubeDistance.squared(4 * x[q], 4 * y[q], ax, ay, bx, by, work);
            if (distance == null) throw new IllegalStateException("sealed exact distance domain");
            pay(work, 2);
            if (distance.numerator() > radiusSquared * distance.denominator()) return false;
        }
        return true;
    }
}

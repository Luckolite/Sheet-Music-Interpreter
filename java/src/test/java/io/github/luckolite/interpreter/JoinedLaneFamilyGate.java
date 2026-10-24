// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Set;

/** Prepaid joined-family predicate; the legacy nonjoined contract remains unchanged. */
final class JoinedLaneFamilyGate {
    private JoinedLaneFamilyGate() {}

    static boolean qualifies(
            MaximalRawBarExtractor.Discovery discovery,
            boolean joined,
            int gap,
            int width,
            Set<Integer> staffPixels,
            long[] remainingWork) {
        if (discovery == null
                || remainingWork == null
                || remainingWork.length != 1
                || remainingWork[0] < 0) return false;
        if (!joined) return discovery.twoOrThreeSameShape(gap);
        long charge = discovery.observedFamilyWorkUpperBound(width, staffPixels);
        if (charge == Long.MAX_VALUE || charge > remainingWork[0]) return false;
        remainingWork[0] -= charge; // Debit before maps, edge scans, or proof allocation.
        return discovery.twoOrThreeObservedFamily(gap, width, staffPixels);
    }
}

// Copyright 2026 Luckolite and Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** All eligible local five-rule groups, without ranking away overlapping alternatives. */
final class PrintedBeamRuleCandidates {
    private PrintedBeamRuleCandidates() {}

    static int[] unique(
            int[] strength,
            int minimum,
            int height,
            int first,
            float nominalBottom,
            float nominalGap,
            FaintQuarterRestInk.Budget budget) {
        if (budget == null) throw new IllegalArgumentException("Missing local rule budget");
        FaintQuarterRestInk.charge(budget, 32, 0);
        if (strength == null
                || strength.length != height
                || height < 1
                || minimum < 1
                || !Float.isFinite(nominalBottom)
                || !Float.isFinite(nominalGap)
                || nominalGap <= 0)
            throw new IllegalArgumentException("Invalid local rule projection");
        FaintQuarterRestInk.charge(budget, (long) height + 1, 32L + 8L * height);
        int[] peaks = new int[height];
        int count = 0;
        // Same prominence and three-row merge law as the general projection detector.
        // A single pass retains every resulting peak; no confidence ranking removes one.
        for (int row = 0; row < height; row++) {
            FaintQuarterRestInk.charge(budget, 16, 0);
            if (strength[row] < minimum) continue;
            boolean maximum = true, prominent = false;
            for (int check = Math.max(0, row - 2);
                    check <= Math.min(height - 1, row + 2);
                    check++) {
                FaintQuarterRestInk.charge(budget, 4, 0);
                if (strength[check] > strength[row]) maximum = false;
                if (strength[check] < strength[row]) prominent = true;
            }
            if (!maximum || !prominent) continue;
            FaintQuarterRestInk.charge(budget, 8, 0);
            if (count == 0 || row - peaks[count - 1] > 3) peaks[count++] = row;
            else if (strength[row] > strength[peaks[count - 1]]) peaks[count - 1] = row;
        }
        int[] found = null;
        float maximumGap = Math.max(40f, height * .035f);
        for (int a = 0; a + 4 < count; a++) {
            FaintQuarterRestInk.charge(budget, 4, 0);
            int top = peaks[a];
            for (int e = a + 4; e < count; e++) {
                FaintQuarterRestInk.charge(budget, 32, 0);
                int bottom = peaks[e];
                float gap = (bottom - top) / 4f;
                if (gap < 2.5f) continue;
                if (gap > maximumGap) break;
                if (gap < nominalGap * .88f
                        || gap > nominalGap * 1.12f
                        || Math.abs(bottom + first - nominalBottom) > nominalGap * .8f) continue;
                float tolerance = Math.max(2f, gap * .24f);
                // Enumerate interior alternatives too: nearest-peak selection is a
                // ranking policy, not evidence that another complete group is absent.
                for (int b = a + 1; b + 2 < e; b++) {
                    FaintQuarterRestInk.charge(budget, 16, 0);
                    float d1 = Math.abs(peaks[b] - (top + gap));
                    if (d1 > tolerance) continue;
                    for (int c = b + 1; c + 1 < e; c++) {
                        FaintQuarterRestInk.charge(budget, 16, 0);
                        float d2 = Math.abs(peaks[c] - (top + 2 * gap));
                        if (d2 > tolerance) continue;
                        for (int d = c + 1; d < e; d++) {
                            FaintQuarterRestInk.charge(budget, 32, 0);
                            float d3 = Math.abs(peaks[d] - (top + 3 * gap));
                            if (d3 > tolerance) continue;
                            float error = Math.max(0f, d1 - .5f);
                            error += Math.max(0f, d2 - .5f);
                            error += Math.max(0f, d3 - .5f);
                            long total =
                                    (long) strength[top]
                                            + strength[peaks[b]]
                                            + strength[peaks[c]]
                                            + strength[peaks[d]]
                                            + strength[bottom];
                            float score = (float) total - error * minimum * .75f;
                            if (score < minimum * 4.5f) continue;
                            if (found != null) return null;
                            FaintQuarterRestInk.charge(budget, 16, 32L + 8L * 5);
                            found = new int[] {top, peaks[b], peaks[c], peaks[d], bottom};
                        }
                    }
                }
            }
        }
        return found;
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Consensus for a dropped repeated letter in an independently located credit line. */
public final class RepeatedCreditLetterConsensus {
    private RepeatedCreditLetterConsensus() {}

    public static String restore(String original, List<String> readings, List<Float> confidence) {
        if (original == null
                || readings == null
                || confidence == null
                || readings.size() != 3
                || confidence.size() != 3) return original;
        String after = readings.get(0);
        if (after == null || after.equals(original)) return original;
        for (int i = 0; i < 3; i++) {
            Float c = confidence.get(i);
            if (c == null
                    || !Float.isFinite(c)
                    || c < .98f
                    || c > 1
                    || !after.equals(readings.get(i))) return original;
        }
        int[] before = original.codePoints().toArray(), next = after.codePoints().toArray();
        if (next.length != before.length + 1) return original;
        for (int i = 1; i < next.length; i++) {
            if (!Character.isLetter(next[i]) || next[i] != next[i - 1]) continue;
            if ((i > 1 && next[i - 2] == next[i])
                    || (i + 1 < next.length && next[i + 1] == next[i])) continue;
            boolean same = true;
            for (int j = 0; j < before.length; j++)
                if (before[j] != next[j < i ? j : j + 1]) {
                    same = false;
                    break;
                }
            if (same) return after;
        }
        return original;
    }
}

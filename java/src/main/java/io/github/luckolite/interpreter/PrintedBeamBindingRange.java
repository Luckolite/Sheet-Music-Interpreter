// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Veto-only float interval. A possible result supplies no staff or owner evidence. */
final class PrintedBeamBindingRange {
    static boolean possible(
            float y,
            int step,
            float nominalBottom,
            float nominalGap,
            float phaseTolerance,
            float gapTolerance,
            float pitchTolerancePerGap) {
        if (!Float.isFinite(y)
                || !Float.isFinite(nominalBottom)
                || !Float.isFinite(nominalGap)
                || nominalGap <= 0
                || !Float.isFinite(phaseTolerance)
                || phaseTolerance < 0
                || !Float.isFinite(gapTolerance)
                || gapTolerance < 0
                || !Float.isFinite(pitchTolerancePerGap)
                || pitchTolerancePerGap < 0) return false;
        float blo = lower(nominalBottom, phaseTolerance),
                bhi = upper(nominalBottom, phaseTolerance);
        float glo = lower(nominalGap, gapTolerance), ghi = upper(nominalGap, gapTolerance);
        if (glo <= 0 || !Float.isFinite(ghi)) return true; // No favorable domain pruning.
        float p0 = step * glo * .5f, p1 = step * ghi * .5f;
        float low = blo - Math.max(p0, p1), high = bhi - Math.min(p0, p1);
        float maximumTolerance = ghi * pitchTolerancePerGap;
        return !(y < low && low - y > maximumTolerance || y > high && y - high > maximumTolerance);
    }

    private static float lower(float center, float tolerance) {
        return Math.nextDown((float) ((double) center - Math.nextUp(tolerance)));
    }

    private static float upper(float center, float tolerance) {
        return Math.nextUp((float) ((double) center + Math.nextUp(tolerance)));
    }
}

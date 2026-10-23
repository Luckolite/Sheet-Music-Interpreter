// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Types a separate short run only inside an independently proved five-rule staff band. */
final class SeparatedRuleRunOwnership {
    private SeparatedRuleRunOwnership() {}

    static boolean matches(
            byte[] raw,
            int w,
            int h,
            int x,
            int first,
            int last,
            StaffPitchTrack track,
            AttachedApproachBranches.Budget budget) {
        if (first < 1
                || last >= h - 1
                || last < first
                || x < 0
                || x >= w
                || track == null
                || !track.verifiedAt(x)) return false;
        float[] frame = track.at(x);
        float gap = frame[1];
        if (!Float.isFinite(frame[0] + gap)
                || gap < 6
                || gap > 64
                || last - first + 1 > Math.max(3, Math.ceil(gap * .24f))) return false;
        boolean band = false;
        float radius = Math.max(1, gap * .13f + .5f);
        for (int line = 0; line < 5; line++) {
            float center = frame[0] - line * gap;
            if (first >= center - radius && last <= center + radius) band = true;
        }
        if (!band) return false;
        for (int y = first; y <= last; y++) if ((raw[y * w + x] & 255) >= 150) return false;
        // A separate run has real paper on both sides; a clipped piece of a head or shaft
        // may not acquire rule ownership merely because it lies near a printed line.
        if ((raw[(first - 1) * w + x] & 255) < 150 || (raw[(last + 1) * w + x] & 255) < 150)
            return false;
        if (!budget.take((long) Math.ceil(gap * gap * 2 + 32))) return false;
        return FlagRootStaffRule.matches(raw, w, h, Math.round((first + last) * .5f), x, gap, 150);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Candidate and rectangular subset observations through existing admission laws.
 * The source plane and semantic context are never cropped or rewritten. */
final class ExistingHeadSubsetPreserver {
    record Region(
            int area, int left, int right, int top, int bottom, float centerX, float centerY) {}

    interface Admission {
        boolean supported(Region region, int[] exactSubset);
    }

    static boolean supported(
            int[] members, int width, float gap, SlurEvidenceBudget budget, Admission admission) {
        int l = width, r = -1, t = Integer.MAX_VALUE, b = -1;
        var seen = new HashSet<Integer>();
        for (int p : members) {
            budget.use(1, 32);
            if (!seen.add(p)) throw new SlurEvidenceBudget.Unknown("DUPLICATE_SUBSET_SOURCE");
            l = Math.min(l, p % width);
            r = Math.max(r, p % width);
            t = Math.min(t, p / width);
            b = Math.max(b, p / width);
        }
        if (r < l || b < t || !Float.isFinite(gap) || gap < 6)
            throw new SlurEvidenceBudget.Unknown("SUBSET_SOURCE_UNKNOWN");
        int minW = (int) Math.ceil(Math.max(2, gap * .38f)),
                minH = (int) Math.ceil(Math.max(2, gap * .30f));
        int minArea = Math.max(4, Math.round(gap * gap * .11f));
        long maximumScope =
                (long) (r - l + 1 + 2 * Math.round(gap) + 4)
                        * (b - t + 1 + 2 * Math.round(gap) + 4);
        budget.use(0, 32 * maximumScope + 4L * members.length + 8192);
        for (int left = l; left <= r; left++)
            for (int right = left + minW - 1; right <= r; right++)
                for (int top = t; top <= b; top++)
                    for (int bottom = top + minH - 1; bottom <= b; bottom++) {
                        int bw = right - left + 1, bh = bottom - top + 1;
                        if (bw > gap * 3.2f || bh > gap * 3.2f) continue;
                        budget.use(members.length, 0);
                        var subset = new int[members.length];
                        int n = 0, sl = width, sr = -1, st = Integer.MAX_VALUE, sb = -1;
                        long sx = 0, sy = 0;
                        for (int p : members) {
                            int x = p % width, y = p / width;
                            if (x < left || x > right || y < top || y > bottom) continue;
                            subset[n++] = p;
                            sx += x;
                            sy += y;
                            sl = Math.min(sl, x);
                            sr = Math.max(sr, x);
                            st = Math.min(st, y);
                            sb = Math.max(sb, y);
                        }
                        if (n < minArea || sl != left || sr != right || st != top || sb != bottom)
                            continue;
                        // Conservative accounting for the unchanged local-paper, pocket,
                        // relative-counter, stem and body checks supplied by the caller.
                        long scope =
                                (long) (bw + 2 * Math.round(gap) + 4)
                                        * (bh + 2 * Math.round(gap) + 4);
                        budget.use(128 * scope, 0);
                        if (admission.supported(
                                new Region(
                                        n,
                                        left,
                                        right,
                                        top,
                                        bottom,
                                        sx / (float) n,
                                        sy / (float) n),
                                Arrays.copyOf(subset, n))) return true;
                    }
        return false;
    }
}

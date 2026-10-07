// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** A proved printed system can end before model-labelled photograph texture. */
final class PrintedStaffEnd {
    static Integer closingBeforeTexture(
            byte[] gray,
            int width,
            int height,
            float gap,
            List<Integer> bars,
            PrintedStaffAdmission.Rules rules) {
        if (gray == null
                || gray.length != (long) width * height
                || gap < 3
                || bars == null
                || bars.size() < 3
                || rules == null) return null;
        int end = bars.get(bars.size() - 1);
        for (int i = bars.size() - 2; i >= Math.max(1, bars.size() - 4); i--) {
            int cut = bars.get(i);
            if (end - cut < gap * 2.5f) continue;
            var left =
                    PrintedStaffAdmission.evidence(
                            gray,
                            width,
                            height,
                            Math.round(cut - gap * 4),
                            Math.round(cut - gap * .1f),
                            gap,
                            rules);
            var right =
                    PrintedStaffAdmission.evidence(
                            gray, width, height, Math.round(cut + gap * .1f), end - 1, gap, rules);
            if (left.columns() >= gap * 3
                    && left.alignedColumns() >= left.columns() * .7f
                    && left.longestRun() >= gap * 2.4f
                    && right.columns() >= gap * 2
                    && right.alignedColumns() <= 1
                    && right.longestRun() <= 1
                    && right.backgroundSamples() > 0
                    && right.darkSamples() >= right.backgroundSamples() * .85f
                    && right.backgroundRange() >= 24) return cut;
        }
        return null;
    }
}

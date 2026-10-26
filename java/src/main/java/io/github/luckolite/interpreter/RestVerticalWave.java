// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A cropped arpeggio remains part of a longer repeatedly reversing stroke. */
final class RestVerticalWave {
    private RestVerticalWave() {}

    static boolean crosses(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap) {
        return crosses(gray, width, height, left, right, top, bottom, gap, null);
    }

    static boolean crosses(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (gray == null || gap < 4 || left < 0 || right >= width || top < 0 || bottom >= height)
            return false;
        int first = Math.max(0, Math.round(top - gap * 3)),
                last = Math.min(height - 1, Math.round(bottom + gap * 3));
        int runStart = -1, runEnd = -1, blank = 0, turns = 0, direction = 0;
        float extreme = Float.NaN;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = first; y <= last; y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                int x0 = -1, x1 = -1;
                int ruleLeft = Math.round(left - gap), ruleRight = Math.round(right + gap);
                boolean rule =
                        left - gap >= 0
                                && right + gap < width
                                && ruleLeft >= 0
                                && ruleRight < width
                                && dark(gray, width, ruleLeft, y, quarterBudget)
                                && dark(gray, width, ruleRight, y, quarterBudget);
                if (rule) continue;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = left; x <= right; x++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (dark(gray, width, x, y, quarterBudget)) {
                            if (x0 < 0) x0 = x;
                            x1 = x;
                        }
                    }
                }

                if (x0 < 0) {
                    if (++blank > Math.max(1, Math.round(gap * .3f))) {
                        if (valid(runStart, runEnd, turns, top, bottom, gap, quarterBudget))
                            return true;
                        runStart = -1;
                        runEnd = -1;
                        turns = 0;
                        direction = 0;
                        extreme = Float.NaN;
                    }
                    continue;
                }
                blank = 0;
                if (runStart < 0) runStart = y;
                runEnd = y;
                float center = (x0 + x1) * .5f, excursion = Math.max(1.5f, gap * .12f);
                if (Float.isNaN(extreme)) {
                    extreme = center;
                    continue;
                }
                if (direction == 0) {
                    if (Math.abs(center - extreme) >= excursion) {
                        direction = center > extreme ? 1 : -1;
                        extreme = center;
                    }
                } else if (direction * (center - extreme) >= 0) extreme = center;
                else if (Math.abs(center - extreme) >= excursion) {
                    turns++;
                    direction = -direction;
                    extreme = center;
                }
            }
        }

        return valid(runStart, runEnd, turns, top, bottom, gap, quarterBudget);
    }

    private static boolean valid(int first, int last, int turns, int top, int bottom, float gap) {
        return valid(first, last, turns, top, bottom, gap, null);
    }

    private static boolean valid(
            int first,
            int last,
            int turns,
            int top,
            int bottom,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return first >= 0
                && first <= top + gap * .3f
                && last >= bottom - gap * .3f
                && last - first >= gap * 3.7f
                && turns >= 5;
    }

    private static boolean dark(byte[] gray, int width, int x, int y) {
        return dark(gray, width, x, y, null);
    }

    private static boolean dark(
            byte[] gray, int width, int x, int y, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return (gray[y * width + x] & 255) < 150;
    }
}

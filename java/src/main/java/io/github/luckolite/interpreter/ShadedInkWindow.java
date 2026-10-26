// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A bounded symbol window, retaining the existing threshold on unshaded paper. */
final class ShadedInkWindow {
    static int limit(
            byte[] gray, int w, int h, int left, int top, int right, int bottom, int normal) {
        return limit(gray, w, h, left, top, right, bottom, normal, 70, 35);
    }

    static int flatSpineLimit(
            byte[] gray, int w, int h, int left, int top, int right, int bottom, int normal) {
        return flatSpineLimit(gray, w, h, left, top, right, bottom, normal, null);
    }

    static int flatSpineLimit(
            byte[] gray,
            int w,
            int h,
            int left,
            int top,
            int right,
            int bottom,
            int normal,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return limit(gray, w, h, left, top, right, bottom, normal, 100, 50, quarterBudget);
    }

    private static int limit(
            byte[] gray,
            int w,
            int h,
            int left,
            int top,
            int right,
            int bottom,
            int normal,
            int maxInk,
            int contrast) {
        return limit(gray, w, h, left, top, right, bottom, normal, maxInk, contrast, null);
    }

    private static int limit(
            byte[] gray,
            int w,
            int h,
            int left,
            int top,
            int right,
            int bottom,
            int normal,
            int maxInk,
            int contrast,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (gray == null) return normal;
        int[] histogram =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) (256))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) (256))))
                        ? new int[256]
                        : null);
        int n = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = Math.max(0, top); y <= Math.min(h - 1, bottom); y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = Math.max(0, left); x <= Math.min(w - 1, right); x++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        histogram[gray[y * w + x] & 255]++;
                        n++;
                    }
                }
            }
        }

        if (n == 0) return normal;
        int ink = 0, paper = 255, sum = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < 256; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                sum += histogram[i];
                if (sum >= n * .02) {
                    ink = i;
                    break;
                }
            }
        }

        sum = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < 256; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                sum += histogram[i];
                if (sum >= n * .9) {
                    paper = i;
                    break;
                }
            }
        }

        if (paper >= 185 || paper < 60 || ink >= maxInk || paper - ink < contrast) return normal;
        return Math.min(paper - 12, Math.round(ink + (paper - ink) * .55f));
    }
}

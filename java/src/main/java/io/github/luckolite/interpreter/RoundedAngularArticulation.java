// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Two independently straight arms remain angular when rounded ink edges are thick. */
final class RoundedAngularArticulation {
    private RoundedAngularArticulation() {}

    static boolean matches(
            int[] pixels, int width, int left, int top, int right, int bottom, int kind) {
        return completeArms(pixels, width, left, top, right, bottom, kind, true);
    }

    /** Open carets can be thin: complete column support and both fitted arms prove the ink. */
    static boolean matchesOpen(
            int[] pixels, int width, int left, int top, int right, int bottom, int kind) {
        return completeArms(pixels, width, left, top, right, bottom, kind, false);
    }

    /**
     * Rule-joined up-carets retain only observed ink. The caller supplies the exact independent
     * rule-row mask used to remove rows while collecting this glyph, never inferred missing ink.
     */
    static boolean matchesRuleJoinedOpen(
            int[] pixels,
            int width,
            int left,
            int top,
            int right,
            int bottom,
            boolean[] rules,
            int rulesTop) {
        if (rules == null
                || top < rulesTop
                || bottom < top
                || (long) bottom - rulesTop >= rules.length) return false;
        return completeArms(pixels, width, left, top, right, bottom, 1, false, rules, rulesTop);
    }

    private static boolean completeArms(
            int[] pixels,
            int width,
            int left,
            int top,
            int right,
            int bottom,
            int kind,
            boolean requireDensity) {
        return completeArms(pixels, width, left, top, right, bottom, kind, requireDensity, null, 0);
    }

    private static boolean completeArms(
            int[] pixels,
            int width,
            int left,
            int top,
            int right,
            int bottom,
            int kind,
            boolean requireDensity,
            boolean[] rules,
            int rulesTop) {
        int transverse = kind == 0 ? bottom - top + 1 : right - left + 1;
        int axial = kind == 0 ? right - left + 1 : bottom - top + 1;
        if (pixels == null
                || pixels.length < 8
                || width <= 0
                || transverse < 8
                || axial < 8
                || kind < 0
                || kind > 2) return false;
        double density = pixels.length / (double) (transverse * axial);
        if ((requireDensity && density < .18) || density > .62) return false;
        double[] sum = new double[transverse], mean = new double[transverse];
        int[] count = new int[transverse], low = new int[transverse], high = new int[transverse];
        java.util.Arrays.fill(low, axial);
        for (int p : pixels) {
            int x = p % width - left, y = p / width - top;
            int t = kind == 0 ? y : x, u = kind == 0 ? x : kind == 1 ? bottom - top - y : y;
            if (t < 0 || t >= transverse || u < 0 || u >= axial) return false;
            if (rules != null && rules[p / width - rulesTop]) return false;
            sum[t] += u;
            count[t]++;
            low[t] = Math.min(low[t], u);
            high[t] = Math.max(high[t], u);
        }
        int peak = 0;
        for (int t = 0; t < transverse; t++) {
            int observedSpan = high[t] - low[t] + 1;
            int requiredSupport = observedSpan;
            if (rules != null)
                for (int u = low[t]; u <= high[t]; u++)
                    if (rules[bottom - u - rulesTop]) requiredSupport--;
            if (count[t] == 0
                    || (observedSpan > axial * .78 && (rules == null || count[t] != observedSpan))
                    || count[t] < requiredSupport * .8) return false;
            mean[t] = sum[t] / count[t];
            if (mean[t] > mean[peak]) peak = t;
        }
        if (peak < transverse * .30 || peak > transverse * .70) return false;
        int end = Math.min(2, transverse / 5);
        double start = 0, finish = 0;
        for (int t = 0; t < end; t++) {
            start += mean[t];
            finish += mean[transverse - 1 - t];
        }
        start /= end;
        finish /= end;
        if (mean[peak] - Math.max(start, finish) < (axial - 1) * .53) return false;
        double[] a = fit(mean, 0, peak), b = fit(mean, peak, transverse - 1);
        if (a[1] <= 0 || b[1] >= 0 || a[1] / -b[1] < .45 || a[1] / -b[1] > 2.2) return false;
        double tolerance = Math.max(.95, (axial - 1) * .08);
        return a[2] <= tolerance && b[2] <= tolerance;
    }

    private static double[] fit(double[] values, int first, int last) {
        double n = last - first + 1, sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = first; i <= last; i++) {
            sx += i;
            sy += values[i];
            sxx += i * (double) i;
            sxy += i * values[i];
        }
        double slope = (n * sxy - sx * sy) / (n * sxx - sx * sx),
                intercept = (sy - slope * sx) / n,
                error = 0;
        for (int i = first; i <= last; i++) {
            double d = values[i] - (intercept + slope * i);
            error += d * d;
        }
        return new double[] {intercept, slope, Math.sqrt(error / n)};
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Recognizes a quarter-rest zigzag whose lower hook returns before the last staff space. */
final class CompactQuarterRestContour {
    private CompactQuarterRestContour() {}

    /** A rest hook can look like a continuous short stem after raster rounding.
     * This only admits the tiny component to independent complete-glyph proof. */
    static boolean compactTail(int width, int height, int area, float gap) {
        return compactTail(width, height, area, gap, null);
    }

    static boolean compactTail(
            int width, int height, int area, float gap, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return Float.isFinite(gap)
                && gap >= 4
                && width > 0
                && height > 0
                && area > 0
                && width <= gap * .88f
                && height <= gap * .65f
                && area <= gap * gap * .45f;
    }

    /** A sharp/natural has two long stable spines; a rest's zigzag or tail does not. */
    static boolean parallelSpines(
            byte[] gray,
            int width,
            int left,
            int right,
            int minY,
            int maxY,
            boolean[] staffLines,
            int lineTop,
            float gap) {
        return parallelSpines(gray, width, left, right, minY, maxY, staffLines, lineTop, gap, null);
    }

    static boolean parallelSpines(
            byte[] gray,
            int width,
            int left,
            int right,
            int minY,
            int maxY,
            boolean[] staffLines,
            int lineTop,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (gray == null
                || width < 1
                || gray.length % width != 0
                || gap < 4
                || maxY - minY < gap * 1.65f) return false;
        int span = Math.round(gap * 1.65f), height = gray.length / width;
        java.util.List<Integer> columns =
                (quarterBudget == null || quarterBudget.allocate(1, 256)
                        ? new java.util.ArrayList<>()
                        : null);
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                boolean spine = false;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int first = Math.max(0, minY);
                            first + span <= Math.min(height, maxY + 1);
                            first++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        int ink = 0, total = 0;
                        {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int y = first; y < first + span; y++) {
                                if (quarterBudget != null)
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                if (y - lineTop >= 0
                                        && y - lineTop < staffLines.length
                                        && staffLines[y - lineTop]) continue;
                                total++;
                                boolean dark = false;
                                {
                                    if (quarterBudget != null)
                                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                    for (int xx = Math.max(left, x - 1);
                                            xx <= Math.min(right, x + 1);
                                            xx++) {
                                        if (quarterBudget != null)
                                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                        if (xx >= 0
                                                && xx < width
                                                && (gray[y * width + xx] & 255) < 125) {
                                            dark = true;
                                            break;
                                        }
                                    }
                                }

                                if (dark) ink++;
                            }
                        }

                        if (total >= gap && ink >= total * .88f) {
                            spine = true;
                            break;
                        }
                    }
                }

                if (spine)
                    FaintQuarterRestInk.add(
                            quarterBudget, columns, FaintQuarterRestInk.boxInt(quarterBudget, x));
            }
        }

        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 64);
            for (int a : columns) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 64);
                    for (int b : columns) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if (b - a >= gap * .35f && b - a <= gap * .9f) return true;
                    }
                }
            }
        }

        return false;
    }

    static boolean hasContrastedInk(
            byte[] gray,
            int width,
            int left,
            int right,
            int minY,
            int maxY,
            boolean[] staffLines,
            int lineTop,
            float gap) {
        return hasContrastedInk(
                gray, width, left, right, minY, maxY, staffLines, lineTop, gap, null);
    }

    static boolean hasContrastedInk(
            byte[] gray,
            int width,
            int left,
            int right,
            int minY,
            int maxY,
            boolean[] staffLines,
            int lineTop,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (gray == null || width < 1 || gray.length % width != 0 || staffLines == null)
            return false;
        int height = gray.length / width, surround = Math.max(3, Math.round(gap));
        int[] tones =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) (256))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) (256))))
                        ? new int[256]
                        : null);
        int count = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = Math.max(0, minY); y <= Math.min(height - 1, maxY); y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (y - lineTop < 0 || y - lineTop >= staffLines.length || staffLines[y - lineTop])
                    continue;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = Math.max(0, left - surround);
                            x <= Math.min(width - 1, right + surround);
                            x++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        tones[gray[y * width + x] & 255]++;
                        count++;
                    }
                }
            }
        }

        if (count == 0) return false;
        int paper = 0, seen = tones[0], target = (count * 3 + 3) / 4;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            while (seen < target && paper < 255) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                seen += tones[++paper];
            }
        }

        int threshold = Math.min(170, Math.max(0, paper - 45)), supported = 0, rows = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = Math.max(0, minY); y <= Math.min(height - 1, maxY); y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (y - lineTop < 0 || y - lineTop >= staffLines.length || staffLines[y - lineTop])
                    continue;
                rows++;
                boolean dark = false;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if ((gray[y * width + x] & 255) <= threshold) {
                            dark = true;
                            break;
                        }
                    }
                }

                if (dark) supported++;
            }
        }

        return rows >= Math.max(1, Math.round(gap)) && supported >= rows * .75f;
    }

    static boolean matches(double[] rows, float gap) {
        return matches(rows, gap, null);
    }

    static boolean matches(double[] rows, float gap, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (rows == null || rows.length < 12 || !Float.isFinite(gap) || gap < 4) return false;
        int n = rows.length;
        double[] smooth =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) (n))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) (n))))
                        ? new double[n]
                        : null);
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < n; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (!Double.isFinite(rows[i])) return false;
                double sum = 0;
                int count = 0;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int j = Math.max(0, i - 1); j <= Math.min(n - 1, i + 1); j++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        sum += rows[j];
                        count++;
                    }
                }

                smooth[i] = sum / count;
            }
        }

        double start = average(smooth, 0, Math.max(1, n / 10), quarterBudget);
        double foot = average(smooth, n - Math.max(2, n / 12), n, quarterBudget);
        double lateFoot = average(smooth, n - 2, n, quarterBudget);
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int first = (int) (n * .22); first <= n * .40; first++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (smooth[first] - start < gap * .26) continue;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int valley = first + Math.max(3, Math.round(gap * .25f));
                            valley <= n * .57;
                            valley++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        if (smooth[first] - smooth[valley] < gap * .20) continue;
                        {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int second = valley + Math.max(3, Math.round(gap * .25f));
                                    second <= n * .73;
                                    second++) {
                                if (quarterBudget != null)
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                if (smooth[second] - smooth[valley] < gap * .18) continue;
                                {
                                    if (quarterBudget != null)
                                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                    for (int hook = second + 2; hook <= n * .93; hook++) {
                                        if (quarterBudget != null)
                                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                        if (smooth[second] - smooth[hook] < gap * .10
                                                || (hook >= n * .80 ? lateFoot : foot)
                                                                - smooth[hook]
                                                        < gap * (hook >= n * .80 ? .04 : .09))
                                            continue;
                                        // A true early hook has a substantial trailing downstroke;
                                        // one isolated noisy pixel near the foot cannot establish
                                        // it.
                                        if (hook - valley < gap * .45f) continue;
                                        if (hook < n * .80 && n - hook < gap * .65f
                                                || n - hook < gap * .25f) continue;
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /** Normalize horizontal compression while retaining full vertical zigzag and hook evidence. */
    static boolean matchesNarrow(double[] rows, float gap) {
        return matchesNarrow(rows, gap, null);
    }

    static boolean matchesNarrow(
            double[] rows, float gap, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (rows == null || rows.length < 12 || !Float.isFinite(gap) || gap < 4) return false;
        int n = rows.length;
        double[] smooth =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) (n))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) (n))))
                        ? new double[n]
                        : null);
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < n; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (!Double.isFinite(rows[i])) return false;
                double sum = 0;
                int count = 0;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int j = Math.max(0, i - 1); j <= Math.min(n - 1, i + 1); j++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        sum += rows[j];
                        count++;
                    }
                }

                smooth[i] = sum / count;
            }
        }

        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 64);
            for (double x : smooth) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                min = Math.min(min, x);
                max = Math.max(max, x);
            }
        }

        double horizontal = Math.min(gap, (max - min) * 1.5);
        if (horizontal < gap * .6) return false;
        double start = average(smooth, 0, Math.max(1, n / 10), quarterBudget);
        double foot = average(smooth, n - Math.max(2, n / 12), n, quarterBudget);
        double lateFoot = average(smooth, n - 2, n, quarterBudget);
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int first = (int) (n * .15); first <= n * .40; first++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (smooth[first] - start < horizontal * .26) continue;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int valley = first + Math.max(3, Math.round(gap * .25f));
                            valley <= n * .57;
                            valley++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        if (smooth[first] - smooth[valley] < horizontal * .20) continue;
                        {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int second = valley + Math.max(3, Math.round(gap * .25f));
                                    second <= n * .73;
                                    second++) {
                                if (quarterBudget != null)
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                if (smooth[second] - smooth[valley] < horizontal * .18) continue;
                                {
                                    if (quarterBudget != null)
                                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                    for (int hook = second + 2; hook <= n * .93; hook++) {
                                        if (quarterBudget != null)
                                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                        if (smooth[second] - smooth[hook] < horizontal * .10
                                                || (hook >= n * .80 ? lateFoot : foot)
                                                                - smooth[hook]
                                                        < horizontal
                                                                * (hook >= n * .80 ? .04 : .09))
                                            continue;
                                        // A true early hook has a substantial trailing downstroke;
                                        // one isolated noisy pixel near the foot cannot establish
                                        // it.
                                        if (hook - valley < gap * .45f) continue;
                                        if (hook < n * .80 && n - hook < gap * .65f
                                                || n - hook < gap * .25f) continue;
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /** Additive overlapping ink can obscure the centre while leaving the full left silhouette. */
    static boolean leftSilhouette(double[] left, double[] right, float gap) {
        return leftSilhouette(left, right, gap, null);
    }

    static boolean leftSilhouette(
            double[] left, double[] right, float gap, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (left == null
                || right == null
                || left.length != right.length
                || left.length < gap * 3
                || left.length > gap * 3.6f) return false;
        int n = left.length;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < n; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (!Double.isFinite(left[i]) || !Double.isFinite(right[i]) || right[i] < left[i])
                    return false;
            }
        }

        if (right[0] - left[0] > gap * .3f || right[n - 1] - left[n - 1] > gap * .3f) return false;
        double start = average(left, 0, Math.max(1, n / 12), quarterBudget);
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int peak = (int) (n * .15); peak <= n * .32; peak++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (left[peak] - start + .5 < gap * .16) continue;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int valley = (int) (n * .30); valley <= n * .5; valley++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        if (valley <= peak || left[peak] - left[valley] < gap * .35) continue;
                        {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int shoulder = (int) (n * .45); shoulder <= n * .62; shoulder++) {
                                if (quarterBudget != null)
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                if (shoulder <= valley || left[shoulder] - left[valley] < gap * .22)
                                    continue;
                                {
                                    if (quarterBudget != null)
                                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                    for (int hook = (int) (n * .55); hook <= n * .75; hook++) {
                                        if (quarterBudget != null)
                                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                        if (hook <= shoulder
                                                || left[shoulder] - left[hook] < gap * .25
                                                || left[n - 1] - left[hook] < gap * .5) continue;
                                        boolean rising = true;
                                        {
                                            if (quarterBudget != null)
                                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                            for (int i = hook + 3; i < n; i++) {
                                                if (quarterBudget != null)
                                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                                if (left[i] + 1 < left[i - 3]) {
                                                    rising = false;
                                                    break;
                                                }
                                            }
                                        }

                                        if (rising) return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /** Both outlines must independently retain every turn of a narrow complete rest. */
    static boolean pairedSilhouette(double[] left, double[] right, boolean[] observed, float gap) {
        return pairedSilhouette(left, right, observed, gap, null);
    }

    static boolean pairedSilhouette(
            double[] left,
            double[] right,
            boolean[] observed,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (left == null
                || right == null
                || observed == null
                || left.length != right.length
                || left.length != observed.length
                || !Float.isFinite(gap)
                || gap < 4
                || left.length < gap * 2.6f
                || left.length > gap * 3.6f) return false;
        int n = left.length, evidence = 0;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < n; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (!Double.isFinite(left[i]) || !Double.isFinite(right[i]) || right[i] < left[i])
                    return false;
                min = Math.min(min, left[i]);
                max = Math.max(max, right[i]);
                if (observed[i]) evidence++;
            }
        }

        if (evidence < n * .6f
                || max - min + 1 < gap * .65f
                || max - min + 1 > gap * 1.15f
                || !observedPair(observed, 0, quarterBudget)
                || !observedPair(observed, n - 2, quarterBudget)) return false;
        double startRight = pair(right, 0, quarterBudget),
                footLeft = pair(left, n - 2, quarterBudget),
                footRight = pair(right, n - 2, quarterBudget);
        if (pair(right, 0, quarterBudget) - pair(left, 0, quarterBudget) + 1 > gap * .3f
                || footRight - footLeft + 1 > gap * .3f) return false;
        int separation = Math.max(2, Math.round(gap * .2f));
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int first = (int) (n * .15); first <= n * .32; first++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (!observedPair(observed, first, quarterBudget)
                        || pair(right, first, quarterBudget) - startRight < gap * .35f
                        || pair(right, first, quarterBudget) - pair(left, first, quarterBudget) + 1
                                < gap * .35f) continue;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int valley = Math.max(first + separation, (int) (n * .32));
                            valley <= n * .52;
                            valley++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        if (!observedPair(observed, valley, quarterBudget)
                                || pair(right, first, quarterBudget)
                                                - pair(right, valley, quarterBudget)
                                        < gap * .2f
                                || pair(left, first, quarterBudget)
                                                - pair(left, valley, quarterBudget)
                                        < gap * .16f) continue;
                        {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int shoulder = Math.max(valley + separation, (int) (n * .5));
                                    shoulder <= n * .65;
                                    shoulder++) {
                                if (quarterBudget != null)
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                if (!observedPair(observed, shoulder, quarterBudget)
                                        || pair(left, shoulder, quarterBudget)
                                                        - pair(left, valley, quarterBudget)
                                                < gap * .2f) continue;
                                {
                                    if (quarterBudget != null)
                                        FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                    for (int hook =
                                                    Math.max(
                                                            shoulder + separation, (int) (n * .65));
                                            hook <= n * .88 && n - 2 - hook >= gap * .35f;
                                            hook++) {
                                        if (quarterBudget != null)
                                            FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                        if (!observedPair(observed, hook, quarterBudget)
                                                || pair(left, shoulder, quarterBudget)
                                                                - pair(left, hook, quarterBudget)
                                                        < gap * .3f
                                                || footLeft - pair(left, hook, quarterBudget)
                                                        < gap * .2f
                                                || pair(right, shoulder, quarterBudget) - footRight
                                                        < gap * .15f
                                                || pair(right, hook, quarterBudget)
                                                                - pair(left, hook, quarterBudget)
                                                                + 1
                                                        < gap * .45f) continue;
                                        boolean returning = true;
                                        {
                                            if (quarterBudget != null)
                                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                            for (int i = hook + 2; i < n; i++) {
                                                if (quarterBudget != null)
                                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                                                if (left[i] + 1 < left[i - 1]) {
                                                    returning = false;
                                                    break;
                                                }
                                            }
                                        }

                                        if (returning) return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /** The observed contour must be one connected raw ink body, including its forked hook. */
    static boolean connectedInk(byte[] gray, int width, int left, int right, int top, int bottom) {
        return connectedInk(gray, width, left, right, top, bottom, null);
    }

    static boolean connectedInk(
            byte[] gray,
            int width,
            int left,
            int right,
            int top,
            int bottom,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (gray == null
                || width < 1
                || gray.length % width != 0
                || left < 0
                || right >= width
                || right < left
                || top < 0
                || bottom >= gray.length / width
                || bottom < top) return false;
        int w = right - left + 1, h = bottom - top + 1, first = -1, ink = 0;
        boolean[] seen =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) (w * h))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) (w * h))))
                        ? new boolean[w * h]
                        : null);
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = 0; y < h; y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = 0; x < w; x++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        if ((gray[(top + y) * width + left + x] & 255) < 170) {
                            ink++;
                            if (first < 0) first = y * w + x;
                        }
                    }
                }
            }
        }

        if (first < 0) return false;
        java.util.ArrayDeque<Integer> pending =
                (quarterBudget == null || quarterBudget.allocate(1, 256)
                        ? new java.util.ArrayDeque<>()
                        : null);
        FaintQuarterRestInk.add(
                quarterBudget, pending, FaintQuarterRestInk.boxInt(quarterBudget, first));
        seen[first] = true;
        int reached = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            while (!pending.isEmpty()) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                int at = pending.removeFirst(), x = at % w, y = at / w;
                reached++;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int dy = -1; dy <= 1; dy++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        {
                            if (quarterBudget != null)
                                FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                            for (int dx = -1; dx <= 1; dx++) {
                                if (quarterBudget != null)
                                    FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                                int xx = x + dx, yy = y + dy;
                                if (xx >= 0
                                        && xx < w
                                        && yy >= 0
                                        && yy < h
                                        && !seen[yy * w + xx]
                                        && (gray[(top + yy) * width + left + xx] & 255) < 170) {
                                    seen[yy * w + xx] = true;
                                    FaintQuarterRestInk.add(
                                            quarterBudget,
                                            pending,
                                            FaintQuarterRestInk.boxInt(quarterBudget, yy * w + xx));
                                }
                            }
                        }
                    }
                }
            }
        }

        return reached == ink;
    }

    private static boolean observedPair(boolean[] observed, int i) {
        return observedPair(observed, i, null);
    }

    private static boolean observedPair(
            boolean[] observed, int i, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return i >= 0 && i + 1 < observed.length && observed[i] && observed[i + 1];
    }

    private static double pair(double[] edge, int i) {
        return pair(edge, i, null);
    }

    private static double pair(double[] edge, int i, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        return (edge[i] + edge[i + 1]) * .5;
    }

    private static double average(double[] values, int first, int last) {
        return average(values, first, last, null);
    }

    private static double average(
            double[] values, int first, int last, FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        double sum = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = first; i < last; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                sum += values[i];
            }
        }

        return sum / Math.max(1, last - first);
    }
}

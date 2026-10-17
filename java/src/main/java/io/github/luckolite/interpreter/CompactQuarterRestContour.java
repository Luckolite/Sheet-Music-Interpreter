// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Recognizes a quarter-rest zigzag whose lower hook returns before the last staff space. */
final class CompactQuarterRestContour {
    private CompactQuarterRestContour() {}

    /** A rest hook can look like a continuous short stem after raster rounding.
     * This only admits the tiny component to independent complete-glyph proof. */
    static boolean compactTail(int width, int height, int area, float gap) {
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
        if (gray == null
                || width < 1
                || gray.length % width != 0
                || gap < 4
                || maxY - minY < gap * 1.65f) return false;
        int span = Math.round(gap * 1.65f), height = gray.length / width;
        java.util.List<Integer> columns = new java.util.ArrayList<>();
        for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++) {
            boolean spine = false;
            for (int first = Math.max(0, minY);
                    first + span <= Math.min(height, maxY + 1);
                    first++) {
                int ink = 0, total = 0;
                for (int y = first; y < first + span; y++) {
                    if (y - lineTop >= 0
                            && y - lineTop < staffLines.length
                            && staffLines[y - lineTop]) continue;
                    total++;
                    boolean dark = false;
                    for (int xx = Math.max(left, x - 1); xx <= Math.min(right, x + 1); xx++)
                        if (xx >= 0 && xx < width && (gray[y * width + xx] & 255) < 125) {
                            dark = true;
                            break;
                        }
                    if (dark) ink++;
                }
                if (total >= gap && ink >= total * .88f) {
                    spine = true;
                    break;
                }
            }
            if (spine) columns.add(x);
        }
        for (int a : columns)
            for (int b : columns) if (b - a >= gap * .35f && b - a <= gap * .9f) return true;
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
        if (gray == null || width < 1 || gray.length % width != 0 || staffLines == null)
            return false;
        int height = gray.length / width, surround = Math.max(3, Math.round(gap));
        int[] tones = new int[256];
        int count = 0;
        for (int y = Math.max(0, minY); y <= Math.min(height - 1, maxY); y++) {
            if (y - lineTop < 0 || y - lineTop >= staffLines.length || staffLines[y - lineTop])
                continue;
            for (int x = Math.max(0, left - surround);
                    x <= Math.min(width - 1, right + surround);
                    x++) {
                tones[gray[y * width + x] & 255]++;
                count++;
            }
        }
        if (count == 0) return false;
        int paper = 0, seen = tones[0], target = (count * 3 + 3) / 4;
        while (seen < target && paper < 255) seen += tones[++paper];
        int threshold = Math.min(170, Math.max(0, paper - 45)), supported = 0, rows = 0;
        for (int y = Math.max(0, minY); y <= Math.min(height - 1, maxY); y++) {
            if (y - lineTop < 0 || y - lineTop >= staffLines.length || staffLines[y - lineTop])
                continue;
            rows++;
            boolean dark = false;
            for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++)
                if ((gray[y * width + x] & 255) <= threshold) {
                    dark = true;
                    break;
                }
            if (dark) supported++;
        }
        return rows >= Math.max(1, Math.round(gap)) && supported >= rows * .75f;
    }

    static boolean matches(double[] rows, float gap) {
        if (rows == null || rows.length < 12 || !Float.isFinite(gap) || gap < 4) return false;
        int n = rows.length;
        double[] smooth = new double[n];
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(rows[i])) return false;
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - 1); j <= Math.min(n - 1, i + 1); j++) {
                sum += rows[j];
                count++;
            }
            smooth[i] = sum / count;
        }
        double start = average(smooth, 0, Math.max(1, n / 10));
        double foot = average(smooth, n - Math.max(2, n / 12), n);
        double lateFoot = average(smooth, n - 2, n);
        for (int first = (int) (n * .22); first <= n * .40; first++) {
            if (smooth[first] - start < gap * .26) continue;
            for (int valley = first + Math.max(3, Math.round(gap * .25f));
                    valley <= n * .57;
                    valley++) {
                if (smooth[first] - smooth[valley] < gap * .20) continue;
                for (int second = valley + Math.max(3, Math.round(gap * .25f));
                        second <= n * .73;
                        second++) {
                    if (smooth[second] - smooth[valley] < gap * .18) continue;
                    for (int hook = second + 2; hook <= n * .93; hook++) {
                        if (smooth[second] - smooth[hook] < gap * .10
                                || (hook >= n * .80 ? lateFoot : foot) - smooth[hook]
                                        < gap * (hook >= n * .80 ? .04 : .09)) continue;
                        // A true early hook has a substantial trailing downstroke;
                        // one isolated noisy pixel near the foot cannot establish it.
                        if (hook - valley < gap * .45f) continue;
                        if (hook < n * .80 && n - hook < gap * .65f || n - hook < gap * .25f)
                            continue;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Normalize horizontal compression while retaining full vertical zigzag and hook evidence. */
    static boolean matchesNarrow(double[] rows, float gap) {
        if (rows == null || rows.length < 12 || !Float.isFinite(gap) || gap < 4) return false;
        int n = rows.length;
        double[] smooth = new double[n];
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(rows[i])) return false;
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - 1); j <= Math.min(n - 1, i + 1); j++) {
                sum += rows[j];
                count++;
            }
            smooth[i] = sum / count;
        }
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double x : smooth) {
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        double horizontal = Math.min(gap, (max - min) * 1.5);
        if (horizontal < gap * .6) return false;
        double start = average(smooth, 0, Math.max(1, n / 10));
        double foot = average(smooth, n - Math.max(2, n / 12), n);
        double lateFoot = average(smooth, n - 2, n);
        for (int first = (int) (n * .15); first <= n * .40; first++) {
            if (smooth[first] - start < horizontal * .26) continue;
            for (int valley = first + Math.max(3, Math.round(gap * .25f));
                    valley <= n * .57;
                    valley++) {
                if (smooth[first] - smooth[valley] < horizontal * .20) continue;
                for (int second = valley + Math.max(3, Math.round(gap * .25f));
                        second <= n * .73;
                        second++) {
                    if (smooth[second] - smooth[valley] < horizontal * .18) continue;
                    for (int hook = second + 2; hook <= n * .93; hook++) {
                        if (smooth[second] - smooth[hook] < horizontal * .10
                                || (hook >= n * .80 ? lateFoot : foot) - smooth[hook]
                                        < horizontal * (hook >= n * .80 ? .04 : .09)) continue;
                        // A true early hook has a substantial trailing downstroke;
                        // one isolated noisy pixel near the foot cannot establish it.
                        if (hook - valley < gap * .45f) continue;
                        if (hook < n * .80 && n - hook < gap * .65f || n - hook < gap * .25f)
                            continue;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Additive overlapping ink can obscure the centre while leaving the full left silhouette. */
    static boolean leftSilhouette(double[] left, double[] right, float gap) {
        if (left == null
                || right == null
                || left.length != right.length
                || left.length < gap * 3
                || left.length > gap * 3.6f) return false;
        int n = left.length;
        for (int i = 0; i < n; i++)
            if (!Double.isFinite(left[i]) || !Double.isFinite(right[i]) || right[i] < left[i])
                return false;
        if (right[0] - left[0] > gap * .3f || right[n - 1] - left[n - 1] > gap * .3f) return false;
        double start = average(left, 0, Math.max(1, n / 12));
        for (int peak = (int) (n * .15); peak <= n * .32; peak++) {
            if (left[peak] - start + .5 < gap * .16) continue;
            for (int valley = (int) (n * .30); valley <= n * .5; valley++) {
                if (valley <= peak || left[peak] - left[valley] < gap * .35) continue;
                for (int shoulder = (int) (n * .45); shoulder <= n * .62; shoulder++) {
                    if (shoulder <= valley || left[shoulder] - left[valley] < gap * .22) continue;
                    for (int hook = (int) (n * .55); hook <= n * .75; hook++) {
                        if (hook <= shoulder
                                || left[shoulder] - left[hook] < gap * .25
                                || left[n - 1] - left[hook] < gap * .5) continue;
                        boolean rising = true;
                        for (int i = hook + 3; i < n; i++)
                            if (left[i] + 1 < left[i - 3]) {
                                rising = false;
                                break;
                            }
                        if (rising) return true;
                    }
                }
            }
        }
        return false;
    }

    /** Both outlines must independently retain every turn of a narrow complete rest. */
    static boolean pairedSilhouette(double[] left, double[] right, boolean[] observed, float gap) {
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
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(left[i]) || !Double.isFinite(right[i]) || right[i] < left[i])
                return false;
            min = Math.min(min, left[i]);
            max = Math.max(max, right[i]);
            if (observed[i]) evidence++;
        }
        if (evidence < n * .6f
                || max - min + 1 < gap * .65f
                || max - min + 1 > gap * 1.15f
                || !observedPair(observed, 0)
                || !observedPair(observed, n - 2)) return false;
        double startRight = pair(right, 0),
                footLeft = pair(left, n - 2),
                footRight = pair(right, n - 2);
        if (pair(right, 0) - pair(left, 0) + 1 > gap * .3f || footRight - footLeft + 1 > gap * .3f)
            return false;
        int separation = Math.max(2, Math.round(gap * .2f));
        for (int first = (int) (n * .15); first <= n * .32; first++) {
            if (!observedPair(observed, first)
                    || pair(right, first) - startRight < gap * .35f
                    || pair(right, first) - pair(left, first) + 1 < gap * .35f) continue;
            for (int valley = Math.max(first + separation, (int) (n * .32));
                    valley <= n * .52;
                    valley++) {
                if (!observedPair(observed, valley)
                        || pair(right, first) - pair(right, valley) < gap * .2f
                        || pair(left, first) - pair(left, valley) < gap * .16f) continue;
                for (int shoulder = Math.max(valley + separation, (int) (n * .5));
                        shoulder <= n * .65;
                        shoulder++) {
                    if (!observedPair(observed, shoulder)
                            || pair(left, shoulder) - pair(left, valley) < gap * .2f) continue;
                    for (int hook = Math.max(shoulder + separation, (int) (n * .65));
                            hook <= n * .88 && n - 2 - hook >= gap * .35f;
                            hook++) {
                        if (!observedPair(observed, hook)
                                || pair(left, shoulder) - pair(left, hook) < gap * .3f
                                || footLeft - pair(left, hook) < gap * .2f
                                || pair(right, shoulder) - footRight < gap * .15f
                                || pair(right, hook) - pair(left, hook) + 1 < gap * .45f) continue;
                        boolean returning = true;
                        for (int i = hook + 2; i < n; i++)
                            if (left[i] + 1 < left[i - 1]) {
                                returning = false;
                                break;
                            }
                        if (returning) return true;
                    }
                }
            }
        }
        return false;
    }

    /** The observed contour must be one connected raw ink body, including its forked hook. */
    static boolean connectedInk(byte[] gray, int width, int left, int right, int top, int bottom) {
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
        boolean[] seen = new boolean[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((gray[(top + y) * width + left + x] & 255) < 170) {
                    ink++;
                    if (first < 0) first = y * w + x;
                }
        if (first < 0) return false;
        java.util.ArrayDeque<Integer> pending = new java.util.ArrayDeque<>();
        pending.add(first);
        seen[first] = true;
        int reached = 0;
        while (!pending.isEmpty()) {
            int at = pending.removeFirst(), x = at % w, y = at / w;
            reached++;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx >= 0
                            && xx < w
                            && yy >= 0
                            && yy < h
                            && !seen[yy * w + xx]
                            && (gray[(top + yy) * width + left + xx] & 255) < 170) {
                        seen[yy * w + xx] = true;
                        pending.add(yy * w + xx);
                    }
                }
        }
        return reached == ink;
    }

    private static boolean observedPair(boolean[] observed, int i) {
        return i >= 0 && i + 1 < observed.length && observed[i] && observed[i + 1];
    }

    private static double pair(double[] edge, int i) {
        return (edge[i] + edge[i + 1]) * .5;
    }

    private static double average(double[] values, int first, int last) {
        double sum = 0;
        for (int i = first; i < last; i++) sum += values[i];
        return sum / Math.max(1, last - first);
    }
}

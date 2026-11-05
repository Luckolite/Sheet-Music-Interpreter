// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** A complete compact returning ink ridge beside a shaded printed staff rule. */
final class ShadedCompactTieRidge {
    private ShadedCompactTieRidge() {}

    /** A miniature tie can fit entirely in the clear gap between two noteheads. */
    static boolean shortInterheadArc(byte[] gray, int width, int height,
            int left, int right, float centerY, float gap) {
        if (gray == null || gray.length != (long) width * height || !Float.isFinite(centerY)
                || !Float.isFinite(gap) || gap < 8 || left < 0 || right >= width) return false;
        int span = right - left + 1;
        if (span < gap * .55f || span > gap * .82f) return false;
        int top = Math.round(centerY + gap * .24f);
        int bottom = Math.round(centerY + gap * .69f);
        if (top < 0 || bottom + 2 >= height) return false;
        float[] ridge = new float[span];
        for (int i = 0; i < span; i++) {
            int x = left + i, total = 0, moment = 0, ink = 0, core = 0;
            for (int y = top; y <= bottom; y++) {
                int weight = Math.max(0, 145 - (gray[y * width + x] & 255));
                if (weight > 0) ink++;
                core = Math.max(core, weight);
                total += weight;
                moment += y * weight;
            }
            if (ink > Math.ceil(gap * .35f) || core < 24 || (gray[(bottom + 2) * width + x] & 255) < 155)
                return false;
            ridge[i] = moment / (float) total;
            if (i > 0 && Math.abs(ridge[i] - ridge[i - 1]) > 1.6f) return false;
        }
        float middle = (ridge[span / 2] + ridge[(span - 1) / 2]) * .5f;
        return middle - ridge[0] >= gap * .055f && middle - ridge[span - 1] >= gap * .055f;
    }
    /** Prove all five physical rules locally before testing the shared bottom rule. */
    static boolean belowVerifiedLocalBottomRule(byte[] gray, int width, int height,
            int left, int right, float spaceCenter, float gap) {
        if (gray == null || gray.length != (long) width * height || !Float.isFinite(spaceCenter)
                || !Float.isFinite(gap) || gap < 8 || left < 0 || right >= width
                || right - left < gap || right - left > gap * 1.8f) return false;
        int bottom = Math.round(spaceCenter + gap * .5f);
        int measuredBottom = bottom;
        for (int rule = 0; rule < 5; rule++) {
            int row = Math.round(bottom - rule * gap);
            if (row < 4 || row + 4 >= height) return false;
            int best = 0, darkestRow = row, darkest = Integer.MAX_VALUE;
            for (int y = row - 2; y <= row + 2; y++) {
                int dark = 0, tone = 0;
                for (int x = left; x <= right; x++) {
                    int ink = gray[y * width + x] & 255;
                    int paper = gray[(y - 2) * width + x] & 255;
                    if (ink + 20 <= paper) dark++;
                    tone += ink;
                }
                best = Math.max(best, dark);
                if (tone < darkest) {
                    darkest = tone;
                    darkestRow = y;
                }
            }
            if (best < (right - left + 1) * .7f) return false;
            if (rule == 0) measuredBottom = darkestRow;
        }
        return belowBottomRule(gray, width, height, left, right, measuredBottom, gap);
    }
    /** A short tie can leave and rejoin the bottom rule below two space notes. */
    static boolean belowBottomRule(byte[] gray, int width, int height, int left, int right,
            float bottomRule, float gap) {
        if (gray == null || gray.length != (long) width * height || !Float.isFinite(bottomRule)
                || gap < 8 || left < 0 || right >= width
                || right - left < gap || right - left > gap * 1.8f) return false;
        int rail = Math.round(bottomRule);
        int maxDepth = Math.round(gap * .45f);
        int paperOffset = Math.round(gap * .65f);
        if (rail < 1 || rail + paperOffset >= height) return false;
        int span = right - left + 1;
        int[] depths = new int[span];
        int strong = 0;
        for (int i = 0; i < span; i++) {
            int x = left + i;
            int background = gray[(rail + paperOffset) * width + x] & 255;
            int line = gray[rail * width + x] & 255;
            if (background < 100 || background > 230 || line > background - 35) return false;
            int darkest = background - 65;
            for (int d = 2; d <= maxDepth; d++) {
                int pixel = gray[(rail + d) * width + x] & 255;
                if (pixel < darkest) {
                    darkest = pixel;
                    depths[i] = d;
                }
            }
            if (depths[i] >= gap * .22f) strong++;
            if (i > 0 && Math.abs(depths[i] - depths[i - 1]) > 2) return false;
        }
        if (depths[0] > 2 || depths[span - 1] > 2 || strong < span * .35f) return false;
        int center = depths[span / 2];
        return center >= gap * .22f
                && center - depths[span / 8] >= 2
                && center - depths[span - 1 - span / 8] >= 2;
    }
    static boolean proved(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap) {
        if (gray == null
                || labels == null
                || gray.length != (long) width * height
                || labels.length != gray.length
                || !Float.isFinite(gap)
                || gap < 8
                || left < 0
                || right >= width
                || right - left < gap
                || right - left > gap * 3.5f) return false;
        for (int side : new int[] {-1, 1}) {
            int top = Math.max(0, Math.round(centerY + (side < 0 ? -1.6f : .35f) * gap));
            int bottom =
                    Math.min(height - 1, Math.round(centerY + (side < 0 ? -.35f : 1.6f) * gap));
            int columns = right - left + 1, rows = bottom - top + 1;
            if (rows < 3) continue;
            int[] deficit = new int[columns * rows], row = new int[columns];
            boolean[] ink = new boolean[deficit.length], seen = new boolean[deficit.length];
            int shaded = 0;
            int[] backgroundRows = new int[rows];
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < columns; x++) row[x] = gray[(top + y) * width + left + x] & 255;
                Arrays.sort(row);
                int background = row[(columns - 1) * 3 / 4];
                backgroundRows[y] = background;
                if (background >= 96 && background <= 200) shaded++;
                for (int x = 0; x < columns; x++) {
                    int at = (top + y) * width + left + x, shade = gray[at] & 255;
                    int local = y * columns + x;
                    deficit[local] = background - shade;
                    ink[local] = shade < 145 && deficit[local] >= 12 && labels[at] != 2;
                }
            }
            if (shaded < rows * .75f) continue;
            Arrays.sort(backgroundRows);
            int branchLimit = Math.min(145, Math.max(80, backgroundRows[rows / 2] - 20));
            int[] queue = new int[ink.length];
            for (int seed = 0; seed < ink.length; seed++) {
                if (!ink[seed] || seen[seed]) continue;
                int count = 1, take = 0, minX = columns, maxX = -1, minY = rows, maxY = -1;
                queue[0] = seed;
                seen[seed] = true;
                while (take < count) {
                    int at = queue[take++], x = at % columns, y = at / columns;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || xx >= columns || yy < 0 || yy >= rows) continue;
                            int next = yy * columns + xx;
                            if (!ink[next] || seen[next]) continue;
                            seen[next] = true;
                            queue[count++] = next;
                        }
                }
                int span = maxX - minX + 1, depth = maxY - minY + 1;
                if (span < gap * .8f
                        || span > gap * 2.6f
                        || depth < 3
                        || depth > gap * .75f
                        || count < span * 1.5f
                        || count > span * gap * .6f) continue;
                double[] mass = new double[span], moment = new double[span];
                int[] core = new int[span], thickness = new int[span];
                for (int i = 0; i < count; i++) {
                    int at = queue[i], x = at % columns - minX, y = at / columns;
                    double weight = deficit[at] - 10;
                    mass[x] += weight;
                    moment[x] += weight * (top + y);
                    core[x] = Math.max(core[x], deficit[at]);
                    thickness[x]++;
                }
                float[] centers = new float[span];
                int strong = 0;
                boolean complete = true;
                for (int x = 0; x < span; x++) {
                    if (mass[x] <= 0 || thickness[x] > gap * .6f) {
                        complete = false;
                        break;
                    }
                    centers[x] = (float) (moment[x] / mass[x]);
                    if (core[x] >= 24) strong++;
                }
                if (!complete || strong < span * .6f) continue;
                int end = Math.max(2, Math.round(span * .2f));
                float first = mean(centers, 0, end), last = mean(centers, span - end, span);
                float middle = mean(centers, Math.round(span * .4f), Math.round(span * .6f));
                float bow = Math.max(1f, gap * .07f);
                if (side * (middle - first) < bow || side * (middle - last) < bow) continue;
                // A staff or beam step returns at only one end. Both halves must turn
                // toward the same middle without switching to another printed stroke.
                float peak = centers[span / 2];
                if (side * (peak - first) < bow || side * (peak - last) < bow) continue;
                boolean smooth = true;
                for (int x = 1; x < span; x++)
                    if (Math.abs(centers[x] - centers[x - 1]) > Math.max(1.5f, gap * .15f))
                        smooth = false;
                if (!smooth) continue;
                float[] sampled = new float[50];
                for (int x = 0; x < sampled.length; x++)
                    sampled[x] = centers[Math.round(x * (span - 1) / 49f)];
                if (!TieArcBranchInk.outwardStems(
                        gray,
                        width,
                        height,
                        left + minX,
                        left + maxX,
                        sampled,
                        side,
                        gap,
                        branchLimit)) return true;
            }
        }
        return false;
    }

    private static float mean(float[] values, int start, int end) {
        float sum = 0;
        for (int i = start; i < end; i++) sum += values[i];
        return sum / (end - start);
    }
}

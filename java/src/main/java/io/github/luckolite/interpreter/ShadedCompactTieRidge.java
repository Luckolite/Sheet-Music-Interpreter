// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** A complete compact returning ink ridge beside a shaded printed staff rule. */
final class ShadedCompactTieRidge {
    private ShadedCompactTieRidge() {}

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

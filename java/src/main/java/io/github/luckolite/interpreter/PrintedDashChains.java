// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Printed extension geometry only; callers must prove direction and musical ownership. */
final class PrintedDashChains {
    record Run(float left, float right, float y, int dashes) {}

    private record Mark(int left, int right, int top, int bottom) {
        float y() {
            return (top + bottom) * .5f;
        }
    }

    static List<Run> detect(byte[] gray, int width, int height, float gap) {
        if (gray == null
                || width < 1
                || height < 1
                || (long) width * height != gray.length
                || !Float.isFinite(gap)
                || gap < 4) return List.of();
        var marks = new ArrayList<Mark>();
        boolean[] seen = new boolean[gray.length];
        int[] queue = new int[gray.length];
        for (int seed = 0; seed < gray.length; seed++) {
            if (seen[seed] || (gray[seed] & 255) >= 165) continue;
            int read = 0, count = 1;
            queue[0] = seed;
            seen[seed] = true;
            int left = seed % width, right = left, top = seed / width, bottom = top;
            while (read < count) {
                int at = queue[read++], x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= width || yy < 0 || yy >= height) continue;
                        int next = yy * width + xx;
                        if (!seen[next] && (gray[next] & 255) < 165) {
                            seen[next] = true;
                            queue[count++] = next;
                        }
                    }
            }
            int w = right - left + 1, h = bottom - top + 1;
            if (w >= 2
                    && w <= gap * 1.4f
                    && h <= Math.max(3, gap * .2f)
                    && w >= 2 * h
                    && count >= w * .65f) marks.add(new Mark(left, right, top, bottom));
        }
        marks.sort(Comparator.comparingDouble(Mark::y));
        var result = new ArrayList<Run>();
        var row = new ArrayList<Mark>();
        float rowY = 0;
        for (var mark : marks) {
            if (!row.isEmpty() && mark.y() - rowY > Math.max(2, gap * .15f)) {
                collect(row, gap, width, height, result);
                row.clear();
            }
            if (row.isEmpty()) rowY = mark.y();
            row.add(mark);
        }
        collect(row, gap, width, height, result);
        return List.copyOf(result);
    }

    private static void collect(List<Mark> row, float gap, int width, int height, List<Run> out) {
        collect(row, gap, width, height, out, 1.5f, 8, .45);
        collect(row, gap, width, height, out, 4f, 4, .15);
    }

    /** Sparse extension dashes need tighter spacing consistency than dense chains. */
    private static void collect(
            List<Mark> row,
            float gap,
            int width,
            int height,
            List<Run> out,
            float maximumGap,
            int minimumCount,
            double maximumVariation) {
        row.sort(Comparator.comparingInt(Mark::left));
        int start = 0;
        for (int i = 1; i <= row.size(); i++) {
            if (i != row.size() && row.get(i).left() - row.get(i - 1).right() <= gap * maximumGap)
                continue;
            int count = i - start;
            if (count >= minimumCount) {
                var first = row.get(start);
                var last = row.get(i - 1);
                double mean = (last.left() - first.left()) / (double) (count - 1),
                        variance = 0,
                        y = 0;
                for (int j = start; j < i; j++) {
                    y += row.get(j).y();
                    if (j > start) {
                        double d = row.get(j).left() - row.get(j - 1).left() - mean;
                        variance += d * d;
                    }
                }
                if (mean > 0
                        && last.right() - first.left() >= gap * 8
                        && Math.sqrt(variance / (count - 1)) / mean <= maximumVariation) {
                    var run =
                            new Run(
                                    first.left() / (float) width,
                                    (last.right() + 1f) / width,
                                    (float) (y / count / height),
                                    count);
                    if (!out.contains(run)) out.add(run);
                }
            }
            start = i;
        }
    }
}

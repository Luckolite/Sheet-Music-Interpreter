// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Attached printed shafts distinguish opposing voices without guessing from pitch. */
final class PrintedStemDirection {
    private PrintedStemDirection() {}

    static int detect(byte[] gray, int width, int height, float x, float y, float gap) {
        return detect(gray, width, height, x, y, gap, null);
    }

    static int detect(
            byte[] gray,
            int width,
            int height,
            float x,
            float y,
            float gap,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        if (gray == null || gray.length != (long) width * height || !Float.isFinite(gap) || gap < 4)
            return 0;
        boolean up = shaft(gray, width, height, x, y, gap, -1, quarterBudget),
                down = shaft(gray, width, height, x, y, gap, 1, quarterBudget);
        return up == down ? 0 : up ? 1 : -1;
    }

    private static boolean shaft(
            byte[] gray, int width, int height, float x, float y, float gap, int direction) {
        return shaft(gray, width, height, x, y, gap, direction, null);
    }

    private static boolean shaft(
            byte[] gray,
            int width,
            int height,
            float x,
            float y,
            float gap,
            int direction,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        float edge = x + (direction < 0 ? .6f : -.6f) * gap;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int col = Math.round(edge - gap * .2f); col <= edge + gap * .2f; col++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (col < 0 || col >= width) continue;
                int hits = 0, total = 0;
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int step = 0; step <= Math.round(gap * 1.9f); step++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        int row = Math.round(y + direction * (gap * .15f + step));
                        if (row < 0 || row >= height) break;
                        total++;
                        if ((gray[row * width + col] & 255) < 165) hits++;
                    }
                }

                if (total >= gap * 1.7f && hits >= total * .92f) return true;
            }
        }

        return false;
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Attached printed shafts distinguish opposing voices without guessing from pitch. */
final class PrintedStemDirection {
    private PrintedStemDirection() {}

    static int detect(byte[] gray, int width, int height, float x, float y, float gap) {
        if (gray == null || gray.length != (long) width * height || !Float.isFinite(gap) || gap < 4)
            return 0;
        boolean up = shaft(gray, width, height, x, y, gap, -1),
                down = shaft(gray, width, height, x, y, gap, 1);
        return up == down ? 0 : up ? 1 : -1;
    }

    private static boolean shaft(
            byte[] gray, int width, int height, float x, float y, float gap, int direction) {
        float edge = x + (direction < 0 ? .6f : -.6f) * gap;
        for (int col = Math.round(edge - gap * .2f); col <= edge + gap * .2f; col++) {
            if (col < 0 || col >= width) continue;
            int hits = 0, total = 0;
            for (int step = 0; step <= Math.round(gap * 1.9f); step++) {
                int row = Math.round(y + direction * (gap * .15f + step));
                if (row < 0 || row >= height) break;
                total++;
                if ((gray[row * width + col] & 255) < 165) hits++;
            }
            if (total >= gap * 1.7f && hits >= total * .92f) return true;
        }
        return false;
    }
}

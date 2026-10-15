// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Raw evidence for a small semantic island where an outer staff rule meets a tie and bar. */
final class TieBarlineCorner {
    private TieBarlineCorner() {}

    static boolean matches(
            byte[] gray,
            int width,
            int height,
            int minX,
            int maxX,
            int minY,
            int maxY,
            float centerX,
            float centerY,
            float top,
            float bottom,
            float gap) {
        if (gray == null
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || gap < 8
                || maxX - minX + 1 > gap * .6f
                || maxY - minY + 1 > gap * .6f) return false;
        for (int vertical : new int[] {-1, 1}) {
            float edge = vertical < 0 ? top : bottom;
            if (Math.abs(centerY - edge) > gap * .3f) continue;
            for (int horizontal : new int[] {-1, 1}) {
                for (int distance = 1; distance <= Math.round(gap * .8f); distance++) {
                    int bar = (horizontal > 0 ? maxX : minX) + horizontal * distance;
                    if (bar < 0 || bar >= width) continue;
                    boolean full = true;
                    for (int y = Math.round(top + gap * .2f);
                            y <= Math.round(bottom - gap * .2f);
                            y++)
                        if (!ink(gray, width, height, bar, y)) {
                            full = false;
                            break;
                        }
                    if (!full
                            || ink(gray, width, height, bar, Math.round(top - gap * .4f))
                            || ink(gray, width, height, bar, Math.round(bottom + gap * .4f)))
                        continue;
                    // A genuine oval must not be discarded merely because it is next to a bar.
                    int inward = Math.round(edge - vertical * gap * .45f);
                    int dark = 0;
                    for (int x = Math.round(centerX - gap * .8f);
                            x <= Math.round(centerX + gap * .8f);
                            x++) {
                        if ((x - bar) * horizontal >= -gap * .2f) continue;
                        if (ink(gray, width, height, x, inward)) dark++;
                    }
                    if (dark > 1) continue;
                    // The curve departs away from the bar, beyond the staff, in two separated
                    // columns.
                    int near = Math.round(centerX - horizontal * gap * 1.2f);
                    int far = Math.round(centerX - horizontal * gap * 2.2f);
                    float nearDepth = strokeDepth(gray, width, height, near, edge, vertical, gap);
                    float farDepth = strokeDepth(gray, width, height, far, edge, vertical, gap);
                    if (nearDepth >= .25f && farDepth >= nearDepth + .08f && farDepth <= .9f)
                        return true;
                }
            }
        }
        return false;
    }

    private static float strokeDepth(
            byte[] gray, int width, int height, int x, float edge, int vertical, float gap) {
        int count = 0;
        float sum = 0;
        for (int d = Math.round(gap * .25f); d <= Math.round(gap); d++)
            if (ink(gray, width, height, x, Math.round(edge + vertical * d))) {
                count++;
                sum += d;
            }
        return count >= 2 && count <= gap * .4f ? sum / count / gap : -1;
    }

    private static boolean ink(byte[] gray, int width, int height, int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height && (gray[y * width + x] & 255) < 165;
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A dark run contained in this notehead cannot establish a separate beam. */
final class HeadInkBeamExclusion {
    private HeadInkBeamExclusion() {}

    static boolean onlyHead(
            byte[] gray,
            int width,
            int height,
            int y,
            int scanLeft,
            int scanRight,
            int stemX,
            int tolerance,
            int headLeft,
            int headRight,
            int headTop,
            int headBottom,
            float gap) {
        if (gray == null
                || gray.length != (long) width * height
                || width <= 0
                || height <= 0
                || y < 0
                || y >= height
                || y < headTop
                || y > headBottom
                || gap <= 0
                || scanLeft < 0
                || scanRight >= width
                || scanRight < scanLeft
                || headLeft < 0
                || headRight >= width
                || headRight < headLeft
                || headTop < 0
                || headBottom >= height
                || headBottom < headTop) return false;
        int padding = Math.max(1, Math.round(gap * .1f));
        int start = -1, end = -1, previous = -1, matches = 0;
        for (int x = scanLeft; x <= scanRight + 1; x++) {
            boolean ink = x <= scanRight && (gray[y * width + x] & 255) <= 165;
            if (ink && start >= 0 && x - previous > 2) {
                if (start <= stemX + tolerance && end >= stemX - tolerance) {
                    if (start < headLeft - padding || end > headRight + padding) return false;
                    matches++;
                }
                start = -1;
            }
            if (ink) {
                if (start < 0) start = x;
                end = previous = x;
            } else if (x > scanRight
                    && start >= 0
                    && start <= stemX + tolerance
                    && end >= stemX - tolerance) {
                if (start < headLeft - padding || end > headRight + padding) return false;
                matches++;
            }
        }
        return matches > 0;
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Ink beyond a traced stem cap needs a source connection to that cap. */
final class DetachedStemInkBand {
    private DetachedStemInkBand() {}

    static boolean beyondWhiteGap(
            byte[] gray,
            int width,
            int height,
            int stemX,
            int stemEnd,
            int y,
            boolean upward,
            float gap,
            int threshold) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || stemX < 0
                || stemX >= width
                || stemEnd < 0
                || stemEnd >= height
                || y < 0
                || y >= height
                || !Float.isFinite(gap)
                || gap <= 0
                || threshold <= 0
                || threshold > 255) return false;
        int direction = upward ? -1 : 1;
        if ((y - stemEnd) * direction <= 2) return false;
        int radius = Math.min(width - 1, Math.max(1, Math.round(gap * .1f))), blank = 0;
        int paperRadius = Math.min(width - 1, Math.max(radius + 2, Math.round(gap * .75f)));
        int[] tones = new int[256];
        for (int row = stemEnd + direction; row != y; row += direction) {
            java.util.Arrays.fill(tones, 0);
            int samples = 0;
            for (int x = Math.max(0, stemX - paperRadius);
                    x <= Math.min(width - 1, stemX + paperRadius);
                    x++) {
                tones[gray[row * width + x] & 255]++;
                samples++;
            }
            int paper = 255, count = 0;
            for (int tone = 0; tone < 256; tone++) {
                count += tones[tone];
                if (count >= Math.ceil(samples * .75f)) {
                    paper = tone;
                    break;
                }
            }
            // Ink lighter than the shaft threshold still supplies a connection.
            // A blank row must also approach the neighboring source paper tone.
            int blankThreshold =
                    Math.max(threshold, paper - Math.max(2, Math.round((paper - threshold) * .1f)));
            boolean ink = false;
            for (int x = Math.max(0, stemX - radius); x <= Math.min(width - 1, stemX + radius); x++)
                if ((gray[row * width + x] & 255) < blankThreshold) ink = true;
            if (ink) blank = 0;
            else if (++blank >= 2) return true;
        }
        return false;
    }
}

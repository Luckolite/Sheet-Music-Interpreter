// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Confirms the two spines, paired bowls and central notch of a printed C clef. */
final class PrintedCClef {
    private PrintedCClef() {}

    static int detect(
            byte[] gray,
            int width,
            int height,
            int minX,
            int maxX,
            int minY,
            int maxY,
            float bottom,
            float gap) {
        if (gray == null
                || gray.length != (long) width * height
                || gap < 4
                || !Float.isFinite(gap)
                || maxY - minY < gap * 2
                || maxY - minY > gap * 5
                || maxX - minX > gap * 3.2f) return ScoreNoteEvent.CLEF_UNKNOWN;
        for (int line = 2; line <= 3; line++) {
            float center = bottom - line * gap;
            if (minY > center - gap || maxY < center + gap) continue;
            for (float stretch : new float[] {.9f, 1f, 1.1f}) {
                float horizontal = gap * stretch;
                for (float x = Math.round(minX - gap * .9f); x <= minX + gap * .3f; x += .5f) {
                    if (matches(gray, width, height, x, center, horizontal, gap))
                        return line == 2 ? ScoreNoteEvent.CLEF_ALTO : ScoreNoteEvent.CLEF_TENOR;
                }
            }
        }
        return ScoreNoteEvent.CLEF_UNKNOWN;
    }

    private static boolean matches(
            byte[] gray, int w, int h, float x, float y, float sx, float sy) {
        if (x - sx * .2f < 0 || x + sx * 2.6f >= w || y - sy * 2.1f < 0 || y + sy * 2.1f >= h)
            return false;
        // Sample between the staff rules: horizontal staff ink cannot supply a spine.
        int first = 0, second = 0, separation = 0;
        for (int i = -7; i <= 7; i += 2) {
            float dy = i * .25f;
            if (ink(gray, w, h, x, y + dy * sy, sx * .08f)) first++;
            if (ink(gray, w, h, x + sx * .55f, y + dy * sy, sx * .09f)) second++;
            if (!ink(gray, w, h, x + sx * .38f, y + dy * sy, 0)) separation++;
        }
        if (first < 7 || second < 7 || separation < 6) return false;
        int strokes = 0, holes = 0;
        for (int sign : Probes.SIGNS) {
            for (float[] p : Probes.BOWLS)
                if (ink(gray, w, h, x + p[0] * sx, y + sign * p[1] * sy, sx * .12f)) strokes++;
            for (float dy : Probes.HOLE_ROWS)
                if (!ink(gray, w, h, x + sx * 1.6f, y + sign * dy * sy, sx * .10f)) holes++;
        }
        return strokes >= 11 && holes >= 7;
    }

    private static final class Probes {
        private static final int[] SIGNS = {-1, 1};
        private static final float[][] BOWLS = {
            {.95f, .25f},
            {1.5f, .25f},
            {2.1f, .5f},
            {2.3f, .75f},
            {2.3f, 1.25f},
            {2.1f, 1.5f},
            {1.9f, 1.75f}
        };
        private static final float[] HOLE_ROWS = {.5f, .75f, 1.25f, 1.5f};

        private Probes() {}
    }

    private static boolean ink(byte[] gray, int w, int h, float x, float y, float radius) {
        int left = Math.max(0, Math.round(x - radius)),
                right = Math.min(w - 1, Math.round(x + radius));
        int top = Math.max(0, Math.round(y - radius)),
                bottom = Math.min(h - 1, Math.round(y + radius));
        int dark = 0, total = 0;
        for (int yy = top; yy <= bottom; yy++)
            for (int xx = left; xx <= right; xx++) {
                total++;
                if ((gray[yy * w + xx] & 255) < 165) dark++;
            }
        return total > 0 && dark >= total * .5f;
    }
}

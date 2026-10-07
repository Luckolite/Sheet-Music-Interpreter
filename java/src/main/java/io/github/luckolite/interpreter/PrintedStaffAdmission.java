// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Rejects exterior texture while retaining weak semantic staffs within printed systems. */
final class PrintedStaffAdmission {
    interface Rules {
        float y(int line, int x);
    }

    record Evidence(
            int columns,
            int alignedColumns,
            int longestRun,
            int minimumRun,
            int darkSamples,
            int backgroundSamples,
            int backgroundRange) {
        boolean admitted() {
            return darkSamples < backgroundSamples * .6f
                    || backgroundSamples == 0
                    || backgroundRange < 24
                    || alignedColumns >= Math.max(12, Math.round(columns * .08f))
                            && longestRun >= minimumRun;
        }

        boolean printed() {
            return alignedColumns >= Math.max(12, Math.round(columns * .08f))
                    && longestRun >= minimumRun;
        }
    }

    static boolean admitted(
            Evidence evidence, float center, float firstPrinted, float lastPrinted) {
        // An interior semantic row can be real even when a shadow obscures its rules.
        // Exterior photo texture has no printed rows bracketing it.
        return evidence.admitted() || center > firstPrinted && center < lastPrinted;
    }

    static Evidence evidence(
            byte[] gray, int width, int height, int left, int right, float gap, Rules rules) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || gap < 3
                || rules == null) return new Evidence(0, 12, 8, 8, 0, 0, 0);
        left = Math.max(0, left);
        right = Math.min(width - 1, right);
        int probe = Math.max(2, Math.round(gap * .32f)),
                radius = Math.max(0, Math.round(gap * .10f));
        int aligned = 0, run = 0, longest = 0, dark = 0, samples = 0, min = 255, max = 0;
        for (int x = left; x <= right; x++) {
            int support = 0;
            for (int line = 0; line < 5; line++) {
                int center = Math.round(rules.y(line, x));
                boolean printed = false;
                for (int dy = -radius; dy <= radius; dy++) {
                    int y = center + dy;
                    if (y - probe < 0 || y + probe >= height) continue;
                    int ink = gray[y * width + x] & 255;
                    if (ink <= 238
                            && (gray[(y - probe) * width + x] & 255) >= ink + 12
                            && (gray[(y + probe) * width + x] & 255) >= ink + 12) {
                        printed = true;
                        break;
                    }
                }
                if (printed) support++;
            }
            if (support >= 4) {
                aligned++;
                longest = Math.max(longest, ++run);
            } else run = 0;
            for (int line = 0; line < 4; line++)
                for (float phase : new float[] {.25f, .5f, .75f}) {
                    int y =
                            Math.round(
                                    rules.y(line, x)
                                            + phase * (rules.y(line + 1, x) - rules.y(line, x)));
                    if (y >= 0 && y < height) {
                        int ink = gray[y * width + x] & 255;
                        samples++;
                        min = Math.min(min, ink);
                        max = Math.max(max, ink);
                        if (ink <= 205) dark++;
                    }
                }
        }
        return new Evidence(
                Math.max(0, right - left + 1),
                aligned,
                longest,
                Math.max(8, Math.round(gap * 3)),
                dark,
                samples,
                max - min);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.regex.Pattern;

/** Evidence for a printed progressive direction ending at the current system's barline. */
final class PrintedCrescendoDashes {
    private static final Pattern CAPTION = Pattern.compile(
            "(?iu)^\\s*cresc(?:endo)?\\.?\\s+poco\\s+a\\s+poco\\s*$");

    private PrintedCrescendoDashes() {}

    static boolean caption(String text) {
        return text != null && CAPTION.matcher(text).matches();
    }

    static boolean reachesBarline(
            PlayingTechniqueDetector.Word word, byte[] gray, int width, int height,
            float gap, int barline) {
        if (!caption(word.text()) || gray == null || gap <= 0) return false;
        return periodicRun(gray, width, height, gap,
                Math.round(word.right() * width - gap * .25f), barline,
                Math.round(word.top() * height), Math.round(word.bottom() * height), true);
    }

    static boolean resumes(
            byte[] gray, int width, int height, float gap, float firstAttack, float staffBottom) {
        return periodicRun(gray, width, height, gap,
                Math.round(firstAttack - gap), Math.round(firstAttack + gap * 12),
                Math.round(staffBottom + gap * .5f), Math.round(staffBottom + gap * 5), false);
    }

    private static boolean periodicRun(
            byte[] gray, int width, int height, float gap,
            int left, int right, int top, int bottom, boolean reachEnd) {
        int look = Math.max(2, Math.round(gap * .34f));
        left = Math.max(0, left);
        right = Math.min(width, right);
        top = Math.max(look, top);
        bottom = Math.min(height - look, bottom);
        if (right - left < gap * 7) return false;
        for (int y = top; y < bottom; y++) {
            int first = -1, previous = -1, count = 0, lastEnd = -1;
            for (int x = left; x < right;) {
                if (!dashInk(gray, width, x, y, look)) {
                    x++;
                    continue;
                }
                int start = x++;
                while (x < right && dashInk(gray, width, x, y, look)) x++;
                int length = x - start;
                if (length < gap * .27f || length > gap * .85f) continue;
                if (first < 0) first = start;
                if (previous >= 0 && (start - previous < gap || start - previous > gap * 1.75f)) {
                    count = 0;
                    break;
                }
                previous = start;
                lastEnd = x;
                count++;
            }
            if (count >= 6 && first - left <= gap * 1.5f && (!reachEnd || right - lastEnd <= gap))
                return true;
        }
        return false;
    }

    private static boolean dashInk(byte[] gray, int width, int x, int y, int look) {
        int value = gray[y * width + x] & 255;
        int above = gray[(y - look) * width + x] & 255;
        int below = gray[(y + look) * width + x] & 255;
        return value + 65 <= Math.min(above, below);
    }
}

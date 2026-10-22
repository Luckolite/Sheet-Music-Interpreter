// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Original recognition frames need five separately witnessed, long raster staff rules. */
final class OriginalRestFrameRules {
    private OriginalRestFrameRules() {}

    static boolean supported(
            byte[] gray, int width, int height, float top, float bottom, float gap) {
        if (gray == null
                || width < 3
                || height < 3
                || width > 8192
                || height > 16384
                || gray.length != (long) width * height
                || !Float.isFinite(top)
                || !Float.isFinite(bottom)
                || !Float.isFinite(gap)
                || gap < 4
                || gap > 64
                || top < 0
                || bottom >= height
                || Math.abs(bottom - top - 4 * gap) > .5f) return false;
        for (int line = 0; line < 5; line++) {
            int y = Math.round(top + line * gap), dark = 0, longest = 0, run = 0;
            if (y < 0 || y >= height) return false;
            for (int x = 0; x < width; x++) {
                if ((gray[y * width + x] & 255) < 170) {
                    dark++;
                    longest = Math.max(longest, ++run);
                } else run = 0;
            }
            if (dark < Math.max(24, width * .25f) || longest < Math.max(24, Math.round(gap * 5)))
                return false;
        }
        return true;
    }
}

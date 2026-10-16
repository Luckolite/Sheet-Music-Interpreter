// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A lighter trace cannot extend a proved shaft through its blank printed cap. */
final class PaleBeamRecovery {
    private PaleBeamRecovery() {}

    static boolean crossesBlankCap(
            byte[] gray, int width, int height, int[] strict, int[] pale, float gap) {
        if (gray == null
                || gray.length != (long) width * height
                || strict == null
                || pale == null
                || strict.length < 3
                || pale.length < 3
                || !Float.isFinite(gap)
                || gap < 4
                || Math.abs(strict[2]) != 1
                || strict[2] != pale[2]
                || Math.abs(strict[0] - pale[0]) > gap * .3f
                || (pale[1] - strict[1]) * strict[2] < gap * .5f) return false;
        int left = Math.min(strict[0], pale[0]) - 1;
        int right = Math.max(strict[0], pale[0]) + 1;
        if (left < 0 || right >= width) return false;
        int blank = 0;
        for (int y = strict[1] + strict[2]; (pale[1] - y) * strict[2] > 0; y += strict[2]) {
            if (y < 0 || y >= height) return false;
            boolean white = true;
            for (int x = left; x <= right; x++) white &= (gray[y * width + x] & 255) >= 250;
            if (!white) {
                int reach = Math.max(3, Math.round(gap));
                int lo = Math.max(0, left - reach), hi = Math.min(width - 1, right + reach);
                int[] samples = new int[hi - lo + 1];
                for (int x = lo; x <= hi; x++) samples[x - lo] = gray[y * width + x] & 255;
                java.util.Arrays.sort(samples);
                int paper = samples[(samples.length - 1) * 3 / 4];
                white = paper >= 235;
                for (int x = left; x <= right; x++) {
                    int shade = gray[y * width + x] & 255;
                    white &= shade >= 235 && paper - shade <= 8;
                }
            }
            blank = white ? blank + 1 : 0;
            if (blank >= 2) return true;
        }
        return false;
    }
}

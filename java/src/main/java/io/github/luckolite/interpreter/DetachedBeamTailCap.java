// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A local paper gap can separate a completed beam shaft from detached lower ink. */
final class DetachedBeamTailCap {
    static int endpoint(
            byte[] gray,
            int width,
            int height,
            int x,
            float headY,
            int end,
            int side,
            float gap,
            int threshold) {
        if (gray == null
                || (long) width * height != gray.length
                || width < 3
                || height < 1
                || x < 1
                || x >= width - 1
                || !Float.isFinite(gap)
                || gap < 8
                || !Float.isFinite(headY)
                || side != 1 && side != -1
                || end < 0
                || end >= height
                || threshold < 1
                || threshold > 220) return end;
        float length = (end - headY) * side;
        if (length < gap * 2.7f || length > gap * 6.5f) return end;
        int near = Math.max(2, Math.round(gap * .35f)), far = Math.round(gap * 1.4f);
        int bright = Math.max(200, threshold + 30);
        for (int distance = near; distance <= far; distance++) {
            int y = end - side * distance, previous = y - side;
            if (y < 0 || y >= height || previous < 0 || previous >= height) continue;
            boolean clear = true;
            for (int xx = x - 1; xx <= x + 1; xx++)
                if ((gray[y * width + xx] & 255) < bright
                        || (gray[previous * width + xx] & 255) < bright) clear = false;
            if (clear) return previous - side;
        }
        return end;
    }
}

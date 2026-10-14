// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Propose predominantly faded glyphs; ordinary dark candidates keep their existing bounds. */
final class FaintDynamicGlyphComponents {
    record Bounds(int left, int top, int right, int bottom, int pixels) {}

    static List<Bounds> find(byte[] gray, int width, int height, boolean[] seen, int[] queue) {
        var result = new ArrayList<>(findAt(gray, width, height, seen, queue, 205, .2f));
        for (var candidate : findAt(gray, width, height, seen, queue, 185, .65f)) {
            boolean covered = false;
            for (var prior : result)
                if (prior.left() <= candidate.left()
                        && prior.top() <= candidate.top()
                        && prior.right() >= candidate.right()
                        && prior.bottom() >= candidate.bottom()) covered = true;
            if (!covered) result.add(candidate);
        }
        return result;
    }

    private static List<Bounds> findAt(
            byte[] gray,
            int width,
            int height,
            boolean[] seen,
            int[] queue,
            int threshold,
            float darkFraction) {
        Arrays.fill(seen, false);
        List<Bounds> result = new ArrayList<>();
        for (int p = 0; p < gray.length; p++) {
            if (seen[p] || (gray[p] & 255) >= threshold) continue;
            int read = 0, count = 1;
            queue[0] = p;
            seen[p] = true;
            int left = p % width, right = left, top = p / width, bottom = top;
            int dark = 0;
            while (read < count) {
                int at = queue[read++], x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                if ((gray[at] & 255) < 145) dark++;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!seen[next] && (gray[next] & 255) < threshold) {
                            seen[next] = true;
                            queue[count++] = next;
                        }
                    }
            }
            if (dark < count * darkFraction && count >= 3)
                result.add(new Bounds(left, top, right + 1, bottom + 1, count));
        }
        return result;
    }
}

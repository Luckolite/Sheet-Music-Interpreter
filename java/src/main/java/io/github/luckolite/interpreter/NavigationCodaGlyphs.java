// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Staff-local raw candidates; the full crossed ring and four holes must verify a coda. */
final class NavigationCodaGlyphs {
    private NavigationCodaGlyphs() {}

    static List<ScoreNavigationDetector.Glyph> detect(
            byte[] gray, int width, int height, List<PlayingTechniqueDetector.Staff> staffs) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || staffs == null) return List.of();
        var result = new ArrayList<ScoreNavigationDetector.Glyph>();
        for (var staff : staffs) {
            float gap = staff.gap();
            if (gap < 5 || !Float.isFinite(gap)) continue;
            // Boundary ownership accepts a sign ending six gaps above the staff.
            // Its full body can extend another four gaps upward; do not clip that proof.
            int top = Math.max(0, Math.round(staff.top() - gap * 10));
            int bottom = Math.min(height - 1, Math.round(staff.top() - gap * .5f));
            if (bottom <= top) continue;
            for (int threshold : new int[] {80, 140}) {
                int rows = bottom - top + 1;
                boolean[] seen = new boolean[width * rows];
                int[] queue = new int[seen.length];
                for (int seed = 0; seed < seen.length; seed++) {
                    if (seen[seed] || (gray[top * width + seed] & 255) > threshold) continue;
                    int take = 0, size = 1, l = width, r = -1, t = rows, b = -1;
                    queue[0] = seed;
                    seen[seed] = true;
                    while (take < size) {
                        int at = queue[take++], x = at % width, y = at / width;
                        l = Math.min(l, x);
                        r = Math.max(r, x);
                        t = Math.min(t, y);
                        b = Math.max(b, y);
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                int xx = x + dx, yy = y + dy;
                                if (xx < 0 || xx >= width || yy < 0 || yy >= rows) continue;
                                int next = yy * width + xx;
                                if (!seen[next] && (gray[top * width + next] & 255) <= threshold) {
                                    seen[next] = true;
                                    queue[size++] = next;
                                }
                            }
                    }
                    float w = r - l + 1, h = b - t + 1;
                    if (t == 0
                            || b == rows - 1
                            || staff.top() - (top + b) > gap * 6
                            || w < gap
                            || w > gap * 3.5f
                            || h < gap * 1.6f
                            || h > gap * 4
                            || w / h < .65f
                            || w / h > 1.15f) continue;
                    if (!CodaGlyphInk.matches(gray, width, height, l, top + t, r, top + b, gap))
                        continue;
                    float cx = (l + r) * .5f / width, cy = (2 * top + t + b) * .5f / height;
                    if (result.stream()
                            .anyMatch(
                                    prior ->
                                            Math.abs(prior.centerX() - cx) * width < gap
                                                    && Math.abs(
                                                                            (prior.top()
                                                                                                    + prior
                                                                                                            .bottom())
                                                                                            * .5f
                                                                                    - cy)
                                                                    * height
                                                            < gap)) continue;
                    result.add(
                            new ScoreNavigationDetector.Glyph(
                                    ScorePlaybackDirection.Kind.CODA,
                                    cx,
                                    (top + t) / (float) height,
                                    (top + b) / (float) height));
                }
            }
        }
        return List.copyOf(result);
    }
}

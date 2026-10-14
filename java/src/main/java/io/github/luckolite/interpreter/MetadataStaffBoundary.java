// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Original raster evidence for advancing an existing metadata staff boundary. */
public final class MetadataStaffBoundary {
    private MetadataStaffBoundary() {}

    public static float advance(
            int width,
            int height,
            boolean[] dark,
            float original,
            float firstLine,
            float gap,
            float slope,
            int support) {
        if (width < 20
                || height < 20
                || dark == null
                || (long) width * height != dark.length
                || !Float.isFinite(original)
                || original < 0
                || !Float.isFinite(firstLine)
                || firstLine < 0
                || firstLine >= height
                || !Float.isFinite(gap)
                || gap <= 0
                || !Float.isFinite(slope)
                || support < 2
                || original - firstLine <= width * .03f) return original;
        int supported = 0;
        for (int line = 0; line < 5; line++) {
            int hits = 0, total = 0;
            for (int x = width / 10; x < width * 9 / 10; x += 3) {
                int y = Math.round(firstLine + slope * x + line * gap);
                boolean ink = false;
                for (int dy = -2; dy <= 2; dy++) {
                    int row = y + dy;
                    if (row >= 0 && row < height && dark[row * width + x]) {
                        ink = true;
                        break;
                    }
                }
                if (ink) hits++;
                total++;
            }
            if (hits > total * .6f) supported++;
        }
        return supported >= 4 ? firstLine : original;
    }
}

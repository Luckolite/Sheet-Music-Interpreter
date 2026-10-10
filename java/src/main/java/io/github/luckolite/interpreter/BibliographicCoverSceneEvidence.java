// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.HashSet;

/** Bibliographic-only photographic-cover evidence; musical staff detection remains unchanged. */
public final class BibliographicCoverSceneEvidence {
    private BibliographicCoverSceneEvidence() {}

    public static boolean unstablePhotoStaff(
            int width,
            int height,
            int[] pixels,
            float nativeStaff,
            float mediumStaff,
            float smallStaff) {
        if (width < 900
                || height < 32
                || height > width * .8f
                || pixels == null
                || (long) width * height != pixels.length
                || !Float.isFinite(nativeStaff)
                || nativeStaff < 0
                || nativeStaff >= height
                || !Float.isFinite(mediumStaff)
                || !Float.isFinite(smallStaff)
                || mediumStaff >= 0
                || smallStaff >= 0) return false;
        return photographicLandscape(width, height, pixels);
    }

    public static boolean photographicLandscape(int width, int height, int[] pixels) {
        if (width < 900
                || height < 32
                || height > width * .8f
                || pixels == null
                || (long) width * height != pixels.length) return false;
        var palette = new HashSet<Integer>();
        int colored = 0, textured = 0, total = 0;
        for (int y = 0; y < height; y += 2)
            for (int x = 0; x < width; x += 2) {
                int p = pixels[y * width + x], r = p >>> 16 & 255, g = p >>> 8 & 255, b = p & 255;
                palette.add((r >> 3) << 10 | (g >> 3) << 5 | (b >> 3));
                if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 25) colored++;
                if (x + 2 < width) {
                    int q = pixels[y * width + x + 2],
                            qr = q >>> 16 & 255,
                            qg = q >>> 8 & 255,
                            qb = q & 255;
                    if (Math.abs(r - qr) + Math.abs(g - qg) + Math.abs(b - qb) > 30
                            && r > 45
                            && g > 45
                            && b > 45
                            && qr > 45
                            && qg > 45
                            && qb > 45) textured++;
                }
                total++;
            }
        return palette.size() >= 1024 && colored > total * .2 && textured > total * .04;
    }
}

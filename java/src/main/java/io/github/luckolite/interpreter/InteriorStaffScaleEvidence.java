// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Source proof for a compressed interior semantic row between independent printed staves. */
final class InteriorStaffScaleEvidence {
    static boolean unsupported(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float gap,
            PrintedStaffAdmission.Rules rules,
            PrintedStaffAdmission.Evidence evidence,
            float aboveGap,
            float belowGap) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || rules == null
                || evidence == null
                || !Float.isFinite(gap)
                || gap < 4
                || !Float.isFinite(aboveGap)
                || !Float.isFinite(belowGap)
                || aboveGap < 4
                || belowGap < 4
                || evidence.alignedColumns() != 0
                || right - left < width * .8f
                || Math.max(aboveGap, belowGap) > Math.min(aboveGap, belowGap) * 1.2f
                || gap >= Math.min(aboveGap, belowGap) * .78f) return false;
        left = Math.max(0, left);
        right = Math.min(width - 1, right);
        int phase = Math.max(1, Math.round(gap * .5f)),
                radius = Math.max(1, Math.round(gap * .12f)),
                flank = Math.max(2, Math.round(gap * .32f));
        for (int shift = -phase; shift <= phase; shift++) {
            int[] runs = new int[32];
            for (int x = left; x <= right; x++) {
                int support = 0, thinSupport = 0;
                for (int line = 0; line < 5; line++) {
                    int center = Math.round(rules.y(line, x)) + shift;
                    boolean hit = false, thin = false;
                    for (int y = Math.max(flank, center - radius);
                            y <= Math.min(height - flank - 1, center + radius);
                            y++) {
                        int ink = gray[y * width + x] & 255;
                        if ((gray[(y - flank) * width + x] & 255) >= ink + 4
                                && (gray[(y + flank) * width + x] & 255) >= ink + 4) {
                            hit = true;
                            int
                                    edge =
                                            Math.min(
                                                    gray[(y - flank) * width + x] & 255,
                                                    gray[(y + flank) * width + x] & 255),
                                    cut = (ink + edge) / 2;
                            int lo = y, hi = y;
                            while (lo > y - flank && (gray[(lo - 1) * width + x] & 255) <= cut)
                                lo--;
                            while (hi < y + flank && (gray[(hi + 1) * width + x] & 255) <= cut)
                                hi++;
                            if (hi - lo + 1 <= Math.max(2, Math.round(gap * .25f))) {
                                thin = true;
                                break;
                            }
                        }
                    }
                    if (hit) support |= 1 << line;
                    if (thin) thinSupport |= 1 << line;
                }
                for (int subset = 0; subset < 32; subset++)
                    if (Integer.bitCount(subset) == 3 || Integer.bitCount(subset) == 4) {
                        int mask = Integer.bitCount(subset) == 3 ? thinSupport : support;
                        runs[subset] = (mask & subset) == subset ? runs[subset] + 1 : 0;
                        if (runs[subset] >= Math.max(12, Math.round(gap * 3))) return false;
                    }
            }
        }
        return true;
    }
}

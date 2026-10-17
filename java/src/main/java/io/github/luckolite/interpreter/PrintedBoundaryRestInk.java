// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Distinguishes a transported rest contour from an independently printed staff boundary. */
final class PrintedBoundaryRestInk {
    private PrintedBoundaryRestInk() {}

    static boolean owns(
            byte[] gray,
            int width,
            int height,
            MeasureRegion region,
            ScoreRestEvent rest,
            float gap,
            int claimLeft,
            int claimRight,
            int top,
            int bottom) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || region == null
                || rest == null
                || !Float.isFinite(gap)
                || gap < 6
                || !Float.isFinite(rest.positionInMeasure())
                || !Float.isFinite(rest.pageY())
                || !Float.isFinite(rest.pageHeight())
                || rest.pageHeight() <= 0
                || !Float.isFinite(region.left())
                || !Float.isFinite(region.right())
                || region.left() < 0
                || region.right() > 1
                || region.right() <= region.left()
                || (region.right() - region.left()) * width < gap * 4
                || rest.positionInMeasure() < 0
                || rest.positionInMeasure() > 1
                || top < 0
                || bottom >= height
                || bottom < top
                || claimLeft < 0
                || claimRight >= width
                || claimRight < claimLeft) return false;
        float x =
                (region.left() + rest.positionInMeasure() * (region.right() - region.left()))
                        * width;
        float cy = (top + (float) bottom) * .5f, half = (bottom - (float) top + 1) * .5f;
        if (!Float.isFinite(x + cy + half)
                || half < gap
                || half > gap * 2
                || cy - half < 0
                || cy + half >= height) return false;
        float left = region.left() * width, right = region.right() * width;
        boolean nearLeft = Math.abs(x - left) <= gap * .75f;
        boolean nearRight = Math.abs(x - right) <= gap * .75f;
        if (nearLeft == nearRight) return false;
        int boundary = Math.round(nearLeft ? left : right), inward = nearLeft ? 1 : -1;
        int a0 = Math.round(boundary + inward * gap * 1.95f);
        int a1 = Math.round(boundary + inward * gap * 1.4f);
        int b0 = Math.round(boundary + inward * gap * .95f);
        int b1 = Math.round(boundary + inward * gap * .4f);
        int al = Math.min(a0, a1),
                ar = Math.max(a0, a1),
                bl = Math.min(b0, b1),
                br = Math.max(b0, b1);
        int y0 = Math.max(0, (int) Math.floor(cy - gap * 4));
        int y1 = Math.min(height - 1, (int) Math.ceil(cy + gap * 4));
        int radius = Math.max(1, Math.round(gap * .3f));
        int xl = boundary - radius, xr = boundary + radius;
        if (claimRight - claimLeft + 1 > gap * 2
                || claimRight - claimLeft + 1 < gap * .7f
                || Math.abs((claimLeft + claimRight) * .5f - x) > 1) return false;
        if (al < 0
                || ar >= width
                || bl < 0
                || br >= width
                || xl < 0
                || xr >= width
                || claimLeft < 0
                || claimRight >= width) return false;
        List<int[]> bands = new ArrayList<>();
        int start = -1;
        for (int y = y0; y <= y1 + 1; y++) {
            boolean rule =
                    y <= y1
                            && supported(gray, width, y, al, ar, 170)
                            && supported(gray, width, y, bl, br, 170);
            if (rule && start < 0) start = y;
            if (!rule && start >= 0) {
                bands.add(new int[] {start, y - 1});
                start = -1;
            }
        }
        int[] owned = null;
        for (int i = 0; i + 4 < bands.size(); i++) {
            boolean complete = true;
            for (int r = 0; r < 5; r++) {
                int[] band = bands.get(i + r);
                if (band[1] - band[0] + 1 > gap * .4f) complete = false;
                if (r > 0) {
                    int[] prior = bands.get(i + r - 1);
                    float distance = (band[0] + band[1] - prior[0] - prior[1]) * .5f;
                    if (Math.abs(distance - gap) > gap * .12f) complete = false;
                }
            }
            if (!complete
                    || cy - half < bands.get(i)[0] - gap * .25f
                    || cy + half > bands.get(i + 4)[1] + gap * .25f) continue;
            if (owned != null) return false;
            owned = new int[] {i, bands.get(i)[0], bands.get(i + 4)[1]};
        }
        if (owned == null) return false;
        int shaftLeft = -1, shaftRight = -1, runs = 0;
        boolean prior = false;
        for (int xx = xl; xx <= xr; xx++) {
            int count = 0, blank = 0, longest = 0;
            for (int yy = owned[1]; yy <= owned[2]; yy++) {
                if ((gray[yy * width + xx] & 255) < 170) {
                    count++;
                    blank = 0;
                } else longest = Math.max(longest, ++blank);
            }
            boolean shaft = count >= (owned[2] - owned[1] + 1) * .94f && longest <= 1;
            if (shaft) {
                if (!prior) runs++;
                if (shaftLeft < 0) shaftLeft = xx;
                shaftRight = xx;
            }
            prior = shaft;
        }
        if (runs != 1
                || shaftRight - shaftLeft + 1 < Math.max(2, Math.round(gap * .08f))
                || shaftRight - shaftLeft + 1 > gap * .35f) return false;
        int fringe = Math.max(1, Math.round(gap * .15f));
        // The barline and rule cores remain strict. Residual attribution is deliberately
        // more conservative: faint glyph bodies must not disappear into paper.
        int[][] envelopes = new int[5][2];
        for (int r = 0; r < 5; r++) {
            int[] band = bands.get(owned[0] + r);
            int lo = band[0], hi = band[1];
            while (lo > Math.max(y0, band[0] - fringe)
                    && supported(gray, width, lo - 1, al, ar, 225)
                    && supported(gray, width, lo - 1, bl, br, 225)) lo--;
            while (hi < Math.min(y1, band[1] + fringe)
                    && supported(gray, width, hi + 1, al, ar, 225)
                    && supported(gray, width, hi + 1, bl, br, 225)) hi++;
            envelopes[r][0] = lo;
            envelopes[r][1] = hi;
        }
        int residual = 0;
        for (int yy = top; yy <= bottom; yy++) {
            boolean rule = false;
            for (int r = 0; r < 5; r++) {
                int[] band = envelopes[r];
                if (yy >= band[0] - 1 && yy <= band[1] + 1) rule = true;
            }
            if (rule) continue;
            for (int xx = claimLeft; xx <= claimRight; xx++)
                if ((gray[yy * width + xx] & 255) < 225) {
                    residual++;
                    if (xx < shaftLeft - fringe || xx > shaftRight + fringe) return false;
                    int near = Math.max(shaftLeft, Math.min(shaftRight, xx));
                    for (int across = Math.min(xx, near); across <= Math.max(xx, near); across++)
                        if ((gray[yy * width + across] & 255) >= 225) return false;
                }
        }
        return residual >= gap;
    }

    private static boolean supported(
            byte[] gray, int width, int y, int left, int right, int threshold) {
        int count = 0;
        for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) < threshold) count++;
        return count >= (right - left + 1) * .85f;
    }
}

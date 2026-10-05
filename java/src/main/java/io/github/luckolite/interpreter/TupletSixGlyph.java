// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A descending upper hook and closed lower bowl distinguish a printed six. */
final class TupletSixGlyph {
    private TupletSixGlyph() {}

    static boolean matches(byte[] gray, int width, int left, int top, int w, int h) {
        if (gray == null
                || width <= 0
                || gray.length % width != 0
                || left < 0
                || top < 0
                || w < 4
                || h < 9
                || (long) left + w > width
                || (long) top + h > gray.length / width) return false;
        int[] min = new int[h], max = new int[h], count = new int[h];
        java.util.Arrays.fill(min, w);
        java.util.Arrays.fill(max, -1);
        for (int y = 0; y < h; y++) {
            int rowBase = (top + y) * width + left;
            for (int x = 0; x < w; x++)
                if ((gray[rowBase + x] & 255) < 165) {
                    min[y] = Math.min(min[y], x);
                    max[y] = x;
                    count[y]++;
                }
        }
        int upperThin = 0, lowerPocket = 0, upperPocket = 0, footWide = 0;
        double high = 0, low = 0;
        int highRows = 0, lowRows = 0;
        int pocketWidth = Math.max(2, Math.round(w * .25f));
        for (int y = 0; y < h; y++) {
            if (y < h * .25f && count[y] > 0) {
                high += (min[y] + max[y]) * .5;
                highRows++;
            }
            if (y >= h * .27f && y < h * .47f && count[y] > 0) {
                low += (min[y] + max[y]) * .5;
                lowRows++;
            }
            if (y >= h * .1f && y < h * .4f && count[y] > 0 && max[y] - min[y] <= w * .55f)
                upperThin++;
            int blank = 0, longest = 0, rowBase = (top + y) * width + left;
            for (int x = min[y] + 1; x < max[y]; x++) {
                if ((gray[rowBase + x] & 255) >= 165) longest = Math.max(longest, ++blank);
                else blank = 0;
            }
            boolean pocket = longest >= pocketWidth;
            if (y < h * .4f && pocket) upperPocket++;
            if (y >= h * .45f && y < h * .88f && min[y] <= w * .3f && max[y] >= w * .65f && pocket)
                lowerPocket++;
            if (y >= h * .8f && max[y] - min[y] >= w * .45f) footWide++;
        }
        return highRows > 0
                && lowRows > 0
                && high / highRows - low / lowRows >= w * .15f
                && upperThin >= Math.max(2, Math.round(h * .2f))
                && upperPocket <= 1
                && lowerPocket >= Math.max(3, Math.round(h * .2f))
                && footWide >= 2;
    }
}

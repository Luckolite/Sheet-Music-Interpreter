// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic glyphs compare actual row scans with the frozen pre-hoist reader. */
public class TupletSixScanParityTest {
    private static void compare(byte[] gray, int width, int left, int top, int w, int h) {
        byte[] saved = gray == null ? null : gray.clone();
        assertEquals(
                Before.matches(gray, width, left, top, w, h),
                TupletSixGlyph.matches(gray, width, left, top, w, h));
        if (gray != null) assertArrayEquals(saved, gray);
    }

    @Test
    public void originalLowerBowlAcrossPaddedOffsetsAndThresholds() {
        int accepted = 0;
        for (int left : new int[] {0, 1, 7})
            for (int top : new int[] {0, 2, 9})
                for (int shade : new int[] {0, 127, 164, 165, 255}) {
                    int width = left + 18 + 5, height = top + 28 + 3;
                    byte[] gray = new byte[width * height];
                    Arrays.fill(gray, (byte) 255);
                    int minX = 18, maxX = -1, minY = 28, maxY = -1;
                    for (int y = 0; y < 28; y++)
                        for (int x = 0; x < 18; x++) {
                            double outer = Math.pow((x - 8) / 6., 2) + Math.pow((y - 18) / 7., 2);
                            double inner = Math.pow((x - 8) / 4., 2) + Math.pow((y - 18) / 5., 2);
                            boolean ink =
                                    outer <= 1 && inner >= 1
                                            || y >= 1
                                                    && y <= 17
                                                    && Math.abs(x - (14 - y * .72)) <= 1.2;
                            if (ink) {
                                gray[(top + y) * width + left + x] = (byte) shade;
                                minX = Math.min(minX, x);
                                maxX = Math.max(maxX, x);
                                minY = Math.min(minY, y);
                                maxY = Math.max(maxY, y);
                            }
                        }
                    compare(gray, width, left + minX, top + minY, maxX - minX + 1, maxY - minY + 1);
                    if (TupletSixGlyph.matches(
                            gray, width, left + minX, top + minY, maxX - minX + 1, maxY - minY + 1))
                        accepted++;
                }
        assertEquals(27, accepted);
    }

    @Test
    public void randomWindowsBlankRowsAndCallerChangesRemainLiteral() {
        Random random = new Random(0x605905L);
        for (int i = 0; i < 96; i++) {
            int w = 4 + random.nextInt(25), h = 9 + random.nextInt(36), left = i % 8, top = i % 5;
            int width = left + w + 3, height = top + h + 2;
            byte[] gray = new byte[width * height];
            random.nextBytes(gray);
            compare(gray, width, left, top, w, h);
            for (int x = left; x < left + w; x++) gray[(top + h / 2) * width + x] = (byte) 255;
            compare(gray, width, left, top, w, h);
            Arrays.fill(gray, (byte) (i % 2 == 0 ? 164 : 165));
            compare(gray, width, left, top, w, h);
        }
    }

    @Test
    public void invalidDimensionsAndOverflowBoundsKeepRejection() {
        byte[] gray = new byte[20 * 30];
        for (int[] args :
                new int[][] {
                    {0, 0, 0, 4, 9},
                    {-1, 0, 0, 4, 9},
                    {20, -1, 0, 4, 9},
                    {20, 0, -1, 4, 9},
                    {20, 0, 0, 3, 9},
                    {20, 0, 0, 4, 8},
                    {20, 17, 0, 4, 9},
                    {20, 0, 22, 4, 9},
                    {20, Integer.MAX_VALUE, 0, 4, 9},
                    {20, 0, Integer.MAX_VALUE, 4, 9},
                    {20, 0, 0, Integer.MAX_VALUE, 9},
                    {20, 0, 0, 4, Integer.MAX_VALUE}
                }) compare(gray, args[0], args[1], args[2], args[3], args[4]);
        compare(null, 20, 0, 0, 4, 9);
        compare(new byte[601], 20, 0, 0, 4, 9);
        compare(new byte[0], 20, 0, 0, 4, 9);
    }

    private static final class Before {
        private Before() {}

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
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if ((gray[(top + y) * width + left + x] & 255) < 165) {
                        min[y] = Math.min(min[y], x);
                        max[y] = x;
                        count[y]++;
                    }
            int upperThin = 0, lowerPocket = 0, upperPocket = 0, footWide = 0;
            double high = 0, low = 0;
            int highRows = 0, lowRows = 0;
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
                int blank = 0, longest = 0;
                for (int x = min[y] + 1; x < max[y]; x++) {
                    if ((gray[(top + y) * width + left + x] & 255) >= 165)
                        longest = Math.max(longest, ++blank);
                    else blank = 0;
                }
                boolean pocket = longest >= Math.max(2, Math.round(w * .25f));
                if (y < h * .4f && pocket) upperPocket++;
                if (y >= h * .45f
                        && y < h * .88f
                        && min[y] <= w * .3f
                        && max[y] >= w * .65f
                        && pocket) lowerPocket++;
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
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Original model-free tile enumeration controls; no segmentation session is created. */
public final class NativeSegmentationTileStartsParity {
    private static final Method STARTS;

    static {
        try {
            STARTS = NativeSegmentation.class.getDeclaredMethod("tileStarts", int.class);
            STARTS.setAccessible(true);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> starts(int size) throws Exception {
        return (List<Integer>) STARTS.invoke(null, size);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0)
            throw new IllegalArgumentException("No models or input arguments accepted");
        if (!visits(384, 513, true)
                .equals(List.of("0:0", "0:64", "192:0", "192:64", "193:0", "193:64")))
            throw new AssertionError("Clamped raster order changed");
        if (!visits(Integer.MIN_VALUE, -1, true).equals(List.of("0:0")))
            throw new AssertionError("Existing invalid-size enumeration changed");
        for (int[] size :
                new int[][] {
                    {32, 96},
                    {319, 317},
                    {320, 320},
                    {321, 319},
                    {384, 513},
                    {513, 515},
                    {2048, 2048},
                    {0, 0},
                    {-1, 33},
                    {33, -1}
                })
            if (!visits(size[0], size[1], false).equals(visits(size[0], size[1], true)))
                throw new AssertionError("Raster sequence changed: " + size[0] + "x" + size[1]);
        var field = NativeSegmentation.class.getDeclaredField("TILE_EDGE_WEIGHTS");
        field.setAccessible(true);
        byte[] weights = (byte[]) field.get(null);
        if (weights.length != 320 * 320)
            throw new AssertionError("Unexpected edge-weight geometry");
        // Distance to the four independently enumerated borders is the legacy definition.
        for (int y = 0; y < 320; y++)
            for (int x = 0; x < 320; x++) {
                int expected = 1;
                while (x >= expected && y >= expected && x < 320 - expected && y < 320 - expected)
                    expected++;
                if ((weights[y * 320 + x] & 255) != expected)
                    throw new AssertionError("Edge ownership changed at " + x + ":" + y);
            }
        if ((weights[159 * 320 + 159] & 255) != 160
                || (weights[160 * 320 + 160] & 255) != 160
                || (weights[128 * 320 + 192] & 255) != 128)
            throw new AssertionError("Unsigned center or overlap weight changed");
        System.out.println(
                "PASS tile-start goldens,10raster sequences and102400 edge-weight controls; models0");
    }

    private static List<String> visits(int width, int height, boolean reuse) throws Exception {
        List<Integer> yStarts = starts(height), xStarts = reuse ? starts(width) : null;
        List<String> result = new ArrayList<>();
        for (int top : yStarts)
            for (int left : reuse ? xStarts : starts(width)) result.add(top + ":" + left);
        return result;
    }
}

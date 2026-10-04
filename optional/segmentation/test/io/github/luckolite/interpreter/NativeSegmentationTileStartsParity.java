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
        System.out.println(
                "PASS original tile-start enumeration goldens and10raster sequences; models0");
    }

    private static List<String> visits(int width, int height, boolean reuse) throws Exception {
        List<Integer> yStarts = starts(height), xStarts = reuse ? starts(width) : null;
        List<String> result = new ArrayList<>();
        for (int top : yStarts)
            for (int left : reuse ? xStarts : starts(width)) result.add(top + ":" + left);
        return result;
    }
}

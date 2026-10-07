// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;

/** Original edge-clipped ovals: an absent search region is an empty candidate set. */
public class ClippedDotSearchTest {
    static final int W = 256, H = 128;
    final byte[] gray = new byte[W * H];

    public ClippedDotSearchTest() {
        Arrays.fill(gray, (byte) 255);
    }

    Object head(int maxX) throws Exception {
        Class<?> type = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        int minX = maxX - 18;
        for (int y = 50; y <= 62; y++)
            for (int x = minX; x <= maxX; x++)
                if (Math.pow((x - (maxX - 9)) / 9., 2) + Math.pow((y - 56) / 6., 2) <= 1)
                    gray[y * W + x] = 60;
        return ctor.newInstance(150, minX, maxX, 50, 62, (float) (maxX - 9), 56f);
    }

    int count(Object head, float gap, byte[] pixels, boolean hollow) throws Exception {
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "countAugmentationDots",
                        List.class,
                        head.getClass(),
                        float.class,
                        byte[].class,
                        int.class,
                        int.class,
                        boolean.class);
        method.setAccessible(true);
        return (int) method.invoke(null, List.of(), head, gap, pixels, W, H, hollow);
    }

    @Test
    public void filledHeadAtRightEdgeHasNoDotSearch() throws Exception {
        Object head = head(W - 1);
        for (float gap : new float[] {12f, 16f, 24f})
            assertEquals(0, count(head, gap, gray, false));
    }

    @Test
    public void hollowHeadAtRightEdgeHasNoDotSearch() throws Exception {
        Object head = head(W - 1);
        assertEquals(0, count(head, 16, gray, true));
    }

    @Test
    public void clippedAnalysisDoesNotMutateCallerPixels() throws Exception {
        Object head = head(W - 1);
        byte[] before = gray.clone();
        count(head, 16, gray, false);
        assertArrayEquals(before, gray);
    }

    @Test
    public void onePixelSlotIsAlsoAnEmptyCandidateSet() throws Exception {
        Object head = head(W - 2);
        assertEquals(0, count(head, 12, gray, false));
    }

    @Test
    public void absentRasterKeepsImmutableCallerCandidatesSafe() throws Exception {
        Object head = head(W - 1);
        assertEquals(0, count(head, 16, null, false));
    }
}

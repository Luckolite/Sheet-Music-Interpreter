// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural rules and scan-row interruptions; no score pixels. */
public class DenseInterruptedBarlineTest {
    static boolean bar(int scale, int paper, int ink, int kind) throws Exception {
        int width = 260 * scale, height = 200 * scale, column = 120 * scale;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) paper);
        int[] rows = {80 * scale, 96 * scale, 112 * scale, 128 * scale, 144 * scale};
        for (int y : rows)
            for (int x = 20 * scale; x < 240 * scale; x++) gray[y * width + x] = (byte) ink;
        if (kind != 2)
            for (int y = 77 * scale; y <= 147 * scale; y++)
                for (int x = column - scale; x <= column + scale; x++)
                    gray[y * width + x] = (byte) ink;
        if (kind == 0)
            for (int y = 110 * scale; y < 114 * scale; y++)
                for (int x = column - 3 * scale; x <= column + 3 * scale; x++)
                    gray[y * width + x] = (byte) paper;
        if (kind == 1)
            for (int y = 100 * scale; y < 106 * scale; y++)
                for (int x = column - 3 * scale; x <= column + 3 * scale; x++)
                    gray[y * width + x] = (byte) paper;
        byte[] before = gray.clone();
        var method =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "rawBarlineSpansStaff",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        float.class,
                        float.class);
        method.setAccessible(true);
        boolean result =
                (boolean)
                        method.invoke(null, gray, width, height, column, rows, 16f * scale, 0f, 0f);
        assertArrayEquals(before, gray);
        return result;
    }

    @Test
    public void denseScanBreakAtMiddleRuleRetainsAllFourSpaceProofs() throws Exception {
        for (int scale : new int[] {1, 2, 3}) assertTrue("scale" + scale, bar(scale, 255, 30, 0));
    }

    @Test
    public void sameDenseBoundaryWorksOnGrayPaper() throws Exception {
        assertTrue(bar(1, 210, 80, 0));
    }

    @Test
    public void denseAggregateCannotReplaceOneMissingStaffSpace() throws Exception {
        for (int scale : new int[] {1, 2, 3}) assertFalse(bar(scale, 255, 30, 1));
    }

    @Test
    public void rulesAloneCannotSupplyVerticalOwnership() throws Exception {
        assertFalse(bar(1, 255, 30, 2));
    }

    @Test
    public void ordinaryUninterruptedBarRemainsAccepted() throws Exception {
        assertTrue(bar(1, 255, 30, 3));
    }
}

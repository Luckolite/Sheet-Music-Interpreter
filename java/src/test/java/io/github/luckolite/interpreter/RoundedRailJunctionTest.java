// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original staff/rule scenes. No score pixels, coordinates, fonts, or masks are reused. */
public class RoundedRailJunctionTest {
    static final int W = 240, H = 140;
    byte[] gray = new byte[W * H], labels = new byte[W * H];

    public RoundedRailJunctionTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void rect(int l, int r, int t, int b, int value, byte label) {
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                gray[y * W + x] = (byte) value;
                labels[y * W + x] = label;
            }
    }

    void page(boolean leftRail, boolean rightRail, boolean junctions) {
        for (int y : new int[] {40, 54, 68, 82, 96}) rect(10, 230, y - 1, y + 1, 0, (byte) 4);
        if (leftRail) rect(90, 92, 40, 96, 0, (byte) 5);
        if (rightRail) rect(100, 102, 40, 96, 0, (byte) 5);
        if (junctions)
            for (int line = 40; line < 96; line += 14)
                for (int d = 0; d < 3; d++) {
                    int n = 3 - d;
                    rect(93, 92 + n, line + 2 + d, line + 2 + d, 0, (byte) 5);
                    rect(100 - n, 99, line + 2 + d, line + 2 + d, 0, (byte) 5);
                    rect(93, 92 + n, line + 12 - d, line + 12 - d, 0, (byte) 5);
                    rect(100 - n, 99, line + 12 - d, line + 12 - d, 0, (byte) 5);
                }
    }

    boolean rounded() {
        return RoundedMeterCounters.matches(gray, W, H, 100, 38, 98, 14);
    }

    @Test
    public void leftRawBoundarySurvivesRoundedStaffJunctions() throws Exception {
        page(true, true, true);
        assertTrue(ParallelStaffRules.proved(gray, W, H, 100, 38, 98, 14));
        assertTrue(rounded());
        assertNotEquals(Integer.MIN_VALUE, raw(92));
    }

    int raw(int column) throws Exception {
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "rawBarlineColumn",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        return (int)
                m.invoke(null, gray, W, H, column, new int[] {40, 54, 68, 82, 96}, 14f, 0f, 0f);
    }

    @Test
    public void rawBoundarySurvivesRoundedStaffJunctions() throws Exception {
        page(true, true, true);
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "rawBarlineColumn",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        assertNotEquals(
                Integer.MIN_VALUE,
                (int) m.invoke(null, gray, W, H, 100, new int[] {40, 54, 68, 82, 96}, 14f, 0f, 0f));
    }

    @Test
    public void neighboringStraightBarCannotConvertRoundedDigitsToPair() {
        for (int i = 0; i < 4; i++) {
            int cy = 47 + i * 14;
            for (int y = cy - 6; y <= cy + 6; y++)
                for (int x = 88; x <= 108; x++) {
                    boolean outer = Math.pow((x - 98) / 10d, 2) + Math.pow((y - cy) / 6d, 2) <= 1;
                    boolean inner = Math.pow((x - 98) / 6d, 2) + Math.pow((y - cy) / 5d, 2) < 1;
                    if (outer && !inner) gray[y * W + x] = 0;
                }
        }
        rect(114, 115, 40, 96, 0, (byte) 5);
        assertFalse(ParallelStaffRules.proved(gray, W, H, 100, 38, 98, 14));
        assertTrue(rounded());
    }

    @Test
    public void missingRailCannotProvePair() {
        page(true, false, true);
        assertFalse(ParallelStaffRules.proved(gray, W, H, 100, 38, 98, 14));
    }

    @Test
    public void missingStaffSpaceCannotProvePair() {
        page(true, true, true);
        rect(90, 92, 84, 94, 255, (byte) 0);
        assertFalse(ParallelStaffRules.proved(gray, W, H, 100, 38, 98, 14));
    }

    @Test
    public void sharedSolidStrokeCannotProvePair() {
        page(true, true, false);
        rect(93, 99, 40, 96, 0, (byte) 5);
        assertFalse(ParallelStaffRules.proved(gray, W, H, 100, 38, 98, 14));
    }

    @Test
    public void pixelsRemainExact() {
        page(true, true, true);
        var before = gray.clone();
        var prior = labels.clone();
        rounded();
        assertArrayEquals(before, gray);
        assertArrayEquals(prior, labels);
    }
}

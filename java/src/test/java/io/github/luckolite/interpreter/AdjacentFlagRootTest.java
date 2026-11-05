// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original paired root bands shifted one pixel from the stem estimate. */
public class AdjacentFlagRootTest {
    private static final int W = 240, H = 160;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    public AdjacentFlagRootTest() {
        Arrays.fill(gray, (byte) 255);
    }

    private void band(int left, int right, int top, int bottom) {
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) gray[y * W + x] = 35;
    }

    private Object staff() throws Exception {
        var type = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var ctor = type.getDeclaredConstructor(float.class, float.class, float.class);
        ctor.setAccessible(true);
        return ctor.newInstance(80f, 139f, 14.75f);
    }

    private boolean roots() throws Exception {
        var staff = staff();
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "adjacentFlagRoots",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        staff.getClass(),
                        boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, gray, labels, W, H, 100, 35, 70, staff, true);
    }

    private boolean tripleRoots() throws Exception {
        var staff = staff();
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "adjacentTripleFlagRoots",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        staff.getClass(),
                        boolean.class,
                        boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, gray, labels, W, H, 100, 35, 85, staff, true, true);
    }

    @Test
    public void threeThickRootsRetainThirtySecondFlagCount() throws Exception {
        band(102, 105, 40, 45);
        band(102, 105, 55, 60);
        band(102, 105, 70, 75);
        assertTrue(tripleRoots());
    }

    @Test
    public void twoRootsAndAThinStaffRuleDoNotBecomeThreeFlags() throws Exception {
        band(102, 105, 40, 45);
        band(102, 105, 55, 60);
        band(20, 220, 70, 70);
        assertFalse(tripleRoots());
    }

    @Test
    public void onePixelShiftStillHasTwoAdjacentRootColumns() throws Exception {
        band(102, 105, 40, 45);
        band(102, 105, 55, 60);
        var staff = staff();
        var oldProbe =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "thickNonHeadBands",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        staff.getClass(),
                        int.class,
                        boolean.class);
        oldProbe.setAccessible(true);
        int first = (int) oldProbe.invoke(null, gray, labels, W, H, 102, 35, 70, staff, 102, true);
        int second = (int) oldProbe.invoke(null, gray, labels, W, H, 103, 35, 70, staff, 103, true);
        assertFalse(
                "The previous fixed probes must miss this shifted pair", first == 2 && second == 2);
        assertTrue(roots());
    }

    @Test
    public void onlyOneColumnCannotProvePair() throws Exception {
        band(103, 105, 40, 45);
        band(103, 105, 55, 60);
        assertFalse(roots());
    }

    @Test
    public void singleBandCannotProveTwoFlags() throws Exception {
        band(102, 105, 40, 45);
        assertFalse(roots());
    }

    @Test
    public void thinSecondBandCannotProveTwoFlags() throws Exception {
        band(102, 105, 40, 45);
        band(102, 105, 55, 56);
        assertFalse(roots());
    }

    @Test
    public void outerReturnBeyondRootWindowCannotProvePair() throws Exception {
        band(105, 110, 40, 45);
        band(105, 110, 55, 60);
        assertFalse(roots());
    }

    @Test
    public void noteheadInkIsNotFlagRoot() throws Exception {
        band(102, 105, 40, 45);
        band(102, 105, 55, 60);
        for (int y = 55; y <= 60; y++)
            for (int x = 102; x <= 105; x++) labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
        assertFalse(roots());
    }

    @Test
    public void sourceIsNotModified() throws Exception {
        band(102, 105, 40, 45);
        band(102, 105, 55, 60);
        var before = gray.clone();
        var beforeLabels = labels.clone();
        roots();
        assertArrayEquals(before, gray);
        assertArrayEquals(beforeLabels, labels);
    }
}

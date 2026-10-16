// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original compact parallel rails with a blurred channel near one stem. */
public class IndependentGraceProbeTest {
    static final int W = 240, H = 180;
    byte[] gray = new byte[W * H];

    void setup(boolean up, boolean second, int span) {
        Arrays.fill(gray, (byte) 255);
        for (int x = 100; x <= 100 + span; x++)
            for (int y = 60; y <= 71; y++)
                if (y <= 63 || second && (y >= 68 || x >= 100 + span - 6))
                    gray[(up ? y : 180 - y) * W + x] = 0;
    }

    int count(boolean up, int span) {
        return PairedGraceBeamInk.count(
                gray,
                W,
                H,
                new int[] {100, up ? 60 : 120, up ? -1 : 1},
                new int[] {100 + span, up ? 60 : 120, up ? -1 : 1},
                14f);
    }

    @Test
    public void upwardFractionalSpanRetainsThreeSeparateCoreWitnesses() {
        setup(true, true, 19);
        assertEquals(2, count(true, 19));
    }

    @Test
    public void downwardFractionalSpanRetainsThreeSeparateCoreWitnesses() {
        setup(false, true, 19);
        assertEquals(2, count(false, 19));
    }

    @Test
    public void singleRailCannotBecomeDouble() {
        setup(true, false, 19);
        assertEquals(0, count(true, 19));
    }

    @Test
    public void narrowerPairStillHasIndependentWitnesses() {
        setup(true, true, 18);
        assertEquals(2, count(true, 18));
    }

    @Test
    public void overlappingProbeColumnsAreExcluded() {
        setup(true, true, 14);
        assertEquals(0, count(true, 14));
    }

    @Test
    public void reversedPairKeepsIndependentWitnesses() {
        setup(true, true, 18);
        assertEquals(
                2,
                PairedGraceBeamInk.count(
                        gray, W, H, new int[] {118, 60, -1}, new int[] {100, 60, -1}, 14f));
    }

    @Test
    public void pixelsAndStemEndpointsStayExact() {
        setup(true, true, 19);
        byte[] before = gray.clone();
        int[] a = {100, 60, -1}, b = {119, 60, -1};
        PairedGraceBeamInk.count(gray, W, H, a, b, 14f);
        assertArrayEquals(before, gray);
        assertArrayEquals(new int[] {100, 60, -1}, a);
        assertArrayEquals(new int[] {119, 60, -1}, b);
    }
}

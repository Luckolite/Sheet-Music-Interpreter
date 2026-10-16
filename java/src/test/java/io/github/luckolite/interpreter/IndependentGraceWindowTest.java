// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original parallel rails whose white channel closes near the first stem. */
public class IndependentGraceWindowTest {
    static final int W = 240, H = 180;
    byte[] gray = new byte[W * H];

    void setup(boolean up, boolean doubleBeam, int bridgeEnd) {
        Arrays.fill(gray, (byte) 255);
        for (int x = 100; x <= 128; x++)
            for (int y = 60; y <= 71; y++)
                if (y <= 63 || doubleBeam && (y >= 68 || x <= bridgeEnd))
                    gray[(up ? y : 180 - y) * W + x] = 0;
    }

    int count(boolean up, boolean reversed) {
        int[] a = {100, up ? 60 : 120, up ? -1 : 1}, b = {128, up ? 60 : 120, up ? -1 : 1};
        return PairedGraceBeamInk.count(gray, W, H, reversed ? b : a, reversed ? a : b, 14f);
    }

    @Test
    public void rightChannelRecoversUpwardPair() {
        setup(true, true, 116);
        assertEquals(2, count(true, false));
    }

    @Test
    public void rightChannelRecoversDownwardPair() {
        setup(false, true, 116);
        assertEquals(2, count(false, false));
    }

    @Test
    public void reversedPairRecoversSameRails() {
        setup(true, true, 116);
        assertEquals(2, count(true, true));
    }

    @Test
    public void singleRailRemainsSingle() {
        setup(true, false, 116);
        assertEquals(0, count(true, false));
    }

    @Test
    public void insufficientIndependentChannelIsRejected() {
        setup(true, true, 123);
        assertEquals(0, count(true, false));
    }

    @Test
    public void inputRemainsExact() {
        setup(true, true, 116);
        byte[] before = gray.clone();
        count(true, false);
        assertArrayEquals(before, gray);
    }
}

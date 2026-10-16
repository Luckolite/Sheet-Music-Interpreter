// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original double rails with gray antialiasing inside their channel. */
public class BlurredGraceChannelTest {
    static final int W = 240, H = 180;
    byte[] gray = new byte[W * H];

    void setup(boolean up, boolean pair, int channel) {
        Arrays.fill(gray, (byte) 255);
        for (int x = 100; x <= 128; x++)
            for (int y = 60; y <= 71; y++) {
                int value = y <= 63 || pair && y >= 68 ? 30 : pair ? channel : 255;
                gray[(up ? y : 180 - y) * W + x] = (byte) value;
            }
    }

    int count(boolean up, boolean reverse) {
        int[] a = {100, up ? 60 : 120, up ? -1 : 1}, b = {128, up ? 60 : 120, up ? -1 : 1};
        return PairedGraceBeamInk.count(gray, W, H, reverse ? b : a, reverse ? a : b, 14f);
    }

    @Test
    public void upwardGrayChannelRetainsTwoRails() {
        setup(true, true, 80);
        assertEquals(2, count(true, false));
    }

    @Test
    public void downwardGrayChannelRetainsTwoRails() {
        setup(false, true, 80);
        assertEquals(2, count(false, false));
    }

    @Test
    public void reverseOrderRetainsTwoRails() {
        setup(true, true, 80);
        assertEquals(2, count(true, true));
    }

    @Test
    public void solidConnectorCannotSplit() {
        setup(true, true, 30);
        assertEquals(0, count(true, false));
    }

    @Test
    public void singleRailRemainsSingle() {
        setup(true, false, 80);
        assertEquals(0, count(true, false));
    }

    @Test
    public void grayInputPreserved() {
        setup(true, true, 80);
        byte[] before = gray.clone();
        count(true, false);
        assertArrayEquals(before, gray);
    }
}

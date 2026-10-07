// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class DetachedStemInkBandTest {
    static final int W = 80, H = 100;

    byte[] paper() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        return gray;
    }

    @Test
    public void detachedLowerArmCannotSupplyAnotherBeam() {
        assertTrue(DetachedStemInkBand.beyondWhiteGap(paper(), W, H, 40, 50, 58, false, 16, 205));
    }

    @Test
    public void detachedUpperArmCannotSupplyAnotherBeam() {
        assertTrue(DetachedStemInkBand.beyondWhiteGap(paper(), W, H, 40, 50, 42, true, 16, 205));
    }

    @Test
    public void continuousFadedShaftRetainsItsConnectedInk() {
        byte[] gray = paper();
        for (int y = 50; y <= 58; y++) gray[y * W + 40] = (byte) 190;
        assertFalse(DetachedStemInkBand.beyondWhiteGap(gray, W, H, 40, 50, 58, false, 16, 205));
    }

    @Test
    public void oneWhiteAntialiasRowDoesNotBreakTheConnection() {
        byte[] gray = paper();
        for (int y = 50; y <= 58; y++) gray[y * W + 40] = 30;
        gray[54 * W + 40] = (byte) 250;
        assertFalse(DetachedStemInkBand.beyondWhiteGap(gray, W, H, 40, 50, 58, false, 16, 205));
    }

    @Test
    public void bandsInsideTheStemAreNotRejected() {
        assertFalse(DetachedStemInkBand.beyondWhiteGap(paper(), W, H, 40, 50, 42, false, 16, 205));
        assertFalse(DetachedStemInkBand.beyondWhiteGap(paper(), W, H, 40, 50, 58, true, 16, 205));
    }

    @Test
    public void invalidSourceCannotProveAWhiteGap() {
        assertFalse(DetachedStemInkBand.beyondWhiteGap(null, W, H, 40, 50, 58, false, 16, 205));
        assertFalse(
                DetachedStemInkBand.beyondWhiteGap(new byte[2], W, H, 40, 50, 58, false, 16, 205));
        assertFalse(
                DetachedStemInkBand.beyondWhiteGap(
                        paper(), W, H, 40, 50, 58, false, Float.NaN, 205));
    }

    @Test
    public void pixelsRemainUnchanged() {
        byte[] gray = paper(), before = gray.clone();
        assertTrue(DetachedStemInkBand.beyondWhiteGap(gray, W, H, 40, 50, 58, false, 16, 205));
        assertArrayEquals(before, gray);
    }
}

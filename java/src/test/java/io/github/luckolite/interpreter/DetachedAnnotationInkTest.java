// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DetachedAnnotationInkTest {
    private static final int W = 360, H = 240, G = 16;
    private final byte[] gray = new byte[W * H];

    public DetachedAnnotationInkTest() {
        Arrays.fill(gray, (byte) 255);
    }

    private void rect(int l, int r, int t, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = 0;
    }

    private void accent() {
        for (int x = 160; x <= 184; x++)
            for (int side : new int[] {-1, 1}) {
                int cy = Math.round(110 + side * 7 * (184 - x) / 24f);
                rect(x, x, cy - 1, cy + 1);
            }
    }

    private boolean accentTip() {
        return DetachedAnnotationInk.accent(gray, W, H, 180, 107, 185, 113, G);
    }

    private void bow() {
        rect(160, 164, 60, 110);
        rect(196, 200, 60, 110);
        rect(160, 200, 60, 64);
    }

    private boolean bowTip() {
        return DetachedAnnotationInk.downBow(gray, W, H, 196, 105, 201, 112, G);
    }

    @Test
    public void accentTipIsNotAnotherNote() {
        accent();
        assertTrue(accentTip());
    }

    @Test
    public void filledOvalIsNotAccent() {
        for (int y = 102; y <= 118; y++)
            for (int x = 160; x <= 184; x++)
                if ((x - 172) * (x - 172) / 144d + (y - 110) * (y - 110) / 64d <= 1)
                    gray[y * W + x] = 0;
        assertFalse(accentTip());
    }

    @Test
    public void oneDiagonalStrokeIsNotAccent() {
        for (int x = 160; x <= 184; x++) rect(x, x, 103 + (x - 160) / 3, 105 + (x - 160) / 3);
        assertFalse(accentTip());
    }

    @Test
    public void downBowEndpointIsNotAnotherNote() {
        bow();
        assertTrue(bowTip());
    }

    @Test
    public void thinSlurTouchingBowDoesNotExtendItsLeg() {
        bow();
        rect(130, 240, 109, 109);
        assertTrue(bowTip());
    }

    @Test
    public void oneStemAndCapAreNotDownBow() {
        rect(196, 200, 60, 110);
        rect(160, 200, 60, 64);
        assertFalse(bowTip());
    }

    @Test
    public void capIsNotAMistakenBottomEndpoint() {
        bow();
        assertFalse(DetachedAnnotationInk.downBow(gray, W, H, 176, 60, 181, 64, G));
    }

    @Test
    public void filledBoxIsNotDownBow() {
        rect(160, 200, 60, 110);
        assertFalse(bowTip());
    }

    @Test
    public void invalidImageIsRejected() {
        assertFalse(DetachedAnnotationInk.accent(null, W, H, 180, 107, 185, 113, G));
    }

    @Test
    public void sourceImageIsNotChanged() {
        bow();
        var copy = gray.clone();
        bowTip();
        assertArrayEquals(copy, gray);
    }

    @Test
    public void lateThresholdComponentsUseOnlyTheirNewQueuePrefix() {
        for (int ink : new int[] {81, 111, 141, 181, 205}) {
            Arrays.fill(gray, (byte) 255);
            accent();
            for (int i = 0; i < gray.length; i++) if (gray[i] == 0) gray[i] = (byte) ink;
            // This larger early dark component is disjoint from the pale accent tip.
            rect(185, 200, 80, 107);
            byte[] before = gray.clone();
            assertTrue(accentTip());
            assertTrue(accentTip());
            assertArrayEquals(before, gray);

            Arrays.fill(gray, (byte) 255);
            bow();
            for (int i = 0; i < gray.length; i++) if (gray[i] == 0) gray[i] = (byte) ink;
            // Its one surviving thick center is disjoint from the pale bow leg.
            rect(200, 202, 111, 113);
            before = gray.clone();
            assertTrue(bowTip());
            assertTrue(bowTip());
            assertArrayEquals(before, gray);
        }
    }

    @Test
    public void rejectedAndChangedRasterCannotReuseAnEarlierComponent() {
        bow();
        byte[] before = gray.clone();
        assertTrue(bowTip());
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 255);
        rect(160, 200, 60, 110);
        before = gray.clone();
        assertFalse(bowTip());
        assertFalse(bowTip());
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 255);
        accent();
        before = gray.clone();
        assertTrue(accentTip());
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 255);
        before = gray.clone();
        assertFalse(accentTip());
        assertFalse(bowTip());
        assertArrayEquals(before, gray);
    }
}

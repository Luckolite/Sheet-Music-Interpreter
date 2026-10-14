// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MeterCrossingStrokeTest {
    static final int W = 300, H = 220;

    byte[] page() {
        byte[] a = new byte[W * H];
        Arrays.fill(a, (byte) 255);
        for (int y = 60; y <= 124; y += 16) for (int x = 20; x < 280; x++) a[y * W + x] = 0;
        return a;
    }

    void stroke(byte[] a, int l, int r, float slope, int radius) {
        for (int x = l; x < r; x++) {
            int y = Math.round(82 + slope * (x - 124));
            for (int dy = -radius; dy <= radius; dy++) a[(y + dy) * W + x] = 0;
        }
    }

    boolean[] mask(byte[] a) {
        return MeterCrossingStroke.mask(a, W, H, 100, 57, 148, 128, 60, 16);
    }

    @Test
    public void supportedRisingStroke() {
        var a = page();
        stroke(a, 74, 174, -.16f, 1);
        assertNotNull(mask(a));
    }

    @Test
    public void supportedFallingStroke() {
        var a = page();
        stroke(a, 74, 174, .16f, 1);
        assertNotNull(mask(a));
    }

    @Test
    public void staffRulesAloneDoNotQualify() {
        assertNull(mask(page()));
    }

    @Test
    public void interiorDiagonalHasNoOutsideProof() {
        var a = page();
        stroke(a, 100, 148, -.16f, 1);
        assertNull(mask(a));
    }

    @Test
    public void oneSidedStrokeHasNoOutsideProof() {
        var a = page();
        stroke(a, 74, 148, -.16f, 1);
        assertNull(mask(a));
    }

    @Test
    public void disconnectedOutsideStrokesCannotCrossBlankCrop() {
        var a = page();
        stroke(a, 74, 98, -.16f, 1);
        stroke(a, 150, 174, -.16f, 1);
        assertNull(mask(a));
    }

    @Test
    public void broadBandCannotBeErased() {
        var a = page();
        stroke(a, 74, 174, -.16f, 6);
        assertNull(mask(a));
    }

    @Test
    public void noSourceMutation() {
        var a = page();
        stroke(a, 74, 174, -.16f, 1);
        var b = a.clone();
        mask(a);
        assertArrayEquals(b, a);
    }

    @Test
    public void invalidInputRejected() {
        assertNull(MeterCrossingStroke.mask(null, W, H, 100, 57, 148, 128, 60, 16));
        assertNull(MeterCrossingStroke.mask(page(), W, H, 100, 57, 148, 128, Float.NaN, 16));
    }

    @Test
    public void curvedStrokeDoesNotProveStraightCrossing() {
        var a = page();
        for (int x = 74; x < 174; x++) {
            int y = Math.round(76 + .009f * (x - 124) * (x - 124));
            for (int dy = -1; dy <= 1; dy++) a[(y + dy) * W + x] = 0;
        }
        assertNull(mask(a));
    }

    @Test
    public void conflictingCrossingsAreAmbiguous() {
        var a = page();
        stroke(a, 74, 174, -.16f, 1);
        stroke(a, 74, 174, .16f, 1);
        assertNull(mask(a));
    }

    @Test
    public void parallelCrossingsAreAmbiguous() {
        var a = page();
        stroke(a, 74, 174, -.16f, 1);
        for (int x = 74; x < 174; x++) {
            int y = Math.round(92 - .16f * (x - 124));
            for (int dy = -1; dy <= 1; dy++) a[(y + dy) * W + x] = 0;
        }
        assertNull(mask(a));
    }

    @Test
    public void rightOnlySupportIsRejected() {
        var a = page();
        stroke(a, 100, 174, -.16f, 1);
        assertNull(mask(a));
    }

    @Test
    public void insufficientImageMarginIsRejected() {
        assertNull(MeterCrossingStroke.mask(page(), W, H, 4, 57, 52, 128, 60, 16));
    }
}

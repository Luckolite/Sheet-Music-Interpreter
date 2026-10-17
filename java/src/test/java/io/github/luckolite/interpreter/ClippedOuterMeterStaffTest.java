// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original procedural complete rails, no score pixels or copied glyphs. */
public class ClippedOuterMeterStaffTest {
    static final int W = 640, H = 300;

    static byte[] rails(int count) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int n = 0; n < count; n++)
            for (int x = 60; x < 580; x++) g[(100 + n * 14) * W + x] = 20;
        digits(g, 100, 14);
        return g;
    }

    static void digits(byte[] g, int first, int gap) {
        for (int base : new int[] {first, first + 2 * gap})
            for (int y = base + 1; y < base + 2 * gap - 1; y++)
                for (int x = 310; x <= 310 + gap; x++) {
                    int row = y - base;
                    if (x >= 310 + gap - 2
                            || Math.abs(x - (310 + gap - row / 2)) <= 1
                            || row >= gap && row <= gap + 2) g[y * W + x] = 20;
                }
    }

    static MeterChangeDetector.Crop top() {
        return new MeterChangeDetector.Crop(300, 105, 340, 170, 108, 14);
    }

    @Test
    public void twoCompleteWitnessesRecoverClippedTop() {
        var c = top();
        var r = MeterCropStaff.refine(rails(5), W, H, c);
        assertEquals(100, r.firstLine());
        assertTrue(r.top() < 100);
        assertEquals(c.left(), r.left());
        assertEquals(c.right(), r.right());
    }

    @Test
    public void twoCompleteWitnessesRecoverClippedBottom() {
        var c = new MeterChangeDetector.Crop(300, 87, 340, 151, 92, 14);
        var r = MeterCropStaff.refine(rails(5), W, H, c);
        assertEquals(100, r.firstLine());
        assertTrue(r.bottom() > 156);
    }

    @Test
    public void unClippedDistantHintRemainsRejected() {
        var c = new MeterChangeDetector.Crop(300, 94, 340, 165, 108, 14);
        assertEquals(c, MeterCropStaff.refine(rails(5), W, H, c));
    }

    @Test
    public void fourRailsDoNotProveStaff() {
        var c = top();
        assertEquals(c, MeterCropStaff.refine(rails(4), W, H, c));
    }

    @Test
    public void blankDoesNotMoveCrop() {
        var c = top();
        assertEquals(c, MeterCropStaff.refine(rails(0), W, H, c));
    }

    @Test
    public void oneSideOfStaffInsufficient() {
        var g = rails(5);
        for (int y = 0; y < H; y++) Arrays.fill(g, y * W + 350, (y + 1) * W, (byte) 255);
        var c = top();
        assertEquals(c, MeterCropStaff.refine(g, W, H, c));
    }

    @Test
    public void excessivePhaseRemainsRejected() {
        var c = new MeterChangeDetector.Crop(300, 110, 340, 180, 113, 14);
        assertEquals(c, MeterCropStaff.refine(rails(5), W, H, c));
    }

    @Test
    public void sourcePixelsUnchanged() {
        var g = rails(5);
        var copy = g.clone();
        MeterCropStaff.refine(g, W, H, top());
        assertArrayEquals(copy, g);
    }

    @Test
    public void scaleAndRasterPhasePreserved() {
        for (int gap : new int[] {10, 14, 20})
            for (int phase : new int[] {0, 1}) {
                byte[] g = new byte[W * H];
                Arrays.fill(g, (byte) 255);
                int first = 80 + phase;
                for (int n = 0; n < 5; n++)
                    for (int x = 60; x < 580; x++) g[(first + n * gap) * W + x] = 20;
                digits(g, first, gap);
                var c =
                        new MeterChangeDetector.Crop(
                                300,
                                first + Math.round(gap * .36f),
                                340,
                                first + 5 * gap,
                                first + Math.round(gap * .6f),
                                gap);
                var r = MeterCropStaff.refine(g, W, H, c);
                assertEquals("gap=" + gap + " phase=" + phase, first, r.firstLine());
                assertTrue(r.top() < first);
            }
    }

    @Test
    public void excessiveClipIsNotExtended() {
        var c = new MeterChangeDetector.Crop(300, 108, 340, 170, 108, 14);
        assertEquals(c, MeterCropStaff.refine(rails(5), W, H, c));
    }

    static void eraseBody(byte[] g, int first, int last) {
        for (int y = first; y < last; y++)
            for (int x = 300; x < 340; x++) if ((y - 100) % 14 != 0) g[y * W + x] = (byte) 255;
    }

    @Test
    public void emptyRailsCannotRecoverDeepCrop() {
        var g = rails(5);
        eraseBody(g, 96, 165);
        var c = top();
        assertEquals(c, MeterCropStaff.refine(g, W, H, c));
    }

    @Test
    public void oneNumeralBandCannotRecoverDeepCrop() {
        var g = rails(5);
        eraseBody(g, 128, 165);
        var c = top();
        assertEquals(c, MeterCropStaff.refine(g, W, H, c));
    }

    @Test
    public void twoThinShaftsCannotRecoverDeepCrop() {
        var g = rails(5);
        eraseBody(g, 96, 165);
        for (int y = 101; y < 155; y++) g[y * W + 320] = 20;
        var c = top();
        assertEquals(c, MeterCropStaff.refine(g, W, H, c));
    }
}

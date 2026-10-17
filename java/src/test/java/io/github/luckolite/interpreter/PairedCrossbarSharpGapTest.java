// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

/** Original sharp and note drawings with independently faded spines. */
public class PairedCrossbarSharpGapTest {
    private SharpPitchAlignmentTest.Page page(int headY, boolean rightSpine, int shade) {
        var p = new SharpPitchAlignmentTest.Page(headY, 124);
        for (int i = 0; i < p.gray.length; i++)
            if ((p.gray[i] & 255) == 255) p.gray[i] = (byte) 240;
        for (int y = 102; y <= 146; y++)
            for (int x = 248; x <= 263; x++) {
                p.labels[y * 420 + x] = 0;
                p.gray[y * 420 + x] = (byte) 240;
            }
        for (int x : new int[] {251, 252, 259, 260})
            if (rightSpine || x < 255)
                for (int y = 106; y <= 142; y++) p.gray[y * 420 + x] = (byte) shade;
        for (int cy : new int[] {117, 131})
            for (int y = cy - 1; y <= cy + 1; y++)
                for (int x = 248; x <= 263; x++) {
                    p.labels[y * 420 + x] = 3;
                    p.gray[y * 420 + x] = 60;
                }
        return p;
    }

    private SharpPitchAlignmentTest.Page broken(int head, int length, boolean both) {
        var p = page(head, true, 80);
        for (int y = 122; y < 122 + length; y++)
            for (int x : new int[] {251, 252, 259, 260})
                if (both || x > 255) p.gray[y * 420 + x] = (byte) 240;
        return p;
    }

    @Test
    public void pairedBarsRecoverOneBrokenSpine() {
        var p = broken(124, 5, false);
        assertEquals(1, p.accidental(p.gray));
    }

    @Test
    public void pairedBarsRecoverBothBrokenSpines() {
        var p = broken(124, 5, true);
        assertEquals(1, p.accidental(p.gray));
    }

    @Test
    public void longerBreakIsNotInvented() {
        var p = broken(124, 8, false);
        assertNotEquals(1, p.accidental(p.gray));
    }

    @Test
    public void adjacentPitchRemainsIndependent() {
        var p = broken(132, 5, false);
        assertNotEquals(1, p.accidental(p.gray));
    }

    @Test
    public void missingCrossbarCannotQualify() {
        var p = broken(124, 5, false);
        for (int y = 130; y <= 132; y++)
            for (int x = 248; x <= 263; x++) {
                p.labels[y * 420 + x] = 0;
                p.gray[y * 420 + x] = (byte) 240;
            }
        assertNotEquals(1, p.accidental(p.gray));
    }

    @Test
    public void missingSpineCannotQualify() {
        var p = page(124, false, 239);
        assertNotEquals(1, p.accidental(p.gray));
    }

    @Test
    public void inputsRetainAllPixels() {
        var p = broken(124, 5, false);
        var l = p.labels.clone();
        var g = p.gray.clone();
        p.accidental(p.gray);
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original thick diagonal crosses and negative geometric controls. */
public class HeavyDoubleSharpTest {
    byte[] cross(boolean split) {
        int w = 18;
        byte[] a = new byte[w * w];
        for (int y = 0; y < w; y++)
            for (int x = 0; x < w; x++)
                if (Math.abs(x - y) <= 4 || Math.abs(x + y - 17) <= 4)
                    a[y * w + x] = (byte) (split && x < 8 ? 3 : 5);
        return a;
    }

    @Test
    public void heavyArmsKeepNarrowNotches() {
        assertTrue(DoubleSharpGlyph.matches(cross(false), 18, 18, 0, 0, 17, 17, (byte) 5, 15));
    }

    @Test
    public void mixedAccidentalLabelsAreOneGlyph() {
        assertTrue(DoubleSharpGlyph.matches(cross(true), 18, 18, 0, 0, 17, 17, (byte) 0, 15));
    }

    @Test
    public void backgroundIsNotUnionInk() {
        assertFalse(DoubleSharpGlyph.matches(new byte[324], 18, 18, 0, 0, 17, 17, (byte) 0, 15));
    }

    @Test
    public void filledSquareIsNotDoubleSharp() {
        byte[] p = new byte[324];
        java.util.Arrays.fill(p, (byte) 5);
        assertFalse(DoubleSharpGlyph.matches(p, 18, 18, 0, 0, 17, 17, (byte) 5, 15));
    }

    @Test
    public void missingLowerArmsCannotPass() {
        byte[] p = cross(false);
        for (int y = 12; y < 18; y++) for (int x = 0; x < 18; x++) p[y * 18 + x] = 0;
        assertFalse(DoubleSharpGlyph.matches(p, 18, 18, 0, 0, 17, 17, (byte) 5, 15));
    }

    @Test
    public void rawWaistSurvivesStemClassification() {
        byte[] g = new byte[324];
        var p = cross(false);
        for (int i = 0; i < g.length; i++) g[i] = (byte) (p[i] == 0 ? 255 : 0);
        assertTrue(DoubleSharpGlyph.matchesRaw(g, 18, 18, 0, 0, 17, 17, 15));
    }

    byte[] compact(boolean rule, boolean lower) {
        byte[] g = new byte[40 * 40];
        java.util.Arrays.fill(g, (byte) 255);
        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                if (Math.abs(x - y) <= 1 || Math.abs(x + y - 8) <= 1)
                    if (lower || y < 6) g[(15 + y) * 40 + 15 + x] = 0;
        if (rule) for (int x = 3; x < 35; x++) for (int y = 18; y <= 20; y++) g[y * 40 + x] = 0;
        return g;
    }

    @Test
    public void staffObscuredCompactWaistStillHasFourArms() {
        assertTrue(DoubleSharpGlyph.matchesRaw(compact(true, true), 40, 40, 15, 15, 23, 23, 13));
    }

    @Test
    public void staffRuleCannotSupplyMissingLowerArms() {
        assertFalse(DoubleSharpGlyph.matchesRaw(compact(true, false), 40, 40, 15, 15, 23, 23, 13));
    }

    @Test
    public void rawRecognitionPreservesInput() {
        var g = compact(true, true);
        var before = g.clone();
        DoubleSharpGlyph.matchesRaw(g, 40, 40, 15, 15, 23, 23, 13);
        assertArrayEquals(before, g);
    }

    @Test
    public void croppedSpineCannotMasqueradeAsCompactCross() {
        var g = compact(true, true);
        for (int y = 6; y <= 15; y++) g[y * 40 + 15] = 0;
        assertFalse(DoubleSharpGlyph.matchesRaw(g, 40, 40, 15, 15, 23, 23, 13));
    }

    @Test
    public void shadedPaperCannotSupplyCrossArms() {
        var g = compact(true, false);
        for (int i = 0; i < g.length; i++) g[i] = (byte) (g[i] == 0 ? 65 : 175);
        assertFalse(DoubleSharpGlyph.matchesRaw(g, 40, 40, 15, 15, 23, 23, 13));
    }

    @Test
    public void completeLabelAndRawOutcomesRereadTheirCallers() {
        byte[] labels = cross(true), before = labels.clone();
        assertTrue(DoubleSharpGlyph.matches(labels, 18, 18, 0, 0, 17, 17, (byte) 0, 15));
        assertTrue(DoubleSharpGlyph.matches(labels, 18, 18, 0, 0, 17, 17, (byte) 0, 15));
        assertArrayEquals(before, labels);
        java.util.Arrays.fill(labels, (byte) 0);
        assertFalse(DoubleSharpGlyph.matches(labels, 18, 18, 0, 0, 17, 17, (byte) 0, 15));
        assertArrayEquals(new byte[labels.length], labels);
        byte[] raw = compact(true, true);
        before = raw.clone();
        assertTrue(DoubleSharpGlyph.matchesRaw(raw, 40, 40, 15, 15, 23, 23, 13));
        assertTrue(DoubleSharpGlyph.matchesRaw(raw, 40, 40, 15, 15, 23, 23, 13));
        assertArrayEquals(before, raw);
        java.util.Arrays.fill(raw, (byte) 255);
        before = raw.clone();
        assertFalse(DoubleSharpGlyph.matchesRaw(raw, 40, 40, 15, 15, 23, 23, 13));
        assertArrayEquals(before, raw);
    }

    @Test
    public void boundedNullShortAndNonfiniteRoutesKeepOriginalGuards() {
        assertFalse(DoubleSharpGlyph.matchesRaw(null, 40, 40, 15, 15, 23, 23, 13));
        assertFalse(DoubleSharpGlyph.matches(null, 4, 4, 0, 0, 3, 3, (byte) 0, Float.NaN));
        try {
            DoubleSharpGlyph.matches(null, 18, 18, 0, 0, 17, 17, (byte) 0, 15);
            fail("the valid label crop retains its original null error");
        } catch (NullPointerException expected) {
        }
        byte[] shortRaster = {(byte) 231};
        try {
            DoubleSharpGlyph.matches(shortRaster, 18, 18, 0, 0, 17, 17, (byte) 0, 15);
            fail("the original label read must reject a short raster");
        } catch (ArrayIndexOutOfBoundsException expected) {
            assertEquals((byte) 231, shortRaster[0]);
        }
        try {
            DoubleSharpGlyph.matchesRaw(shortRaster, 40, 40, 15, 15, 23, 23, 13);
            fail("the original local-paper read must reject a short raster");
        } catch (ArrayIndexOutOfBoundsException expected) {
            assertEquals((byte) 231, shortRaster[0]);
        }
        byte[] emptyLabels = new byte[324], white = new byte[1600];
        java.util.Arrays.fill(white, (byte) 255);
        byte[] before = white.clone();
        for (float gap :
                new float[] {
                    -0f, 0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
                }) {
            assertFalse(DoubleSharpGlyph.matches(emptyLabels, 18, 18, 0, 0, 17, 17, (byte) 0, gap));
            assertFalse(DoubleSharpGlyph.matchesRaw(white, 40, 40, 15, 15, 23, 23, gap));
        }
        assertArrayEquals(before, white);
        assertArrayEquals(new byte[324], emptyLabels);
    }
}

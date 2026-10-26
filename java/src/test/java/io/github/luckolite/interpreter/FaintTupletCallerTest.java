// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

/** Caller-only counterexample compiles against both the original and successor APIs. */
public class FaintTupletCallerTest {
    static byte[] faint() throws Exception {
        byte[] g = InStaffPrimaryBeamContextTest.ink(true, true, 3);
        for (int cx : new int[] {180, 245, 310})
            for (int y = 120; y < 166; y++)
                for (int x = cx - 11; x <= cx - 9; x++)
                    if ((g[y * 600 + x] & 255) < 165 && y != 140 && y != 156)
                        g[y * 600 + x] = (byte) 180;
        return g;
    }

    static byte[] oneLevel(int digit) throws Exception {
        byte[] g = InStaffPrimaryBeamContextTest.ink(true, false, 3);
        for (int y : new int[] {188, 204})
            for (int x = 20; x <= 580; x++) g[y * 600 + x] = (byte) 255;
        var glyph = CurledTupletThreeTest.glyph(digit, 22, .5f, .15f, .8f);
        for (int y = 0; y < 100; y++)
            for (int x = 0; x < 100; x++) {
                int xx = 229 + x - glyph.left(), yy = 187 + y - glyph.top();
                if (xx < 0 || xx >= 600 || yy < 0 || yy >= 320) continue;
                int v = (glyph.gray()[y * 100 + x] & 255) < 165 ? 194 : 255;
                g[yy * 600 + xx] = (byte) Math.min(g[yy * 600 + xx] & 255, v);
            }
        return g;
    }

    @Test
    public void faintRealConnectedHeadsRetainThePrintedThree() throws Exception {
        var g = faint();
        var copy = g.clone();
        var r =
                TripletRhythmDetector.apply(
                        InStaffPrimaryBeamContextTest.notes(),
                        InStaffPrimaryBeamContextTest.BARS,
                        g,
                        600,
                        320);
        for (var n : r) assertEquals(3, n.tupletDivisor());
        assertArrayEquals(copy, g);
    }

    @Test
    public void realPaperBreakRefusesTheSamePrintedThree() throws Exception {
        var g = faint();
        for (int y = 130; y <= 132; y++)
            for (int x = 168; x <= 172; x++) g[y * 600 + x] = (byte) 255;
        var n = InStaffPrimaryBeamContextTest.notes();
        assertEquals(
                n, TripletRhythmDetector.apply(n, InStaffPrimaryBeamContextTest.BARS, g, 600, 320));
    }

    @Test
    public void independentLiteralWitnessSupportsASingleThresholdThree() throws Exception {
        var r =
                TripletRhythmDetector.apply(
                        InStaffPrimaryBeamContextTest.notes(),
                        InStaffPrimaryBeamContextTest.BARS,
                        oneLevel(3),
                        600,
                        320);
        for (var n : r) assertEquals(3, n.tupletDivisor());
    }

    @Test
    public void faintOtherDigitsCannotBorrowThePhysicalGroup() throws Exception {
        var notes = InStaffPrimaryBeamContextTest.notes();
        for (int digit : new int[] {0, 1, 2, 4, 5, 6, 7, 8, 9})
            assertEquals(
                    "digit=" + digit,
                    notes,
                    TripletRhythmDetector.apply(
                            notes, InStaffPrimaryBeamContextTest.BARS, oneLevel(digit), 600, 320));
    }

    @Test
    public void mixedStrengthFourCrossbarsRemainContradictions() throws Exception {
        var notes = InStaffPrimaryBeamContextTest.notes();
        for (int size : new int[] {14, 22, 30})
            for (float phase : new float[] {.25f, .5f})
                for (float shear : new float[] {-.15f, .15f}) {
                    var glyph = CurledTupletThreeTest.glyph(4, size, phase, shear, .8f);
                    int height = glyph.height();
                    for (int bar : new int[] {211, 220, 229})
                        for (float row : new float[] {.36f, .46f, .56f, .66f}) {
                            byte[] g = InStaffPrimaryBeamContextTest.ink(true, false, 3);
                            for (int y : new int[] {188, 204})
                                for (int x = 20; x <= 580; x++) g[y * 600 + x] = (byte) 255;
                            int band = Math.round(row * height);
                            for (int y = 0; y < 100; y++)
                                for (int x = 0; x < 100; x++) {
                                    int xx = 229 + x - glyph.left(), yy = 187 + y - glyph.top();
                                    if (xx < 0 || xx >= 600 || yy < 0 || yy >= 320) continue;
                                    if ((glyph.gray()[y * 100 + x] & 255) < 165) {
                                        int local = y - glyph.top();
                                        int value = local >= band && local <= band + 2 ? bar : 194;
                                        g[yy * 600 + xx] =
                                                (byte) Math.min(g[yy * 600 + xx] & 255, value);
                                    }
                                }
                            assertEquals(
                                    "size=" + size + " phase=" + phase + " shear=" + shear + " bar="
                                            + bar + " row=" + row,
                                    notes,
                                    TripletRhythmDetector.apply(
                                            notes,
                                            InStaffPrimaryBeamContextTest.BARS,
                                            g,
                                            600,
                                            320));
                        }
                }
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Original generated three-head engraving; no score pixels or recorded model data. */
public class InStaffPrimaryBeamContextTest {
    static final int W = 600, H = 320;
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(0, 1, 96f / H, 224f / H));

    static void line(byte[] g, int x0, int y0, int x1, int y1, int thick) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = Math.round(x0 + (x1 - x0) * i / (float) Math.max(1, n)),
                    y = Math.round(y0 + (y1 - y0) * i / (float) Math.max(1, n));
            for (int dy = -thick / 2; dy <= thick / 2; dy++)
                for (int dx = -thick / 2; dx <= thick / 2; dx++)
                    if (x + dx >= 0 && x + dx < W && y + dy >= 0 && y + dy < H)
                        g[(y + dy) * W + x + dx] = 0;
        }
    }

    static byte[] ink(boolean middle, boolean numeral, int digit) throws Exception {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 140; y <= 204; y += 16) line(g, 20, y, 580, y, 1);
        for (int cx : new int[] {180, 245, 310}) {
            for (int y = 104; y <= 116; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if ((x - cx) * (x - cx) / 100.0 + (y - 110) * (y - 110) / 36.0 <= 1)
                        g[y * W + x] = 0;
            if (middle || cx != 245) line(g, cx - 10, 110, cx - 10, 170, 3);
        }
        line(g, 170, 170, 300, 170, 5);
        if (numeral) {
            var r = CurledTupletThreeTest.glyph(digit, 22, .5f, .15f, .8f);
            for (int y = 0; y < 100; y++)
                for (int x = 0; x < 100; x++) {
                    int xx = 229 + x - r.left(), yy = 187 + y - r.top();
                    if (xx < 0 || xx >= W || yy < 0 || yy >= H) continue;
                    int v = r.gray()[y * 100 + x] & 255;
                    v = 100 + v * 155 / 255;
                    g[yy * W + xx] = (byte) Math.min(g[yy * W + xx] & 255, v);
                }
        }
        return g;
    }

    static List<ScoreNoteEvent> notes() {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {180, 245, 310})
            n.add(
                    new ScoreNoteEvent(0, x / (float) W, 12, 0, 1, 110f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(-1));
        return List.copyOf(n);
    }

    @Test
    public void actualThreeAttachedHeadsOwnInStaffPrintedNumeral() throws Exception {
        var g = ink(true, true, 3);
        var before = g.clone();
        var n = TripletRhythmDetector.apply(notes(), BARS, g, W, H);
        assertArrayEquals(before, g);
        for (var v : n) {
            assertEquals(3, v.tupletDivisor());
            assertEquals(2, v.tupletNormalNotes());
        }
    }

    @Test
    public void missingMiddleShaftCannotSupplyCompleteGroup() throws Exception {
        var n = notes();
        assertEquals(n, TripletRhythmDetector.apply(n, BARS, ink(false, true, 3), W, H));
    }

    @Test
    public void noPrintedNumeralCannotInventTriplet() throws Exception {
        var n = notes();
        assertEquals(n, TripletRhythmDetector.apply(n, BARS, ink(true, false, 3), W, H));
    }

    @Test
    public void otherDigitsRemainOtherDigits() throws Exception {
        var n = notes();
        for (int digit : new int[] {0, 1, 2, 4, 5, 6, 7, 8, 9})
            assertEquals(
                    "digit" + digit,
                    n,
                    TripletRhythmDetector.apply(n, BARS, ink(true, true, digit), W, H));
    }

    @Test
    public void splitPrimaryBeamCannotProvideOnePhysicalGroup() throws Exception {
        var g = ink(true, true, 3);
        for (int x = 208; x <= 225; x++) for (int y = 166; y <= 174; y++) g[y * W + x] = (byte) 255;
        var n = notes();
        assertEquals(n, TripletRhythmDetector.apply(n, BARS, g, W, H));
    }

    @Test
    public void literalRestSlotCannotBecomeThirdSoundingHead() throws Exception {
        var all = notes();
        var actual = List.of(all.get(0), all.get(2));
        var rest = new ScoreRestEvent(0, 245f / W, 110f / H, 20f / H, 0, 1, .5);
        var r =
                TripletRhythmDetector.withRests(
                        actual, List.of(rest), BARS, ink(false, true, 3), W, H);
        for (var n : r.notes()) assertEquals(1, n.tupletDivisor());
        assertEquals(List.of(rest), r.rests());
    }

    @Test
    public void conflictingStaffDeclarationsCannotOwnSamePhysicalBeam() throws Exception {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int staff = 0; staff < 2; staff++)
            for (int x : new int[] {180, 245, 310})
                n.add(
                        new ScoreNoteEvent(
                                        0,
                                        x / (float) W,
                                        12,
                                        staff,
                                        2,
                                        110f / H,
                                        false,
                                        0,
                                        1,
                                        2,
                                        0,
                                        1)
                                .withStemDirection(-1));
        var bars = List.of(new MeasureRegion(0, 1, 64f / H, 1));
        var r = TripletRhythmDetector.apply(n, bars, ink(true, true, 3), W, H);
        for (var v : r) assertEquals(1, v.tupletDivisor());
    }
}

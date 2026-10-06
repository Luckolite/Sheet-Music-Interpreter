// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original tiny numeral geometry with pale printing and intersecting staff rules. */
public class TupletNumeralInkTest {
    private final String[] glyph = {
        "..#######...",
        ".##########.",
        "###......###",
        "####.....###",
        "####.....###",
        "####.....###",
        ".##.....####",
        ".......####.",
        "......####..",
        "....#####...",
        "....#####...",
        "....#####...",
        "......####..",
        ".......####.",
        "##.....####.",
        "###....####.",
        "###....####.",
        "###....####.",
        ".###....###.",
        "..########..",
        "..########..",
        "....####...."
    };

    private byte[] image(int paper, int ink, boolean rule, boolean eight) {
        byte[] gray = new byte[400 * 240];
        Arrays.fill(gray, (byte) paper);
        for (int y = 0; y < glyph.length; y++)
            for (int x = 0; x < 12; x++)
                if (glyph[y].charAt(x) == '#' || eight && x < 2 && y > 1 && y < glyph.length - 2)
                    gray[(145 + y) * 400 + 119 + x] = (byte) ink;
        if (rule) for (int x = 60; x < 195; x++) gray[150 * 400 + x] = (byte) ink;
        return gray;
    }

    private List<ScoreNoteEvent> detect(byte[] gray, boolean quarters) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (float x : new float[] {.25f, .3125f, .375f})
            notes.add(
                    new ScoreNoteEvent(
                            0, x, 2, 0, 1, .4f, false, 0, quarters ? 0 : 1, 2, quarters ? 1 : 0));
        return TripletRhythmDetector.apply(
                notes, List.of(new MeasureRegion(0, 1, .2f, .6f)), gray, 400, 240);
    }

    @Test
    public void paleThreeUsesLocalPaperContrast() {
        for (var n : detect(image(245, 180, false, false), false))
            assertEquals(3, n.tupletDivisor());
    }

    @Test
    public void thinIntersectingRuleDoesNotJoinTheNumeralToTheWholeStaff() {
        for (var n : detect(image(245, 100, true, false), false))
            assertEquals(3, n.tupletDivisor());
    }

    @Test
    public void paleIntersectingRuleAlsoWorks() {
        for (var n : detect(image(245, 180, true, false), false))
            assertEquals(3, n.tupletDivisor());
    }

    @Test
    public void gentlySlopedBlurredRuleAlsoWorks() {
        var gray = image(245, 100, false, false);
        for (int x = 75; x <= 175; x++) {
            int y = 150 + Math.round((x - 125) * .05f);
            for (int d = -1; d <= 1; d++) gray[(y + d) * 400 + x] = 100;
        }
        for (var n : detect(gray, false)) assertEquals(3, n.tupletDivisor());
    }

    @Test
    public void darkPaperDoesNotFillTheGlyphCounters() {
        for (var n : detect(image(180, 110, false, false), false))
            assertEquals(3, n.tupletDivisor());
    }

    @Test
    public void paleEightCannotBecomeAThree() {
        for (var n : detect(image(245, 180, false, true), false))
            assertEquals(1, n.tupletDivisor());
    }

    @Test
    public void crossingLineCannotOpenAClosedEight() {
        for (var n : detect(image(245, 100, true, true), false)) assertEquals(1, n.tupletDivisor());
    }

    @Test
    public void quarterNotesStillRequireBothBracketArms() {
        for (var n : detect(image(245, 180, false, false), true))
            assertEquals(1, n.tupletDivisor());
    }

    @Test
    public void imageIsUnchanged() {
        var gray = image(245, 180, true, false);
        var copy = gray.clone();
        detect(gray, false);
        assertArrayEquals(copy, gray);
    }

    @Test
    public void completeWindowKeepsOriginalNumeralPixelsAndOwnsResult() {
        byte[] gray = image(245, 180, false, false);
        byte[] before = gray.clone();
        var window = TupletNumeralInk.window(gray, 400, 240, 100, 150, 160, 160, 12, 190);
        var edges = TupletNumeralInk.ruleEdgesWindow(gray, 400, 240, 100, 150, 160, 160, 12, 190);
        assertNotNull(window);
        assertNotNull(edges);
        assertEquals(76, window.left());
        assertEquals(70, window.top());
        assertEquals(99, window.width());
        assertEquals(170, window.height());
        assertNotSame(gray, window.pixels());
        for (int y = 0; y < window.height(); y++)
            for (int x = 0; x < window.width(); x++)
                assertEquals(
                        (byte) ((before[(y + 70) * 400 + x + 76] & 255) <= 190 ? 0 : 255),
                        window.pixels()[y * window.width() + x]);
        assertEquals(window.left(), edges.left());
        assertEquals(window.top(), edges.top());
        assertEquals(window.width(), edges.width());
        assertEquals(window.height(), edges.height());
        assertArrayEquals(window.pixels(), edges.pixels());
        assertArrayEquals(before, gray);
        window.pixels()[0] = 0;
        var repeated = TupletNumeralInk.window(gray, 400, 240, 100, 150, 160, 160, 12, 190);
        assertNotSame(window.pixels(), repeated.pixels());
        assertEquals((byte) 255, repeated.pixels()[0]);
        gray[145 * 400 + 121] = (byte) 245;
        byte[] changed = gray.clone();
        var reread = TupletNumeralInk.window(gray, 400, 240, 100, 150, 160, 160, 12, 190);
        assertEquals((byte) 255, reread.pixels()[75 * 99 + 45]);
        assertArrayEquals(changed, gray);
    }

    @Test
    public void boundedInvalidAndNonfiniteGapsKeepOriginalEarlyRejections() {
        byte[] gray = image(245, 180, false, false);
        byte[] before = gray.clone();
        assertNull(TupletNumeralInk.window(null, 400, 240, 100, 150, 160, 160, 12, 190));
        for (float gap : new float[] {-0f, 0f, Float.NEGATIVE_INFINITY, Float.NaN}) {
            assertNull(TupletNumeralInk.window(gray, 400, 240, 100, 150, 160, 160, gap, 190));
            assertNull(
                    TupletNumeralInk.ruleEdgesWindow(gray, 400, 240, 100, 150, 160, 160, gap, 190));
        }
        assertNull(
                TupletNumeralInk.window(
                        new byte[] {(byte) 255}, 1, 1, 0, 0, 0, 0, Float.POSITIVE_INFINITY, 190));
        assertNull(TupletNumeralInk.window(gray, 400, 240, 100, 150, 160, 160, 12, 99));
        assertArrayEquals(before, gray);
    }
}

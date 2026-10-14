// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric equation controls; no source-score pixels. */
public class ApproximateTempoEquationTest {
    private TempoUnitNoteTest.Page page() {
        var p = new TempoUnitNoteTest.Page();
        p.erase(164, 62, 32, 27);
        // Small lower-case c and a, then a separate period.
        p.rect(165, 70, 3, 18, (byte) 5);
        p.rect(165, 70, 11, 3, (byte) 5);
        p.rect(165, 85, 11, 3, (byte) 5);
        p.rect(183, 73, 3, 15, (byte) 5);
        p.rect(183, 73, 12, 3, (byte) 5);
        p.rect(183, 85, 12, 3, (byte) 5);
        p.rect(192, 70, 3, 18, (byte) 5);
        p.rect(201, 85, 3, 3, (byte) 5);
        // Separate connected 9 and 6 glyphs, farther from equals than the ordinary search.
        p.rect(230, 62, 12, 3, (byte) 5);
        p.rect(230, 62, 3, 15, (byte) 5);
        p.rect(230, 74, 12, 3, (byte) 5);
        p.rect(239, 62, 3, 27, (byte) 5);
        p.rect(230, 86, 12, 3, (byte) 5);
        p.rect(250, 62, 3, 27, (byte) 5);
        p.rect(250, 62, 12, 3, (byte) 5);
        p.rect(250, 74, 12, 3, (byte) 5);
        p.rect(250, 86, 12, 3, (byte) 5);
        p.rect(259, 74, 3, 15, (byte) 5);
        return p;
    }

    private List<ScoreTempoChange> read(TempoUnitNoteTest.Page p, String text) throws Exception {
        return read(p, text, 98, 40, 265, 98);
    }

    private List<ScoreTempoChange> read(
            TempoUnitNoteTest.Page p, String text, int left, int top, int right, int bottom)
            throws Exception {
        return read(
                p,
                text,
                left,
                top,
                right,
                bottom,
                List.of(new MeasureRegion(20f / p.w, 460f / p.w, 100f / p.h, 164f / p.h)));
    }

    private List<ScoreTempoChange> read(
            TempoUnitNoteTest.Page p,
            String text,
            int left,
            int top,
            int right,
            int bottom,
            List<MeasureRegion> measures)
            throws Exception {
        var words =
                List.of(
                        new PlayingTechniqueDetector.Word(
                                text,
                                left / (float) p.w,
                                top / (float) p.h,
                                right / (float) p.w,
                                bottom / (float) p.h));
        byte[] before = p.gray.clone();
        List<ScoreTempoChange> out;
        out = TempoChangeDetector.detectWithWords(List.of(), words, p.gray, p.w, p.h, measures);
        assertArrayEquals(before, p.gray);
        return out;
    }

    @Test
    public void approximateQuarterGetsItsPrintedRateAndOpeningPosition() throws Exception {
        var out = read(page(), "√= ca. 96");
        assertEquals(1, out.size());
        assertEquals(96, out.get(0).bpm(), 0);
        assertEquals(1, out.get(0).beatUnit(), 0);
        assertEquals(0, out.get(0).measureIndex());
        assertEquals(0, out.get(0).positionInMeasure(), 0);
    }

    @Test
    public void omittedOcrBeatGlyphStillNeedsOpticalBeat() throws Exception {
        assertEquals(1, read(page(), "= ca. 96").size());
    }

    @Test
    public void commonOcrQuarterAliasStillRequiresPrintedBeat() throws Exception {
        assertEquals(1, read(page(), "J = ca. 96").size());
        var p = page();
        p.erase(98, 40, 28, 59);
        assertTrue(read(p, "J = ca. 96").isEmpty());
    }

    @Test
    public void hollowBeatKeepsHalfNotePulse() throws Exception {
        var p = page();
        p.note(110, 89, true);
        var out = read(p, "√= ca. 96");
        assertEquals(1, out.size());
        assertEquals(2, out.get(0).beatUnit(), 0);
        assertEquals(192, out.get(0).bpm(), 0);
    }

    @Test
    public void missingPrintedEqualsIsRejected() throws Exception {
        var p = page();
        p.erase(131, 70, 20, 20);
        assertTrue(read(p, "√= ca. 96").isEmpty());
    }

    @Test
    public void oneEqualsStrokeIsRejected() throws Exception {
        var p = page();
        p.erase(131, 79, 20, 6);
        assertTrue(read(p, "√= ca. 96").isEmpty());
    }

    @Test
    public void missingBeatIsRejected() throws Exception {
        var p = page();
        p.erase(98, 40, 28, 59);
        assertTrue(read(p, "√= ca. 96").isEmpty());
    }

    @Test
    public void disconnectedDigitsAreNotAnEquation() throws Exception {
        var p = page();
        p.erase(98, 40, 110, 59);
        assertTrue(read(p, "√= ca. 96").isEmpty());
    }

    @Test
    public void labelsRangesDecimalsAndInvalidRatesAreRejected() throws Exception {
        for (String text :
                List.of(
                        "Op. ca. 96",
                        "= ca. 96-120",
                        "= ca. 96/120",
                        "= ca. 96.5",
                        "= ca. 960",
                        "= ca. 450",
                        "= ca. 12",
                        "Allegro = ca. 96",
                        "= circa 96 bpm")) assertTrue(text, read(page(), text).isEmpty());
    }

    @Test
    public void touchingDigitGroupsAreRejected() throws Exception {
        var p = page();
        p.rect(240, 75, 13, 2, (byte) 5);
        assertTrue(read(p, "√= ca. 96").isEmpty());
    }

    @Test
    public void threeOcrDigitsRequireThreePhysicalGroups() throws Exception {
        assertTrue(read(page(), "√= ca. 196").isEmpty());
    }

    @Test
    public void flaggedBeatRetainsEighthPulse() throws Exception {
        var p = page();
        p.erase(0, 0, 280, 99);
        p.rect(80, 40, 2, 28, (byte) 5);
        p.rect(73, 64, 9, 6, (byte) 5);
        p.rect(82, 41, 4, 4, (byte) 5);
        p.rect(85, 44, 4, 7, (byte) 5);
        p.rect(87, 50, 2, 6, (byte) 5);
        p.rect(104, 58, 11, 2, (byte) 5);
        p.rect(104, 63, 11, 2, (byte) 5);
        p.rect(125, 57, 2, 12, (byte) 5);
        p.rect(125, 57, 8, 2, (byte) 5);
        p.rect(125, 67, 8, 2, (byte) 5);
        p.rect(141, 59, 2, 10, (byte) 5);
        p.rect(141, 59, 8, 2, (byte) 5);
        p.rect(141, 67, 8, 2, (byte) 5);
        p.rect(147, 57, 2, 12, (byte) 5);
        p.rect(156, 67, 2, 2, (byte) 5);
        p.rect(175, 54, 8, 2, (byte) 5);
        p.rect(175, 54, 2, 9, (byte) 5);
        p.rect(175, 61, 8, 2, (byte) 5);
        p.rect(181, 54, 2, 16, (byte) 5);
        p.rect(175, 68, 8, 2, (byte) 5);
        p.rect(190, 54, 2, 16, (byte) 5);
        p.rect(190, 54, 8, 2, (byte) 5);
        p.rect(190, 61, 8, 2, (byte) 5);
        p.rect(190, 68, 8, 2, (byte) 5);
        p.rect(196, 61, 2, 9, (byte) 5);
        var out = read(p, "√= ca. 96", 70, 38, 206, 73);
        assertEquals(1, out.size());
        assertEquals(.5, out.get(0).beatUnit(), 0);
        assertEquals(48, out.get(0).bpm(), 0);
    }

    @Test
    public void dottedBeatRetainsAugmentation() throws Exception {
        var p = page();
        p.rect(125, 88, 3, 3, (byte) 5);
        var out = read(p, "√= ca. 96");
        assertEquals(1, out.size());
        assertEquals(1.5, out.get(0).beatUnit(), 0);
        assertEquals(144, out.get(0).bpm(), 0);
    }

    @Test
    public void misleadingDigitsWithoutApproximationSyntaxStayRejected() throws Exception {
        for (String text : List.of("96", "=96", "ca. 96", "√= Op. 96", "√= cap. 96"))
            assertTrue(text, read(page(), text).isEmpty());
    }

    @Test
    public void elevatedSectionEquationUsesItsRetainedMeasureEnvelope() throws Exception {
        var p = page();
        p.erase(0, 100, 500, 65);
        for (int y = 150; y <= 182; y += 8) p.rect(20, y, 460, 1, (byte) 4);
        var out =
                read(
                        p,
                        "J = ca. 96",
                        98,
                        40,
                        265,
                        98,
                        List.of(new MeasureRegion(20f / p.w, 460f / p.w, 125f / p.h, 190f / p.h)));
        assertEquals(1, out.size());
        assertEquals(96, out.get(0).bpm(), 0);
        assertEquals(0, out.get(0).measureIndex());
    }

    @Test
    public void distantEquationDoesNotJumpOverAnUnrelatedSection() throws Exception {
        var p = page();
        p.erase(0, 100, 500, 65);
        for (int y = 210; y <= 242; y += 8) p.rect(20, y, 460, 1, (byte) 4);
        assertTrue(
                read(
                                p,
                                "J = ca. 96",
                                98,
                                40,
                                265,
                                98,
                                List.of(
                                        new MeasureRegion(
                                                20f / p.w, 460f / p.w, 205f / p.h, 248f / p.h)))
                        .isEmpty());
    }

    @Test
    public void elevatedEquationUsesClosestPhysicalBoundaryDespiteRowRounding() throws Exception {
        var p = page();
        p.erase(0, 100, 500, 65);
        for (int y = 150; y <= 182; y += 8) p.rect(20, y, 460, 1, (byte) 4);
        var out =
                read(
                        p,
                        "J = ca. 96",
                        98,
                        40,
                        265,
                        98,
                        List.of(
                                new MeasureRegion(20f / p.w, 97f / p.w, 125f / p.h, 190f / p.h),
                                new MeasureRegion(
                                        101f / p.w, 460f / p.w, 125.02f / p.h, 190f / p.h)));
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).measureIndex());
        assertEquals(0, out.get(0).positionInMeasure(), 0);
    }
}

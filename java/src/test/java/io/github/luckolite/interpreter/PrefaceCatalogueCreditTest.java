// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original multilingual prose and educational-advertisement fixtures. */
public class PrefaceCatalogueCreditTest {
    static ScoreCreditsDetector.Line line(
            String text, float x, float y, float width, float height) {
        return new ScoreCreditsDetector.Line(text, x, y, x + width, y + height);
    }

    static ScoreCreditsDetector.Page prose(String heading, int count, boolean spaced) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        ls.add(line(heading, 700, 110, 350, 55));
        for (int i = 0; i < count; i++)
            ls.add(
                    line(
                            "We explain the daily practice sequence " + i,
                            650,
                            200 + i * (spaced ? 180 : 40),
                            490,
                            32));
        return new ScoreCreditsDetector.Page(1800, 2400, 220, ls, false, false);
    }

    static boolean paragraph(ScoreCreditsDetector.Page p, ScoreCreditsDetector.Line l)
            throws Exception {
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "paragraphLine",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, l, p.lines());
    }

    @Test
    public void narrowEnglishPrefaceColumnIsProse() throws Exception {
        var p = prose("PREFACE", 7, false);
        assertTrue(paragraph(p, p.lines().get(1)));
    }

    @Test
    public void germanPrefaceHeadingIsSupported() throws Exception {
        var p = prose("VORWORT", 7, false);
        assertTrue(paragraph(p, p.lines().get(1)));
    }

    @Test
    public void frenchPrefaceHeadingIsSupported() throws Exception {
        var p = prose("PRÉFACE", 7, false);
        assertTrue(paragraph(p, p.lines().get(1)));
    }

    @Test
    public void sparseLinesAreNotParagraph() throws Exception {
        var p = prose("PREFACE", 7, true);
        assertFalse(paragraph(p, p.lines().get(1)));
    }

    @Test
    public void missingPrefaceHeadingRetainsEvidence() throws Exception {
        var p = prose("Harbor Collection", 7, false);
        assertFalse(paragraph(p, p.lines().get(1)));
    }

    @Test
    public void threeLinesAreInsufficient() throws Exception {
        var p = prose("PREFACE", 3, false);
        assertFalse(paragraph(p, p.lines().get(1)));
    }

    @Test
    public void explicitAuthorIsNotProse() throws Exception {
        var p = prose("PREFACE", 7, false);
        assertFalse(paragraph(p, line("Composed by Morgan Vale", 650, 200, 490, 32)));
    }

    static ScoreCreditsDetector.Page catalogue(String heading, int instruments) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        ls.add(line("From the educational library", 400, 100, 800, 30));
        ls.add(line(heading, 400, 150, 800, 50));
        ls.add(line("Salient Features", 120, 230, 400, 35));
        String[] names = {"FLUTE", "OBOE", "VIOLIN", "CLARINET", "GUITAR", "BASSOON"};
        for (int i = 0; i < instruments; i++) ls.add(line(names[i], 650, 350 + i * 70, 300, 30));
        return new ScoreCreditsDetector.Page(1800, 2400, 800, ls, false, false);
    }

    @Test
    public void methodInventoryIsCatalogue() {
        assertTrue(ScoreCreditsDetector.isProductCatalogue(catalogue("Elementary Methods", 6)));
    }

    @Test
    public void smallInventoryNotCatalogue() {
        assertFalse(ScoreCreditsDetector.isProductCatalogue(catalogue("Elementary Methods", 3)));
    }

    @Test
    public void ordinaryWorkHeadingNotCatalogue() {
        assertFalse(ScoreCreditsDetector.isProductCatalogue(catalogue("Harbor Evening", 6)));
    }

    @Test
    public void catalogueDoesNotReplaceOpeningTitle() {
        var opening =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        400,
                        List.of(
                                line("Harbor Evening", 500, 100, 800, 90),
                                line("Music by Morgan Vale", 1150, 220, 500, 30)),
                        false,
                        false);
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Evening",
                        List.of(opening, catalogue("Elementary Methods", 6)),
                        Set.of());
        assertEquals("Harbor Evening", r.title());
        assertEquals("Morgan Vale", r.composer());
        assertFalse(r.unclassifiedCredits().contains("Salient Features"));
    }

    @Test
    public void falseStaffPrefaceDoesNotProduceAuthor() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Collection", List.of(prose("PREFACE", 7, false)), Set.of());
        assertTrue(r.unclassifiedCredits().stream().noneMatch(n -> n.startsWith("We explain")));
        assertEquals("", r.composer());
    }
}

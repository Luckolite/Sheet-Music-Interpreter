// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Invented work and OCR strings; no score scans or private library metadata. */
public class IndependentOpeningHeaderTest {
    private static ScoreCreditsDetector.Line row(String s, float l, float t, float r, float b) {
        return new ScoreCreditsDetector.Line(s, l, t, r, b);
    }

    private static List<ScoreHeaderReadConsensus.Reading> reads(String s) {
        var out = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int scale : new int[] {1, 2, 4})
            for (String mode : List.of("tight", "horizontal"))
                for (double width : new double[] {1, 1.5})
                    out.add(new ScoreHeaderReadConsensus.Reading(scale, mode, width, s, .97));
        return out;
    }

    private static final ScoreCreditsDetector.Line TITLE =
            row("The Amber Gote", 110, 212, 320, 245);
    private static final ScoreCreditsDetector.Line HEADER =
            row("Flauto Princioale", 500, 130, 1080, 200);
    private static final ScoreCreditsDetector.Line CAPTION = row("Nocturne VII", 70, 270, 620, 340);
    private static final ScoreCreditsDetector.Line BODY =
            row("Softly through the distant evening", 580, 215, 1320, 249);

    private static List<ScoreCreditsDetector.Page> pages(
            boolean repeated, List<ScoreCreditsDetector.ReviewCredit> reviews) {
        var first =
                new ScoreCreditsDetector.Page(
                        1400,
                        2000,
                        260,
                        List.of(TITLE, HEADER, CAPTION, BODY),
                        false,
                        false,
                        reviews);
        return repeated
                ? List.of(
                        first,
                        new ScoreCreditsDetector.Page(
                                1400,
                                2000,
                                240,
                                List.of(row("Flauto Principale", 485, 90, 1070, 160))))
                : List.of(first);
    }

    private static MusicMetadataCatalogue catalogue(Set<String> people) {
        return new MusicMetadataCatalogue(
                List.of(
                        new MusicMetadataCatalogue.Work(
                                "fictional-amber", 7, "piano nocturne", "The Amber Gate")),
                people,
                Set.of("flauto"));
    }

    private static Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> evidence(
            boolean header) {
        var out = new HashMap<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>>();
        out.put(TITLE, reads("The Amber Gate"));
        out.put(CAPTION, reads("Nocturne VII"));
        if (header) out.put(HEADER, reads("Flauto Principale"));
        return out;
    }

    @Test
    public void nominateIndependentHeaderDespiteWrongBaseline() {
        var p = pages(true, List.of());
        var baseline = ScoreCreditsDetector.detect("unrelated-import", p, Set.of());
        assertEquals(BODY.text(), baseline.title());
        assertTrue(OpeningWorkHeading.headerCandidates(p.get(0)).contains(HEADER));
        var selected =
                CorroboratedCatalogueWorkTitle.select(
                        "unrelated-import", p, Set.of(), evidence(true), catalogue(Set.of()));
        assertNotNull(selected);
        assertEquals("The Amber Gate Nocturne VII", selected.after().title());
        assertEquals(baseline.artist(), selected.after().artist());
        assertEquals(baseline.composer(), selected.after().composer());
        assertEquals(baseline.arranger(), selected.after().arranger());
    }

    @Test
    public void independentHeaderNeedsLiteralAndRepeatedProof() {
        assertNull(
                CorroboratedCatalogueWorkTitle.select(
                        "unrelated-import",
                        pages(true, List.of()),
                        Set.of(),
                        evidence(false),
                        catalogue(Set.of())));
        assertNull(
                CorroboratedCatalogueWorkTitle.select(
                        "unrelated-import",
                        pages(false, List.of()),
                        Set.of(),
                        evidence(true),
                        catalogue(Set.of())));
    }

    @Test
    public void neverDisplaceOwnedCreditOrPerson() {
        var p = pages(true, List.of(new ScoreCreditsDetector.ReviewCredit(BODY, BODY.text())));
        assertNull(
                CorroboratedCatalogueWorkTitle.select(
                        "unrelated-import", p, Set.of(), evidence(true), catalogue(Set.of())));
        assertNull(
                CorroboratedCatalogueWorkTitle.select(
                        "unrelated-import",
                        pages(true, List.of()),
                        Set.of(),
                        evidence(true),
                        catalogue(Set.of(BODY.text()))));
    }
}

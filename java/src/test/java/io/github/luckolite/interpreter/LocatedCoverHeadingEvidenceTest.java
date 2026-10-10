// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fictional headings and original-source confidence, without score fixtures. */
public class LocatedCoverHeadingEvidenceTest {
    private static final ScoreCreditsDetector.Line ORIGINAL =
            new ScoreCreditsDetector.Line("ELARA VAIE", 480, 110, 900, 220);

    private static ScoreCreditsDetector.Page page(boolean photo, ScoreCreditsDetector.Line line) {
        return new ScoreCreditsDetector.Page(1800, 1200, -1, List.of(line), false, photo);
    }

    private static List<String> readings(String value) {
        return List.of(value, value, value);
    }

    @Test
    public void originalConsensusDoesNotDependOnImportLabel() {
        var p = page(true, ORIGINAL);
        var e =
                LocatedCoverHeadingEvidence.fromOriginal(
                        p, ORIGINAL, readings("ELARA VALE"), List.of(.97f, .96f, .99f));
        assertNotNull(e);
        assertEquals("ELARA VALE", LocatedCoverHeadingEvidence.merge(p, List.of(e)).get(0).text());
    }

    @Test
    public void unanimousLowerConfidenceDoesNotReplaceLocatedEvidence() {
        assertNull(
                LocatedCoverHeadingEvidence.fromOriginal(
                        page(true, ORIGINAL),
                        ORIGINAL,
                        readings("ELARA VALE"),
                        List.of(.84f, .85f, .84f)));
    }

    @Test
    public void twoOfThreeReadingsAreInsufficient() {
        assertNull(
                LocatedCoverHeadingEvidence.fromOriginal(
                        page(true, ORIGINAL),
                        ORIGINAL,
                        List.of("ELARA VALE", "ELARA VAIE", "ELARA VALE"),
                        List.of(.99f, .99f, .99f)));
    }

    @Test
    public void ordinaryCoversKeepTheirExistingRouting() {
        assertNull(
                LocatedCoverHeadingEvidence.fromOriginal(
                        page(false, ORIGINAL),
                        ORIGINAL,
                        readings("ELARA VALE"),
                        List.of(.99f, .99f, .99f)));
    }

    @Test
    public void aDifferentMergedHeadingCannotBeOverwritten() {
        var e =
                LocatedCoverHeadingEvidence.fromOriginal(
                        page(true, ORIGINAL),
                        ORIGINAL,
                        readings("ELARA VALE"),
                        List.of(.99f, .99f, .99f));
        var other = new ScoreCreditsDetector.Line("Silver Harbor", 480, 110, 900, 220);
        assertEquals(
                List.of(other), LocatedCoverHeadingEvidence.merge(page(true, other), List.of(e)));
    }

    @Test
    public void workIdentifiersRemainProtected() {
        var work = new ScoreCreditsDetector.Line("Silver Harbor Op. 14", 480, 110, 1350, 220);
        assertNull(
                LocatedCoverHeadingEvidence.fromOriginal(
                        page(true, work),
                        work,
                        readings("Silver Harbor Op. 15"),
                        List.of(.99f, .99f, .99f)));
    }

    @Test
    public void invalidConfidenceCannotEstablishSourceEvidence() {
        assertNull(
                LocatedCoverHeadingEvidence.fromOriginal(
                        page(true, ORIGINAL),
                        ORIGINAL,
                        readings("ELARA VALE"),
                        List.of(Float.NaN, .99f, .99f)));
        assertNull(
                LocatedCoverHeadingEvidence.fromOriginal(
                        page(true, ORIGINAL),
                        ORIGINAL,
                        readings("ELARA VALE"),
                        List.of(1.1f, .99f, .99f)));
    }
}

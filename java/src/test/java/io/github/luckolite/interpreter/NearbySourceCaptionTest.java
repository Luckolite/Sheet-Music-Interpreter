// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original fictional heading geometry and generic source wording only. */
public class NearbySourceCaptionTest {
    private static final String RAW = "Suite trom the Motion Picture",
            READ = "Suite from the Motion Picture";

    private static ScoreCreditsDetector.Line line(
            String text, float left, float top, float right, float bottom) {
        return new ScoreCreditsDetector.Line(text, left, top, right, bottom);
    }

    private static ScoreCreditsDetector.Page page(String caption) {
        return new ScoreCreditsDetector.Page(
                1000,
                1400,
                330,
                List.of(
                        line("Moorland Echoes", 350, 40, 650, 80),
                        line(caption, 325, 66, 675, 90),
                        line("Composed by Rowan Vale", 650, 180, 950, 200)));
    }

    private static ScoreCreditsDetector.Page withLines(
            ScoreCreditsDetector.Page p, List<ScoreCreditsDetector.Line> lines) {
        return new ScoreCreditsDetector.Page(
                p.width(),
                p.height(),
                p.notationTop(),
                lines,
                p.creditsOnly(),
                p.photographicCover(),
                p.reviewCredits());
    }

    private static ScoreCreditsDetector.Result detect(ScoreCreditsDetector.Page p) {
        return ScoreCreditsDetector.detect("", List.of(p), Set.of());
    }

    private static List<ScoreCreditsDetector.Line> merge(
            ScoreCreditsDetector.Page p, List<String> readings, List<Float> confidence) {
        return ScoreCreditsDetector.mergeNearbySourceCaptionConsensus(
                p, p.lines().get(1), readings, confidence);
    }

    private static List<ScoreCreditsDetector.Line> merge(
            ScoreCreditsDetector.Page p, String reading) {
        return merge(p, List.of(reading, reading, reading), List.of(.99f, .98f, .97f));
    }

    @Test
    public void malformedConnectorNominatesPixelsOnly() {
        var p = page(RAW);
        assertEquals(
                List.of(p.lines().get(1)), ScoreCreditsDetector.nearbySourceCaptionCandidates(p));
        assertEquals("Moorland Echoes", detect(p).title());
    }

    @Test
    public void threeOriginalReadingsRepairCaptionAndTitle() {
        var p = page(RAW);
        var lines = merge(p, READ);
        assertEquals(READ, lines.get(1).text());
        assertEquals(
                "Moorland Echoes Suite from the Motion Picture",
                detect(withLines(p, lines)).title());
        assertEquals(detect(p).composer(), detect(withLines(p, lines)).composer());
        assertEquals(detect(p).artist(), detect(withLines(p, lines)).artist());
        assertEquals(detect(p).arranger(), detect(withLines(p, lines)).arranger());
        assertEquals(
                detect(p).unclassifiedCredits(), detect(withLines(p, lines)).unclassifiedCredits());
    }

    @Test
    public void exactPrintedCaptionNeedsNoRereadAndJoinsBoundedOverlap() {
        var p = page(READ);
        assertTrue(ScoreCreditsDetector.nearbySourceCaptionCandidates(p).isEmpty());
        assertEquals("Moorland Echoes Suite from the Motion Picture", detect(p).title());
    }

    @Test
    public void correctedLinePreservesEveryCoordinate() {
        var p = page(RAW);
        var after = merge(p, READ).get(1);
        var before = p.lines().get(1);
        assertEquals(before.left(), after.left(), 0);
        assertEquals(before.top(), after.top(), 0);
        assertEquals(before.right(), after.right(), 0);
        assertEquals(before.bottom(), after.bottom(), 0);
    }

    @Test
    public void exactlyThreeAgreeingLiteralReadingsRequired() {
        var p = page(RAW);
        for (var readings :
                List.of(
                        List.of(READ, READ),
                        List.of(READ, READ, READ, READ),
                        List.of(READ, READ, RAW),
                        List.of(READ, READ, READ.toUpperCase())))
            assertEquals(p.lines(), merge(p, readings, List.of(.99f, .99f, .99f)));
    }

    @Test
    public void finiteStrongConfidenceRequired() {
        var p = page(RAW);
        for (float bad :
                new float[] {
                    .949f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1.001f
                })
            assertEquals(p.lines(), merge(p, List.of(READ, READ, READ), List.of(.99f, bad, .99f)));
        assertEquals(p.lines(), merge(p, List.of(READ, READ, READ), List.of(.99f, .99f)));
        assertEquals(
                p.lines(), merge(p, List.of(READ, READ, READ), Arrays.asList(.99f, null, .99f)));
    }

    @Test
    public void adjacentConfidenceBoundaryKeepsCaptionLiteralGuard() {
        var p = page(RAW);
        var readings = List.of(READ, READ, READ);
        assertEquals(p.lines(), merge(p, readings, List.of(Math.nextDown(.95f), .99f, .99f)));
        var accepted = merge(p, readings, List.of(.95f, .99f, .99f));
        assertEquals(READ, accepted.get(1).text());
        assertEquals(
                "Moorland Echoes Suite from the Motion Picture",
                detect(withLines(p, accepted)).title());
    }

    @Test
    public void missingNullBlankOrMultilineEvidenceRejected() {
        var p = page(RAW);
        assertEquals(p.lines(), merge(p, null, null));
        for (String bad :
                List.of("", "Suite from\nthe Motion Picture", "Suite from\rthe Motion Picture"))
            assertEquals(p.lines(), merge(p, bad));
        assertEquals(
                p.lines(), merge(p, Arrays.asList(READ, null, READ), List.of(.99f, .99f, .99f)));
    }

    @Test
    public void consensusCannotAddDeleteOrReplaceTitleContent() {
        var p = page(RAW);
        for (String bad :
                List.of(
                        "Suite from the Moving Picture",
                        "Suite from the Motion Pictures",
                        "Suite from Motion Picture",
                        "Suite from the Motion Picture 2",
                        "Suite of the Motion Picture",
                        "Suite from the Motion Picture Rowan Vale"))
            assertEquals(p.lines(), merge(p, bad));
    }

    @Test
    public void candidateCannotNeedMultipleConnectorChangesOrCompletion() {
        for (String bad :
                List.of(
                        "Suite txom the Motion Picture",
                        "Suite frm the Motion Picture",
                        "Suite fro the Motion Picture",
                        "Suite froom the Motion Picture",
                        "Suite from the Moving Picture",
                        "Suite from the Motion Picture 2",
                        "Suite fro\u212a the Motion Picture",
                        "\u017fuite trom the Motion Picture"))
            assertTrue(
                    bad, ScoreCreditsDetector.nearbySourceCaptionCandidates(page(bad)).isEmpty());
    }

    @Test
    public void duplicateOrCompetingSourceCaptionsAreNotNominated() {
        for (String caption : List.of(RAW, READ)) {
            var p = page(RAW);
            var lines = new ArrayList<>(p.lines());
            lines.add(line(caption, 325, 66, 675, 90));
            assertTrue(
                    ScoreCreditsDetector.nearbySourceCaptionCandidates(withLines(p, lines))
                            .isEmpty());
        }
    }

    @Test
    public void creditsOnlyPhotographicAndStaffFreePagesExcluded() {
        var p = page(RAW);
        for (var q :
                List.of(
                        new ScoreCreditsDetector.Page(1000, 1400, 330, p.lines(), true, false),
                        new ScoreCreditsDetector.Page(1000, 1400, 330, p.lines(), false, true),
                        new ScoreCreditsDetector.Page(1000, 1400, -1, p.lines())))
            assertTrue(ScoreCreditsDetector.nearbySourceCaptionCandidates(q).isEmpty());
    }

    @Test
    public void wrongAlignmentGapSizeAndOverlappingBaselinesExcluded() {
        var p = page(RAW);
        for (var bad :
                List.of(
                        line(RAW, 50, 66, 400, 90),
                        line(RAW, 325, 64, 675, 88),
                        line(RAW, 325, 129, 675, 153),
                        line(RAW, 325, 70, 675, 90),
                        line(RAW, 325, 60, 675, 110),
                        line(RAW, 100, 66, 900, 90))) {
            var lines = new ArrayList<>(p.lines());
            lines.set(1, bad);
            assertTrue(
                    bad.toString(),
                    ScoreCreditsDetector.nearbySourceCaptionCandidates(withLines(p, lines))
                            .isEmpty());
        }
    }

    @Test
    public void nonfiniteAndReversedBoxesExcluded() {
        var p = page(RAW);
        for (var bad :
                List.of(
                        line(RAW, Float.NaN, 66, 675, 90),
                        line(RAW, 325, 66, Float.POSITIVE_INFINITY, 90),
                        line(RAW, 675, 66, 325, 90),
                        line(RAW, 325, 90, 675, 66))) {
            var lines = new ArrayList<>(p.lines());
            lines.set(1, bad);
            assertTrue(
                    ScoreCreditsDetector.nearbySourceCaptionCandidates(withLines(p, lines))
                            .isEmpty());
        }
    }

    @Test
    public void exactOtherCaptionKeepsItsPreviousOverlapLimit() {
        var p = page("Concert Version");
        assertTrue(ScoreCreditsDetector.nearbySourceCaptionCandidates(p).isEmpty());
        assertEquals("Moorland Echoes", detect(p).title());
    }

    @Test
    public void horizontalSpaceNormalizationEmitsObservedLiteral() {
        var p = page(RAW);
        var r = "Suite\tfrom the Motion Picture";
        assertEquals(
                READ,
                merge(p, List.of(r, READ, "  " + READ + "  "), List.of(.99f, .98f, .97f))
                        .get(1)
                        .text());
    }

    @Test
    public void laterNotationPageDoesNotGainOpeningOverlapException() {
        var empty = new ScoreCreditsDetector.Page(1000, 1400, 330, List.of());
        assertEquals(
                "Moorland Echoes",
                ScoreCreditsDetector.detect("", List.of(empty, page(READ)), Set.of()).title());
    }

    @Test
    public void caseOnlyGenericWordsCanRetainTheirObservedSpelling() {
        var p = page("Suite trom The Motion Picture");
        assertEquals(READ, merge(p, READ).get(1).text());
    }

    private static ScoreCreditsDetector.Page withOtherHeading(String caption, String heading) {
        var p = page(caption);
        var lines = new ArrayList<>(p.lines());
        lines.add(line(heading, 350, 8, 650, 38));
        return withLines(p, lines);
    }

    @Test
    public void competingHeadingBlocksOriginalPixelNomination() {
        var p = withOtherHeading(RAW, "Cobalt Harbor");
        assertTrue(ScoreCreditsDetector.nearbySourceCaptionCandidates(p).isEmpty());
        assertEquals(p.lines(), merge(p, READ));
    }

    @Test
    public void competingHeadingAlsoBlocksOverlapExceptionForExactCaption() {
        assertFalse(detect(withOtherHeading(READ, "Cobalt Harbor")).title().contains(READ));
    }

    @Test
    public void ordinarySameBaselineFragmentKeepsCaptionNomination() {
        var p = page(RAW);
        var lines = new ArrayList<>(p.lines());
        lines.add(line("II", 655, 42, 675, 78));
        assertEquals(
                List.of(p.lines().get(1)),
                ScoreCreditsDetector.nearbySourceCaptionCandidates(withLines(p, lines)));
    }

    @Test
    public void ExplicitRoleAndLegalLinesAreNotCompetingHeadings() {
        for (String heading : List.of("Composed by Cobalt Harbor", "Copyright Cobalt Harbor")) {
            var p = withOtherHeading(RAW, heading);
            assertEquals(
                    List.of(p.lines().get(1)),
                    ScoreCreditsDetector.nearbySourceCaptionCandidates(p));
        }
    }
}

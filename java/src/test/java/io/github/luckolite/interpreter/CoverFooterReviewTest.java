// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original fictional cover geometry; no commercial score or contributor fixture. */
public class CoverFooterReviewTest {
    private static ScoreCreditsDetector.Line line(String s, float x, float y, float w, float h) {
        return new ScoreCreditsDetector.Line(s, x, y, x + w, y + h);
    }

    private static ScoreCreditsDetector.Line footer(String s) {
        return line(s, 180, 1250, 640, 60);
    }

    private static ScoreCreditsDetector.Page page(String s) {
        return new ScoreCreditsDetector.Page(
                1000, 1400, -1, List.of(line("MORNING HARBOUR", 100, 40, 800, 70), footer(s)));
    }

    private static ScoreCreditsDetector.Result detect(ScoreCreditsDetector.Page p) {
        return ScoreCreditsDetector.detect("", List.of(p), Set.of());
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

    private static List<ScoreCreditsDetector.Line> merge(ScoreCreditsDetector.Page p, String s) {
        return ScoreCreditsDetector.mergeCoverFooterReviewConsensus(
                p, p.lines().get(1), List.of(s, s, s), List.of(.99f, .98f, .97f));
    }

    private static void invariant(ScoreCreditsDetector.Result a, ScoreCreditsDetector.Result b) {
        assertEquals(a.title(), b.title());
        assertEquals(a.artist(), b.artist());
        assertEquals(a.composer(), b.composer());
        assertEquals(a.arranger(), b.arranger());
    }

    @Test
    public void literalPersonLikeFooterOnlyRemainsForReview() {
        var p = page("ROWAN VALE");
        var r = detect(p);
        assertEquals("MORNING HARBOUR", r.title());
        assertEquals(List.of("ROWAN VALE"), r.unclassifiedCredits());
        assertEquals("", r.artist());
        assertEquals("", r.composer());
        assertEquals("", r.arranger());
    }

    @Test
    public void indistinguishableWorkLikeFooterHasSameUnresolvedSemantics() {
        var r = detect(page("AUTUMN RIVER"));
        assertEquals(List.of("AUTUMN RIVER"), r.unclassifiedCredits());
        assertEquals("MORNING HARBOUR", r.title());
        assertEquals("", r.artist());
        assertEquals("", r.composer());
    }

    @Test
    public void strongOriginalConsensusRefinesFragmentedLiteralAndPreservesBounds() {
        var p = page("ROWAN H A RLS");
        var merged = merge(p, "ROWAN HARIS");
        assertEquals("ROWAN HARIS", merged.get(1).text());
        assertEquals(p.lines().get(1).left(), merged.get(1).left(), 0);
        assertEquals(p.lines().get(1).bottom(), merged.get(1).bottom(), 0);
        invariant(detect(p), detect(withLines(p, merged)));
        assertEquals(List.of("ROWAN HARIS"), detect(withLines(p, merged)).unclassifiedCredits());
    }

    @Test
    public void originalLiteralSurvivesFailedRereads() {
        var p = page("ROWAN H A RLS");
        assertEquals(
                p.lines(),
                ScoreCreditsDetector.mergeCoverFooterReviewConsensus(
                        p, p.lines().get(1), List.of(), List.of()));
        assertEquals(List.of("ROWAN H A RLS"), detect(p).unclassifiedCredits());
    }

    @Test
    public void confidenceMustBeFiniteStrongAndInRange() {
        var p = page("ROWAN H A RLS");
        for (float value : new float[] {.949f, Float.NaN, Float.POSITIVE_INFINITY, 1.01f})
            assertEquals(
                    p.lines(),
                    ScoreCreditsDetector.mergeCoverFooterReviewConsensus(
                            p,
                            p.lines().get(1),
                            List.of("ROWAN HARIS", "ROWAN HARIS", "ROWAN HARIS"),
                            List.of(.99f, value, .99f)));
    }

    @Test
    public void exactThreeLiteralReadingsRequired() {
        var p = page("ROWAN H A RLS");
        for (var readings :
                List.of(
                        List.of("ROWAN HARIS", "ROWAN HARIS"),
                        List.of("ROWAN HARIS", "ROWAN HARIS", "ROWAN HARLS"),
                        List.of("ROWAN HARIS", "ROWAN HARIS", "ROWAN HARIS", "ROWAN HARIS"),
                        List.of("ROWAN HARIS", "ROWAN\nHARIS", "ROWAN HARIS")))
            assertEquals(
                    p.lines(),
                    ScoreCreditsDetector.mergeCoverFooterReviewConsensus(
                            p, p.lines().get(1), readings, List.of(.99f, .99f, .99f)));
    }

    @Test
    public void unrelatedOrCompletedOrShortenedNamesCannotReplaceLiteral() {
        var p = page("ROWAN H A RLS");
        for (String s :
                List.of("MORGAN HARIS", "ROWAN HARRIS", "ROWAN HARI", "ROWAN HARTS", "ROWAN HARIT"))
            assertEquals(p.lines(), merge(p, s));
    }

    @Test
    public void individualInitialsAndContributorSeparatorsAreProtected() {
        for (String s :
                List.of(
                        "ROWAN A VALE",
                        "ROWAN A. VALE",
                        "ROWAN VALE; MIRA WREN",
                        "ROWAN VALE & MIRA WREN")) {
            var p = page(s);
            assertEquals(p.lines(), merge(p, "ROWAN AVALE"));
        }
    }

    @Test
    public void ordinaryAlphabeticWordBoundariesDoNotMove() {
        var p = page("ROWAN STONE WREN");
        assertEquals(p.lines(), merge(p, "ROWAN STONEW REN"));
    }

    @Test
    public void bareIsolatedInitialCannotChangeItsLetter() {
        var p = page("ROWAN I VALE");
        assertEquals(p.lines(), merge(p, "ROWAN L VALE"));
    }

    @Test
    public void punctuatedInitialCannotChangeItsLetter() {
        var p = page("ROWAN I. VALE");
        assertEquals(p.lines(), merge(p, "ROWAN L. VALE"));
    }

    @Test
    public void initialWithNoncomposingMarkCannotChangeItsBase() {
        var p = page("ROWAN I\u030B VALE");
        assertEquals(p.lines(), merge(p, "ROWAN L\u030B VALE"));
    }

    @Test
    public void internalLetterWithNoncomposingMarkCannotChangeItsBase() {
        var p = page("ROWAN VAI\u030BE");
        assertEquals(p.lines(), merge(p, "ROWAN VAL\u030BE"));
    }

    @Test
    public void accentLossCannotBeVotedIntoAName() {
        var p = page("ROWAN VÁLE");
        assertEquals(p.lines(), merge(p, "ROWAN VALE"));
        assertEquals(List.of("ROWAN VÁLE"), detect(p).unclassifiedCredits());
    }

    @Test
    public void canonicalAccentsMayAgreeWithoutAsciiFolding() {
        var p = page("ROWAN VÁLE");
        var m =
                ScoreCreditsDetector.mergeCoverFooterReviewConsensus(
                        p,
                        p.lines().get(1),
                        List.of("ROWAN VA\u0301LE", "ROWAN VÁLE", "ROWAN VÁLE"),
                        List.of(.99f, .99f, .99f));
        assertEquals("ROWAN VA\u0301LE", m.get(1).text());
        invariant(detect(p), detect(withLines(p, m)));
    }

    @Test
    public void supplementaryLettersCannotBeSubstitutedOrDeleted() {
        var p = page("ROWAN \uD801\uDC00ALE");
        assertEquals(p.lines(), merge(p, "ROWAN VALE"));
    }

    @Test
    public void punctuationIsNotLostOrReplaced() {
        for (String s : List.of("ROWAN O’VALE", "ROWAN VALE-WREN")) {
            var p = page(s);
            assertEquals(p.lines(), merge(p, s.replace("’", "").replace("-", "")));
        }
    }

    @Test
    public void legalPublicationInstrumentAndRoleCaptionsAbstain() {
        for (String s :
                List.of(
                        "ALL RIGHTS RESERVED",
                        "ROWAN PRESS",
                        "ROWAN PRESSES",
                        "ROWAN WEBSITE",
                        "ROWAN WEBSITES",
                        "PRINTED ROWAN",
                        "VIOLIN SOLO",
                        "PUBLISHED ROWAN",
                        "ROWAN EDITIONS",
                        "MUSIC BY ROWAN",
                        "TRANSCRIBED BY ROWAN"))
            assertTrue(s, ScoreCreditsDetector.coverFooterReviewCandidates(page(s)).isEmpty());
    }

    @Test
    public void notationAndCreditsOnlyAndPhotographicPagesAbstain() {
        var p = page("ROWAN VALE");
        for (var q :
                List.of(
                        new ScoreCreditsDetector.Page(1000, 1400, 280, p.lines()),
                        new ScoreCreditsDetector.Page(1000, 1400, -1, p.lines(), true),
                        new ScoreCreditsDetector.Page(1000, 1400, -1, p.lines(), false, true)))
            assertTrue(ScoreCreditsDetector.coverFooterReviewCandidates(q).isEmpty());
    }

    @Test
    public void subsequentPageCannotAddGenericFooterReview() {
        var p = page("ROWAN VALE");
        var first =
                new ScoreCreditsDetector.Page(
                        1000, 1400, -1, List.of(line("EARLY DAWN", 100, 40, 800, 70)));
        var r = ScoreCreditsDetector.detect("", List.of(first, p), Set.of());
        assertFalse(r.unclassifiedCredits().contains("ROWAN VALE"));
    }

    @Test
    public void duplicateOrStaleLocatedLineIsRejected() {
        var p = page("ROWAN H A RLS");
        var lines = new ArrayList<>(p.lines());
        lines.add(p.lines().get(1));
        assertTrue(ScoreCreditsDetector.coverFooterReviewCandidates(withLines(p, lines)).isEmpty());
        var stale = footer("ROWAN H A RLT");
        assertEquals(
                p.lines(),
                ScoreCreditsDetector.mergeCoverFooterReviewConsensus(
                        p,
                        stale,
                        List.of("ROWAN HARIS", "ROWAN HARIS", "ROWAN HARIS"),
                        List.of(.99f, .99f, .99f)));
    }

    @Test
    public void malformedGeometryIsRejected() {
        var p = page("ROWAN VALE");
        for (var bad :
                List.of(
                        line("ROWAN VALE", Float.NaN, 1250, 640, 60),
                        line("ROWAN VALE", 180, 1250, Float.POSITIVE_INFINITY, 60),
                        line("ROWAN VALE", -2, 1250, 640, 60),
                        line("ROWAN VALE", 180, 1250, 640, 200)))
            assertTrue(
                    ScoreCreditsDetector.coverFooterReviewCandidates(
                                    withLines(p, List.of(p.lines().get(0), bad)))
                            .isEmpty());
        assertTrue(
                ScoreCreditsDetector.coverFooterReviewCandidates(
                                new ScoreCreditsDetector.Page(Float.NaN, 1400, -1, p.lines()))
                        .isEmpty());
    }

    @Test
    public void multipleFootersOrLargeBodyOrCompetingHeadingAbstain() {
        var p = page("ROWAN VALE");
        for (var extra :
                List.of(
                        footer("MIRA WREN"),
                        line("ANOTHER HEADING", 100, 150, 800, 70),
                        line("A SIMPLE QUOTATION", 100, 600, 800, 30))) {
            var lines = new ArrayList<>(p.lines());
            lines.add(extra);
            assertTrue(
                    ScoreCreditsDetector.coverFooterReviewCandidates(withLines(p, lines))
                            .isEmpty());
        }
    }

    @Test
    public void photographicAndLongTitleResultsRemainExactlyUntouched() {
        var p = page("ROWAN H A RLS");
        var photo = new ScoreCreditsDetector.Page(1000, 1400, -1, p.lines(), false, true);
        assertEquals(photo.lines(), merge(photo, "ROWAN HARIS"));
        assertEquals(detect(photo), detect(withLines(photo, merge(photo, "ROWAN HARIS"))));
        var longPage =
                withLines(
                        p,
                        List.of(
                                line(
                                        "THE DISTANT SHORE BEYOND THE MORNING HARBOUR",
                                        50,
                                        40,
                                        900,
                                        70),
                                p.lines().get(1)));
        invariant(detect(longPage), detect(withLines(longPage, merge(longPage, "ROWAN HARIS"))));
    }

    @Test
    public void lowerCaptionAboveCreditBoundaryIsOutsideNewRoute() {
        var p = page("ROWAN VALE");
        assertTrue(
                ScoreCreditsDetector.coverFooterReviewCandidates(
                                withLines(
                                        p,
                                        List.of(
                                                p.lines().get(0),
                                                line("ROWAN VALE", 180, 1100, 640, 60))))
                        .isEmpty());
    }

    @Test
    public void identifierOrExplicitRoleCannotEnterCorrection() {
        for (String s : List.of("ROWAN VALE IV", "ROWAN VALE 4", "ARRANGED BY ROWAN")) {
            var p = page(s);
            assertEquals(p.lines(), merge(p, "ROWAN VALE"));
        }
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Original fictional text and independently constructed geometry. Apache-2.0. */
public final class CurvedCoverCreditReviewSyntheticTest {
    private static int checks;

    private static void check(boolean result) {
        checks++;
        if (!result) throw new AssertionError("Synthetic check " + checks);
    }

    private static ScoreCreditsDetector.Line line(String text, float x, float y, float w, float h) {
        return new ScoreCreditsDetector.Line(text, x, y, x + w, y + h);
    }

    private static ScoreCreditsDetector.Page page() {
        return new ScoreCreditsDetector.Page(
                1600,
                1200,
                -1,
                List.of(
                        line("MEADOW UNDER", 480, 170, 240, 120),
                        line("STARLIGHT", 960, 150, 180, 60)),
                false,
                true);
    }

    private static CurvedTitleCompletion.Reading reading(
            int scale,
            double factor,
            String printed,
            String nativeText,
            double confidence,
            boolean name) {
        var words = new ArrayList<ScoreCreditsDetector.Line>();
        String[] tokens = nativeText.split(" ");
        float width = (550f - (tokens.length - 1) * 10) / tokens.length;
        for (int i = 0; i < tokens.length; i++)
            words.add(line(tokens[i], 10 + i * (width + 10), 10, width, 50));
        var nativeLine =
                new CurvedTitleCompletion.NativeLine(line(nativeText, 10, 10, 550, 50), words);
        return new CurvedTitleCompletion.Reading(
                scale,
                factor,
                1600,
                600,
                new double[] {0, 0, name ? 60 : 230},
                name ? 80 : 40,
                name ? new double[] {350, 10, 1250, 110} : new double[] {450, 130, 1180, 330},
                600,
                80,
                printed,
                confidence,
                List.of(nativeLine));
    }

    private static List<CurvedTitleCompletion.Reading> readings() {
        var result = new ArrayList<CurvedTitleCompletion.Reading>();
        for (int scale : new int[] {1, 2, 4}) {
            result.add(
                    reading(
                            scale,
                            1,
                            "MEADOW UNDER THE STARLIGHT",
                            "MEADOW UNDER THE STARLIGHT",
                            .99,
                            false));
            result.add(
                    reading(
                            scale,
                            1,
                            "THE LANTERN COLLECTIVE",
                            scale == 1 ? "THE LANTERN COLLECTIVE" : "THE LANTEN COLLECTIVE",
                            .995,
                            true));
        }
        return result;
    }

    private static List<CurvedTitleCompletion.Reading> replace(
            int index, CurvedTitleCompletion.Reading value) {
        var result = new ArrayList<>(readings());
        result.set(index, value);
        return result;
    }

    private static CurvedTitleCompletion.Reading changeGeometry(
            CurvedTitleCompletion.Reading r, double[] q, double[] box, double median) {
        return new CurvedTitleCompletion.Reading(
                r.scale(),
                r.widthFactor(),
                r.sourceWidth(),
                r.sourceHeight(),
                q,
                median,
                box,
                r.width(),
                r.height(),
                r.portable(),
                r.confidence(),
                r.nativeLines());
    }

    public static void main(String[] args) {
        var original = page();
        var observations = readings();
        var proposal = CurvedCoverCreditReview.propose(original, observations);
        check(proposal != null);
        check(proposal.requiresReview());
        check(proposal.value().equals("THE LANTERN COLLECTIVE"));
        var selected = List.of(proposal.work().page());
        var selection =
                CurvedCoverCreditReview.select(
                        "Imported cover", List.of(original), selected, proposal);
        check(selection.accepted());
        check(selection.requiresReview());
        check(selection.pages().get(0).lines().equals(selected.get(0).lines()));
        check(
                selection.pages().get(0).reviewCredits().size()
                        == selected.get(0).reviewCredits().size() + 1);
        check(
                ScoreCreditsDetector.detect("Imported cover", selection.pages(), java.util.Set.of())
                        .equals(selection.result()));
        var persisted = selection.pages().get(0);
        var restoredReviews =
                persisted.reviewCredits().stream()
                        .map(
                                c ->
                                        new ScoreCreditsDetector.ReviewCredit(
                                                new ScoreCreditsDetector.Line(
                                                        c.line().text(),
                                                        c.line().left(),
                                                        c.line().top(),
                                                        c.line().right(),
                                                        c.line().bottom()),
                                                c.value()))
                        .toList();
        var restored =
                new ScoreCreditsDetector.Page(
                        persisted.width(),
                        persisted.height(),
                        persisted.notationTop(),
                        persisted.lines().stream()
                                .map(
                                        l ->
                                                new ScoreCreditsDetector.Line(
                                                        l.text(),
                                                        l.left(),
                                                        l.top(),
                                                        l.right(),
                                                        l.bottom()))
                                .toList(),
                        persisted.creditsOnly(),
                        persisted.photographicCover(),
                        restoredReviews);
        check(
                ScoreCreditsDetector.detect("Imported cover", List.of(restored), java.util.Set.of())
                        .equals(selection.result()));
        var forged =
                new CurvedCoverCreditReview.Proposal(
                        original,
                        null,
                        proposal.value(),
                        proposal.anchor(),
                        proposal.readings(),
                        true);
        check(
                !CurvedCoverCreditReview.select(
                                "Imported cover", List.of(original), selected, forged)
                        .accepted());
        var marker = persisted.reviewCredits().get(persisted.reviewCredits().size() - 1);
        check(CurvedCoverCreditReview.retainedValue(persisted, marker).equals(proposal.value()));
        check(
                CurvedCoverCreditReview.retainedValue(
                                persisted,
                                new ScoreCreditsDetector.ReviewCredit(
                                        marker.line(), "DIFFERENT NAME"))
                        .isEmpty());
        check(
                CurvedCoverCreditReview.retainedValue(
                                new ScoreCreditsDetector.Page(
                                        1600,
                                        1200,
                                        -1,
                                        persisted.lines(),
                                        false,
                                        false,
                                        persisted.reviewCredits()),
                                marker)
                        .isEmpty());
        check(
                ScoreCreditsDetector.detect(
                                "Imported cover",
                                List.of(selected.get(0), persisted),
                                java.util.Set.of())
                        .unclassifiedCredits()
                        .isEmpty());
        var extraMarker =
                new ScoreCreditsDetector.ReviewCredit(
                        line("THE WILLOW COLLECTIVE", 350, 130, 900, 100), "THE WILLOW COLLECTIVE");
        var ambiguous =
                new ScoreCreditsDetector.Page(
                        persisted.width(),
                        persisted.height(),
                        persisted.notationTop(),
                        persisted.lines(),
                        false,
                        true,
                        List.of(marker, extraMarker));
        check(!CurvedCoverCreditReview.retainedValue(ambiguous, extraMarker).isEmpty());
        check(
                ScoreCreditsDetector.detect(
                                "Imported cover", List.of(ambiguous), java.util.Set.of())
                        .unclassifiedCredits()
                        .isEmpty());
        var ordinary = new ArrayList<>(persisted.lines());
        ordinary.add(marker.line());
        check(
                CurvedCoverCreditReview.retainedValue(
                                new ScoreCreditsDetector.Page(
                                        1600,
                                        1200,
                                        -1,
                                        ordinary,
                                        false,
                                        true,
                                        persisted.reviewCredits()),
                                marker)
                        .isEmpty());
        check(selection.result().title().equals("MEADOW UNDER THE STARLIGHT"));
        check(selection.result().artist().isEmpty());
        check(selection.result().composer().isEmpty());
        check(selection.result().arranger().isEmpty());
        check(selection.result().unclassifiedCredits().equals(List.of("THE LANTERN COLLECTIVE")));
        check(original.lines().get(0).text().equals("MEADOW UNDER"));
        check(
                CurvedCoverCreditReview.select(
                                        "Imported cover",
                                        List.of(original),
                                        List.of(original),
                                        proposal)
                                .accepted()
                        == false);
        check(CurvedCoverCreditReview.propose(original, observations.subList(0, 4)) == null);
        check(
                CurvedCoverCreditReview.propose(
                                original,
                                replace(
                                        3,
                                        reading(
                                                2,
                                                1,
                                                "THE LANTERN COLLECTIVE",
                                                "THE LANTERN COLECTVE",
                                                .995,
                                                true)))
                        == null);
        check(
                CurvedCoverCreditReview.propose(
                                original,
                                replace(
                                        3,
                                        reading(
                                                2,
                                                1,
                                                "THE LANTERN COLLECTIVE",
                                                "THE LANTERN COLLECTION",
                                                .995,
                                                true)))
                        == null);
        check(
                CurvedCoverCreditReview.propose(
                                original,
                                replace(
                                        3,
                                        reading(
                                                2,
                                                1,
                                                "THE LANTERN COLLECTIVE",
                                                "THE LANTEN COLLECTIVE",
                                                .989,
                                                true)))
                        == null);
        check(
                CurvedCoverCreditReview.propose(
                                original,
                                replace(
                                        3,
                                        reading(
                                                2,
                                                1,
                                                "THE LANTERN COLLECTIVE",
                                                "THE LANTEN COLLECTIVE",
                                                Double.NaN,
                                                true)))
                        == null);
        check(
                CurvedCoverCreditReview.propose(
                                original,
                                replace(
                                        3,
                                        reading(
                                                2,
                                                1.5,
                                                "THE LANTERN COLLECTIVE",
                                                "THE LANTEN COLLECTIVE",
                                                .995,
                                                true)))
                        == null);
        check(
                CurvedCoverCreditReview.propose(
                                original,
                                replace(
                                        3,
                                        reading(
                                                2,
                                                1,
                                                "THE CANDLE COLLECTIVE",
                                                "THE CANDLE COLLECTIVE",
                                                .995,
                                                true)))
                        == null);
        var excluded =
                reading(1, 1.5, "THE LANTERN COLLECTIVE", "THE LANTERN COLLECTIVE", .5, true);
        excluded =
                changeGeometry(
                        excluded, new double[] {0, 0, 410}, new double[] {350, 360, 1250, 460}, 80);
        for (boolean prepend : new boolean[] {true, false}) {
            var withExcluded = new ArrayList<>(observations);
            if (prepend) withExcluded.add(0, excluded);
            else withExcluded.add(excluded);
            var qualified = CurvedCoverCreditReview.propose(original, withExcluded);
            check(qualified != null);
            check(qualified.anchor().mapBounds()[1] == 10);
            var retainedSelection =
                    CurvedCoverCreditReview.select(
                            "Imported cover",
                            List.of(original),
                            List.of(qualified.work().page()),
                            qualified);
            check(retainedSelection.accepted());
            var retainedMarker = retainedSelection.pages().get(0).reviewCredits().get(0);
            check(retainedMarker.line().top() == 10);
            check(retainedMarker.line().bottom() == 110);
        }
        var noExact = new ArrayList<>(observations);
        noExact.set(
                1, reading(1, 1, "THE LANTERN COLLECTIVE", "THE LANTEN COLLECTIVE", .995, true));
        check(CurvedCoverCreditReview.propose(original, noExact) == null);
        var duplicate = new ArrayList<>(observations);
        duplicate.add(observations.get(1));
        check(CurvedCoverCreditReview.propose(original, duplicate) == null);
        var dual = new ArrayList<>(observations);
        for (int scale : new int[] {1, 2, 4})
            dual.add(
                    reading(
                            scale,
                            1.5,
                            "THE LANTERN COLLECTIVE",
                            "THE LANTERN COLLECTIVE",
                            .995,
                            true));
        check(CurvedCoverCreditReview.propose(original, dual) != null);
        dual.add(observations.get(1));
        check(CurvedCoverCreditReview.propose(original, dual) == null);
        for (String invalid :
                new String[] {
                    "THE\nLANTERN COLLECTIVE",
                    "THE LANTERN\u202e COLLECTIVE",
                    "THE CAFE\u0301 COLLECTIVE",
                    "PUBLISHED BY LANTERN",
                    "WWW LANTERN",
                    "A B C D E",
                    "https://lantern.test",
                    "LANTERN EDITIONS",
                    "LANTERN PRESSES",
                    "LANTERN WEBSITES",
                    "LANTERN PUBLISHERS",
                    "LANTERN PRINTED",
                    "LANTERN VOLUMES",
                    "LANTERN COLLECTIONS",
                    "LANTERN BOOKS"
                }) {
            var changed = new ArrayList<>(observations);
            for (int i : new int[] {1, 3, 5})
                changed.set(
                        i, reading(observations.get(i).scale(), 1, invalid, invalid, .995, true));
            check(CurvedCoverCreditReview.propose(original, changed) == null);
        }
        var below = new ArrayList<>(observations);
        for (int i : new int[] {1, 3, 5})
            below.set(
                    i,
                    changeGeometry(
                            observations.get(i),
                            new double[] {0, 0, 410},
                            new double[] {350, 360, 1250, 460},
                            80));
        check(CurvedCoverCreditReview.propose(original, below) == null);
        var intersect = new ArrayList<>(observations);
        for (int i : new int[] {1, 3, 5})
            intersect.set(
                    i,
                    changeGeometry(
                            observations.get(i),
                            new double[] {0, 0, 200},
                            new double[] {350, 150, 1250, 250},
                            80));
        check(CurvedCoverCreditReview.propose(original, intersect) == null);
        var drift =
                replace(
                        3,
                        changeGeometry(
                                observations.get(3),
                                new double[] {0, 0, 160},
                                new double[] {350, 110, 1250, 210},
                                80));
        check(CurvedCoverCreditReview.propose(original, drift) == null);
        var plain = new ScoreCreditsDetector.Page(1600, 1200, -1, original.lines(), false, false);
        check(CurvedCoverCreditReview.propose(plain, observations) == null);
        var notation =
                new ScoreCreditsDetector.Page(1600, 1200, 500, original.lines(), false, true);
        check(CurvedCoverCreditReview.propose(notation, observations) == null);
        var later =
                new ScoreCreditsDetector.Page(
                        1600,
                        2400,
                        650,
                        List.of(line("Composed by THE LANTERN COLLECTIVE", 100, 100, 1400, 45)),
                        true,
                        false);
        var full = List.of(original, later);
        var fullSelected = List.of(proposal.work().page(), later);
        check(
                !CurvedCoverCreditReview.select("Imported cover", full, fullSelected, proposal)
                        .accepted());
        var wrongLater = new ScoreCreditsDetector.Page(1600, 2400, 650, List.of(), true, false);
        check(
                !CurvedCoverCreditReview.select(
                                "Imported cover",
                                full,
                                List.of(proposal.work().page(), wrongLater),
                                proposal)
                        .accepted());
        double before = proposal.readings().get(1).quadratic()[2];
        observations.get(1).quadratic()[2] = 400;
        check(proposal.readings().get(1).quadratic()[2] == before);
        var exported = proposal.readings();
        exported.get(1).mapBounds()[0] = -1;
        check(proposal.readings().get(1).mapBounds()[0] >= 0);
        check(
                CurvedCoverCreditReview.select(
                                "Imported cover", List.of(original), selected, proposal)
                        .accepted());
        var exportedAnchor = proposal.anchor();
        exportedAnchor.mapBounds()[1] = 360;
        check(proposal.anchor().mapBounds()[1] == 10);
        System.out.println("checks=" + checks);
    }

    @org.junit.Test
    public void originalFictionalCurvedCreditControls() {
        checks = 0;
        main(new String[0]);
        org.junit.Assert.assertEquals(71, checks);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class PrimaryTitleBoundaryRegression {
    static int passed;

    static void check(boolean value) {
        if (!value) throw new AssertionError("boundary=" + (passed + 1));
        passed++;
    }

    static List<ScoreHeaderReadConsensus.Reading> reads(String text) {
        var out = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int scale : new int[] {1, 2, 4})
            for (String mode : List.of("tight", "horizontal"))
                for (double width : new double[] {1, 1.5})
                    out.add(new ScoreHeaderReadConsensus.Reading(scale, mode, width, text, .99));
        return out;
    }

    public static void main(String[] args) {
        var heading = new ScoreCreditsDetector.Line("0CEAN", 300, 80, 700, 160);
        var service =
                new ScoreCreditsDetector.Line("Transcribed by Example Service", 100, 220, 900, 245);
        var first =
                new ScoreCreditsDetector.Page(
                        1000,
                        1400,
                        400,
                        List.of(
                                heading,
                                new ScoreCreditsDetector.Line(
                                        "Music by Clara Example", 500, 180, 900, 205),
                                service),
                        false,
                        false,
                        List.of(new ScoreCreditsDetector.ReviewCredit(service, "Example Service")));
        var later =
                new ScoreCreditsDetector.Page(
                        1000,
                        1400,
                        300,
                        List.of(
                                new ScoreCreditsDetector.Line(
                                        "Arranged by Felix Sample", 400, 160, 900, 185)));
        var pages = List.of(first, later);
        var before = ScoreCreditsDetector.detect("OCEAN", pages, Set.of());
        var result =
                CorroboratedPrimaryTitle.select("OCEAN", pages, Set.of(), reads("OCEAN"), null);
        check(result != null && result.after().title().equals("OCEAN"));
        check(result.before().equals(before));
        check(result.originalPages().equals(pages));
        check(result.pages().get(1).equals(later));
        check(result.pages().get(0).reviewCredits().equals(first.reviewCredits()));
        check(
                result.before().artist().equals(result.after().artist())
                        && result.before().composer().equals(result.after().composer())
                        && result.before().arranger().equals(result.after().arranger()));
        check(
                new HashSet<>(result.after().unclassifiedCredits())
                        .containsAll(before.unclassifiedCredits()));
        check(!result.before().composer().isEmpty() && !result.before().arranger().isEmpty());
        var noConsensus = new ArrayList<>(reads("OCEAN"));
        for (int i = 6; i < 12; i++) {
            var r = noConsensus.get(i);
            noConsensus.set(
                    i,
                    new ScoreHeaderReadConsensus.Reading(
                            r.scale(), r.mode(), r.widthFactor(), "OTHER", r.confidence()));
        }
        check(CorroboratedPrimaryTitle.select("OCEAN", pages, Set.of(), noConsensus, null) == null);
        var invalid = new ArrayList<>(reads("OCEAN"));
        var r = invalid.get(0);
        invalid.set(
                0,
                new ScoreHeaderReadConsensus.Reading(
                        r.scale(), r.mode(), r.widthFactor(), r.text(), Double.NaN));
        check(CorroboratedPrimaryTitle.select("OCEAN", pages, Set.of(), invalid, null) == null);
        var unknown = Set.of("Clara Example", "Felix Sample");
        var knownResult =
                CorroboratedPrimaryTitle.select("OCEAN", pages, unknown, reads("OCEAN"), null);
        check(
                knownResult != null
                        && knownResult
                                .before()
                                .equals(ScoreCreditsDetector.detect("OCEAN", pages, unknown)));
        var fit = HeaderCropBudget.fit(20000, 20000);
        check(Math.round(fit.width() * 1.5) * fit.height() <= HeaderCropBudget.MAX_PIXELS);
        var low = HeaderCropBudget.plan(1000, 1400, 300, 80, 700, 160, 1, "tight");
        var high = HeaderCropBudget.plan(1000, 1400, 300, 80, 700, 160, 4, "tight");
        check(low.width() < high.width() && low.height() < high.height());
        boolean rejected = false;
        try {
            HeaderCropBudget.plan(1000, 1400, 300, 80, 700, 160, 3, "tight");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected);
        System.out.println("passed=" + passed);
    }
}

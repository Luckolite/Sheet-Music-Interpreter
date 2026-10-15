// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class MergedInstrumentTitleContinuationTest {
    @org.junit.Test
    public void syntheticControls() throws Exception {
        main(new String[0]);
    }

    static int checks;
    static ScoreCreditsDetector.Line heading =
            new ScoreCreditsDetector.Line("VIOLIN HARBOR", 5, 40, 985, 160);
    static ScoreCreditsDetector.Line continuation =
            new ScoreCreditsDetector.Line("COLLECTION", 260, 180, 900, 260);

    static ScoreCreditsDetector.Page page(
            ScoreCreditsDetector.Line h,
            ScoreCreditsDetector.Line l,
            boolean photo,
            boolean creditsOnly,
            boolean caption,
            boolean pairs,
            boolean verified) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        ls.add(h);
        ls.add(l);
        if (caption)
            ls.add(
                    new ScoreCreditsDetector.Line(
                            "8 Violin Solos with Authentic CD Tracks", 480, 300, 940, 325));
        if (pairs)
            for (int i = 0; i < 4; i++) {
                ls.add(
                        new ScoreCreditsDetector.Line(
                                "Morning Song", 650, 400 + i * 150, 950, 420 + i * 150));
                ls.add(
                        new ScoreCreditsDetector.Line(
                                new String[] {"ASHFORD", "VALE", "ROWAN", "MERRICK"}[i],
                                700,
                                422 + i * 150,
                                950,
                                442 + i * 150));
            }
        return new ScoreCreditsDetector.Page(
                1000,
                1500,
                -1,
                ls,
                creditsOnly,
                photo,
                verified ? List.of(new ScoreCreditsDetector.ReviewCredit(l, l.text())) : List.of());
    }

    static void check(
            String label, boolean expected, ScoreCreditsDetector.Page p, Set<String> known)
            throws Exception {
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "mergedInstrumentTitleContinuation",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        ScoreCreditsDetector.Line.class,
                        Set.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(null, p, p.lines().get(0), p.lines().get(1), known);
        if (result != expected)
            throw new AssertionError(label + " expected=" + expected + " actual=" + result);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        var p = page(heading, continuation, true, false, true, true, false);
        check("merged title", true, p, Set.of());
        check("known contributor", false, p, Set.of("Collection"));
        check(
                "explicit verified review",
                false,
                page(heading, continuation, true, false, true, true, true),
                Set.of());
        check(
                "not photographic",
                false,
                page(heading, continuation, false, false, true, true, false),
                Set.of());
        check(
                "credits only",
                false,
                page(heading, continuation, true, true, true, true, false),
                Set.of());
        check(
                "no anthology caption",
                false,
                page(heading, continuation, true, false, false, true, false),
                Set.of());
        check(
                "no repeated pairs",
                false,
                page(heading, continuation, true, false, true, false, false),
                Set.of());
        check(
                "no instrument prefix",
                false,
                page(
                        new ScoreCreditsDetector.Line("EVENING HARBOR", 5, 40, 985, 160),
                        continuation,
                        true,
                        false,
                        true,
                        true,
                        false),
                Set.of());
        check(
                "small name",
                false,
                page(
                        heading,
                        new ScoreCreditsDetector.Line("ASHFORD", 650, 180, 900, 210),
                        true,
                        false,
                        true,
                        true,
                        false),
                Set.of());
        check(
                "mixed case name",
                false,
                page(
                        heading,
                        new ScoreCreditsDetector.Line("Ashford", 260, 180, 900, 260),
                        true,
                        false,
                        true,
                        true,
                        false),
                Set.of());
        check(
                "separated name",
                false,
                page(
                        heading,
                        new ScoreCreditsDetector.Line("ASHFORD", 260, 270, 900, 350),
                        true,
                        false,
                        true,
                        true,
                        false),
                Set.of());
        var result = ScoreCreditsDetector.detect("Harbor Collection", List.of(p), Set.of());
        if (result.unclassifiedCredits().contains("COLLECTION"))
            throw new AssertionError("title remains credit");
        checks++;
        if (!result.unclassifiedCredits()
                .containsAll(List.of("ASHFORD", "VALE", "ROWAN", "MERRICK")))
            throw new AssertionError("lost anthology names: " + result);
        checks++;
        System.out.println("Synthetic controls passed: " + checks);
    }
}

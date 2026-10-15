// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class RepeatedWorkCreditAccentTest {
    @org.junit.Test
    public void syntheticControls() throws Exception {
        main(new String[0]);
    }

    static int checks;

    static ScoreCreditsDetector.Page page(String name, String work) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        ls.add(new ScoreCreditsDetector.Line("HARBOR CLASSICS", 100, 30, 940, 130));
        ls.add(
                new ScoreCreditsDetector.Line(
                        "8 Violin Solos with Authentic CD Tracks", 200, 240, 950, 270));
        String[] names = {name, "VALE", "MERRICK", "LINDEN"};
        for (int i = 0; i < 4; i++) {
            ls.add(
                    new ScoreCreditsDetector.Line(
                            i == 0 ? work : "Evening Song " + i,
                            650,
                            400 + i * 150,
                            950,
                            420 + i * 150));
            ls.add(new ScoreCreditsDetector.Line(names[i], 700, 422 + i * 150, 950, 442 + i * 150));
        }
        return new ScoreCreditsDetector.Page(1000, 1400, -1, ls, false, true);
    }

    static List<ScoreHeaderReadConsensus.Reading> reads(String... words) {
        var result = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        int i = 0;
        for (int scale : List.of(1, 2, 4))
            for (String mode : List.of("tight", "horizontal"))
                for (double width : List.of(1., 1.5))
                    result.add(
                            new ScoreHeaderReadConsensus.Reading(
                                    scale, mode, width, words[i++ % words.length], .99));
        return result;
    }

    static ReviewNameReadConsensus.Proposal p(
            int index,
            ScoreCreditsDetector.Page page,
            List<ScoreHeaderReadConsensus.Reading> reads) {
        return new ReviewNameReadConsensus.Proposal(index, page, page.lines().get(3), reads);
    }

    static void check(
            String label,
            boolean expected,
            List<ScoreCreditsDetector.Page> pages,
            List<ReviewNameReadConsensus.Proposal> proposals)
            throws Exception {
        var method =
                ReviewNameReadConsensus.class.getDeclaredMethod(
                        "repeatedAccentSelection",
                        List.class,
                        List.class,
                        ReviewNameReadConsensus.Proposal.class);
        method.setAccessible(true);
        Object found = method.invoke(null, pages, proposals, proposals.get(0));
        if ((found != null) != expected) throw new AssertionError(label);
        checks++;
    }

    public static void main(String[] a) throws Exception {
        var front = page("RÖWAN", "Morning Song");
        var back = page("ROWAN", "Morning Song");
        var pages = List.of(front, back);
        var target = p(0, front, reads("ROWAN", "RÓWAN"));
        var donor = p(1, back, reads("RÓWÁN"));
        check("repeated same work", true, pages, List.of(target, donor));
        check(
                "original literal supported",
                false,
                pages,
                List.of(p(0, front, reads("RÖWAN", "ROWAN")), donor));
        check(
                "independent literal consensus preserved",
                false,
                pages,
                List.of(p(0, front, reads("RÓWAN")), donor));
        check("weak donor", false, pages, List.of(target, p(1, back, reads("RÓWÁN", "ROWAN"))));
        var other = page("ROWAN", "Evening Song");
        check(
                "different work",
                false,
                List.of(front, other),
                List.of(target, p(1, other, reads("RÓWÁN"))));
        check("same page", false, List.of(front), List.of(target, p(0, front, reads("RÓWÁN"))));
        check("missing donor", false, pages, List.of(target));
        check(
                "different letters",
                false,
                pages,
                List.of(p(0, front, reads("ROVEN", "RÓVEN")), donor));
        var third = page("ROWAN", "Morning Song");
        check(
                "conflicting donors",
                false,
                List.of(front, back, third),
                List.of(target, donor, p(2, third, reads("RÒWÀN"))));
        check(
                "incomplete reads",
                false,
                pages,
                List.of(p(0, front, target.readings().subList(0, 11)), donor));
        var duplicate = new ArrayList<>(target.readings());
        duplicate.set(1, duplicate.get(0));
        check("duplicate views", false, pages, List.of(p(0, front, duplicate), donor));
        var low = new ArrayList<>(donor.readings());
        var r = low.get(0);
        low.set(
                0,
                new ScoreHeaderReadConsensus.Reading(
                        r.scale(), r.mode(), r.widthFactor(), r.text(), .5));
        check("low donor confidence", false, pages, List.of(target, p(1, back, low)));
        var selected =
                ReviewNameReadConsensus.selectDocument(
                        "Harbor Classics", pages, List.of(target, donor));
        if (selected.after().unclassifiedCredits().contains("RÖWAN")
                || !selected.after().unclassifiedCredits().contains("RÓWÁN")
                || !selected.after()
                        .unclassifiedCredits()
                        .containsAll(List.of("VALE", "MERRICK", "LINDEN")))
            throw new AssertionError("document result " + selected.after());
        checks++;
        if (!selected.before().title().equals(selected.after().title())
                || !selected.before().composer().equals(selected.after().composer())
                || !selected.before().artist().equals(selected.after().artist())
                || !selected.before().arranger().equals(selected.after().arranger()))
            throw new AssertionError("assigned fields changed");
        checks++;
        var reversed =
                ReviewNameReadConsensus.selectDocument(
                        "Harbor Classics", pages, List.of(donor, target));
        if (!reversed.after().equals(selected.after())) throw new AssertionError("proposal order");
        checks++;
        System.out.println("Repeated-credit controls passed: " + checks);
    }
}

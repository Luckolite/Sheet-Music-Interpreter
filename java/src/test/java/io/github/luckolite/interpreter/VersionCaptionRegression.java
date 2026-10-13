// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class VersionCaptionRegression {
    static int passed;

    static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        passed++;
    }

    static ScoreCreditsDetector.Line heading() {
        return new ScoreCreditsDetector.Line("Ocean Sky", 200, 60, 800, 160);
    }

    static ScoreCreditsDetector.Line caption() {
        return new ScoreCreditsDetector.Line("Piano Dersion", 280, 161, 720, 204);
    }

    static List<ScoreCreditsDetector.Page> pages() {
        return List.of(
                new ScoreCreditsDetector.Page(
                        1000, 600, -1, List.of(heading(), caption()), false, true, List.of()));
    }

    static List<ScoreCreditsDetector.Page> page(ScoreCreditsDetector.Line caption) {
        return List.of(
                new ScoreCreditsDetector.Page(
                        1000, 600, -1, List.of(heading(), caption), false, true, List.of()));
    }

    static List<ScoreHeaderReadConsensus.Reading> reads(String text) {
        var out = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int scale : new int[] {1, 2, 4})
            for (String mode : List.of("tight", "horizontal"))
                for (double width : new double[] {1, 1.5})
                    out.add(new ScoreHeaderReadConsensus.Reading(scale, mode, width, text, .98));
        return out;
    }

    static List<ScoreHeaderReadConsensus.Reading> english() {
        var out = reads("Piano Dersion");
        out.set(0, new ScoreHeaderReadConsensus.Reading(1, "tight", 1, "~Piano Version-", .875));
        out.set(2, new ScoreHeaderReadConsensus.Reading(1, "horizontal", 1, "Piano Version-", .91));
        out.set(
                6,
                new ScoreHeaderReadConsensus.Reading(2, "horizontal", 1, " Piano Version-", .92));
        return out;
    }

    static CorroboratedVersionCaption.Selection select(
            List<ScoreHeaderReadConsensus.Reading> medium,
            List<ScoreHeaderReadConsensus.Reading> english) {
        return CorroboratedVersionCaption.select(
                "Ocean Sky", pages(), Set.of(), caption(), medium, english);
    }

    public static void main(String[] args) {
        var medium = reads("Piano Dersion");
        var english = english();
        var selected = select(medium, english);
        check(selected != null, "two-reader literal selection");
        check(selected.after().title().equals("Ocean Sky Piano Version"), "literal caption joined");
        check(
                selected.before().artist().equals(selected.after().artist())
                        && selected.before().composer().equals(selected.after().composer())
                        && selected.before().arranger().equals(selected.after().arranger()),
                "roles preserved");
        check(
                selected.originalPages().equals(pages())
                        && selected.originalPages()
                                .get(0)
                                .lines()
                                .get(1)
                                .text()
                                .equals("Piano Dersion"),
                "raw source retained");
        check(
                select(medium, reads("Piano Dersion")) == null,
                "grammar cannot construct missing letters");
        var two = english();
        two.set(0, medium.get(0));
        check(select(medium, two) == null, "three literals required");
        var oneScale = english();
        oneScale.set(6, medium.get(6));
        oneScale.set(
                3,
                new ScoreHeaderReadConsensus.Reading(1, "horizontal", 1.5, "Piano Version", .96));
        check(select(medium, oneScale) == null, "two strong scales required");
        var oneMode = english();
        oneMode.set(0, medium.get(0));
        oneMode.set(
                10, new ScoreHeaderReadConsensus.Reading(4, "horizontal", 1, "Piano Version", .96));
        check(select(medium, oneMode) == null, "two exact view modes required");
        var eight = reads("Piano Dersion");
        for (int i = 0; i < 4; i++)
            eight.set(
                    i,
                    new ScoreHeaderReadConsensus.Reading(
                            eight.get(i).scale(),
                            eight.get(i).mode(),
                            eight.get(i).widthFactor(),
                            "unrelated",
                            .98));
        check(select(eight, english) == null, "nine medium witnesses required");
        check(
                select(reads("Guitar Dersion"), english) == null,
                "medium must confirm caption prefix");
        var conflict = english();
        conflict.set(
                1, new ScoreHeaderReadConsensus.Reading(1, "tight", 1.5, "Guitar Version", .99));
        check(select(medium, conflict) == null, "competing literal caption");
        check(
                select(reads("Piano Xerslon"), english) == null,
                "medium disagreement exceeds one glyph");
        var invalid = english();
        invalid.set(
                1,
                new ScoreHeaderReadConsensus.Reading(1, "tight", 1.5, "Piano Dersion", Double.NaN));
        check(select(medium, invalid) == null, "nonfinite confidence");
        invalid = english();
        invalid.set(1, new ScoreHeaderReadConsensus.Reading(1, null, 1.5, "Piano Dersion", .99));
        check(select(medium, invalid) == null, "missing mode");
        invalid = english();
        invalid.set(1, invalid.get(0));
        check(select(medium, invalid) == null, "duplicate view");
        check(select(medium.subList(0, 11), english) == null, "incomplete grid");
        invalid = english();
        invalid.set(1, new ScoreHeaderReadConsensus.Reading(8, "tight", 1.5, "Piano Dersion", .99));
        check(select(medium, invalid) == null, "unrecognized scale");
        invalid = english();
        invalid.set(
                1, new ScoreHeaderReadConsensus.Reading(1, "tight", 1.5, "Piano\nVersion", .99));
        check(select(medium, invalid) == null, "multiline evidence");
        check(
                CorroboratedVersionCaption.select(
                                "Ocean Sky",
                                pages(),
                                Set.of(),
                                new ScoreCreditsDetector.Line("Piano Dersion", 281, 161, 720, 204),
                                medium,
                                english)
                        == null,
                "evidence must bind exact source row");
        check(
                CorroboratedVersionCaption.nominate("Ocean Sky", pages(), Set.of("Ocean Sky"))
                        == null,
                "known contributor heading protected");
        check(
                CorroboratedVersionCaption.nominate("Ocean Sky", pages(), Set.of("Piano Dersion"))
                        == null,
                "known raw contributor protected");
        check(
                CorroboratedVersionCaption.select(
                                "Ocean Sky",
                                pages(),
                                Set.of("Piano Version"),
                                caption(),
                                medium,
                                english)
                        == null,
                "known literal contributor protected");
        var protectedPage =
                new ScoreCreditsDetector.Page(
                        1000,
                        600,
                        -1,
                        List.of(heading(), caption()),
                        false,
                        true,
                        List.of(new ScoreCreditsDetector.ReviewCredit(caption(), "Piano Dersion")));
        check(
                CorroboratedVersionCaption.nominate("Ocean Sky", List.of(protectedPage), Set.of())
                        == null,
                "review evidence protected");
        check(
                CorroboratedVersionCaption.nominate(
                                "Ocean Sky",
                                page(
                                        new ScoreCreditsDetector.Line(
                                                "Piano Dersion", 280, 400, 720, 443)),
                                Set.of())
                        == null,
                "distant row");
        check(
                CorroboratedVersionCaption.nominate(
                                "Ocean Sky",
                                page(
                                        new ScoreCreditsDetector.Line(
                                                "Piano Dersion", 280, 10, 720, 53)),
                                Set.of())
                        == null,
                "row above title");
        check(
                CorroboratedVersionCaption.nominate(
                                "Ocean Sky",
                                page(
                                        new ScoreCreditsDetector.Line(
                                                "Piano Version", 280, 161, 720, 204)),
                                Set.of())
                        == null,
                "correct existing caption untouched");
        var unknown = new ScoreCreditsDetector.Line("Cavern Dersion", 280, 161, 720, 204);
        check(
                CorroboratedVersionCaption.select(
                                "Ocean Sky",
                                page(unknown),
                                Set.of(),
                                unknown,
                                reads("Cavern Dersion"),
                                reads("Cavern Version"))
                        == null,
                "unknown variant not guessed");
        check(
                CorroboratedVersionCaption.nominate(
                                "Ocean Sky",
                                page(
                                        new ScoreCreditsDetector.Line(
                                                "Arranged by Piano Dersion", 280, 161, 720, 204)),
                                Set.of())
                        == null,
                "explicit role row protected");
        var later =
                new ScoreCreditsDetector.Page(
                        1000,
                        600,
                        300,
                        List.of(
                                new ScoreCreditsDetector.Line(
                                        "Arranged by Alice Example", 200, 60, 800, 90)),
                        false,
                        false,
                        List.of());
        var whole = new ArrayList<>(pages());
        whole.add(later);
        var all =
                CorroboratedVersionCaption.select(
                        "Ocean Sky", whole, Set.of(), caption(), medium, english);
        check(
                all != null
                        && all.pages().get(1).equals(later)
                        && all.before().arranger().equals(all.after().arranger()),
                "later page and arranger retained");
        var malformed =
                new ScoreCreditsDetector.Page(
                        Float.NaN, 600, -1, List.of(heading(), caption()), false, true, List.of());
        check(
                CorroboratedVersionCaption.nominate("Ocean Sky", List.of(malformed), Set.of())
                        == null,
                "nonfinite page");
        check(
                CorroboratedVersionCaption.nominate(
                                "Ocean Sky",
                                List.of(
                                        new ScoreCreditsDetector.Page(
                                                1000,
                                                600,
                                                -1,
                                                List.of(heading(), caption()),
                                                true,
                                                true,
                                                List.of())),
                                Set.of())
                        == null,
                "credits-only page");
        check(
                selected.before().unclassifiedCredits().equals(List.of("Piano Dersion"))
                        && selected.after().unclassifiedCredits().isEmpty(),
                "only title-owned caption leaves review");
        var correct = new ScoreCreditsDetector.Line("Piano Version", 280, 161, 720, 204);
        check(
                ScoreCreditsDetector.detect("Ocean Sky", page(correct), Set.of())
                        .title()
                        .equals("Ocean Sky Piano Version"),
                "short import hint cannot discard literal version");
        var protectedCorrect =
                new ScoreCreditsDetector.Page(
                        1000,
                        600,
                        -1,
                        List.of(heading(), correct),
                        false,
                        true,
                        List.of(new ScoreCreditsDetector.ReviewCredit(correct, "Piano Version")));
        check(
                ScoreCreditsDetector.detect("Ocean Sky", List.of(protectedCorrect), Set.of())
                        .title()
                        .equals("Ocean Sky"),
                "typed contributor review prevents subtitle ownership");
        check(
                ScoreCreditsDetector.detect("Ocean Sky", page(correct), Set.of("Piano Version"))
                        .title()
                        .equals("Ocean Sky"),
                "known name prevents subtitle ownership");
        var name = new ScoreCreditsDetector.Line("Alice Example", 300, 220, 700, 249);
        var extra =
                new ScoreCreditsDetector.Page(
                        1000, 600, -1, List.of(heading(), caption(), name), false, true, List.of());
        var preserved =
                CorroboratedVersionCaption.select(
                        "Ocean Sky", List.of(extra), Set.of(), caption(), medium, english);
        check(
                preserved != null
                        && preserved.after().unclassifiedCredits().equals(List.of("Alice Example")),
                "unrelated bare credit review retained");
        var damaged = new ScoreCreditsDetector.Line("Vaxert Dersiot", 280, 161, 720, 204);
        check(
                CorroboratedVersionCaption.select(
                                "Ocean Sky", page(damaged), Set.of(), damaged, medium, english)
                        != null,
                "damaged type only nominates literal reread");
        var numbered = new ScoreCreditsDetector.Line("Piano Dersion 2", 280, 161, 720, 204);
        check(
                CorroboratedVersionCaption.select(
                                "Ocean Sky",
                                page(numbered),
                                Set.of(),
                                numbered,
                                reads("Piano Version 2"),
                                reads("Piano Version 3"))
                        == null,
                "two readers cannot substitute edition numbers");
        check(
                CorroboratedVersionCaption.select(
                                "Ocean Sky",
                                page(numbered),
                                Set.of(),
                                numbered,
                                reads("Piano Version 3"),
                                reads("Piano Version 3"))
                        == null,
                "raw edition number retained");
        check(
                CorroboratedVersionCaption.select(
                                "Ocean Sky",
                                page(numbered),
                                Set.of(),
                                numbered,
                                reads("Piano Version 2"),
                                reads("Piano Version 2"))
                        != null,
                "agreed unchanged edition number accepted");
        var bareName = new ScoreCreditsDetector.Line("Alice Dersion", 280, 161, 720, 204);
        check(
                CorroboratedVersionCaption.select(
                                "Ocean Sky",
                                page(bareName),
                                Set.of(),
                                bareName,
                                reads("Alice Dersion"),
                                reads("Alice Version"))
                        == null,
                "unrecognized type remains review evidence");
        System.out.println("passed=" + passed);
    }
}

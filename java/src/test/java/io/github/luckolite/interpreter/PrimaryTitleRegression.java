// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class PrimaryTitleRegression {
    static int passed;

    static void check(boolean value) {
        if (!value) throw new AssertionError("control=" + (passed + 1));
        passed++;
    }

    static List<ScoreCreditsDetector.Page> page(String text) {
        return List.of(
                new ScoreCreditsDetector.Page(
                        1000,
                        1400,
                        280,
                        List.of(new ScoreCreditsDetector.Line(text, 300, 80, 700, 160))));
    }

    static List<ScoreHeaderReadConsensus.Reading> reads(String text) {
        var out = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int s : new int[] {1, 2, 4})
            for (String m : List.of("tight", "horizontal"))
                for (double f : new double[] {1, 1.5})
                    out.add(new ScoreHeaderReadConsensus.Reading(s, m, f, text, .99));
        return out;
    }

    public static void main(String[] args) {
        var p = page("0CEAN");
        var empty = Set.<String>of();
        var r = CorroboratedPrimaryTitle.select("OCEAN", p, empty, reads("OCEAN"), null);
        check(r != null && r.after().title().equals("OCEAN"));
        check(r.originalPages().equals(p));
        check(
                r.before().artist().equals(r.after().artist())
                        && r.before().composer().equals(r.after().composer())
                        && r.before().arranger().equals(r.after().arranger()));
        check(CorroboratedPrimaryTitle.select("OCEAN", p, empty, reads("OTHER"), null) == null);
        check(CorroboratedPrimaryTitle.select("OTHER", p, empty, reads("OCEAN"), null) == null);
        check(
                CorroboratedPrimaryTitle.select(
                                "OCEAN", p, empty, reads("OCEAN").subList(0, 11), null)
                        == null);
        check(CorroboratedPrimaryTitle.nominate("Room 101", page("Room 101"), empty) == null);
        check(CorroboratedPrimaryTitle.nominate("OCEAN", page("OCEAN"), empty) == null);
        check(CorroboratedPrimaryTitle.nominate("imported", page("The Harbor"), empty) == null);
        check(CorroboratedPrimaryTitle.nominate("OCEAN", null, empty) == null);
        check(CorroboratedPrimaryTitle.nominate(null, p, empty) == null);
        var spaced =
                CorroboratedPrimaryTitle.select(
                        "Tender light",
                        page("Ten°er1ight"),
                        empty,
                        reads("Tenderlight"),
                        reads("Tender light"));
        check(spaced != null && spaced.after().title().equals("Tender light"));
        var duplicate = new ArrayList<>(reads("OCEAN"));
        duplicate.set(1, duplicate.get(0));
        check(CorroboratedPrimaryTitle.select("OCEAN", p, empty, duplicate, null) == null);
        var low =
                reads("OCEAN").stream()
                        .map(
                                v ->
                                        new ScoreHeaderReadConsensus.Reading(
                                                v.scale(), v.mode(), v.widthFactor(), v.text(), .5))
                        .toList();
        check(CorroboratedPrimaryTitle.select("OCEAN", p, empty, low, null) == null);
        var cover =
                List.of(
                        new ScoreCreditsDetector.Page(
                                1000, 1400, -1, page("0CEAN").get(0).lines(), false, true));
        check(CorroboratedPrimaryTitle.select("OCEAN", cover, empty, reads("OCEAN"), null) != null);
        var multi = new ArrayList<>(p);
        multi.add(new ScoreCreditsDetector.Page(1000, 1400, -1, List.of()));
        check(CorroboratedPrimaryTitle.select("OCEAN", multi, empty, reads("OCEAN"), null) != null);
        var extended = new ArrayList<>(p.get(0).lines());
        extended.add(new ScoreCreditsDetector.Line("Allegro", -1, 450, 150, 470));
        var raw = List.of(new ScoreCreditsDetector.Page(1000, 1400, 280, extended));
        var retained = CorroboratedPrimaryTitle.select("OCEAN", raw, empty, reads("OCEAN"), null);
        check(retained != null && retained.pages().get(0).lines().get(1).equals(extended.get(1)));
        var badMode = new ArrayList<>(reads("Tender light"));
        var first = badMode.get(0);
        badMode.set(
                0,
                new ScoreHeaderReadConsensus.Reading(
                        first.scale(),
                        null,
                        first.widthFactor(),
                        first.text(),
                        first.confidence()));
        check(
                CorroboratedPrimaryTitle.select(
                                "Tender light",
                                page("Ten°er1ight"),
                                empty,
                                reads("Tenderlight"),
                                badMode)
                        == null);
        check(
                CorroboratedPrimaryTitle.select(
                                "Tender light",
                                page("Ten°er1ight"),
                                empty,
                                reads("Tenderlight"),
                                reads("\nTender light\n"))
                        == null);
        check(
                CorroboratedPrimaryTitle.select(
                                "Tender light",
                                page("Ten°er1ight"),
                                empty,
                                reads("Tenderlight"),
                                reads("Tender light\u200b"))
                        == null);
        check(
                CorroboratedPrimaryTitle.select(
                                "The Harbor No. 17",
                                page("The Harbor No. 17 Opus 31"),
                                empty,
                                reads("The Harbor No. 17"),
                                null)
                        == null);
        check(
                CorroboratedPrimaryTitle.select(
                                "Northern Lights",
                                page("N0RTHERN LIGHTS - PIANO VERSION"),
                                empty,
                                reads("Northern Lights"),
                                null)
                        == null);
        check(
                CorroboratedPrimaryTitle.nominate(
                                "The Harbor No. 17", page("The Harbor No. 17"), empty)
                        == null);
        var caseOnly = CorroboratedPrimaryTitle.select("Ocean", p, empty, reads("ocean"), null);
        check(caseOnly != null && caseOnly.after().title().equalsIgnoreCase("ocean"));
        System.out.println("passed=" + passed);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original fictional source ownership, work identity and contributor vetoes. */
public final class CoherentWorkOwnershipRegression {
    static int passed;

    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++;
    }

    static List<ScoreCreditsDetector.Page> pages(String caption) {
        return List.of(
                new ScoreCreditsDetector.Page(
                        1400,
                        2000,
                        260,
                        List.of(
                                new ScoreCreditsDetector.Line("The Amber Gote", 110, 212, 320, 245),
                                new ScoreCreditsDetector.Line(
                                        "Flauto Solista", 500, 130, 1080, 200),
                                new ScoreCreditsDetector.Line(caption, 70, 270, 620, 340))),
                new ScoreCreditsDetector.Page(
                        1400,
                        2000,
                        240,
                        List.of(
                                new ScoreCreditsDetector.Line(
                                        "Flauto Solista", 485, 90, 1070, 160))));
    }

    static MusicMetadataCatalogue catalogue(Set<String> people) {
        return new MusicMetadataCatalogue(
                List.of(
                        new MusicMetadataCatalogue.Work(
                                "fictional-amber", 7, "nocturne", "The Amber Gate")),
                people,
                Set.of("flauto"));
    }

    static Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> medium(
            List<ScoreCreditsDetector.Page> pages) {
        var l = pages.get(0).lines();
        return Map.of(
                l.get(0),
                VersionCaptionRegression.reads("The Amber Gate"),
                l.get(1),
                VersionCaptionRegression.reads("Flauto Solista"),
                l.get(2),
                CoherentWorkReadRegression.grid("horizontal", "NOCTURNEVII", "NOCTURNEVIIer", .96));
    }

    static Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> secondary(
            List<ScoreCreditsDetector.Page> pages) {
        return Map.of(
                pages.get(0).lines().get(2),
                CoherentWorkReadRegression.grid("tight", "NocturneVII", "NOCTURNEVIIer", .72));
    }

    static CorroboratedCatalogueWorkTitle.Selection select(
            List<ScoreCreditsDetector.Page> p, MusicMetadataCatalogue c) {
        return CorroboratedCatalogueWorkTitle.select(
                "unrelated-import", p, Set.of(), medium(p), secondary(p), c);
    }

    public static void main(String[] args) {
        var p = pages("NOCTURNEVIIer");
        var c = catalogue(Set.of());
        var selected = select(p, c);
        check(selected != null, "independent literal work selection");
        check(
                selected.after().title().equals("The Amber Gate NOCTURNE VII"),
                "source literals joined");
        check(
                selected.before().artist().equals(selected.after().artist())
                        && selected.before().composer().equals(selected.after().composer())
                        && selected.before().arranger().equals(selected.after().arranger()),
                "contributors preserved");
        check(
                CorroboratedCatalogueWorkTitle.select("unrelated-import", p, Set.of(), medium(p), c)
                        == null,
                "primary half-grid alone cannot select");
        check(
                select(p, catalogue(Set.of("The Amber Gote"))) == null,
                "positive printed person veto");
        check(select(pages("NOCTURNEVI"), c) == null, "printed original ordinal preserved");
        check(select(List.of(p.get(0)), c) == null, "repeated part required");
        var ambiguous =
                new MusicMetadataCatalogue(
                        List.of(
                                new MusicMetadataCatalogue.Work(
                                        "fictional-one", 7, "nocturne", "The Amber Gate"),
                                new MusicMetadataCatalogue.Work(
                                        "fictional-two", 7, "nocturne", "The Amber Gate")),
                        Set.of(),
                        Set.of("flauto"));
        check(select(p, ambiguous) == null, "ambiguous public work rejected");
        var first = p.get(0);
        var credit =
                new ScoreCreditsDetector.ReviewCredit(first.lines().get(2), "Unresolved Service");
        var protectedPages =
                List.of(
                        new ScoreCreditsDetector.Page(
                                first.width(),
                                first.height(),
                                first.notationTop(),
                                first.lines(),
                                false,
                                false,
                                List.of(credit)),
                        p.get(1));
        check(select(protectedPages, c) == null, "review credit ownership preserved");
        System.out.println("passed=" + passed);
    }
}

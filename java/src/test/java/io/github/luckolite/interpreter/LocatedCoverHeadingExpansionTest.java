// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class LocatedCoverHeadingExpansionTest {
    static int checks;

    static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }

    static ScoreCreditsDetector.Line original =
            new ScoreCreditsDetector.Line("ARBOR SONG", 153, 700, 741, 839);

    static ScoreCreditsDetector.Page page(boolean photo, ScoreCreditsDetector.Line... extra) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(original);
        lines.addAll(Arrays.asList(extra));
        return new ScoreCreditsDetector.Page(1800, 1000, -1, lines, false, photo);
    }

    static LocatedCoverHeadingEvidence read(
            ScoreCreditsDetector.Page p, List<String> texts, List<Float> scores) {
        return LocatedCoverHeadingExpansion.confirm(p, original, texts, scores);
    }

    @org.junit.Test
    public void originalExpansionControls() {
        var p = page(true);
        var high = List.of(.99f, .98f, .97f);
        check(
                read(p, List.of("HARBOR SONG", "HARBOR SONG", "HARBOR SONG"), high) != null,
                "leading letter recovered");
        check(
                read(p, List.of("ARBOR SONGS", "ARBOR SONGS", "ARBOR SONGS"), high) != null,
                "trailing letter recovered");
        check(
                read(p, List.of("ARBOR LONG", "ARBOR LONG", "ARBOR LONG"), high) == null,
                "internal substitution rejected");
        check(
                read(p, List.of("2ARBOR SONG", "2ARBOR SONG", "2ARBOR SONG"), high) == null,
                "identifier addition rejected");
        check(
                read(p, List.of("A ARBOR SONG", "A ARBOR SONG", "A ARBOR SONG"), high) == null,
                "new word rejected");
        check(
                read(p, List.of("HARBOR SONG", "ARBOR SONG", "HARBOR SONG"), high) == null,
                "disagreement rejected");
        check(
                read(
                                p,
                                List.of("HARBOR SONG", "HARBOR SONG", "HARBOR SONG"),
                                List.of(.99f, .94f, .99f))
                        == null,
                "weak reading rejected");
        check(
                LocatedCoverHeadingExpansion.region(page(false), original) == null,
                "nonphoto excluded");
        check(
                LocatedCoverHeadingExpansion.region(
                                page(
                                        true,
                                        new ScoreCreditsDetector.Line(
                                                "CAPTION", 130, 710, 149, 750)),
                                original)
                        == null,
                "neighbor collision excluded");
        var region = LocatedCoverHeadingExpansion.region(p, original);
        check(
                region.left() < 137 && region.top() == 700 && region.bottom() == 839,
                "bounded horizontal only");

        check(
                LocatedCoverHeadingExpansion.region(
                                new ScoreCreditsDetector.Page(
                                        Float.NaN, 1000, -1, List.of(original), false, true),
                                original)
                        == null,
                "nonfinite page rejected");
        check(
                LocatedCoverHeadingExpansion.region(
                                new ScoreCreditsDetector.Page(
                                        1800, 0, -1, List.of(original), false, true),
                                original)
                        == null,
                "empty page rejected");
        for (var bad :
                List.of(
                        new ScoreCreditsDetector.Line("ARBOR SONG", -1, 700, 741, 839),
                        new ScoreCreditsDetector.Line("ARBOR SONG", 153, 700, 1801, 839),
                        new ScoreCreditsDetector.Line("ARBOR SONG", 153, 700, 741, 1001),
                        new ScoreCreditsDetector.Line("ARBOR SONG", 153, Float.NaN, 741, 839),
                        new ScoreCreditsDetector.Line("ARBOR SONG", 741, 700, 153, 839))) {
            check(
                    LocatedCoverHeadingExpansion.region(
                                    new ScoreCreditsDetector.Page(
                                            1800, 1000, -1, List.of(bad), false, true),
                                    bad)
                            == null,
                    "invalid line bounds rejected");
        }
        assertCount();
    }

    static void assertCount() {
        org.junit.Assert.assertEquals(17, checks);
    }
}

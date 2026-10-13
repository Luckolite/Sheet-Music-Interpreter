// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original fictional literal and competing-reader controls; no score-derived text. */
public final class ComplementaryCaptionRegression {
    static int passed;

    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++;
    }

    static List<ScoreHeaderReadConsensus.Reading> english() {
        var out = VersionCaptionRegression.reads("Piano Dersion");
        for (int i : new int[] {2, 6, 10}) {
            var v = out.get(i);
            out.set(
                    i,
                    new ScoreHeaderReadConsensus.Reading(
                            v.scale(),
                            v.mode(),
                            v.widthFactor(),
                            "Piano Version",
                            i == 6 ? .88 : .94));
        }
        return out;
    }

    static List<ScoreHeaderReadConsensus.Reading> medium() {
        var out = VersionCaptionRegression.reads("Piano Dersion");
        out.set(4, new ScoreHeaderReadConsensus.Reading(2, "tight", 1, "Piano Version", .94));
        return out;
    }

    public static void main(String[] args) {
        var m = medium();
        var e = english();
        var selected = VersionCaptionRegression.select(m, e);
        check(selected != null, "independent literal supplies other mode");
        check(selected.after().title().equals("Ocean Sky Piano Version"), "literal preserved");
        check(
                selected.before().artist().equals(selected.after().artist())
                        && selected.before().composer().equals(selected.after().composer())
                        && selected.before().arranger().equals(selected.after().arranger()),
                "roles preserved");
        check(
                VersionCaptionRegression.select(VersionCaptionRegression.reads("Piano Dersion"), e)
                        == null,
                "near medium cannot supply exact missing mode");
        var low = medium();
        low.set(4, new ScoreHeaderReadConsensus.Reading(2, "tight", 1, "Piano Version", .89));
        check(VersionCaptionRegression.select(low, e) == null, "weak independent literal rejected");
        var same = VersionCaptionRegression.reads("Piano Dersion");
        same.set(6, new ScoreHeaderReadConsensus.Reading(2, "horizontal", 1, "Piano Version", .98));
        check(
                VersionCaptionRegression.select(same, e) == null,
                "same mode cannot supply diversity");
        var wrong = medium();
        wrong.set(4, new ScoreHeaderReadConsensus.Reading(2, "tight", 1, "Guitar Version", .99));
        check(
                VersionCaptionRegression.select(wrong, e) == null,
                "different complete caption cannot supply view");
        var two = english();
        two.set(6, VersionCaptionRegression.reads("Piano Dersion").get(6));
        check(
                VersionCaptionRegression.select(m, two) == null,
                "three English literals still required");
        var scale = english();
        scale.set(
                10, new ScoreHeaderReadConsensus.Reading(4, "horizontal", 1, "Piano Version", .88));
        check(
                VersionCaptionRegression.select(m, scale) == null,
                "two strong English scales still required");
        var sparse = medium();
        for (int i = 0; i < 4; i++) {
            var v = sparse.get(i);
            sparse.set(
                    i,
                    new ScoreHeaderReadConsensus.Reading(
                            v.scale(), v.mode(), v.widthFactor(), "unrelated", .98));
        }
        check(
                VersionCaptionRegression.select(sparse, e) == null,
                "nine medium prefix witnesses still required");
        System.out.println("passed=" + passed);
    }
}

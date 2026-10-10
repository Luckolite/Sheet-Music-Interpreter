// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import io.github.luckolite.interpreter.ScoreCreditsDetector;

/** Fictional checks for contributor crop routing and unchanged source selection. */
public final class BibliographicCropRoutingControls {
    private static int checks;

    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }

    private static ScoreCreditsDetector.Line line(
            String text, float left, float top, float right, float bottom) {
        return new ScoreCreditsDetector.Line(text, left, top, right, bottom);
    }

    private static ScoreCreditsDetector.Page page(ScoreCreditsDetector.Line... lines) {
        var rows = new ArrayList<ScoreCreditsDetector.Line>();
        rows.add(line("River Song", 600, 20, 1200, 70));
        rows.addAll(List.of(lines));
        return new ScoreCreditsDetector.Page(1800, 2400, 400, rows, true);
    }

    public static void main(String[] args) {
        var label = line("Words and Music by MAYA REED,", 1000, 100, 1700, 130);
        var continuation = line("ALEX RIVERS, JAMIE BROOKS", 1000, 136, 1700, 166);
        var p = page(label, continuation);
        var before = List.copyOf(p.lines());
        check(ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(p, label));
        check(ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(p, continuation));
        check(
                !ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(
                        page(continuation), continuation));
        var otherColumn = line(continuation.text(), 100, 136, 700, 166);
        check(
                !ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(
                        page(label, otherColumn), otherColumn));
        check(
                !ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(
                        p, p.lines().get(0)));
        check(
                !ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(
                        p, line(label.text(), 900, 100, 1600, 130)));
        var caption = line("(Quietly)", 700, 210, 1100, 240);
        check(
                !ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(
                        page(caption), caption));
        var below = line("Composed by MAYA REED", 1000, 700, 1700, 730);
        check(
                !ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(
                        page(below), below));
        var tiny = line("Composed by MAYA REED", 1000, 100, 1700, 110);
        check(
                !ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(
                        page(tiny), tiny));
        var arranged = line("Arranged by ALEX RIVERS", 1000, 100, 1700, 130);
        check(
                ScoreCreditsDetector.bibliographicCreditLineNeedsHorizontalMargins(
                        page(arranged), arranged));
        check(p.lines().equals(before));
        check(
                ScoreCreditsDetector.bibliographicLineCandidates(p)
                        .containsAll(List.of(label, continuation)));
        check(
                ScoreCreditsDetector.mergeBibliographicLineConsensus(
                                p,
                                continuation,
                                List.of("ALEX RIVERS", "ALEX RIVERS", "ALEX RIVERS"))
                        .equals(p.lines()));
        check(
                ScoreCreditsDetector.mergeBibliographicLineConsensus(
                                p,
                                continuation,
                                List.of(
                                        "Arranged by ALEX RIVERS, JAMIE BROOKS",
                                        "Arranged by ALEX RIVERS, JAMIE BROOKS",
                                        "Arranged by ALEX RIVERS, JAMIE BROOKS"))
                        .equals(p.lines()));
        System.out.println("PASS " + checks + " routing controls");
    }
}

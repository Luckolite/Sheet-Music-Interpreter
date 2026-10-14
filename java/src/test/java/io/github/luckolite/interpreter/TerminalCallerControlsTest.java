// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import io.github.luckolite.interpreter.ScoreCreditsDetector;
import io.github.luckolite.interpreter.ScoreCreditsDetector.*;

public class TerminalCallerControlsTest {
    static void ck(boolean b, String n) {
        if (!b) throw new AssertionError(n);
    }

    @org.junit.Test
    public void originalControls() {
        var title = new Line("ORIGINAL SONG", 450, 120, 1300, 230);
        var credit = new Line("-JORDAN RA", 1397, 309, 1601, 345);
        var p = new Page(1800, 2500, 461, List.of(title, credit));
        ck(
                ScoreCreditsDetector.terminalCreditStrokeCandidates(p).equals(List.of(credit)),
                "header candidate");
        var repaired =
                ScoreCreditsDetector.mergeTerminalCreditStroke(
                        p,
                        credit,
                        List.of("JORDAN RAI", "JORDAN RAI", "JORDAN RAI"),
                        List.of(.94f, .95f, .96f),
                        List.of(true, true, true));
        ck(repaired.get(1).text().equals("JORDAN RAI"), "merge pixel-backed terminal");
        ck(repaired.get(0).equals(title), "preserve title");
        ck(
                ScoreCreditsDetector.terminalCreditStrokeCandidates(
                                new Page(1800, 2500, 461, p.lines(), false, true))
                        .isEmpty(),
                "photo negative");
        ck(
                ScoreCreditsDetector.terminalCreditStrokeCandidates(
                                new Page(1800, 2500, 461, p.lines(), true, false))
                        .isEmpty(),
                "credits only negative");
        ck(
                ScoreCreditsDetector.terminalCreditStrokeCandidates(
                                new Page(1800, 2500, 300, p.lines()))
                        .isEmpty(),
                "below staff negative");
        ck(
                ScoreCreditsDetector.terminalCreditStrokeCandidates(
                                new Page(1800, 2500, 461, List.of(credit)))
                        .isEmpty(),
                "no title negative");
        ck(
                ScoreCreditsDetector.mergeTerminalCreditStroke(
                                p,
                                credit,
                                List.of("JORDAN RAI", "JORDAN RAI", "JORDAN RAI"),
                                List.of(.94f, .95f, .96f),
                                List.of(true, false, true))
                        .equals(p.lines()),
                "missing stroke preserves page");
        System.out.println("8 original caller controls passed");
    }
}

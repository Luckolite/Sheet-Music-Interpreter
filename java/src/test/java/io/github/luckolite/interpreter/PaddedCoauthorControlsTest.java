// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import io.github.luckolite.interpreter.ScoreCreditsDetector;
import io.github.luckolite.interpreter.ScoreCreditsDetector.*;

public class PaddedCoauthorControlsTest {
    static int count = 0;

    static void ck(boolean b, String n) {
        count++;
        if (!b) throw new AssertionError(n);
    }

    @org.junit.Test
    public void originalControls() {
        var line = new Line("Jordan Rai, Emily Wells", 1200, 400, 1650, 450);
        var explicit = new Line("Music by Jordan RAI and Emily Wellis", 200, 2200, 1000, 2240);
        var correct = new Line("Music by Jordan Rai and Emily Wellis", 200, 2200, 1000, 2240);
        var p = new Page(1800, 2500, 570, List.of(line, correct));
        var text =
                List.of(
                        "Jordan Rai, Emily Wellis",
                        "Jordan Rai, Emily Wellis",
                        "Jordan Rai, Emily Wellis");
        var scores = List.of(.97f, .98f, .96f);
        ck(
                ScoreCreditsDetector.paddedCoauthorCandidates(p).equals(List.of(line)),
                "corroborated pair");
        ck(
                ScoreCreditsDetector.mergePaddedCoauthor(p, line, text, scores)
                        .get(0)
                        .text()
                        .equals(text.get(0)),
                "three readings restore letter");
        ck(
                ScoreCreditsDetector.paddedCoauthorCandidates(
                                new Page(1800, 2500, 570, List.of(line)))
                        .isEmpty(),
                "no explicit corroboration");
        ck(
                ScoreCreditsDetector.paddedCoauthorCandidates(
                                new Page(
                                        1800,
                                        2500,
                                        570,
                                        List.of(
                                                line,
                                                new Line(
                                                        "Music by Different Name and Emily Wellis",
                                                        200,
                                                        2200,
                                                        1000,
                                                        2240))))
                        .isEmpty(),
                "different people");
        ck(
                ScoreCreditsDetector.paddedCoauthorCandidates(
                                new Page(1800, 2500, 570, p.lines(), false, true))
                        .isEmpty(),
                "photo");
        ck(
                ScoreCreditsDetector.paddedCoauthorCandidates(
                                new Page(1800, 2500, 570, p.lines(), true, false))
                        .isEmpty(),
                "creditsOnly");
        ck(
                ScoreCreditsDetector.paddedCoauthorCandidates(new Page(1800, 2500, 390, p.lines()))
                        .isEmpty(),
                "below staff");
        ck(
                ScoreCreditsDetector.mergePaddedCoauthor(p, line, text, List.of(.97f, .93f, .96f))
                        .equals(p.lines()),
                "low confidence");
        ck(
                ScoreCreditsDetector.mergePaddedCoauthor(
                                p,
                                line,
                                List.of(text.get(0), "Jordan Rai, Emily Welles", text.get(0)),
                                scores)
                        .equals(p.lines()),
                "disagreement");
        ck(
                ScoreCreditsDetector.mergePaddedCoauthor(
                                p, line, text, List.of(.97f, Float.NaN, .96f))
                        .equals(p.lines()),
                "nan");
        ck(
                ScoreCreditsDetector.mergePaddedCoauthor(p, line, text, List.of(.97f, 1.1f, .96f))
                        .equals(p.lines()),
                "out of range");
        ck(
                ScoreCreditsDetector.mergePaddedCoauthor(
                                p, line, text.subList(0, 2), scores.subList(0, 2))
                        .equals(p.lines()),
                "two scales");
        ck(
                ScoreCreditsDetector.mergePaddedCoauthor(
                                p,
                                line,
                                List.of(
                                        "Jordan Rai, Emily Welles",
                                        "Jordan Rai, Emily Welles",
                                        "Jordan Rai, Emily Welles"),
                                scores)
                        .equals(p.lines()),
                "uncorroborated agreement");
        ck(
                ScoreCreditsDetector.paddedCoauthorRegion(p, line).left() == 1188,
                "width scaled margin");
        System.out.println(count + " original controls passed");
    }
}

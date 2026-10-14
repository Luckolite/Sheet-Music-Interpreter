// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original engraved part-heading and opening-work layouts. */
public class EngravedPartTitleTest {
    static ScoreCreditsDetector.Result read(
            String part, String work, float workY, boolean photo, Set<String> known) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        if (!part.isEmpty()) ls.add(new ScoreCreditsDetector.Line(part, 600, 160, 1200, 220));
        ls.add(new ScoreCreditsDetector.Line("Evening Wind", 160, 225, 400, 270));
        ls.add(new ScoreCreditsDetector.Line(work, 100, workY, 760, workY + 80));
        return ScoreCreditsDetector.detect(
                "Evening Wind",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 280, ls, false, photo)),
                known);
    }

    @Test
    public void principalPartDoesNotReplaceWorkTitle() {
        assertEquals(
                "Evening Wind CONCERTO II",
                read("Violino Principale", "CONCERTOII", 285, false, Set.of()).title());
    }

    @Test
    public void singleDamagedPartLetterIsSupported() {
        assertEquals(
                "Evening Wind CONCERTO II",
                read("Violino Princjpale", "CONCERTOII", 285, false, Set.of()).title());
    }

    @Test
    public void anotherInstrumentPartIsSupported() {
        assertEquals(
                "Evening Wind SONATA 3",
                read("Oboe Primo", "SONATA 3", 285, false, Set.of()).title());
    }

    @Test
    public void noPartContextDoesNotJoin() {
        assertEquals("Evening Wind", read("", "CONCERTOII", 285, false, Set.of()).title());
    }

    @Test
    public void distantWorkLabelDoesNotJoin() {
        assertEquals(
                "Evening Wind",
                read("Violino Principale", "CONCERTOII", 600, false, Set.of()).title());
    }

    @Test
    public void tempoLabelDoesNotBecomeWorkNumber() {
        assertEquals(
                "Evening Wind",
                read("Violino Principale", "ALLEGRO II", 285, false, Set.of()).title());
    }

    @Test
    public void knownHeadingNameIsNotExpanded() {
        assertEquals(
                "Evening Wind",
                read("Violino Principale", "CONCERTOII", 285, false, Set.of("Evening Wind"))
                        .title());
    }

    @Test
    public void photoLayoutDoesNotUseEngravedRule() {
        assertFalse(
                read("Violino Principale", "CONCERTOII", 285, true, Set.of())
                        .title()
                        .equals("Evening Wind CONCERTO II"));
    }

    @Test
    public void missingWorkNumberDoesNotJoin() {
        assertEquals(
                "Evening Wind",
                read("Violino Principale", "CONCERTO", 285, false, Set.of()).title());
    }

    @Test
    public void partInBodyNotHeading() throws Exception {
        var l = new ScoreCreditsDetector.Line("Violino Principale", 100, 700, 500, 760);
        var p = new ScoreCreditsDetector.Page(1800, 2400, 280, List.of(l), false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "instrumentalPartHeading",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(null, p, l));
    }

    @Test
    public void rightSideNarrativeDoesNotOutrankNumberedWork() {
        var ls =
                List.of(
                        new ScoreCreditsDetector.Line("Violino Principale", 600, 160, 1200, 220),
                        new ScoreCreditsDetector.Line("Evening Wind", 160, 225, 400, 270),
                        new ScoreCreditsDetector.Line("CONCERTOII", 100, 285, 760, 365),
                        new ScoreCreditsDetector.Line(
                                "the quiet wind blows across the fields", 750, 227, 1680, 270));
        var r =
                ScoreCreditsDetector.detect(
                        "Old import",
                        List.of(new ScoreCreditsDetector.Page(1800, 2400, 280, ls, false, false)),
                        Set.of());
        assertEquals("Evening Wind CONCERTO II", r.title());
    }
}

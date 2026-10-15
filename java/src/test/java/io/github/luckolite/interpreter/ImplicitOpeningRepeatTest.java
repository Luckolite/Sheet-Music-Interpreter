// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImplicitOpeningRepeatTest {
    static List<PrintedRepeatGlyphs.Mark> end() {
        return List.of(new PrintedRepeatGlyphs.Mark(6, true, .9f, 0, 1));
    }

    static List<PrintedEndingBrackets.Mark> endings() {
        return List.of(
                new PrintedEndingBrackets.Mark(4, 6, List.of(1), "1.", .2f, 0, 1),
                new PrintedEndingBrackets.Mark(6, 7, List.of(2), "2.", .2f, 0, 1));
    }

    static List<Integer> route(PrintedRepeatNavigation.Result r) {
        var p = ScoreNavigationPlan.create(8, r.directions());
        var out = new ArrayList<Integer>();
        for (int i = 0; i < p.measureCount(); i++) out.add(p.sourceMeasure(i));
        return out;
    }

    @Test
    public void openingRepeatWithEndings() {
        var r = PrintedRepeatNavigation.assemble(end(), endings(), 0, true);
        assertTrue(r.unresolved().isEmpty());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 6, 7), route(r));
    }

    @Test
    public void noOpeningProofStaysUnresolved() {
        var r = PrintedRepeatNavigation.assemble(end(), endings(), 0);
        assertTrue(r.directions().isEmpty());
        assertFalse(r.unresolved().isEmpty());
    }

    @Test
    public void ordinaryOpeningRepeat() {
        assertEquals(
                List.of(0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 6, 7),
                route(PrintedRepeatNavigation.assemble(end(), List.of(), 0, true)));
    }

    @Test
    public void incompleteEndingsNotPlayed() {
        assertTrue(
                PrintedRepeatNavigation.assemble(end(), endings().subList(0, 1), 0, true)
                        .directions()
                        .isEmpty());
    }

    @Test
    public void explicitStartWins() {
        var signs = new ArrayList<>(end());
        signs.add(new PrintedRepeatGlyphs.Mark(2, false, .1f, 0, 1));
        assertEquals(
                List.of(0, 1, 2, 3, 4, 5, 2, 3, 6, 7),
                route(PrintedRepeatNavigation.assemble(signs, endings(), 0, true)));
    }

    @Test
    public void evidenceLabelsImplicitStart() {
        var r = PrintedRepeatNavigation.assemble(end(), endings(), 0, true);
        var start =
                r.directions().stream()
                        .filter(d -> d.kind() == ScorePlaybackDirection.Kind.REPEAT_START)
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                start.details()
                        .evidence()
                        .get(0)
                        .sourceId()
                        .startsWith("implicit-score-beginning:"));
    }
}

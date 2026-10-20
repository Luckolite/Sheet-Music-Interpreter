// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RecognizedMultiRestEntryPointTest {
    private static SheetInterpreter.Annotations tokens(int n, boolean foreign) {
        return new SheetInterpreter.Annotations(
                List.of(),
                List.of(),
                List.of(
                        new SheetInterpreter.NumberToken(
                                n,
                                foreign ? .815f : .437f,
                                .21f,
                                foreign ? .83f : .452f,
                                .2725f,
                                foreign ? .815f : .437f)),
                List.of(),
                List.of());
    }

    private static ScorePageInterpretation run(int n, String kind, boolean foreign)
            throws Exception {
        var p = MultiRestLocalScenes.pixels(n, kind);
        byte[] l = p.labels().clone(), g = p.gray().clone();
        var score = SheetInterpreter.analyze(p.labels(), p.gray(), 1000, 400, tokens(n, foreign));
        assertArrayEquals(l, p.labels());
        assertArrayEquals(g, p.gray());
        return score;
    }

    private static void positive(ScorePageInterpretation s, int n) {
        assertEquals(
                n,
                s.rests().stream().filter(r -> r.measureIndex() < n && r.isFullMeasure()).count());
        assertTrue(s.notes().stream().anyMatch(x -> x.measureIndex() >= n));
        assertTrue(s.measures().size() > n);
        assertTrue(s.tempoChanges().isEmpty());
        assertTrue(s.meterChanges().isEmpty());
        for (var r : s.rests())
            if (r.measureIndex() < n) {
                assertEquals(4, r.durationBeats(), 0);
                assertEquals(3, r.resolvedDurationBeats(3), 0);
            }
    }

    @Test
    public void realFacadeFourRecognizedSlotsBecomeFullRests() throws Exception {
        positive(run(4, "plain", false), 4);
    }

    @Test
    public void realFacadeTwoNeverBecomesFour() throws Exception {
        positive(run(2, "plain", false), 2);
    }

    @Test
    public void realFacadeRejectsNoBarAndWrittenSilence() throws Exception {
        for (var k : List.of("barless", "written")) {
            var s = run(4, k, false);
            assertFalse(s.rests().stream().anyMatch(ScoreRestEvent::isFullMeasure));
        }
    }

    @Test
    public void realFacadeForeignCountWithoutPrintedCountIsNotSilence() throws Exception {
        var s = run(4, "no-count", true);
        assertFalse(s.rests().stream().anyMatch(ScoreRestEvent::isFullMeasure));
    }
}

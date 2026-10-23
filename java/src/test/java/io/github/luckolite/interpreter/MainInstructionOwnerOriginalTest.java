// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Standalone-only bridge ownership; ordinary heads need explicit identities too. */
public final class MainInstructionOwnerOriginalTest {
    static ScoreNoteEvent note(float p, int step) {
        return new ScoreNoteEvent(0, p, step, 0, 1, .4f, false, 0, 0, 2, 2);
    }

    static List<Map<String, Object>> events() {
        return List.of(
                new LinkedHashMap<>(Map.of("startBeat", 0.0, "durationBeats", 2.0)),
                new LinkedHashMap<>(Map.of("startBeat", 2.0, "durationBeats", 2.0)));
    }

    @Test
    public void ordinaryHeadsKeepTheirExactSourceIndicesAndClocks() {
        var e = events();
        Main.attachNotePerformance(List.of(note(.12f, 0), note(.82f, 2)), e);
        assertEquals(0, e.get(0).get("sourceNoteIndex"));
        assertEquals(1, e.get(1).get("sourceNoteIndex"));
        for (int i = 0; i < 2; i++) {
            assertEquals(i * 2.0, e.get(i).get("startBeat"));
            assertEquals(2.0, e.get(i).get("durationBeats"));
            assertEquals(3, e.get(i).size());
        }
    }

    @Test
    public void mixedPitchedAndUnpitchedHeadsShareTheSameIndexContract() {
        var e = events();
        Main.attachNotePerformance(
                List.of(note(.12f, 0), note(.82f, 2).withKind(ScoreNoteEvent.Kind.UNPITCHED)), e);
        assertEquals(0, e.get(0).get("sourceNoteIndex"));
        assertEquals(1, e.get(1).get("sourceNoteIndex"));
    }

    @Test
    public void emptyPageDoesNotInventAnOwner() {
        var e = new ArrayList<Map<String, Object>>();
        Main.attachNotePerformance(List.of(), e);
        assertTrue(e.isEmpty());
    }
}

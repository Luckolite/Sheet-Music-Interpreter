// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original small heads and principal notes; no external score material. */
public class MainWrittenPlacementExportTest {
    static ScoreNoteEvent note(float x, int step, boolean grace) {
        return new ScoreNoteEvent(
                        0, x, step, 0, 1, .3f, false, 0, grace ? 3 : 0, 2, grace ? .125f : 4)
                .withArticulations(grace ? NoteOrnament.GRACE : 0);
    }

    static List<Map<String, Object>> events(int count) {
        var out = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < count; i++)
            out.add(new LinkedHashMap<>(Map.of("startBeat", i + .03125, "durationBeats", .0625)));
        return out;
    }

    static void attach(List<ScoreNoteEvent> notes, List<Map<String, Object>> events)
            throws Exception {
        var m =
                Main.class.getDeclaredMethod(
                        "attachWrittenPlacements", List.class, List.class, float[].class);
        m.invoke(null, notes, events, new float[] {4});
    }

    @Test
    public void prefixSharesWrittenOnsetAndKeepsPrincipalIdentity() throws Exception {
        var notes = List.of(note(.1f, 0, true), note(.2f, 1, true), note(.3f, 2, false));
        var e = events(3);
        attach(notes, e);
        var g = (ScoreNoteTiming.WrittenPlacement) e.get(0).get("writtenPlacement");
        var p = (ScoreNoteTiming.WrittenPlacement) e.get(2).get("writtenPlacement");
        assertEquals(2, g.principalIndex());
        assertEquals(0, g.ordinal());
        assertFalse(g.afterGrace());
        assertEquals(p.onsetBeats(), g.onsetBeats(), 0);
        assertEquals(4, p.durationBeats(), 0);
        assertEquals(.125, g.durationBeats(), 0);
        assertTrue(g.stealPercent() > 0);
    }

    @Test
    public void metadataDoesNotRewritePlaybackBudget() throws Exception {
        var notes = List.of(note(.1f, 0, true), note(.3f, 2, false));
        var e = events(2);
        attach(notes, e);
        for (int i = 0; i < 2; i++) {
            assertEquals(i + .03125, (double) e.get(i).get("startBeat"), 0);
            assertEquals(.0625, (double) e.get(i).get("durationBeats"), 0);
        }
    }

    @Test
    public void trailingGraceOwnsWrittenRelease() throws Exception {
        var notes = List.of(note(.1f, 2, false), note(.8f, 0, true), note(.9f, 1, true));
        var e = events(3);
        attach(notes, e);
        var p = (ScoreNoteTiming.WrittenPlacement) e.get(0).get("writtenPlacement");
        var g = (ScoreNoteTiming.WrittenPlacement) e.get(2).get("writtenPlacement");
        assertEquals(0, g.principalIndex());
        assertEquals(1, g.ordinal());
        assertTrue(g.afterGrace());
        assertEquals(p.onsetBeats() + p.durationBeats(), g.onsetBeats(), 0);
    }

    @Test
    public void ordinaryPageKeepsOriginalJsonShape() throws Exception {
        var e = events(1);
        var before = new ArrayList<>(e);
        attach(List.of(note(.2f, 0, false)), e);
        assertEquals(before, e);
        assertFalse(e.get(0).containsKey("writtenPlacement"));
    }

    @Test
    public void unownedGraceDoesNotInventPrincipal() throws Exception {
        var notes = List.of(note(.1f, 0, true), note(.2f, 1, true));
        var e = events(2);
        attach(notes, e);
        assertTrue(e.stream().noneMatch(n -> n.containsKey("writtenPlacement")));
    }

    @Test
    public void jsonIncludesExplicitWrittenComponents() throws Exception {
        var notes = List.of(note(.1f, 0, true), note(.3f, 2, false));
        var e = events(2);
        attach(notes, e);
        var json = Main.json(e);
        assertTrue(json.contains("\"writtenPlacement\""));
        assertTrue(json.contains("\"principalIndex\":1"));
        assertTrue(json.contains("\"afterGrace\":false"));
    }
}

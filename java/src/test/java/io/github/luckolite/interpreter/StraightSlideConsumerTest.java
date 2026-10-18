// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original semantic records; no commercial image, score coordinates, or OCR. */
public class StraightSlideConsumerTest {
    static ScoreNoteEvent note(int bar, float x, int step, int flags) {
        return new ScoreNoteEvent(bar, x, step, 0, 1, .4f, false, 0, 0, 2, 1)
                .withClef(30)
                .withArticulations(flags);
    }

    static Map<String, Object> event(int bar, double start, double duration, int midi) {
        var m = new HashMap<String, Object>();
        m.put("measureIndex", bar);
        m.put("staffIndex", 0);
        m.put("staffCount", 1);
        m.put("midi", midi);
        m.put("startBeat", start);
        m.put("durationBeats", duration);
        m.put("durationFallback", false);
        return m;
    }

    static List<Map<String, Object>> apply(
            List<ScoreNoteEvent> notes, List<Map<String, Object>> events) {
        Main.attachNotePerformance(notes, events);
        return events;
    }

    @Test
    public void risingUsesActualPreviousMidiAndTargetSlot() {
        var a = event(0, 0, 2, 72);
        var b = event(0, 2, 1, 74);
        apply(List.of(note(0, .2f, 5, 0), note(0, .8f, 6, 16576)), List.of(a, b));
        assertEquals(
                Map.of("style", "continuous", "sourceNoteIndex", 0, "sourceMidi", 72),
                b.get("slide"));
        assertFalse(a.containsKey("slide"));
        assertEquals(2.0, b.get("startBeat"));
        assertEquals(1.0, b.get("durationBeats"));
    }

    @Test
    public void descendingRetainsResolvedPreviousAccidentalPitch() {
        var b = event(1, 2, 1, 60);
        apply(List.of(note(0, .8f, 8, 0), note(1, .1f, 4, 24768)), List.of(event(0, 0, 2, 77), b));
        assertEquals(77, ((Map<?, ?>) b.get("slide")).get("sourceMidi"));
    }

    @Test
    public void unconnectedApproachDoesNotInventEndpoint() {
        var b = event(0, 2, 1, 74);
        apply(List.of(note(0, .2f, 5, 0), note(0, .8f, 6, 192)), List.of(event(0, 0, 2, 72), b));
        assertFalse(b.containsKey("slide"));
    }

    @Test
    public void noPreviousDoesNotInventEndpoint() {
        var b = event(0, 0, 1, 74);
        apply(List.of(note(0, .8f, 6, 16576)), List.of(b));
        assertFalse(b.containsKey("slide"));
    }

    @Test
    public void ambiguousPreviousChordDoesNotInventEndpoint() {
        var b = event(0, 2, 1, 74);
        apply(
                List.of(note(0, .2f, 5, 0), note(0, .2f, 3, 0), note(0, .8f, 6, 16576)),
                List.of(event(0, 0, 2, 72), event(0, 0, 2, 69), b));
        assertFalse(b.containsKey("slide"));
    }

    @Test
    public void restGapDoesNotInventEndpoint() {
        var b = event(0, 2.5, 1, 74);
        apply(List.of(note(0, .2f, 5, 0), note(0, .8f, 6, 16576)), List.of(event(0, 0, 2, 72), b));
        assertFalse(b.containsKey("slide"));
    }

    @Test
    public void unknownWrittenClockAbstains() {
        var a = event(0, 0, 2, 72);
        a.put("durationFallback", true);
        var b = event(0, 2, 1, 74);
        apply(List.of(note(0, .2f, 5, 0), note(0, .8f, 6, 16576)), List.of(a, b));
        assertFalse(b.containsKey("slide"));
    }

    @Test
    public void graceSourceAbstains() {
        var b = event(0, 2, 1, 74);
        apply(
                List.of(note(0, .2f, 5, 32768), note(0, .8f, 6, 16576)),
                List.of(event(0, 0, 2, 72), b));
        assertFalse(b.containsKey("slide"));
    }

    @Test
    public void wrongStaffAbstains() {
        var b = event(0, 2, 1, 74);
        b.put("staffIndex", 1);
        apply(List.of(note(0, .2f, 5, 0), note(0, .8f, 6, 16576)), List.of(event(0, 0, 2, 72), b));
        assertFalse(b.containsKey("slide"));
    }

    @Test
    public void staleOriginalIndexAbstains() {
        var b = event(0, 2, 1, 74);
        b.put("sourceNoteIndex", 0);
        apply(List.of(note(0, .2f, 5, 0), note(0, .8f, 6, 16576)), List.of(event(0, 0, 2, 72), b));
        assertFalse(b.containsKey("slide"));
    }

    @Test
    public void unpitchedEventAbstains() {
        var b = event(0, 2, 1, 74);
        b.put("kind", "UNPITCHED");
        apply(List.of(note(0, .2f, 5, 0), note(0, .8f, 6, 16576)), List.of(event(0, 0, 2, 72), b));
        assertFalse(b.containsKey("slide"));
    }

    @Test
    public void wavyWhiteKeyGlissRemainsSeparate() {
        var a = event(0, 0, 2, 72);
        var b = event(0, 2, 1, 74);
        apply(List.of(note(0, .2f, 5, 224), note(0, .8f, 6, 0)), List.of(a, b));
        assertEquals(Map.of("style", "white_keys", "targetMidi", 74), a.get("glissando"));
        assertFalse(b.containsKey("slide"));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original retained-clef events exercise the actual JSON bridge identity method. */
public class IntrinsicClefBridgeTest {
    private ScoreNoteEvent note(int clef) {
        return new ScoreNoteEvent(0, .1f, 0, 1, 2, .5f, false, 0, 0, 2, 1).withClef(clef);
    }

    @Test
    public void intrinsicFifteenthIsConcertG4WithoutDirection() throws Exception {
        assertEquals(67, Main.noteIdentity(note(32), 0).get("midi"));
    }

    @Test
    public void intrinsicEightIsConcertG3WithoutDirection() throws Exception {
        assertEquals(55, Main.noteIdentity(note(25), 0).get("midi"));
    }

    @Test
    public void independentDirectionAppliesExactlyOnce() throws Exception {
        assertEquals(55, Main.noteIdentity(note(32).withOctaveShift(-1), 0).get("midi"));
    }

    @Test
    public void originalOrdinaryClefsKeepConcertIdentity() throws Exception {
        assertEquals(43, Main.noteIdentity(note(18), 0).get("midi"));
        assertEquals(64, Main.noteIdentity(note(30), 0).get("midi"));
        assertEquals(76, Main.noteIdentity(note(37), 0).get("midi"));
    }

    @Test
    public void unpitchedRetainsDisplayFifteenthWithoutMidi() throws Exception {
        var n = note(32).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var out = Main.noteIdentity(n, 0);
        assertEquals(32, out.get("clefBottomDiatonic"));
        assertEquals("G", out.get("displayStep"));
        assertEquals(4, out.get("displayOctave"));
        assertFalse(out.containsKey("midi"));
    }
}

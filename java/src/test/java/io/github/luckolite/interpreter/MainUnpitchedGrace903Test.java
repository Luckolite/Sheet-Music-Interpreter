// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original source records: no raster, score, inferred voice or instrument. */
public class MainUnpitchedGrace903Test {
    private static ScoreNoteEvent note() {
        return new ScoreNoteEvent(
                0,
                .1f,
                0,
                0,
                1,
                .3f,
                false,
                0,
                0,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                1,
                1,
                0,
                0,
                ScoreNoteEvent.CLEF_TREBLE);
    }

    @Test
    public void rawUnpitchedGraceCannotBecomeAnOrdinaryJsonAttack() {
        var source =
                note().withKind(ScoreNoteEvent.Kind.UNPITCHED)
                        .withArticulations(NoteOrnament.GRACE);
        assertThrows(IOException.class, () -> Main.noteIdentity(source, 0));
    }

    @Test
    public void neutralDeadDoesNotProveUnpitchedGraceOwnership() {
        var source =
                note().withKind(ScoreNoteEvent.Kind.UNPITCHED)
                        .withArticulations(
                                NoteOrnament.GRACE | TabEffect.encode(TabEffect.DEAD, 0));
        assertThrows(IOException.class, () -> Main.noteIdentity(source, 0));
    }

    @Test
    public void pitchedGraceRetainsExactLegacyPitchIdentity() throws Exception {
        assertEquals(
                "{\"midi\":64,\"clefInferred\":false}",
                Main.json(Main.noteIdentity(note().withArticulations(NoteOrnament.GRACE), 0)));
    }

    @Test
    public void ordinaryUnpitchedDeadRetainsDisplayAndNoMidi() throws Exception {
        var source =
                note().withKind(ScoreNoteEvent.Kind.UNPITCHED)
                        .withArticulations(TabEffect.encode(TabEffect.DEAD, 0));
        var identity = Main.noteIdentity(source, 0);
        assertEquals("UNPITCHED", identity.get("kind"));
        assertEquals("E", identity.get("displayStep"));
        assertFalse(identity.containsKey("midi"));
    }
}

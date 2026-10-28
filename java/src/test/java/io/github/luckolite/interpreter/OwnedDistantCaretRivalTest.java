// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Every old geometric rival remains present when a distant source proof is added. */
public final class OwnedDistantCaretRivalTest {
    private static void noNewCaret(OwnedDistantCaretOriginalTest.Scene s) {
        for (var note : s.apply().notes())
            assertEquals(0, note.articulations() & NoteArticulation.MARCATO);
    }

    @Test
    public void unprovedCloserRetainedAttackCannotBeDropped() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        s.notes.add(OwnedDistantCaretOriginalTest.Scene.note(200, 330, 0));
        noNewCaret(s);
    }

    @Test
    public void unprovedFartherRetainedAttackCannotBeDropped() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        s.notes.add(OwnedDistantCaretOriginalTest.Scene.note(200, 360, 0));
        noNewCaret(s);
    }

    @Test
    public void aCaretInsideContinuousLetterBodiesRemainsText() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        for (int left : new int[] {163, 175, 219, 231})
            for (int y = 252; y <= 263; y++)
                for (int x = left; x <= left + 6; x++) s.pixel(x, y, 75);
        noNewCaret(s);
    }

    @Test
    public void literalUpBowDoesNotTurnIntoMarcato() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        for (int y = 248; y <= 264; y++) for (int x = 187; x <= 213; x++) s.pixel(x, y, 255);
        for (int x = 188; x <= 212; x++) {
            int y = 261 - Math.abs(x - 200);
            s.pixel(x, y, x < 200 ? 195 : 75);
            s.pixel(x, y + 1, x < 200 ? 195 : 75);
        }
        noNewCaret(s);
    }

    @Test
    public void aStaffStripeCannotCloseAClippedCaret() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        for (int x = 150; x <= 275; x++) s.pixel(x, 263, 75);
        noNewCaret(s);
    }

    @Test
    public void allNonArticulationFieldsAndOriginalSecondaryOriginRemainExact() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var before = s.score();
        var result = s.apply();
        assertEquals(before.notes().size(), result.notes().size());
        for (int i = 0; i < before.notes().size(); i++)
            assertEquals(
                    before.notes().get(i),
                    result.notes().get(i).withArticulations(before.notes().get(i).articulations()));
        var old = s.source().source();
        assertEquals(old, result.beamSource());
    }
}

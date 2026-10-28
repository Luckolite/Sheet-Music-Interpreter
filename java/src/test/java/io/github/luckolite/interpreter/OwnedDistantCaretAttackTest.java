// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import org.junit.Test;

/** Late intake has exact written-attack identity in addition to anchor proximity. */
public final class OwnedDistantCaretAttackTest {
    private static void head(OwnedDistantCaretOriginalTest.Scene s, int cx, int cy) {
        for (int y = cy - 6; y <= cy + 6; y++)
            for (int x = cx - 10; x <= cx + 10; x++)
                if (Math.pow((x - cx) / 8.5, 2) + Math.pow((y - cy) / 5.5, 2) <= 1)
                    s.pixel(x, y, 50);
    }

    @Test
    public void aRealSharedShaftChordRetainsTheSameWrittenAttack() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        head(s, 200, 336);
        s.notes.add(OwnedDistantCaretOriginalTest.Scene.note(200, 336, 0));
        var c = s.source();
        assertEquals("", c.statistics().refusal());
        assertTrue(
                c.source().groups().stream()
                        .flatMap(g -> g.columns().stream())
                        .anyMatch(
                                x ->
                                        x.members().stream()
                                                .map(ScoreBeamSource.Anchor::noteIndex)
                                                .toList()
                                                .containsAll(java.util.List.of(0, 2))));
        var a = s.apply().notes();
        assertEquals(NoteArticulation.MARCATO, a.get(0).articulations());
        assertEquals(NoteArticulation.MARCATO, a.get(2).articulations());
    }

    @Test
    public void xProximityCannotStampADifferentWrittenAttack() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        head(s, 205, 392);
        s.notes.add(OwnedDistantCaretOriginalTest.Scene.note(205, 392, 0));
        assertTrue(5 < OwnedDistantCaretOriginalTest.GAP * .45f);
        assertNotEquals(s.notes.get(0).positionInMeasure(), s.notes.get(2).positionInMeasure(), 0f);
        assertEquals(1, s.source().source().groups().size());
        var a = s.apply().notes();
        assertEquals(NoteArticulation.MARCATO, a.get(0).articulations());
        assertEquals(0, a.get(2).articulations());
    }
}

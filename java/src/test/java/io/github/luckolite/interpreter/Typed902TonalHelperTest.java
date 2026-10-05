// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original scalars and pixels; no score scans or mirrored recognition implementation. */
public class Typed902TonalHelperTest {
    private ScoreNoteEvent note(int m, float x, int step, boolean tied, int marks) {
        return new ScoreNoteEvent(
                m,
                x,
                step,
                0,
                1,
                .6f,
                tied,
                1,
                0,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                1,
                3,
                .25f,
                marks,
                ScoreNoteEvent.CLEF_TREBLE,
                false,
                .5f,
                false,
                0,
                1,
                2,
                1);
    }

    private ScoreNoteEvent unpitched(ScoreNoteEvent n) {
        return n.withKind(ScoreNoteEvent.Kind.UNPITCHED);
    }

    @Test
    public void octaveSpanLeavesFullUnpitchedRecordUnchanged() {
        var g = OctaveMarkDetectorTest.page();
        OctaveMarkDetectorTest.dash(g, 120, 370, 55);
        var p = OctaveMarkDetectorTest.note(150, 0);
        var u = unpitched(OctaveMarkDetectorTest.note(300, 0));
        var result =
                OctaveMarkDetectorTest.apply(
                        g, List.of(OctaveMarkDetectorTest.word("8va", 80, 40)), List.of(p, u));
        assertEquals(p.withOctaveShift(1), result.get(0));
        assertSame(u, result.get(1));
    }

    @Test
    public void artificialHarmonicCannotTransposeUnpitchedStoppedHead() throws Exception {
        var fixture = new ArtificialHarmonicsTest();
        Method shape = ArtificialHarmonicsTest.class.getDeclaredMethod("shape", boolean.class);
        shape.setAccessible(true);
        shape.invoke(fixture, true);
        Method note =
                ArtificialHarmonicsTest.class.getDeclaredMethod("note", int.class, float.class);
        note.setAccessible(true);
        var stopped = unpitched((ScoreNoteEvent) note.invoke(fixture, 0, 160f));
        Method apply = ArtificialHarmonicsTest.class.getDeclaredMethod("apply", List.class);
        apply.setAccessible(true);
        assertEquals(List.of(stopped), apply.invoke(fixture, List.of(stopped)));
    }

    @Test
    public void artificialHarmonicCannotConsumeUnpitchedTouchAttack() throws Exception {
        var fixture = new ArtificialHarmonicsTest();
        Method shape = ArtificialHarmonicsTest.class.getDeclaredMethod("shape", boolean.class);
        shape.setAccessible(true);
        shape.invoke(fixture, true);
        Method note =
                ArtificialHarmonicsTest.class.getDeclaredMethod("note", int.class, float.class);
        note.setAccessible(true);
        var stopped = (ScoreNoteEvent) note.invoke(fixture, 0, 160f);
        var touch = unpitched((ScoreNoteEvent) note.invoke(fixture, 3, 130f));
        Method apply = ArtificialHarmonicsTest.class.getDeclaredMethod("apply", List.class);
        apply.setAccessible(true);
        assertEquals(List.of(stopped, touch), apply.invoke(fixture, List.of(stopped, touch)));
    }

    @Test
    public void mixedCoincidentOctaveHeadsRetainDistinctKindsAndFullMetadata() {
        var g = OctaveMarkDetectorTest.page();
        OctaveMarkDetectorTest.dash(g, 120, 370, 55);
        var base = OctaveMarkDetectorTest.note(150, 0);
        var u =
                new ScoreNoteEvent(
                        base.measureIndex(),
                        base.positionInMeasure(),
                        base.staffStep(),
                        base.staffIndex(),
                        base.staffCount(),
                        base.pageY(),
                        true,
                        2,
                        2,
                        1,
                        .5f,
                        5,
                        .75f,
                        NoteArticulation.STACCATO,
                        base.clefBottomDiatonic(),
                        true,
                        .25f,
                        true,
                        -1,
                        3,
                        4,
                        -1,
                        ScoreNoteEvent.Kind.UNPITCHED);
        var notes = List.of(base, u);
        var gBefore = g.clone();
        var result =
                OctaveMarkDetectorTest.apply(
                        g, List.of(OctaveMarkDetectorTest.word("8va", 80, 40)), notes);
        assertEquals(2, result.size());
        assertEquals(base.withOctaveShift(1), result.get(0));
        assertSame(u, result.get(1));
        assertEquals(notes, List.of(base, u));
        assertArrayEquals(gBefore, g);
    }

    @Test
    public void harmonicPreservesUnrelatedUnpitchedOccurrenceAndItsOrder() throws Exception {
        var fixture = new ArtificialHarmonicsTest();
        Method shape = ArtificialHarmonicsTest.class.getDeclaredMethod("shape", boolean.class);
        shape.setAccessible(true);
        shape.invoke(fixture, true);
        Method note =
                ArtificialHarmonicsTest.class.getDeclaredMethod("note", int.class, float.class);
        note.setAccessible(true);
        var stopped = (ScoreNoteEvent) note.invoke(fixture, 0, 160f);
        var touch = (ScoreNoteEvent) note.invoke(fixture, 3, 130f);
        var u = unpitched(note(0, .82f, 0, false, NoteArticulation.STACCATO));
        var notes = List.of(u, stopped, touch);
        Method apply = ArtificialHarmonicsTest.class.getDeclaredMethod("apply", List.class);
        apply.setAccessible(true);
        @SuppressWarnings("unchecked")
        var result = (List<ScoreNoteEvent>) apply.invoke(fixture, notes);
        assertEquals(List.of(u, stopped.withOctaveShift(2)), result);
        assertSame(u, result.get(0));
        assertEquals(List.of(u, stopped, touch), notes);
    }

    @Test
    public void coincidentPitchedAndUnpitchedTouchClaimDoesNotConsumeEitherAttack()
            throws Exception {
        var fixture = new ArtificialHarmonicsTest();
        Method shape = ArtificialHarmonicsTest.class.getDeclaredMethod("shape", boolean.class);
        shape.setAccessible(true);
        shape.invoke(fixture, true);
        Method note =
                ArtificialHarmonicsTest.class.getDeclaredMethod("note", int.class, float.class);
        note.setAccessible(true);
        var stopped = (ScoreNoteEvent) note.invoke(fixture, 0, 160f);
        var touch = (ScoreNoteEvent) note.invoke(fixture, 3, 130f);
        var cross = unpitched(touch);
        var notes = List.of(stopped, touch, cross);
        Method apply = ArtificialHarmonicsTest.class.getDeclaredMethod("apply", List.class);
        apply.setAccessible(true);
        assertEquals(notes, apply.invoke(fixture, notes));
        assertNotEquals(touch, cross);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original scalar and raster controls through actual tonal helper entry points. */
public class TypedTonalHelpersTest {
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
    public void glissCannotBorrowPastAnUnpitchedAttack() {
        var start =
                new ScoreNoteEvent(0, .1f, 0, 0, 1, .6f, false, 0, 0, 2, 1)
                        .withClef(30)
                        .withArticulations(NoteOrnament.GLISSANDO);
        var cross = unpitched(note(0, .3f, 2, false, 0));
        var end = new ScoreNoteEvent(0, .5f, 4, 0, 1, .6f, false, 0, 0, 2, 1).withClef(30);
        assertNull(GlissPitchTarget.next(start, List.of(start, cross, end)));
        assertEquals(end, GlissPitchTarget.next(start, List.of(start, end)));
        assertNull(GlissPitchTarget.next(unpitched(start), List.of(end)));
    }

    @Test
    public void slideCannotBorrowPastAnUnpitchedAttack() {
        var first = note(0, .1f, 0, false, 0);
        var cross = unpitched(note(0, .3f, 2, false, 0));
        var end = note(0, .5f, 4, false, NoteOrnament.SLIDE | NoteOrnament.FROM_PREVIOUS);
        assertNull(SlidePitchSource.previous(end, List.of(first, cross, end)));
        assertEquals(first, SlidePitchSource.previous(end, List.of(first, end)));
        assertNull(SlidePitchSource.previous(unpitched(end), List.of(first, end)));
    }

    @Test
    public void tonalTieGuardPreservesUnpitchedRecordWithoutPitchQuery() {
        var cross = unpitched(note(4, .3f, 0, true, 0));
        assertEquals(
                List.of(cross),
                ScoreTiePitchGuard.apply(List.of(cross), List.of(new ScoreKeyChange(0, 0))));
        assertSame(
                cross,
                ScoreTiePitchGuard.recheckWithKeyContext(
                                List.of(cross), List.of(new ScoreKeyChange(0, 3)))
                        .get(0));
    }

    @Test
    public void pitchedTieCannotUseAnUnpitchedPriorPitch() {
        var cross = unpitched(note(4, .1f, 0, false, 0));
        var end = note(4, .3f, 0, true, 0);
        var result =
                ScoreTiePitchGuard.apply(List.of(cross, end), List.of(new ScoreKeyChange(0, 0)));
        assertSame(cross, result.get(0));
        assertFalse(result.get(1).tiedFromPrevious());
        assertEquals(
                end.withKind(ScoreNoteEvent.Kind.PITCHED).stemDirection(),
                result.get(1).stemDirection());
        assertEquals(end.boundaryTies(), result.get(1).boundaryTies());
        assertEquals(end.tupletNormalNotes(), result.get(1).tupletNormalNotes());
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
    public void ornamentsKeepSourceIndicesAndDoNotBorrowUnpitchedOwner() throws Exception {
        var fixture = new PortableNoteOrnamentsTest();
        Method page =
                PortableNoteOrnamentsTest.class.getDeclaredMethod("page", int.class, int.class);
        page.setAccessible(true);
        Method recognizer = PortableNoteOrnamentsTest.class.getDeclaredMethod("recognizer");
        recognizer.setAccessible(true);
        Method staffs = PortableNoteOrnamentsTest.class.getDeclaredMethod("staffs");
        staffs.setAccessible(true);
        var p = note(0, 100f / 360, 0, false, 0);
        var u = unpitched(note(0, 180f / 360, 0, false, 0));
        @SuppressWarnings("unchecked")
        var s = (List<PlayingTechniqueDetector.Staff>) staffs.invoke(fixture);
        var result =
                PortableNoteOrnaments.applyWithAlignedStaffs(
                        (PortableOrnamentGlyphs) recognizer.invoke(fixture),
                        (byte[]) page.invoke(fixture, 168, 110),
                        360,
                        280,
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        List.of(p, u),
                        List.of(),
                        s);
        assertEquals(List.of(p, u), result);
        var pitchedOwner = u.withKind(ScoreNoteEvent.Kind.PITCHED);
        result =
                PortableNoteOrnaments.applyWithAlignedStaffs(
                        (PortableOrnamentGlyphs) recognizer.invoke(fixture),
                        (byte[]) page.invoke(fixture, 168, 110),
                        360,
                        280,
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        List.of(p, pitchedOwner),
                        List.of(),
                        s);
        assertEquals(0, result.get(0).articulations());
        assertEquals(NoteOrnament.TURN, NoteOrnament.type(result.get(1).articulations()));
    }

    @Test
    public void tupletsAndAttachedRestEvidenceRetainUnpitchedKinds() {
        var a = RestTripletTest.note(.25f, 0, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var b = RestTripletTest.note(.3125f, .25f, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var rests = List.of(RestTripletTest.rest(.375f));
        var result = RestTripletTest.apply(List.of(a, b), rests, RestTripletTest.ink(false));
        RestTripletTest.triplets(result, 2, 1);
        for (var n : result.notes()) {
            assertEquals(ScoreNoteEvent.Kind.UNPITCHED, n.kind());
            assertThrows(IllegalStateException.class, n::diatonicPitchIdentity);
        }
        assertEquals(1. / 6, result.notes().get(1).followingRestBeats(), .00001);
        assertEquals(
                result,
                RestTripletTest.apply(result.notes(), result.rests(), RestTripletTest.ink(false)));
    }

    @Test
    public void repeatedTiesCannotBorrowUnpitchedPitchAtEntry() {
        var u = unpitched(note(0, .1f, 0, true, 0));
        var p = note(0, .5f, 0, true, 0);
        var source =
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(0, .5f, 0, 1), new MeasureRegion(.5f, 1, 0, 1)),
                        List.of(u, p));
        var plan =
                ScoreNavigationPlan.create(
                        2,
                        List.of(
                                new ScorePlaybackDirection(
                                        0, ScorePlaybackDirection.Kind.REPEAT_START),
                                new ScorePlaybackDirection(
                                        2, ScorePlaybackDirection.Kind.REPEAT_END)));
        var result =
                ScoreNavigationProjection.project(
                        source, plan, new ScoreNavigationProjection.Defaults(0, 90, 4, 4));
        assertEquals(4, result.notes().size());
        assertTrue(result.notes().get(0).tiedFromPrevious());
        assertFalse(result.notes().get(2).tiedFromPrevious());
        assertFalse(result.notes().get(3).tiedFromPrevious());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, result.notes().get(2).kind());
    }
}

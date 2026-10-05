// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original typed dead-string tokens, paired geometry, and retained rhythm controls. */
public class TypedTablatureDecoderTest {
    private static final int W = 500, H = 400;

    private TablatureDecoder.Staff tab(float standard, List<TablatureDecoder.Fret> frets) {
        return new TablatureDecoder.Staff(140, 20, standard, frets, List.of(20f, 480f));
    }

    private TablatureDecoder.Fret fret(
            int x,
            int string,
            int number,
            float duration,
            int beams,
            int dots,
            int tuplet,
            int marks) {
        return new TablatureDecoder.Fret(
                x, 140 + string * 20, string, number, duration, beams, dots, marks, false, tuplet);
    }

    private ScorePageInterpretation standalone(List<TablatureDecoder.Fret> frets) {
        return TablatureDecoder.apply(
                new ScorePageInterpretation(List.of(), List.of()), List.of(tab(-1, frets)), W, H);
    }

    private ScoreNoteEvent note(float x, int staff, int step) {
        return new ScoreNoteEvent(
                0,
                x,
                step,
                staff,
                2,
                .15f + staff * .1f,
                false,
                1,
                2,
                2,
                0,
                5,
                .25f,
                NoteArticulation.TENUTO,
                30,
                true,
                .125f,
                true,
                1,
                5,
                4,
                -1);
    }

    private ScorePageInterpretation paired(
            List<ScoreNoteEvent> notes, List<TablatureDecoder.Fret> frets) {
        return TablatureDecoder.apply(
                new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, .1f, .3f)), notes),
                List.of(tab(40, frets)),
                W,
                H);
    }

    private List<TablatureDecoder.Fret> dead(int x) {
        return List.of(
                fret(x, 0, -1, 0, 0, 0, 1, 0),
                fret(x, 1, -1, 0, 0, 0, 1, 0),
                fret(x, 2, -1, 0, 0, 0, 1, 0));
    }

    private int midi(ScoreNoteEvent n, ScorePageInterpretation score) throws Exception {
        var m =
                TablatureDecoder.class.getDeclaredMethod(
                        "printedMidi", ScoreNoteEvent.class, ScorePageInterpretation.class);
        m.setAccessible(true);
        return (int) m.invoke(null, n, score);
    }

    @Test
    public void standaloneDeadTokenKeepsUnknownDurationWithoutPitch() throws Exception {
        var n = standalone(List.of(fret(100, 2, -1, 0, 0, 0, 1, 0))).notes().get(0);
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, n.kind());
        assertEquals(0f, n.unbeamedDurationBeats(), 0);
        assertEquals(0, n.beamCount());
        assertEquals(TabEffect.DEAD, TabEffect.kind(n.articulations()));
        assertEquals(0, TabEffect.delta(n.articulations()));
        assertThrows(IllegalStateException.class, n::diatonicPitchIdentity);
        var cause =
                assertThrows(
                        InvocationTargetException.class,
                        () -> midi(n, new ScorePageInterpretation(List.of(), List.of())));
        assertTrue(cause.getCause() instanceof IllegalStateException);
    }

    @Test
    public void writtenDeadRhythmKeepsBeamsDotsTupletAndGeometry() {
        var n =
                standalone(List.of(fret(100, 2, -1, 0, 2, 1, 3, NoteArticulation.TENUTO)))
                        .notes()
                        .get(0);
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, n.kind());
        assertEquals(2, n.beamCount());
        assertEquals(1, n.augmentationDots());
        assertEquals(3, n.tupletDivisor());
        assertEquals(2, n.tupletNormalNotes());
        assertEquals(180f / H, n.pageY(), 0);
        assertEquals((100f - 20) / (480 - 20), n.positionInMeasure(), 0);
        assertEquals(.25, ScoreNoteTiming.writtenDurationBeats(n), .000001);
        assertEquals(NoteArticulation.TENUTO, n.articulations() & NoteArticulation.TENUTO);
    }

    @Test
    public void deadEffectHasNeutralTargetEvenWhenInputHasStalePitchEffect() {
        var n =
                standalone(
                                List.of(
                                        fret(
                                                100,
                                                2,
                                                -1,
                                                1,
                                                0,
                                                0,
                                                1,
                                                TabEffect.encode(TabEffect.SLIDE, 7))))
                        .notes()
                        .get(0);
        assertEquals(TabEffect.encode(TabEffect.DEAD, 0), n.articulations());
    }

    @Test
    public void lowerUnknownNegativeTokenRejectsInsteadOfBecomingOpenString() {
        for (int value : new int[] {-3, -99, Integer.MIN_VALUE})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> standalone(List.of(fret(100, 0, value, 1, 0, 0, 1, 0))));
    }

    @Test
    public void deadTokenNeedsARealPhysicalString() {
        assertThrows(
                IllegalArgumentException.class,
                () -> standalone(List.of(fret(100, 6, -1, 1, 0, 0, 1, 0))));
    }

    @Test
    public void restTokenRemainsSilentAndAttachesToDeadAttack() {
        var score =
                standalone(
                        List.of(
                                fret(100, 0, -1, 1, 0, 1, 1, 0),
                                fret(200, 0, -2, .5f, 0, 0, 1, 0),
                                fret(300, 0, 5, 1, 0, 0, 1, 0)));
        assertEquals(2, score.notes().size());
        assertEquals(1, score.rests().size());
        var u = score.notes().get(0);
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, u.kind());
        assertEquals(.5f, u.followingRestBeats(), 0);
        assertEquals(1, u.augmentationDots());
        assertEquals(
                2, ScoreNoteTiming.beatInMeasure(score.notes().get(1), score.notes(), 4), .0001);
        assertEquals(ScoreNoteEvent.Kind.PITCHED, score.notes().get(1).kind());
    }

    @Test
    public void pairedDeadClusterRetainsAllNotationMetadata() throws Exception {
        var p = note(.6f, 0, 4);
        var score = paired(List.of(p), dead(300));
        assertEquals(1, score.notes().size());
        assertTrue(score.rests().isEmpty());
        var u = score.notes().get(0);
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, u.kind());
        assertEquals(TabEffect.DEAD, TabEffect.kind(u.articulations()));
        assertEquals(0, TabEffect.delta(u.articulations()));
        for (var field : ScoreNoteEvent.class.getRecordComponents())
            if (!Set.of("kind", "articulations").contains(field.getName()))
                assertEquals(
                        field.getName(),
                        field.getAccessor().invoke(p),
                        field.getAccessor().invoke(u));
        assertEquals(p.articulations(), u.articulations() & ~TabEffect.ALL);
        assertEquals(
                ScoreNoteTiming.writtenDurationBeats(p),
                ScoreNoteTiming.writtenDurationBeats(u),
                0);
    }

    @Test
    public void pairedDeadClusterKeepsUnknownRhythmUnknown() {
        var p = new ScoreNoteEvent(0, .6f, 4, 0, 1, .15f, false, 0, 0, 2, 0);
        var u = paired(List.of(p), dead(300)).notes().get(0);
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, u.kind());
        assertEquals(0f, u.unbeamedDurationBeats(), 0);
    }

    @Test
    public void pairedMixedLiveAndDeadClusterLeavesLivePitchUntouched() {
        var frets = new ArrayList<>(dead(300));
        frets.add(fret(300, 3, 7, 1, 0, 0, 1, 0));
        var p = note(.6f, 0, 4);
        assertEquals(List.of(p), paired(List.of(p), frets).notes());
    }

    @Test
    public void duplicateDeadStringsDoNotMeetThreeDistinctStringProof() {
        var p = note(.6f, 0, 4);
        var frets =
                List.of(
                        fret(300, 0, -1, 1, 0, 0, 1, 0),
                        fret(300, 0, -1, 1, 0, 0, 1, 0),
                        fret(300, 1, -1, 1, 0, 0, 1, 0));
        assertEquals(List.of(p), paired(List.of(p), frets).notes());
    }

    @Test
    public void pairedRestsAreNotDeadStrings() {
        var p = note(.6f, 0, 4);
        var frets =
                List.of(
                        fret(300, 0, -2, 1, 0, 0, 1, 0),
                        fret(300, 1, -2, 1, 0, 0, 1, 0),
                        fret(300, 2, -2, 1, 0, 0, 1, 0));
        assertEquals(List.of(p), paired(List.of(p), frets).notes());
    }

    @Test
    public void pairedDeadClusterRetainsOtherNotationLaneAndExistingRest() {
        var p = note(.6f, 0, 4);
        var other = note(.6f, 1, 6);
        var rest = new ScoreRestEvent(0, .4f, .15f, .05f, 0, 2, .5);
        var base =
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(0, 1, .1f, .3f)),
                        List.of(p, other),
                        1,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(rest));
        var result = TablatureDecoder.apply(base, List.of(tab(40, dead(300))), W, H);
        assertEquals(List.of(rest), result.rests());
        assertEquals(2, result.notes().size());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, result.notes().get(0).kind());
        assertEquals(other, result.notes().get(1));
    }

    @Test
    public void pairedAgreementAndEffectsSkipExistingUnpitchedAttack() {
        var u = note(.6f, 0, 4).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var frets = List.of(fret(300, 0, 19, 1, 0, 0, 1, TabEffect.encode(TabEffect.BEND, 2)));
        assertEquals(List.of(u), paired(List.of(u), frets).notes());
    }

    @Test
    public void knownLiveFretsRemainPitchedWithTuningCapoAndHarmonicPitch() throws Exception {
        int[] tuning = {62, 57, 55, 50, 45, 38};
        var f = fret(100, 1, 7, 2, 0, 1, 1, 0);
        var score =
                TablatureDecoder.apply(
                        new ScorePageInterpretation(List.of(), List.of()),
                        List.of(tab(-1, List.of(f))),
                        W,
                        H,
                        tuning,
                        2);
        var n = score.notes().get(0);
        assertEquals(ScoreNoteEvent.Kind.PITCHED, n.kind());
        assertEquals(66, midi(n, score));
        assertEquals(2f, n.unbeamedDurationBeats(), 0);
        assertEquals(1, n.augmentationDots());
        var harmonic = fret(100, 0, 7, 1, 0, 0, 1, TabEffect.encode(TabEffect.HARMONIC, 0));
        score = standalone(List.of(harmonic));
        assertEquals(83, midi(score.notes().get(0), score));
    }

    @Test
    public void threeUnpitchedAttacksCannotVoteForPairedOctaveTransposition() {
        var notes =
                List.of(
                        note(.2f, 0, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED),
                        note(.4f, 0, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED),
                        note(.6f, 0, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED));
        var frets =
                List.of(
                        fret(100, 0, 0, 1, 0, 0, 1, 0),
                        fret(200, 0, 0, 1, 0, 0, 1, 0),
                        fret(300, 0, 0, 1, 0, 0, 1, 0));
        assertEquals(notes, paired(notes, frets).notes());
    }
}

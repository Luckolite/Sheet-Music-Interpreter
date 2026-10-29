// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original numbered pitched anacrusis with an omitted silent staff; no captured score material. */
public class OmittedNumberedPickupLaneOriginalTest {
    static final List<MeasureRegion> SHORT =
            List.of(
                    new MeasureRegion(.1f, .2f, .2f, .4f),
                    new MeasureRegion(.21f, .51f, .2f, .4f),
                    new MeasureRegion(.52f, .87f, .2f, .4f));

    static ScoreNoteEvent note(float pos, int staff, int count) {
        return new ScoreNoteEvent(
                0, pos, 3, staff, count, .25f, false, 1, 0, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 1);
    }

    static List<ScoreNoteEvent> pitched() {
        return List.of(note(.25f, 0, 2), note(.8f, 0, 2));
    }

    static ScorePageInterpretation score(
            List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests, boolean proof) {
        return new ScorePageInterpretation(
                        SHORT,
                        notes,
                        0,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 12, 8)),
                        rests)
                .withOpeningPickup(proof);
    }

    static double span(List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests, boolean proof) {
        return ScoreOpeningDuration.provedQuarterBeats(score(notes, rests, proof), 6);
    }

    @Test
    public void independentlyNumberedPitchedPickupAllowsWhollyOmittedBass() {
        assertEquals(3, span(pitched(), List.of(), true), 0);
    }

    @Test
    public void unknownZeroDoesNotAuthenticateMissingLane() {
        assertTrue(Double.isNaN(span(pitched(), List.of(), false)));
    }

    @Test
    public void legacyUnnumberedApiStillRequiresEveryStaff() {
        assertTrue(
                Double.isNaN(
                        ScoreOpeningDuration.provedQuarterBeats(
                                List.of(note(.7f, 0, 2), note(.9f, 0, 2)),
                                List.of(),
                                SHORT,
                                6,
                                0)));
    }

    @Test
    public void partialPitchedSecondLaneMustAgree() {
        var n = new ArrayList<>(pitched());
        n.add(note(.6f, 1, 2));
        assertTrue(Double.isNaN(span(n, List.of(), true)));
    }

    @Test
    public void partialRestSecondLaneMustAgree() {
        assertTrue(
                Double.isNaN(
                        span(
                                pitched(),
                                List.of(new ScoreRestEvent(0, .5f, .35f, .02f, 1, 2, 1.5)),
                                true)));
    }

    @Test
    public void explicitAgreeingRestLaneStillWorks() {
        assertEquals(
                3,
                span(pitched(), List.of(new ScoreRestEvent(0, .5f, .35f, .02f, 1, 2, 3)), true),
                0);
    }

    @Test
    public void existingFullMeasureRestLaneSemanticsRemain() {
        assertEquals(
                3,
                span(
                        pitched(),
                        List.of(ScoreRestEvent.fullMeasure(0, .5f, .35f, .02f, 1, 2)),
                        true),
                0);
    }

    @Test
    public void silentPickupStillNeedsEveryExplicitLane() {
        assertTrue(
                Double.isNaN(
                        span(
                                List.of(),
                                List.of(new ScoreRestEvent(0, .5f, .25f, .02f, 0, 2, 1)),
                                true)));
    }

    @Test
    public void silentFullMeasureRestsDoNotShorten() {
        assertTrue(
                Double.isNaN(
                        span(
                                List.of(),
                                List.of(
                                        ScoreRestEvent.fullMeasure(0, .5f, .25f, .02f, 0, 2),
                                        ScoreRestEvent.fullMeasure(0, .5f, .35f, .02f, 1, 2)),
                                true)));
    }

    @Test
    public void unpitchedOnlyContentsCannotOmitStaff() {
        assertTrue(
                Double.isNaN(
                        span(
                                pitched().stream()
                                        .map(n -> n.withKind(ScoreNoteEvent.Kind.UNPITCHED))
                                        .toList(),
                                List.of(),
                                true)));
    }

    @Test
    public void graceOnlyContentsCannotOmitStaff() {
        assertTrue(
                Double.isNaN(
                        span(
                                pitched().stream()
                                        .map(n -> n.withArticulations(NoteOrnament.GRACE))
                                        .toList(),
                                List.of(),
                                true)));
    }

    @Test
    public void graceDoesNotAddToProvedOrdinarySpan() {
        var n = new ArrayList<>(pitched());
        n.add(note(.4f, 0, 2).withArticulations(NoteOrnament.GRACE));
        assertEquals(3, span(n, List.of(), true), 0);
    }

    @Test
    public void incomingTieKeepsNominalClock() {
        var n = new ArrayList<>(pitched());
        n.set(
                0,
                new ScoreNoteEvent(
                        0, .25f, 3, 0, 2, .25f, true, 1, 0, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 1));
        assertTrue(Double.isNaN(span(n, List.of(), true)));
    }

    @Test
    public void boundaryTieKeepsNominalClock() {
        var n = new ArrayList<>(pitched());
        n.set(0, n.get(0).withBoundaryTies(1));
        assertTrue(Double.isNaN(span(n, List.of(), true)));
    }

    @Test
    public void crossStaffOwnershipKeepsNominalClock() {
        var n = new ArrayList<>(pitched());
        n.set(0, n.get(0).withCrossStaffBeam());
        assertTrue(Double.isNaN(span(n, List.of(), true)));
    }

    @Test
    public void nominalLengthContentsCannotShorten() {
        assertTrue(
                Double.isNaN(
                        ScoreOpeningDuration.provedQuarterBeats(
                                score(pitched(), List.of(), true), 3)));
    }

    @Test
    public void fullWidthLayoutCannotBeShortenedByNumberFlag() {
        var s =
                new ScorePageInterpretation(
                                List.of(
                                        new MeasureRegion(.1f, .35f, .2f, .4f),
                                        new MeasureRegion(.36f, .61f, .2f, .4f),
                                        new MeasureRegion(.62f, .87f, .2f, .4f)),
                                pitched(),
                                0,
                                List.of(),
                                List.of(),
                                List.of(new ScoreMeterChange(0, 12, 8)),
                                List.of())
                        .withOpeningPickup(true);
        assertTrue(Double.isNaN(ScoreOpeningDuration.provedQuarterBeats(s, 6)));
    }

    @Test
    public void terminalDurationStillNeedsExplicitLanesAndRests() {
        var n =
                List.of(
                        new ScoreNoteEvent(
                                2,
                                .25f,
                                3,
                                0,
                                2,
                                .25f,
                                false,
                                1,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1));
        assertTrue(
                Double.isNaN(
                        ScoreOpeningDuration.provedClosingQuarterBeats(
                                n,
                                List.of(new ScoreRestEvent(2, .8f, .25f, .02f, 0, 2, 1.5)),
                                3,
                                6)));
    }

    @Test
    public void boundaryClockPreservesPrintedMeterAndSourceContents() {
        var s = score(pitched(), List.of(), true);
        var n = s.notes();
        var r = s.rests();
        var clock = ScoreBoundaryClock.resolve(s, 6);
        assertEquals(3, clock.openingQuarterBeats(), 0);
        assertEquals(3, clock.meter(s, 6).startBeat(1), 0);
        assertEquals(9, clock.meter(s, 6).startBeat(2), 0);
        assertEquals(12, s.meterChanges().get(0).numerator());
        assertEquals(8, s.meterChanges().get(0).denominator());
        assertSame(n, s.notes());
        assertSame(r, s.rests());
    }

    @Test
    public void oneKnownPitchCannotAuthenticateUnknownOpeningDuration() {
        var n = new ArrayList<>(pitched());
        n.add(
                new ScoreNoteEvent(
                        0, .6f, 4, 0, 2, .25f, false, 0, 0, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 0));
        assertTrue(Double.isNaN(span(n, List.of(), true)));
    }

    @Test
    public void unknownOtherLaneCannotBeTreatedAsOmitted() {
        var n = new ArrayList<ScoreNoteEvent>();
        n.add(note(.25f, 0, 3));
        n.add(note(.8f, 0, 3));
        n.add(
                new ScoreNoteEvent(
                        0, .6f, 4, 1, 3, .35f, false, 0, 0, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 0));
        assertTrue(Double.isNaN(span(n, List.of(), true)));
    }

    @Test
    public void rhythmlessTabPitchesCannotProveDuration() {
        var frets =
                List.of(
                        new TablatureDecoder.Fret(40, 100, 0, 3, 0, 0, 0, 0),
                        new TablatureDecoder.Fret(70, 110, 1, 5, 0, 0, 0, 0),
                        new TablatureDecoder.Fret(200, 120, 2, 7, 0, 0, 0, 0));
        var tab = new TablatureDecoder.Staff(100, 10, -1, frets, List.of(20f, 100f, 300f));
        var decoded =
                TablatureDecoder.apply(
                        new ScorePageInterpretation(List.of(), List.of()), List.of(tab), 500, 500);
        assertFalse(decoded.notes().isEmpty());
        assertTrue(
                decoded.notes().stream()
                        .allMatch(n -> Double.isNaN(ScoreNoteTiming.writtenDurationBeats(n))));
        assertTrue(Double.isNaN(span(decoded.notes(), List.of(), true)));
    }
}

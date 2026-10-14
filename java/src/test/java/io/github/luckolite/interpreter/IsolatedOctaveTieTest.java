// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original event-level isolated octave marks, tied notes and ownership negatives. */
public class IsolatedOctaveTieTest {
    private ScoreNoteEvent note(int x, int step, int staff, boolean tied) {
        return new ScoreNoteEvent(
                        0,
                        x / 700f,
                        step,
                        staff,
                        2,
                        (staff == 0 ? 150 : 330) / 600f,
                        tied,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        1)
                .withClef(ScoreNoteEvent.CLEF_TREBLE);
    }

    private List<ScoreNoteEvent> apply(List<ScoreNoteEvent> notes) {
        return OctaveMarkDetectorTest.apply(
                OctaveMarkDetectorTest.page(),
                List.of(OctaveMarkDetectorTest.word("8va", 130, 40)),
                notes);
    }

    @Test
    public void isolatedOctaveIncludesTiedContinuation() {
        var n = apply(List.of(note(150, 2, 0, false), note(300, 2, 0, true)));
        assertEquals(1, n.get(0).octaveShift());
        assertEquals(1, n.get(1).octaveShift());
    }

    @Test
    public void tieChainKeepsShiftUntilNewAttack() {
        var n =
                apply(
                        List.of(
                                note(150, 2, 0, false),
                                note(250, 2, 0, true),
                                note(350, 2, 0, true),
                                note(450, 2, 0, false)));
        assertEquals(List.of(1, 1, 1, 0), n.stream().map(ScoreNoteEvent::octaveShift).toList());
    }

    @Test
    public void independentReattackDoesNotInherit() {
        assertEquals(
                0,
                apply(List.of(note(150, 2, 0, false), note(300, 2, 0, false)))
                        .get(1)
                        .octaveShift());
    }

    @Test
    public void differentPitchCannotInherit() {
        assertEquals(
                0,
                apply(List.of(note(150, 2, 0, false), note(300, 3, 0, true))).get(1).octaveShift());
    }

    @Test
    public void otherStaffCannotInherit() {
        assertEquals(
                0,
                apply(List.of(note(150, 2, 0, false), note(300, 2, 1, true))).get(1).octaveShift());
    }

    @Test
    public void restBeforeContinuationCannotInherit() {
        assertEquals(
                0,
                apply(List.of(note(150, 2, 0, false), note(300, 2, 0, true).withLeadingRest(1)))
                        .get(1)
                        .octaveShift());
    }

    @Test
    public void explicitNewDirectionOverridesCarry() {
        var n =
                OctaveMarkDetectorTest.apply(
                        OctaveMarkDetectorTest.page(),
                        List.of(
                                OctaveMarkDetectorTest.word("8va", 130, 40),
                                OctaveMarkDetectorTest.word("15ma", 280, 40)),
                        List.of(note(150, 2, 0, false), note(300, 2, 0, true)));
        assertEquals(2, n.get(1).octaveShift());
    }

    @Test
    public void ambiguousUnisonDoesNotChooseAnOwner() {
        assertEquals(
                0,
                apply(
                                List.of(
                                        note(150, 2, 0, false),
                                        note(150, 2, 0, false),
                                        note(300, 2, 0, true)))
                        .get(2)
                        .octaveShift());
    }

    private ScoreNoteEvent pitched(int bar, float position, int accidental, boolean tied) {
        return new ScoreNoteEvent(bar, position, 2, 0, 2, 150 / 600f, tied, 0, 0, accidental, 1)
                .withClef(ScoreNoteEvent.CLEF_TREBLE);
    }

    @Test
    public void tieAcrossBarRetainsIsolatedShift() {
        var notes =
                List.of(
                        pitched(0, .3f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, false),
                        pitched(1, .3f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, true));
        var m = List.of(new MeasureRegion(0, .5f, 0, 1), new MeasureRegion(.5f, 1, 0, 1));
        var n =
                OctaveMarkDetector.apply(
                        List.of(OctaveMarkDetectorTest.word("8va", 90, 40)),
                        List.of(OctaveMarkDetectorTest.TOP, OctaveMarkDetectorTest.BOTTOM),
                        m,
                        notes,
                        OctaveMarkDetectorTest.page(),
                        700,
                        600);
        assertEquals(List.of(1, 1), n.stream().map(ScoreNoteEvent::octaveShift).toList());
    }

    @Test
    public void explicitAccidentalContradictionDoesNotCarry() {
        var n =
                apply(
                        List.of(
                                pitched(0, .2f, ScoreNoteEvent.ACCIDENTAL_SHARP, false),
                                pitched(0, .5f, ScoreNoteEvent.ACCIDENTAL_NATURAL, true)));
        assertEquals(0, n.get(1).octaveShift());
    }

    @Test
    public void implicitTiedAccidentalCanCarry() {
        var n =
                apply(
                        List.of(
                                pitched(0, .2f, ScoreNoteEvent.ACCIDENTAL_SHARP, false),
                                pitched(0, .5f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, true)));
        assertEquals(1, n.get(1).octaveShift());
    }

    @Test
    public void nonAdjacentBarCannotCarry() {
        var notes =
                List.of(
                        pitched(0, .2f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, false),
                        pitched(2, .5f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, true));
        var m =
                List.of(
                        OctaveMarkDetectorTest.M.get(0),
                        OctaveMarkDetectorTest.M.get(0),
                        OctaveMarkDetectorTest.M.get(0));
        var n =
                OctaveMarkDetector.apply(
                        List.of(OctaveMarkDetectorTest.word("8va", 130, 40)),
                        List.of(OctaveMarkDetectorTest.TOP, OctaveMarkDetectorTest.BOTTOM),
                        m,
                        notes,
                        OctaveMarkDetectorTest.page(),
                        700,
                        600);
        assertEquals(0, n.get(1).octaveShift());
    }

    @Test
    public void printedDashedEndIsNotExtended() {
        var g = OctaveMarkDetectorTest.page();
        OctaveMarkDetectorTest.dash(g, 120, 370, 55);
        var n =
                OctaveMarkDetectorTest.apply(
                        g,
                        List.of(OctaveMarkDetectorTest.word("8va", 80, 40)),
                        List.of(note(150, 2, 0, false), note(500, 2, 0, true)));
        assertEquals(0, n.get(1).octaveShift());
    }

    @Test
    public void unshiftedReattackInterruptsCarryChain() {
        var n =
                apply(
                        List.of(
                                note(150, 2, 0, false),
                                note(250, 2, 0, false),
                                note(350, 2, 0, true)));
        assertEquals(List.of(1, 0, 0), n.stream().map(ScoreNoteEvent::octaveShift).toList());
    }
}

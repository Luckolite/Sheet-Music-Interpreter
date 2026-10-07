// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete triplet slots include a rest and independently held voices. */
public class ContinuedTripletRestTest {
    final List<ScoreNoteEvent> score = new ArrayList<>(), moving = new ArrayList<>();
    final List<Integer> slots = new ArrayList<>();
    ScoreNoteEvent middle, whole;

    private float x(int slot) {
        return .05f + slot * .075f;
    }

    private ScoreNoteEvent note(
            float x, int beam, float duration, int direction, float lead, float follow) {
        return new ScoreNoteEvent(
                0, x, 4, 1, 2, .55f, false, 0, beam, 0, duration, 1, follow, 0, 0, false, lead,
                false, 0, 0, 1, direction);
    }

    private void draw(boolean leading) {
        for (int slot = 0; slot < 12; slot++) {
            if (slot == (leading ? 0 : 6)) continue;
            var n =
                    note(
                            x(slot),
                            1,
                            0,
                            leading && slot >= 6 ? -1 : 1,
                            leading && slot == 1 ? .5f : 0,
                            !leading && slot == 5 ? .5f : 0);
            score.add(n);
            moving.add(n);
            slots.add(slot);
        }
        score.add(note(x(0), 0, 2, -1, 0, 0));
        if (!leading) {
            whole = note(x(0), 0, 4, 0, 0, 0);
            score.add(whole);
            middle = note(x(6), 0, 2, -1, 0, 0);
            score.add(middle);
        }
    }

    private double onset(ScoreNoteEvent n) {
        return ScoreNoteTiming.beatInMeasure(n, score, 4);
    }

    private double duration(ScoreNoteEvent n) {
        return ScoreNoteTiming.resolvedWrittenDurationBeats(n, score, 4);
    }

    private void replace(int at, ScoreNoteEvent n) {
        score.remove(moving.get(at));
        moving.set(at, n);
        score.add(n);
    }

    @Test
    public void internalRestPreservesAllWrittenTripletAttacks() {
        draw(false);
        for (int i = 0; i < moving.size(); i++)
            assertEquals(slots.get(i) / 3., onset(moving.get(i)), .0001);
    }

    @Test
    public void internalRestPreservesEveryTripletDuration() {
        draw(false);
        for (var n : moving) assertEquals(1 / 3., duration(n), .0001);
    }

    @Test
    public void heldHalfAtRestKeepsItsIndependentClock() {
        draw(false);
        assertEquals(2, onset(middle), .0001);
        assertEquals(2, duration(middle), .0001);
    }

    @Test
    public void heldWholeIsNotShortenedByTheRest() {
        draw(false);
        assertEquals(0, onset(whole), .0001);
        assertEquals(4, duration(whole), .0001);
    }

    @Test
    public void leadingRestOwnsOneTripletSlot() {
        draw(true);
        assertEquals(1 / 3., onset(moving.get(0)), .0001);
        assertEquals(1 / 3., duration(moving.get(0)), .0001);
    }

    @Test
    public void directionChangeBetweenTripletGroupsKeepsTheClock() {
        draw(true);
        for (int i = 0; i < moving.size(); i++)
            assertEquals(slots.get(i) / 3., onset(moving.get(i)), .0001);
    }

    @Test
    public void openingChordKeepsBothTripletDurations() {
        draw(false);
        var chord = note(x(0), 1, 0, 1, 0, 0);
        score.add(chord);
        assertEquals(1 / 3., duration(chord), .0001);
        assertEquals(1 / 3., duration(moving.get(0)), .0001);
    }

    @Test
    public void noteAndRestMetadataAreReadOnly() {
        draw(false);
        var before = List.copyOf(score);
        assertEquals(1 / 3., duration(moving.get(0)), .0001);
        assertEquals(before, score);
    }

    @Test
    public void missingAttackCannotProveACompleteVoice() {
        draw(false);
        score.remove(moving.get(3));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void wrongRestLengthCannotFillTheTripletClock() {
        draw(false);
        replace(5, note(x(5), 1, 0, 1, 0, 1));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void restWithoutPrintedSpaceAbstains() {
        draw(false);
        replace(6, note(x(5) + .04f, 1, 0, 1, 0, 0));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void unknownMovingShaftAbstains() {
        draw(false);
        replace(3, note(x(3), 1, 0, 0, 0, 0));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void directionChangeWithinATripletAbstains() {
        draw(false);
        replace(1, note(x(1), 1, 0, -1, 0, 0));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void aForeignSixteenthIsNotAnEighthTriplet() {
        draw(false);
        replace(3, note(x(3), 2, 0, 1, 0, 0));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void counterVoiceAtTheWrongColumnAbstains() {
        draw(false);
        score.remove(middle);
        score.add(note(x(6) + .04f, 0, 2, -1, 0, 0));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void aLeadingRestNeedsAnIndependentClockAnchor() {
        draw(true);
        score.removeIf(n -> n.beamCount() == 0);
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void aChordCannotGiveConflictingRestHints() {
        draw(false);
        score.add(note(x(5), 1, 0, 1, 0, 0));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void aChordCannotHideOpposingShafts() {
        draw(false);
        score.add(note(x(0), 1, 0, -1, 0, 0));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }

    @Test
    public void anOrdinarySixBeatPhraseKeepsItsWrittenEighths() {
        draw(false);
        for (var n : moving)
            assertEquals(.5, ScoreNoteTiming.resolvedWrittenDurationBeats(n, score, 6), .0001);
    }

    @Test
    public void aConflictingTupletCannotBeReinterpreted() {
        draw(false);
        replace(3, moving.get(3).withTupletRatio(5, 4));
        assertEquals(.5, duration(moving.get(0)), .0001);
    }
}

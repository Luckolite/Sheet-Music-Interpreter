// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic rhythms and ambiguity controls, no private score data. */
public class CompleteOpposedWrittenClockTest {
    static List<ScoreNoteEvent> mixed(boolean rest) {
        float[] up = {.06f, .21f, .37f, .52f, .68f, .79f, .93f},
                down = {.06f, .21f, .37f, .52f, .68f, .86f};
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < up.length; i++)
            notes.add(
                    new ScoreNoteEvent(0, up[i], 12 + i % 3, 0, 1, .3f, false, 0, 1)
                            .withStemDirection(1)
                            .withTupletRatio(i < 4 ? 1 : 3, i < 4 ? 1 : 2));
        for (int i = rest ? 1 : 0; i < down.length; i++)
            notes.add(
                    new ScoreNoteEvent(0, down[i], 2 + i % 2, 0, 1, .4f, false, 0, 1)
                            .withStemDirection(-1));
        return notes;
    }

    static ScoreNoteEvent replace(ScoreNoteEvent n, String key, Object value) throws Exception {
        var c = ScoreNoteEvent.class.getRecordComponents();
        var values = new Object[c.length];
        var types = new Class<?>[c.length];
        for (int i = 0; i < c.length; i++) {
            types[i] = c[i].getType();
            values[i] = c[i].getName().equals(key) ? value : c[i].getAccessor().invoke(n);
        }
        return ScoreNoteEvent.class.getDeclaredConstructor(types).newInstance(values);
    }

    static Object proof(List<ScoreNoteEvent> notes) throws Exception {
        var method =
                ParallelTripletClock.class.getDeclaredMethod(
                        "findCompleteOpposedWrittenVoices",
                        ScoreNoteEvent.class,
                        List.class,
                        double.class);
        method.setAccessible(true);
        return method.invoke(null, notes.get(0), notes, 3d);
    }

    static void placements(List<ScoreNoteEvent> notes, boolean rest) {
        for (boolean batched : new boolean[] {false, true}) {
            var session = batched ? ScoreNoteTiming.beginTimingSession() : null;
            try {
                for (int stem : new int[] {1, -1}) {
                    double onset = stem == -1 && rest ? .5 : 0;
                    for (var n : notes)
                        if (n.stemDirection() == stem) {
                            var p = ScoreNoteTiming.writtenPlacement(n, notes, 3).orElseThrow();
                            assertEquals(onset, p.onsetBeats(), 1e-9);
                            assertEquals(
                                    ScoreNoteTiming.writtenDurationBeats(n),
                                    p.durationBeats(),
                                    1e-9);
                            onset += ScoreNoteTiming.writtenDurationBeats(n);
                        }
                    assertEquals(3, onset, 1e-9);
                }
            } finally {
                if (session != null) session.close();
            }
        }
    }

    @Test
    public void completeMixedLanesKeepEachWrittenAttackAndDuration() {
        placements(mixed(false), false);
    }

    @Test
    public void repeatedCoincidentShaftsAnchorMissingLeadingRest() {
        placements(mixed(true), true);
    }

    @Test
    public void explicitLeadingRestUsesTheSameIndependentClock() {
        var n = mixed(true);
        n.set(7, n.get(7).withLeadingRest(.5f));
        placements(n, true);
    }

    @Test
    public void unknownStemCannotEstablishASecondVoice() throws Exception {
        var n = mixed(false);
        n.set(8, n.get(8).withStemDirection(0));
        assertNull(proof(n));
    }

    @Test
    public void bridgeNeedsItsSeparateCrossStaffProof() throws Exception {
        var n = mixed(false);
        n.set(8, n.get(8).withCrossStaffBeam());
        assertNull(proof(n));
    }

    @Test
    public void graceAttacksCannotFillTheMetricalLane() throws Exception {
        var n = mixed(false);
        n.set(8, n.get(8).withArticulations(NoteOrnament.GRACE));
        assertNull(proof(n));
    }

    @Test
    public void singleLaneCannotProveAnOpposingClock() throws Exception {
        var n = mixed(false);
        n.removeIf(x -> x.stemDirection() == -1);
        assertNull(proof(n));
    }

    @Test
    public void twoIncompleteLanesCannotInventAnOpening() throws Exception {
        var n = mixed(true);
        n.remove(0);
        assertNull(proof(n));
    }

    @Test
    public void coincidentPositionsWithDifferentBeatsDeclineTheRepair() throws Exception {
        var n = mixed(false);
        n.set(n.size() - 1, replace(n.get(n.size() - 1), "positionInMeasure", .93f));
        assertNull(proof(n));
    }

    @Test
    public void missingInternalAttackCannotBecomeALeadingRest() throws Exception {
        var n = mixed(false);
        n.remove(9);
        assertNull(proof(n));
    }

    @Test
    public void aSingleCoincidentAnchorIsInsufficient() throws Exception {
        var n = mixed(false);
        for (int i = 8; i < n.size(); i++)
            n.set(i, replace(n.get(i), "positionInMeasure", n.get(i).positionInMeasure() + .04f));
        assertNull(proof(n));
    }

    @Test
    public void conflictingChordDurationsCannotEstablishALane() throws Exception {
        var n = mixed(false);
        n.add(n.get(0).withTupletRatio(3, 2));
        assertNull(proof(n));
    }

    @Test
    public void anInteriorLeadingRestHintIsAmbiguous() throws Exception {
        var n = mixed(false);
        n.set(9, n.get(9).withLeadingRest(.5f));
        assertNull(proof(n));
    }

    @Test
    public void overfullVoiceCannotBeCompressedToFit() throws Exception {
        var n = mixed(false);
        n.add(replace(n.get(7), "positionInMeasure", .99f));
        assertNull(proof(n));
    }

    @Test
    public void opposedQuarterAndEighthClocksWorkAcrossMeters() {
        for (int beats : new int[] {2, 3, 4}) {
            var notes = new ArrayList<ScoreNoteEvent>();
            for (int i = 0; i < beats; i++)
                notes.add(
                        new ScoreNoteEvent(
                                        0, .05f + i * .9f / beats, 12, 0, 1, .3f, false, 0, 0, 0, 1)
                                .withStemDirection(1));
            for (int i = 0; i < beats * 2; i++)
                notes.add(
                        new ScoreNoteEvent(0, .05f + i * .45f / beats, 2, 0, 1, .4f, false, 0, 1)
                                .withStemDirection(-1));
            for (var n : notes) {
                var p = ScoreNoteTiming.writtenPlacement(n, notes, beats).orElseThrow();
                assertEquals((n.positionInMeasure() - .05) * beats / .9, p.onsetBeats(), 1e-6);
                assertEquals(n.stemDirection() == 1 ? 1 : .5, p.durationBeats(), 1e-9);
            }
        }
    }
}

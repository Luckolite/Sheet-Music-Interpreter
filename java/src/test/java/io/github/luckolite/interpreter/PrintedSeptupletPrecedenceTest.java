// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Procedural competing numerals and unrelated pitches; no source-score pixels or coordinates. */
public class PrintedSeptupletPrecedenceTest {
    private static final int W = 500, H = 240;
    private static final String[] THREE = {
        "..#######...",
        ".##########.",
        "###......###",
        "####.....###",
        "####.....###",
        "####.....###",
        ".##.....####",
        ".......####.",
        "......####..",
        "....#####...",
        "....#####...",
        "....#####...",
        "......####..",
        ".......####.",
        "##.....####.",
        "###....####.",
        "###....####.",
        "###....####.",
        ".###....###.",
        "..########..",
        "..########..",
        "....####...."
    };

    private static byte[] ink(boolean seven, boolean three) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        if (seven)
            for (int y = 0; y < 22; y++) {
                int left = y < 3 ? 0 : Math.max(0, 9 - (y - 3) / 2),
                        right = y < 3 ? 11 : Math.min(11, left + 3);
                for (int x = left; x <= right; x++) gray[(10 + y) * W + 214 + x] = 0;
            }
        if (three)
            for (int y = 0; y < THREE.length; y++)
                for (int x = 0; x < 12; x++)
                    if (THREE[y].charAt(x) == '#') gray[(50 + y) * W + 134 + x] = 0;
        return gray;
    }

    private static List<ScoreNoteEvent> notes(int size, int beams) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < size; i++)
            notes.add(
                    new ScoreNoteEvent(
                            0, .20f + i * .08f, i - 2, 0, 1, .4f, false, 0, beams, 2, 0, 1));
        return notes;
    }

    private static List<ScoreNoteEvent> apply(
            List<ScoreNoteEvent> n, boolean seven, boolean three) {
        return TripletRhythmDetector.apply(
                n, List.of(new MeasureRegion(0, 1, .1f, .8f)), ink(seven, three), W, H);
    }

    private static void allSeven(List<ScoreNoteEvent> n) {
        for (var note : n) {
            assertEquals(7, note.tupletDivisor());
            assertEquals(4, note.tupletNormalNotes());
        }
    }

    @Test
    public void completeSevenOwnsItsAttacksBeforeShorterCandidate() {
        allSeven(apply(notes(7, 2), true, true));
    }

    @Test
    public void thirtySecondsKeepTheirPrintedValueAndSevenInFourRatio() {
        var result = apply(notes(7, 3), true, true);
        allSeven(result);
        for (var note : result)
            assertEquals(1.0 / 14, ScoreNoteTiming.writtenDurationBeats(note), 1e-8);
    }

    @Test
    public void isolatedSevenKeepsExistingRecognition() {
        allSeven(apply(notes(7, 2), true, false));
    }

    @Test
    public void threeAloneCannotBecomeSeven() {
        var result = apply(notes(7, 2), false, true);
        assertTrue(result.stream().noneMatch(n -> n.tupletDivisor() == 7));
        assertTrue(result.subList(0, 3).stream().allMatch(n -> n.tupletDivisor() == 3));
    }

    @Test
    public void missingNumeralsPreserveOrdinaryValues() {
        var n = notes(7, 2);
        assertEquals(n, apply(n, false, false));
    }

    @Test
    public void sixOrEightAttacksCannotBecomeSeven() {
        for (int size : new int[] {6, 8})
            assertTrue(
                    apply(notes(size, 2), true, true).stream()
                            .noneMatch(n -> n.tupletDivisor() == 7));
    }

    @Test
    public void differentWrittenDurationPreventsSevenMembership() {
        var n = notes(7, 2);
        var old = n.get(4);
        n.set(
                4,
                new ScoreNoteEvent(
                        0,
                        old.positionInMeasure(),
                        old.staffStep(),
                        0,
                        1,
                        .4f,
                        false,
                        0,
                        1,
                        2,
                        0,
                        1));
        assertTrue(apply(n, true, true).stream().noneMatch(x -> x.tupletDivisor() == 7));
    }

    @Test
    public void inputOrderAndPitchesArePreserved() {
        var n = notes(7, 2);
        Collections.reverse(n);
        var result = apply(n, true, true);
        allSeven(result);
        for (int i = 0; i < n.size(); i++)
            assertEquals(n.get(i).staffStep(), result.get(i).staffStep());
    }
}

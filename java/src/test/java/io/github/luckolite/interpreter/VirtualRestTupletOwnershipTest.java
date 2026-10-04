// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original numeral drawing and ensemble voices with a silent neighbouring staff. */
public class VirtualRestTupletOwnershipTest {
    static final int W = 400, H = 500;

    static byte[] image() {
        String[] rows = {
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
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < 12; x++)
                if (rows[y].charAt(x) == '#') gray[(175 + y) * W + 180 + x] = 0;
        return gray;
    }

    static List<ScoreNoteEvent> notes() {
        var n = new ArrayList<ScoreNoteEvent>();
        for (float x : new float[] {156, 186, 216})
            n.add(
                    new ScoreNoteEvent(0, x / W, 0, 2, 4, 140f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(-1));
        return n;
    }

    static List<MeasureRegion> bars() {
        return List.of(new MeasureRegion(0, 1, .06f, .828f));
    }

    @Test
    public void printedBelowStaffTripletIsReadWithoutRests() {
        assertTrue(
                TripletRhythmDetector.apply(notes(), bars(), image(), W, H).stream()
                        .allMatch(n -> n.tupletDivisor() == 3));
    }

    @Test
    public void silentNeighbourRestCannotInventStaffGeometry() {
        var rest = new ScoreRestEvent(0, .5f, 225f / H, .02f, 3, 4, 4);
        var result = TripletRhythmDetector.withRests(notes(), List.of(rest), bars(), image(), W, H);
        assertTrue(
                result.notes().stream()
                        .allMatch(
                                n ->
                                        n.tupletDivisor() == 3
                                                && n.tupletNormalNotes() == 2
                                                && n.stemDirection() == -1));
        assertEquals(List.of(rest), result.rests());
    }

    @Test
    public void actualOtherStaffHeadsStillOwnTheirPitchGeometry() {
        var n = new ArrayList<>(notes());
        n.add(new ScoreNoteEvent(0, .5f, -4, 3, 4, 225f / H, false, 0, 0, 2, 4, 1));
        var result =
                TripletRhythmDetector.withRests(
                        n,
                        List.of(new ScoreRestEvent(0, .5f, 225f / H, .02f, 3, 4, 4)),
                        bars(),
                        image(),
                        W,
                        H);
        assertTrue(result.notes().subList(0, 3).stream().allMatch(x -> x.tupletDivisor() == 1));
    }
}

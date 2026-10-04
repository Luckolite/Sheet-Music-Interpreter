// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural raster: compressed middle spacing needs physical beam ownership. */
public class UnevenPrintedTripletTest {
    private static final int W = 400, H = 500;
    private static final String[] THREE = {
        "..#######...", ".##########.", "###......###", "####.....###",
        "####.....###", "####.....###", ".##.....####", ".......####.",
        "......####..", "....#####...", "....#####...", "....#####...",
        "......####..", ".......####.", "##.....####.", "###....####.",
        "###....####.", "###....####.", ".###....###.", "..########..",
        "..########..", "....####...."
    };

    private static byte[] image(boolean shaft, boolean beam, boolean numeral) {
        var gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        if (beam)
            for (int y = 184; y <= 190; y++) for (int x = 126; x <= 246; x++) gray[y * W + x] = 0;
        for (int head : new int[] {120, 160, 240}) {
            for (int y = 255; y <= 264; y++)
                for (int x = head - 7; x <= head + 7; x++) gray[y * W + x] = 0;
            if (shaft || head != 160)
                for (int y = 187; y <= 260; y++)
                    for (int x = head + 5; x <= head + 7; x++) gray[y * W + x] = 0;
        }
        if (numeral)
            for (int y = 0; y < THREE.length; y++)
                for (int x = 0; x < 12; x++)
                    if (THREE[y].charAt(x) == '#') gray[(140 + y) * W + 178 + x] = 0;
        return gray;
    }

    private static List<ScoreNoteEvent> notes(int middleStem) {
        var notes = new ArrayList<ScoreNoteEvent>();
        int i = 0;
        for (int x : new int[] {120, 160, 240})
            notes.add(
                    new ScoreNoteEvent(0, x / (float) W, i++, 0, 1, .52f, false, 0, 1)
                            .withStemDirection(x == 160 ? middleStem : 1));
        return notes;
    }

    private static List<ScoreNoteEvent> apply(
            boolean shaft, boolean beam, boolean numeral, int middleStem) {
        return TripletRhythmDetector.apply(
                notes(middleStem),
                List.of(new MeasureRegion(0, 1, .32f, .64f)),
                image(shaft, beam, numeral),
                W,
                H);
    }

    @Test
    public void unequalEngravingRetainsAThreeOwnedByAllShafts() {
        assertTrue(
                apply(true, true, true, 1).stream()
                        .allMatch(n -> n.tupletDivisor() == 3 && n.tupletNormalNotes() == 2));
    }

    @Test
    public void aMissingMiddleShaftCannotBeInventedFromTheEndpoints() {
        assertTrue(apply(false, true, true, 1).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void aNumeralWithoutItsContinuousBeamKeepsTheOrdinaryRhythm() {
        assertTrue(apply(true, false, true, 1).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void beamWithoutPrintedThreeCannotRelabelUnequalNotes() {
        assertTrue(apply(true, true, false, 1).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void unknownOrOpposingMiddleShaftDoesNotJoinThePrintedGroup() {
        assertTrue(apply(true, true, true, 0).stream().allMatch(n -> n.tupletDivisor() == 1));
        assertTrue(apply(true, true, true, -1).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void anOpposingChordShaftCannotBorrowTheOtherVoicesNumeral() {
        var n = notes(1);
        n.add(new ScoreNoteEvent(0, .3f, 5, 0, 1, .40f, false, 0, 1).withStemDirection(-1));
        var gray = image(true, true, true);
        for (int y = 195; y <= 204; y++) for (int x = 113; x <= 127; x++) gray[y * W + x] = 0;
        for (int y = 200; y <= 270; y++) for (int x = 113; x <= 115; x++) gray[y * W + x] = 0;
        for (int y = 267; y <= 273; y++) for (int x = 114; x <= 246; x++) gray[y * W + x] = 0;
        var result =
                TripletRhythmDetector.apply(
                        n, List.of(new MeasureRegion(0, 1, .32f, .64f)), gray, W, H);
        assertTrue(result.stream().allMatch(x -> x.tupletDivisor() == 1));
    }
}

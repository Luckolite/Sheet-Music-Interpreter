// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original stylized descending accent stroke; no score pixels or extracted outlines. */
public class DescendingAccentFiveTest {
    private static byte[] ink(int scale) {
        int width = 500 * scale, height = 240 * scale;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int y = 0; y < 24; y++) {
            int left = y < 6 ? 4 : y < 10 ? 3 : y < 16 ? 7 : y < 22 ? 11 : 11;
            int right = y < 6 ? 10 : y < 10 ? 8 : y < 16 ? 13 : y < 22 ? 14 : 13;
            for (int x = left; x <= right; x++)
                for (int dy = 0; dy < scale; dy++)
                    for (int dx = 0; dx < scale; dx++)
                        gray[((30 + y) * scale + dy) * width + (170 + x) * scale + dx] = 0;
        }
        return gray;
    }

    private static List<ScoreNoteEvent> notes(boolean mixed) {
        List<ScoreNoteEvent> notes = new ArrayList<>();
        float[] p =
                mixed
                        ? new float[] {.20f, .28f, .36f, .44f, .50f, .54f, .58f}
                        : new float[] {.20f, .28f, .36f, .44f, .52f};
        for (int i = 0; i < p.length; i++)
            notes.add(
                    new ScoreNoteEvent(
                            0, p[i], 0, 0, 1, .4f, false, 0, mixed && i >= 3 ? 2 : 1, 2, 0));
        return notes;
    }

    @Test
    public void shortGlyphMustHaveAnActualReturningFoot() {
        byte[] g = new byte[500 * 240];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < 12; y++) {
            int left = y < 3 ? 2 : y < 5 ? 1 : y < 6 ? 4 : y < 8 ? 5 : y < 11 ? 6 : 7;
            int right = y < 5 ? 5 : y < 6 ? 6 : 8;
            for (int x = left; x <= right; x++) g[(30 + y) * 500 + 174 + x] = 0;
        }
        var notes = notes(true);
        assertEquals(
                notes,
                TripletRhythmDetector.apply(
                        notes, List.of(new MeasureRegion(0, 1, .2f, .7f)), g, 500, 240));
    }

    @Test
    public void descendingStrokeDoesNotScaleFiveEqualAttacks() {
        var notes = notes(false);
        assertEquals(
                notes,
                TripletRhythmDetector.apply(
                        notes, List.of(new MeasureRegion(0, 1, .1f, .8f)), ink(1), 500, 240));
    }

    @Test
    public void descendingStrokeDoesNotScaleSubdividedAttacks() {
        var notes = notes(true);
        assertEquals(
                notes,
                TripletRhythmDetector.apply(
                        notes, List.of(new MeasureRegion(0, 1, .1f, .8f)), ink(1), 500, 240));
    }

    @Test
    public void scaledDescendingStrokeAlsoAbstains() {
        var notes = notes(true);
        assertEquals(
                notes,
                TripletRhythmDetector.apply(
                        notes, List.of(new MeasureRegion(0, 1, .1f, .8f)), ink(2), 1000, 480));
    }
}

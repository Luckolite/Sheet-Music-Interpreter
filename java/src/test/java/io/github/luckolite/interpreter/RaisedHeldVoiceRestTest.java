// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural raised rest after an upper moving voice over a held whole note. */
public class RaisedHeldVoiceRestTest {
    private List<ScoreRestEvent> read(boolean held, boolean stems, int voiceStaff, float duration) {
        byte[] gray = new byte[600 * 280];
        Arrays.fill(gray, (byte) 240);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 580; x++) gray[y * 600 + x] = 0;
        int[][] rows = {
            {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 8}, {7, 10}, {7, 11}, {6, 11},
            {6, 11}, {5, 11}, {5, 11}, {4, 10}, {4, 9}, {5, 9}, {6, 9}, {7, 10}, {8, 11}, {6, 12},
            {4, 13}, {3, 13}, {2, 13}, {2, 6}, {3, 6}, {3, 6}, {4, 7}, {5, 7}, {6, 8}, {7, 9},
            {8, 9}
        };
        for (int row = 0; row < rows.length; row++)
            for (int y = 49 + Math.round(row * 1.5f); y <= 50 + Math.round(row * 1.5f); y++)
                for (int x = 450 + rows[row][0]; x <= 450 + rows[row][1]; x++)
                    gray[y * 600 + x] = 0;

        var notes = new ArrayList<ScoreNoteEvent>();
        if (held)
            notes.add(
                    new ScoreNoteEvent(
                            0, 150 / 600f, 0, 0, 1, 110 / 280f, false, 0, 0, 2, duration));
        for (int x : new int[] {150, 410}) {
            notes.add(
                    new ScoreNoteEvent(
                            0, x / 600f, 4, voiceStaff, 1, 78 / 280f, false, 0, 1, 2, 0));
            for (int y = 74; y <= 82; y++)
                for (int xx = x - 7; xx <= x + 7; xx++)
                    if ((xx - x) * (xx - x) / 49.0 + (y - 78) * (y - 78) / 16.0 <= 1)
                        gray[y * 600 + xx] = 0;
            if (stems)
                for (int y = 35; y <= 76; y++)
                    for (int xx = x + 8; xx <= x + 10; xx++) gray[y * 600 + xx] = 0;
        }
        if (stems)
            for (int y = 35; y <= 38; y++) for (int x = 159; x <= 419; x++) gray[y * 600 + x] = 0;
        byte[] original = gray.clone();
        var result =
                SixteenthRestDetector.detect(
                        gray,
                        600,
                        280,
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                        notes);
        assertArrayEquals(original, gray);
        return result;
    }

    private boolean target(List<ScoreRestEvent> rests) {
        return rests.stream()
                .anyMatch(
                        x ->
                                x.durationBeats() == 1
                                        && x.positionInMeasure() > .7
                                        && x.pageY() < .35);
    }

    @Test
    public void upperMovingVoiceOverHeldWholeAllowsItsTrailingQuarter() {
        assertTrue(target(read(true, true, 0, 4)));
    }

    @Test
    public void absentWholeDoesNotProveTheRaisedVoice() {
        assertFalse(target(read(false, true, 0, 4)));
    }

    @Test
    public void halfNoteDoesNotProveFullBarSustain() {
        assertFalse(target(read(true, true, 0, 2)));
    }

    @Test
    public void semanticBeamsWithoutPrintedStemsCannotAuthorizePlacement() {
        assertFalse(target(read(true, false, 0, 4)));
    }

    @Test
    public void anotherStaffCannotAuthorizePlacement() {
        assertFalse(target(read(true, true, 1, 4)));
    }
}

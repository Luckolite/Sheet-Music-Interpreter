// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rest polygon above a separately attached up-stem beamed voice. */
public class UpStemQuarterRestVoiceTest {
    private static List<ScoreRestEvent> read(
            boolean shaft,
            boolean beam,
            int direction,
            int otherStaff,
            boolean dot,
            boolean overlap) {
        int W = 600, H = 280;
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 240);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 580; x++) gray[y * W + x] = 0;
        int[][] rows = {
            {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 8}, {7, 10}, {7, 11}, {6, 11},
            {6, 11}, {5, 11}, {5, 11}, {4, 10}, {4, 9}, {5, 9}, {6, 9}, {7, 10}, {8, 11}, {6, 12},
            {4, 13}, {3, 13}, {2, 13}, {2, 6}, {3, 6}, {3, 6}, {4, 7}, {5, 7}, {6, 8}, {7, 9},
            {8, 9}
        };
        for (int r = 0; r < rows.length; r++)
            for (int y = 49 + Math.round(r * 1.5f); y <= 50 + Math.round(r * 1.5f); y++)
                for (int x = 150 + rows[r][0]; x <= 150 + rows[r][1]; x++) gray[y * W + x] = 0;
        int headY = overlap ? 86 : 150;
        for (int cx : new int[] {160, 280})
            for (int y = headY - 6; y <= headY + 6; y++)
                for (int x = cx - 8; x <= cx + 8; x++)
                    if (Math.pow((x - cx) / 8., 2) + Math.pow((y - headY) / 6., 2) <= 1)
                        gray[y * W + x] = 0;
        if (shaft)
            for (int x : new int[] {169, 289})
                for (int y = 73; y <= headY; y++)
                    for (int dx = -1; dx <= 1; dx++) gray[y * W + x + dx] = 0;
        if (beam)
            for (int y = 70; y <= 76; y++) for (int x = 169; x <= 289; x++) gray[y * W + x] = 0;
        if (dot)
            for (int y = 58; y <= 62; y++)
                for (int x = 173; x <= 177; x++)
                    if ((x - 175) * (x - 175) + (y - 60) * (y - 60) <= 4) gray[y * W + x] = 0;
        var first =
                new ScoreNoteEvent(0, 160f / W, 0, 0, 1, headY / (float) H, false, 0, 1, 2, 0, 1)
                        .withStemDirection(direction);
        var other =
                new ScoreNoteEvent(
                                0,
                                280f / W,
                                0,
                                otherStaff,
                                1,
                                headY / (float) H,
                                false,
                                0,
                                1,
                                2,
                                0,
                                1)
                        .withStemDirection(direction);
        return SixteenthRestDetector.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(0, 1, 0, 1)),
                List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                List.of(first, other));
    }

    @Test
    public void completeUpStemBeamAllowsASeparateQuarterRest() {
        assertTrue(
                read(true, true, 1, 0, false, false).stream()
                        .anyMatch(r -> r.durationBeats() == 1 && r.pageY() < .35));
    }

    @Test
    public void separateDotStillExtendsTheRecoveredQuarterRest() {
        assertTrue(
                read(true, true, 1, 0, true, false).stream()
                        .anyMatch(r -> r.durationBeats() == 1.5 && r.pageY() < .35));
    }

    @Test
    public void unannotatedShaftIsReadBeforeGuideVoiceAssignment() {
        assertTrue(
                read(true, true, 0, 0, false, false).stream()
                        .anyMatch(r -> r.durationBeats() == 1 && r.pageY() < .35));
    }

    @Test
    public void missingShaftCannotAuthorizeTheRest() {
        assertTrue(read(false, true, 1, 0, false, false).isEmpty());
    }

    @Test
    public void disconnectedBeamCannotAuthorizeTheRest() {
        assertTrue(read(true, false, 1, 0, false, false).isEmpty());
    }

    @Test
    public void oppositeRecordedShaftCannotAuthorizeTheRest() {
        assertTrue(read(true, true, -1, 0, false, false).isEmpty());
    }

    @Test
    public void anotherStaffCannotAuthorizeThisRest() {
        assertTrue(read(true, true, 1, 1, false, false).isEmpty());
    }

    @Test
    public void restStillMustBeClearOfThePrintedHead() {
        assertTrue(read(true, true, 1, 0, false, true).isEmpty());
    }
}

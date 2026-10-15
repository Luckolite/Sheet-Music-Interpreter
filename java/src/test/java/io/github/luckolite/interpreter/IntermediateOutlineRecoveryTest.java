// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic pale filled notation; no source-score pixels. */
public class IntermediateOutlineRecoveryTest {
    private static final int W = 240, H = 220;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    private void fixture(boolean down, int paper, int inside, boolean rectangle) {
        Arrays.fill(gray, (byte) paper);
        Arrays.fill(labels, (byte) 0);
        for (int y = 60; y <= 140; y += 20)
            for (int x = 15; x < 225; x++) {
                gray[y * W + x] = 30;
                labels[y * W + x] = 4;
            }
        for (int y = 81; y <= 99; y++)
            for (int x = 78; x <= 102; x++) {
                float ellipse = (x - 90) * (x - 90) / 144f + (y - 90) * (y - 90) / 81f;
                if (rectangle || ellipse <= 1)
                    gray[y * W + x] =
                            (byte)
                                    (rectangle
                                            ? (x == 78 || x == 102 || y == 81 || y == 99
                                                    ? 30
                                                    : inside)
                                            : ellipse > .65f ? 30 : inside);
            }
        int sx = down ? 78 : 102;
        for (int y = down ? 90 : 42; y <= (down ? 138 : 90); y++) {
            gray[y * W + sx] = 30;
            labels[y * W + sx] = 1;
        }
    }

    private List<FadedNoteheadRecovery.Head> find() {
        return FadedNoteheadRecovery.find(labels, gray, W, H, 20, 40, 160);
    }

    @Test
    public void intermediateOutlineStillEnclosesGrayFill() {
        fixture(true, 240, 195, false);
        for (int y = 81; y <= 99; y++)
            for (int x = 78; x <= 102; x++)
                if (gray[y * W + x] == 30 && labels[y * W + x] != 1) gray[y * W + x] = (byte) 160;
        assertEquals(1, find().size());
    }

    @Test
    public void intermediateOutlineDoesNotRecoverWhiteInterior() {
        fixture(true, 240, 240, false);
        for (int y = 81; y <= 99; y++)
            for (int x = 78; x <= 102; x++)
                if (gray[y * W + x] == 30 && labels[y * W + x] != 1) gray[y * W + x] = (byte) 160;
        assertTrue(find().isEmpty());
    }

    @Test
    public void recoveredBeamedHeadRetainsEighthDuration() {
        fixture(true, 240, 195, false);
        for (int y = 81; y <= 99; y++)
            for (int x = 78; x <= 102; x++)
                if (gray[y * W + x] == 30 && labels[y * W + x] != 1) gray[y * W + x] = (byte) 160;
        for (int y = 88; y <= 90; y++) for (int x = 86; x <= 93; x++) gray[y * W + x] = (byte) 235;
        for (int y = 160; y <= 166; y++)
            for (int x = 78; x <= 160; x++) {
                gray[y * W + x] = 30;
                labels[y * W + x] = 5;
            }
        for (int y = 90; y <= 166; y++) {
            gray[y * W + 78] = 30;
            labels[y * W + 78] = 1;
            gray[y * W + 158] = 30;
            labels[y * W + 158] = 1;
        }
        for (int y = 81; y <= 99; y++)
            for (int x = 158; x <= 182; x++)
                if ((x - 170) * (x - 170) / 144f + (y - 90) * (y - 90) / 81f <= 1) {
                    gray[y * W + x] = 30;
                    labels[y * W + x] = 2;
                }
        for (int y = 91; y <= 166; y++)
            for (int x : new int[] {77, 78, 79, 157, 158, 159}) {
                gray[y * W + x] = 30;
                labels[y * W + x] = 1;
            }
        var notes =
                OmrScoreInterpreter.extract(
                        labels, gray, W, H, List.of(new MeasureRegion(.06f, .95f, .2f, .75f)));
        assertEquals(2, notes.size());
        assertEquals(5, notes.get(0).staffStep());
        assertEquals(1, notes.get(0).beamCount());
        assertEquals(0f, notes.get(0).unbeamedDurationBeats(), .001f);
    }
}

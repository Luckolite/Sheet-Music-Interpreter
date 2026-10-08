// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural upper-half/lower-quarter voice rests; no score pixels. */
public class StackedVoiceRestRecognitionTest {
    private static final int W = 600, H = 300, GAP = 16;

    private byte[] page(boolean upper, boolean hook, boolean zigzag) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 100; y <= 164; y += GAP) for (int x = 20; x < 580; x++) gray[y * W + x] = 0;
        if (upper)
            for (int y = 94; y < 100; y++) for (int x = 150; x <= 173; x++) gray[y * W + x] = 0;
        int[][] contour = {
            {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 8}, {7, 10}, {7, 11}, {6, 11},
            {6, 11}, {5, 11}, {5, 11}, {4, 10}, {4, 9}, {5, 9}, {6, 9}, {7, 10}, {8, 11}, {6, 12},
            {4, 13}, {3, 13}, {2, 13}, {2, 5}, {2, 5}, {2, 5}, {2, 5}, {3, 5}, {3, 5}, {3, 5},
            {3, 5}
        };
        for (int row = 0; row < contour.length; row++) {
            int left = contour[row][0], right = contour[row][1];
            if (!hook && row >= 19) {
                left = 7;
                right = 10;
            }
            if (!zigzag && row < 19) {
                left = 5;
                right = 8;
            }
            int first = 132 + Math.round(GAP * .6f + row * GAP * 3f / 31);
            int last = 132 + Math.round(GAP * .6f + (row + 1) * GAP * 3f / 31);
            for (int y = first; y <= last; y++)
                for (int x = 150 + left; x <= 150 + right; x++) gray[y * W + x] = 0;
        }
        return gray;
    }

    private List<ScoreRestEvent> detect(byte[] gray) {
        return SixteenthRestDetector.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(.04f, .95f, .2f, .9f)),
                List.of(new SixteenthRestDetector.Staff(100, 164, GAP, 0, 1)),
                List.of());
    }

    @Test
    public void twoSilentVoicesRetainBothWrittenRests() {
        var rests = detect(page(true, true, true));
        assertEquals(rests.toString(), 2, rests.size());
        assertTrue(rests.stream().anyMatch(r -> r.durationBeats() == 2));
        assertTrue(rests.stream().anyMatch(r -> r.durationBeats() == 1));
    }

    @Test
    public void lowerPlacementStillNeedsOpposingVoiceEvidence() {
        assertTrue(detect(page(false, true, true)).isEmpty());
    }

    @Test
    public void upperRestCannotAuthorizeAnIncompleteLowerHook() {
        var rests = detect(page(true, false, true));
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(2, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void upperRestCannotAuthorizeALowerHookWithoutZigzag() {
        var rests = detect(page(true, true, false));
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(2, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void rasterIsUnchanged() {
        byte[] gray = page(true, true, true), before = gray.clone();
        detect(gray);
        assertArrayEquals(before, gray);
    }
}

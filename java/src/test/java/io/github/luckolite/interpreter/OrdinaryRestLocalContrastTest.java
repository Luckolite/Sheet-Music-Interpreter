// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural pale connectors on dark paper; no source-score pixels. */
public class OrdinaryRestLocalContrastTest {
    private static final int W = 640, H = 260, GAP = 16;

    private byte[] page() {
        byte[] gray = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                gray[y * W + x] = (byte) (130 + x * 10 / W + (x * 17 + y * 29) % 4);
        for (int line = 0; line < 5; line++)
            for (int x = 20; x < W - 20; x++) gray[(80 + line * GAP) * W + x] = 40;
        return gray;
    }

    private void eighth(byte[] gray, boolean bulb, boolean tail, int shift) {
        if (bulb)
            for (int y = 97; y <= 107; y++)
                for (int x = 235; x <= 247; x++)
                    if ((x - 241) * (x - 241) / 36.0 + (y - 102) * (y - 102) / 25.0 <= 1)
                        gray[(y + shift) * W + x] = 65;
        if (tail)
            for (int y = 98; y <= 129; y++) {
                int x = 250 - (y - 98) * 12 / 31;
                gray[(y + shift) * W + x] = 92;
                gray[(y + shift) * W + x + 1] = 92;
            }
    }

    private void quarter(byte[] gray, boolean zigzag, boolean hook) {
        int[][] rows = {
            {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 8}, {7, 10}, {7, 11}, {6, 11},
            {6, 11}, {5, 11}, {5, 11}, {4, 10}, {4, 9}, {5, 9}, {6, 9}, {7, 10}, {8, 11}, {6, 12},
            {4, 13}, {3, 13}, {2, 13}, {2, 5}, {2, 5}, {2, 5}, {2, 5}, {3, 5}, {3, 5}, {3, 5},
            {3, 5}
        };
        for (int row = 0; row < rows.length; row++) {
            int left = rows[row][0], right = rows[row][1];
            if (!hook && row >= 19) {
                left = 7;
                right = 10;
            }
            if (!zigzag && row < 19) {
                left = 5;
                right = 8;
            }
            int first = 80 + Math.round(GAP * .6f + row * GAP * 3f / 31);
            int last = 80 + Math.round(GAP * .6f + (row + 1) * GAP * 3f / 31);
            for (int y = first; y <= last; y++)
                for (int x = 320 + left; x <= 320 + right; x++)
                    gray[y * W + x] = (byte) (row % 5 == 0 ? 65 : 92);
        }
    }

    private List<ScoreRestEvent> read(byte[] gray, List<ScoreNoteEvent> owners) {
        return SixteenthRestDetector.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(0, 1, .2f, .9f)),
                List.of(new SixteenthRestDetector.Staff(80, 144, GAP, 0, 1)),
                owners);
    }

    @Test
    public void darkPaperKeepsThePaleCompleteEighthBody() {
        byte[] gray = page();
        eighth(gray, true, true, 0);
        var rests = read(gray, List.of());
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(.5, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void darkPaperKeepsThePaleCompleteQuarterBody() {
        byte[] gray = page();
        quarter(gray, true, true);
        var rests = read(gray, List.of());
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(1, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void isolatedBulbCannotAddSilence() {
        byte[] gray = page();
        eighth(gray, true, false, 0);
        assertTrue(read(gray, List.of()).isEmpty());
    }

    @Test
    public void incompleteQuarterBodiesCannotAddSilence() {
        byte[] noHook = page();
        quarter(noHook, true, false);
        byte[] noZigzag = page();
        quarter(noZigzag, false, true);
        assertTrue(read(noHook, List.of()).isEmpty());
        assertTrue(read(noZigzag, List.of()).isEmpty());
    }

    @Test
    public void noteOwnerKeepsItsFlagColumn() {
        byte[] gray = page();
        eighth(gray, true, true, 0);
        var owner = new ScoreNoteEvent(0, 243f / W, 3, 0, 1, 114f / H, false, 0, 1);
        assertTrue(read(gray, List.of(owner)).isEmpty());
    }

    @Test
    public void crossedHeadWithContinuingStemCannotBecomeRest() {
        byte[] gray = page();
        for (int k = -6; k <= 6; k++)
            for (int t = 0; t < 2; t++) {
                gray[(102 + k) * W + 241 + k + t] = 65;
                gray[(102 - k) * W + 241 + k + t] = 65;
            }
        for (int y = 103; y <= 166; y++) for (int x = 235; x <= 237; x++) gray[y * W + x] = 92;
        assertTrue(read(gray, List.of()).isEmpty());
    }

    @Test
    public void darkPaperTextureCannotCreateRest() {
        assertTrue(read(page(), List.of()).isEmpty());
    }

    @Test
    public void belowStaffBodyCannotBecomeOrdinaryRest() {
        byte[] gray = page();
        eighth(gray, true, true, GAP * 4);
        assertTrue(read(gray, List.of()).isEmpty());
    }

    @Test
    public void decodingPreservesSourcePixels() {
        byte[] gray = page();
        quarter(gray, true, true);
        eighth(gray, true, true, 0);
        byte[] before = gray.clone();
        read(gray, List.of());
        assertArrayEquals(before, gray);
    }

    @Test
    public void shortenedUpwardTailKeepsItsCompleteBulb() {
        byte[] gray = page(), untouched = gray.clone();
        eighth(gray, true, true, 0);
        for (int y = 123; y <= 129; y++)
            for (int x = 235; x <= 252; x++) gray[y * W + x] = untouched[y * W + x];
        var rests = read(gray, List.of());
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(.5, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void incompleteDiagonalWithoutItsFootCannotAddSilence() {
        byte[] gray = page(), untouched = gray.clone();
        eighth(gray, true, true, 0);
        for (int y = 113; y <= 129; y++)
            for (int x = 235; x <= 252; x++) gray[y * W + x] = untouched[y * W + x];
        assertTrue(read(gray, List.of()).isEmpty());
    }

    @Test
    public void sparseFlagPixelsDoNotSupplyACompleteRestBulb() {
        byte[] gray = page();
        eighth(gray, false, true, 0);
        for (int y : new int[] {99, 102, 105})
            for (int x = 241; x <= 243; x++) gray[y * W + x] = 65;
        assertTrue(read(gray, List.of()).isEmpty());
    }
}

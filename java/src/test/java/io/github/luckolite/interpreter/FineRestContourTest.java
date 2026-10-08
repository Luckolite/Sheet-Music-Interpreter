// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original blurred contours with separate dark body and pale ink fringe. */
public class FineRestContourTest {
    private static final int W = 640, H = 260, GAP = 16;

    private static final class Page {
        final byte[] gray = new byte[W * H];

        Page() {
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    gray[y * W + x] = (byte) (130 + x * 10 / W + (x * 17 + y * 29) % 4);
            for (int line = 0; line < 5; line++)
                box(20, 80 + line * GAP, W - 21, 80 + line * GAP, 40);
        }

        void box(int l, int t, int r, int b, int value) {
            for (int y = t; y <= b; y++)
                for (int x = l; x <= r; x++) gray[y * W + x] = (byte) value;
        }

        void eighth(boolean bulb, boolean complete) {
            if (bulb)
                for (int y = 97; y <= 107; y++)
                    for (int x = 235; x <= 247; x++)
                        if ((x - 241) * (x - 241) / 36.0 + (y - 102) * (y - 102) / 25.0 <= 1)
                            box(x, y, x, y, 80);
            for (int y = 98; y <= (complete ? 129 : 111); y++) {
                int x = 250 - (y - 98) * 12 / 31;
                if (y >= 119) box(x - 5, y, x + 5, y, 103);
                box(x, y, x + 1, y, 70);
            }
        }

        void quarter(boolean zigzag, boolean hook) {
            box(320, 90, 338, 139, 103);
            int[][] rows = {
                {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 8}, {7, 10}, {7, 11}, {6, 11},
                {6, 11}, {5, 11}, {5, 11}, {4, 10}, {4, 9}, {5, 9}, {6, 9}, {7, 10}, {8, 11},
                {6, 12}, {4, 13}, {3, 13}, {2, 13}, {2, 5}, {2, 5}, {2, 5}, {2, 5}, {3, 5}, {3, 5},
                {3, 5}, {3, 5}
            };
            for (int row = 0; row < rows.length; row++) {
                int l = rows[row][0], r = rows[row][1];
                if (!hook && row >= 19) {
                    l = 7;
                    r = 10;
                }
                if (!zigzag && row < 19) {
                    l = 5;
                    r = 8;
                }
                int first = 80 + Math.round(GAP * .6f + row * GAP * 3f / 31),
                        last = 80 + Math.round(GAP * .6f + (row + 1) * GAP * 3f / 31);
                box(320 + l, first, 320 + r, last, 80);
            }
        }

        List<ScoreRestEvent> read(List<ScoreNoteEvent> notes) {
            return SixteenthRestDetector.detect(
                    gray,
                    W,
                    H,
                    List.of(new MeasureRegion(0, 1, .2f, .9f)),
                    List.of(new SixteenthRestDetector.Staff(80, 144, GAP, 0, 1)),
                    notes);
        }
    }

    @Test
    public void blurredFootKeepsItsWholeEighthDuration() {
        var p = new Page();
        p.eighth(true, true);
        var rests = p.read(List.of());
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(.5, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void faintFringeCannotFlattenACompleteQuarterContour() {
        var p = new Page();
        p.quarter(true, true);
        var rests = p.read(List.of());
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(1, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void incompleteEighthFootStillCannotSupplySilence() {
        var p = new Page();
        p.eighth(true, false);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void diagonalWithoutCompleteBulbStillCannotSupplySilence() {
        var p = new Page();
        p.eighth(false, true);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void incompleteQuarterHookStillCannotSupplySilence() {
        var p = new Page();
        p.quarter(true, false);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void lowerHookWithoutUpperZigzagStillCannotSupplySilence() {
        var p = new Page();
        p.quarter(false, true);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void sparseNoteOwnerKeepsItsBlurredFlagColumn() {
        var p = new Page();
        p.eighth(true, true);
        var n = new ScoreNoteEvent(0, 243f / W, 3, 0, 1, 114f / H, false, 0, 1);
        assertTrue(p.read(List.of(n)).isEmpty());
    }

    @Test
    public void recoveringFineContoursPreservesSourceInk() {
        var p = new Page();
        p.eighth(true, true);
        p.quarter(true, true);
        var before = p.gray.clone();
        p.read(List.of());
        assertArrayEquals(before, p.gray);
    }

    @Test
    public void invalidPaperLevelCannotChangeTheSourcePacket() {
        var p = new Page();
        for (float level : new float[] {Float.NaN, Float.POSITIVE_INFINITY, 119, 341})
            assertSame(p.gray, RestPaperTone.normalizeOrdinaryRestInk(p.gray, W, H, GAP, level));
    }
}

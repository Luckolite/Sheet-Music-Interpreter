// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original shaded ovals with known outline, centre and duration. */
public class ShadedHollowPocketTest {
    private static final int W = 500, H = 260, GAP = 16, CX = 200, CY = 128;

    private byte[][] page(int paper, boolean hollow, boolean ruled, boolean open) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) paper);
        for (int y = 80; y <= 144; y += 16)
            for (int x = 20; x < 480; x++) {
                labels[y * W + x] = 4;
                gray[y * W + x] = 35;
            }
        for (int y = CY - 7; y <= CY + 7; y++)
            for (int x = CX - 10; x <= CX + 10; x++) {
                if (Math.pow((x - CX) / 10., 2) + Math.pow((y - CY) / 7., 2) > 1) continue;
                labels[y * W + x] = 2;
                boolean centre = Math.pow((x - CX) / 7.5, 2) + Math.pow((y - CY) / 4.5, 2) < 1;
                gray[y * W + x] = (byte) (hollow && centre ? paper : 35);
                if (ruled && y == CY) gray[y * W + x] = 35;
                if (open && x >= CX + 7 && Math.abs(y - CY) <= 2) gray[y * W + x] = (byte) paper;
            }
        for (int y = CY - 45; y < CY; y++) {
            labels[y * W + CX + 10] = 1;
            gray[y * W + CX + 10] = 35;
        }
        return new byte[][] {labels, gray};
    }

    private boolean proved(byte[][] a) {
        return ShadedHollowPocket.proved(a[1], W, H, CX - 10, CY - 7, CX + 10, CY + 7, GAP);
    }

    @Test
    public void darkPaperClosedOvalIsHollow() {
        assertTrue(proved(page(105, true, false, false)));
    }

    @Test
    public void ruledDarkPaperOvalHasTwoClosedPockets() {
        assertTrue(proved(page(105, true, true, false)));
    }

    @Test
    public void filledOvalHasNoEnclosedPocket() {
        assertFalse(proved(page(105, false, false, false)));
    }

    @Test
    public void openArcIsNotAClosedOval() {
        assertFalse(proved(page(105, true, false, true)));
    }

    @Test
    public void whitePaperUsesExistingRecovery() {
        assertFalse(proved(page(250, true, false, false)));
    }

    @Test
    public void darkHalfRetainsTwoBeatBaseDuration() {
        var a = page(105, true, true, false);
        var m = List.of(new MeasureRegion(.04f, .96f, 60f / H, 170f / H));
        var notes = OmrScoreInterpreter.analyze(a[0], a[1], W, H, m).notes();
        assertEquals(1, notes.size());
        assertEquals(2, notes.get(0).unbeamedDurationBeats(), 0);
    }

    @Test
    public void filledDarkQuarterRetainsOneBeatBaseDuration() {
        var a = page(105, false, false, false);
        var m = List.of(new MeasureRegion(.04f, .96f, 60f / H, 170f / H));
        var notes = OmrScoreInterpreter.analyze(a[0], a[1], W, H, m).notes();
        assertEquals(1, notes.size());
        assertEquals(1, notes.get(0).unbeamedDurationBeats(), 0);
    }

    @Test
    public void preservesRawPixels() {
        var a = page(105, true, true, false);
        byte[] saved = a[1].clone();
        proved(a);
        assertArrayEquals(saved, a[1]);
    }
}

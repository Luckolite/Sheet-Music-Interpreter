// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated ink and rule geometry, without photograph pixels. */
public class ShadedLedgerRuleTest {
    private static final int W = 500, H = 300, G = 16, X = 220, Y = 88;

    private byte[][] page(boolean ledger, int paper) {
        byte[] a = new byte[W * H], b = new byte[W * H];
        Arrays.fill(b, (byte) paper);
        for (int y = 120; y <= 184; y += G)
            for (int x = 20; x < 480; x++) {
                a[y * W + x] = 4;
                b[y * W + x] = 35;
            }
        for (int y = Y - 6; y <= Y + 6; y++)
            for (int x = X - 9; x <= X + 9; x++)
                if (Math.pow((x - X) / 9., 2) + Math.pow((y - Y) / 6., 2) <= 1) {
                    a[y * W + x] = 2;
                    b[y * W + x] = 35;
                }
        for (int y = Y; y < Y + 45; y++) {
            a[y * W + X - 9] = 1;
            b[y * W + X - 9] = 35;
        }
        if (ledger)
            for (int y = Y; y < 120; y += G)
                for (int x = X - 17; x <= X + 17; x++) {
                    a[y * W + x] = 4;
                    b[y * W + x] = 35;
                }
        return new byte[][] {a, b};
    }

    private List<ScoreNoteEvent> notes(byte[][] a) {
        return OmrScoreInterpreter.analyze(
                        a[0], a[1], W, H, List.of(new MeasureRegion(.04f, .96f, 40f / H, 230f / H)))
                .notes();
    }

    @Test
    public void darkPaperIsNotPrintedLedgerInk() {
        assertTrue(notes(page(false, 130)).isEmpty());
    }

    @Test
    public void genuineLedgerHeadSurvivesDarkPaper() {
        assertEquals(1, notes(page(true, 130)).size());
    }

    @Test
    public void whitePaperLedgerHeadSurvives() {
        assertEquals(1, notes(page(true, 250)).size());
    }

    @Test
    public void flatShadowHasNoThinPhase() {
        byte[] a = new byte[W * H];
        Arrays.fill(a, (byte) 100);
        assertFalse(ShadedLedgerRule.thin(a, W, H, X, Y, G));
    }

    @Test
    public void localRuleHasVisibleFlanks() {
        var a = page(true, 130);
        assertTrue(ShadedLedgerRule.thin(a[1], W, H, X + 15, Y, G));
    }

    @Test
    public void preservesInput() {
        var a = page(true, 130);
        byte[] b = a[1].clone(), c = a[0].clone();
        notes(a);
        assertArrayEquals(b, a[1]);
        assertArrayEquals(c, a[0]);
    }
}

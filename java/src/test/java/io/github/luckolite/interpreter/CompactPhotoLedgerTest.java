// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class CompactPhotoLedgerTest {
    static final int W = 320, H = 240, X = 160, Y = 138;

    static byte[][] page(boolean ledger, boolean white) {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Arrays.fill(g, (byte) (white ? 250 : 200));
        for (int y = 42; y <= 90; y += 12)
            for (int x = 20; x < 300; x++) {
                l[y * W + x] = 4;
                g[y * W + x] = 35;
            }
        if (!white)
            for (int y = 96; y < 132; y++) for (int x = 20; x < 300; x++) g[y * W + x] = (byte) 155;
        for (int y = Y - 3; y <= Y + 3; y++)
            for (int x = X - 3; x <= X + 3; x++) {
                g[y * W + x] = (byte) 160;
                if ((x - X) * (x - X) + (y - Y) * (y - Y) <= 9) l[y * W + x] = 2;
            }
        for (int y = Y; y < Y + 28; y++) g[y * W + X - 3] = (byte) 165;
        for (int x = 20; x < 300; x++) g[(Y + 4) * W + x] = (byte) 145;
        if (ledger)
            for (int y = 102; y < 138; y += 12)
                for (int x = X - 10; x <= X + 10; x++) g[y * W + x] = (byte) 145;
        return new byte[][] {l, g};
    }

    static List<ScoreNoteEvent> notes(byte[][] p) {
        return OmrScoreInterpreter.analyze(
                        p[0], p[1], W, H, List.of(new MeasureRegion(.05f, .95f, .05f, .85f)))
                .notes();
    }

    @Test
    public void shadedArtworkCannotSupplyLedgerChain() {
        assertTrue(notes(page(false, false)).isEmpty());
    }

    @Test
    public void printedLedgerChainRetainsHead() {
        assertEquals(1, notes(page(true, false)).size());
    }

    @Test
    public void inputPixelsRemain() {
        var p = page(false, false);
        var l = p[0].clone();
        var g = p[1].clone();
        notes(p);
        assertArrayEquals(l, p[0]);
        assertArrayEquals(g, p[1]);
    }

    @Test
    public void invalidImageCannotContradictAHead() {
        assertFalse(
                CompactLedgerContrast.unsupported(
                        null, 320, 240, 157, 135, 163, 141, 27, 138, 90, 12));
        assertFalse(
                CompactLedgerContrast.unsupported(
                        new byte[320 * 240],
                        320,
                        240,
                        157,
                        135,
                        163,
                        141,
                        27,
                        Float.POSITIVE_INFINITY,
                        90,
                        12));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original shaded score rectangle ending beside broad non-staff photograph texture. */
public class ShadedPrintedStaffEndTest {
    static final int W = 480, H = 220;
    static final float GAP = 16;
    static final int CUT = 260, END = 420;

    static class Drawing {
        final byte[] g = new byte[W * H], l = new byte[W * H];

        Drawing(boolean continuing, int rules, int mode) {
            Arrays.fill(g, (byte) 130);
            for (int y = 0; y < H; y++)
                for (int x = CUT + 1; x < W; x++)
                    g[y * W + x] =
                            (byte)
                                    (mode == 1
                                            ? 255
                                            : mode == 2
                                                    ? 105
                                                    : 95 + ((x / 7) % 8) * 4 + ((y / 19) % 3) * 6);
            for (int k = 0; k < 5; k++) {
                int y = 70 + k * 16;
                for (int x = 20; x <= END; x++) {
                    l[y * W + x] = 4;
                    if (k < rules && (x <= CUT || continuing)) g[y * W + x] = 25;
                }
            }
            for (int x : new int[] {20, CUT, END})
                for (int y = 70; y <= 134; y++) {
                    l[y * W + x] = 1;
                    g[y * W + x] = 20;
                }
            for (int cx : new int[] {105, 190}) {
                for (int y = 98; y <= 106; y++)
                    for (int x = cx - 7; x <= cx + 7; x++)
                        if ((x - cx) * (x - cx) / 49d + (y - 102) * (y - 102) / 16d <= 1) {
                            l[y * W + x] = 2;
                            g[y * W + x] = 15;
                        }
                for (int y = 53; y <= 102; y++) {
                    l[y * W + cx + 7] = 1;
                    g[y * W + cx + 7] = 15;
                }
            }
        }

        Integer cut(List<Integer> bars) {
            return PrintedStaffEnd.closingBeforeTexture(
                    g, W, H, GAP, bars, (line, x) -> 70 + line * GAP);
        }
    }

    @Test
    public void printedRulesEndBeforeTheTextureBar() {
        assertEquals(Integer.valueOf(CUT), new Drawing(false, 5, 0).cut(List.of(20, CUT, END)));
    }

    @Test
    public void continuingPrintedStaffRetainsItsEnding() {
        assertNull(new Drawing(true, 5, 0).cut(List.of(20, CUT, END)));
    }

    @Test
    public void threeRulesCannotProveAStaffEnd() {
        assertNull(new Drawing(false, 3, 0).cut(List.of(20, CUT, END)));
    }

    @Test
    public void whitePaperWithoutTextureIsNotPruned() {
        assertNull(new Drawing(false, 5, 1).cut(List.of(20, CUT, END)));
    }

    @Test
    public void smoothDarkPaperIsNotTextureProof() {
        assertNull(new Drawing(false, 5, 2).cut(List.of(20, CUT, END)));
    }

    @Test
    public void tinyOverhangCannotRemoveARealEnding() {
        assertNull(new Drawing(false, 5, 0).cut(List.of(20, CUT, CUT + 24)));
    }

    @Test
    public void inputMasksRemainUnchanged() {
        var d = new Drawing(false, 5, 0);
        var g = d.g.clone();
        var l = d.l.clone();
        d.cut(List.of(20, CUT, END));
        assertArrayEquals(g, d.g);
        assertArrayEquals(l, d.l);
    }

    @Test
    public void geometricStageDoesNotEmitTheTextureMeasure() {
        var d = new Drawing(false, 5, 0);
        var measures = OmrMeasurePostProcessor.process(d.l, d.g, W, H);
        assertEquals(measures.toString(), 1, measures.size());
    }
}

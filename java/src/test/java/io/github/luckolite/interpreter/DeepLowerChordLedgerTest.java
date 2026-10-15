// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeepLowerChordLedgerTest {
    static final int W = 640, H = 600;

    static class Page {
        byte[] labels = new byte[W * H], gray = new byte[W * H];

        Page(int missing, boolean wide, boolean thick) {
            Arrays.fill(gray, (byte) 255);
            for (int top : new int[] {80, 400})
                for (int i = 0; i < 5; i++) line(20, 620, top + i * 16, 1, 4);
            for (int i = 1; i <= 9; i++)
                if (i != missing)
                    line(wide ? 20 : 130, wide ? 620 : 170, 144 + i * 16, thick ? 12 : 1, 5);
            for (int y : new int[] {176, 232, 288}) head(150, y, true);
            head(450, 112, false);
            line(460, 461, 80, 33, 1);
            head(450, 432, false);
            line(460, 461, 400, 33, 1);
            for (int i = 1; i <= 9; i++)
                if (i != missing)
                    for (int x = wide ? 20 : 130; x <= (wide ? 620 : 170); x++)
                        gray[(144 + i * 16) * W + x] = 0;
        }

        void line(int l, int r, int y, int h, int type) {
            for (int yy = y; yy < y + h; yy++)
                for (int x = l; x <= r; x++) {
                    gray[yy * W + x] = 0;
                    labels[yy * W + x] = (byte) type;
                }
        }

        void head(int cx, int cy, boolean hollow) {
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = cx - 11; x <= cx + 11; x++)
                    if (Math.pow((x - cx) / 11d, 2) + Math.pow((y - cy) / 7d, 2) <= 1) {
                        labels[y * W + x] = 2;
                        gray[y * W + x] =
                                (byte)
                                        (hollow
                                                        && Math.pow((x - cx) / 7d, 2)
                                                                        + Math.pow((y - cy) / 4d, 2)
                                                                < 1
                                                ? 255
                                                : 0);
                    }
        }

        List<ScoreNoteEvent> notes() {
            return OmrScoreInterpreter.extract(
                    labels,
                    gray,
                    W,
                    H,
                    List.of(
                            new MeasureRegion(.03f, .97f, .1f, .54f),
                            new MeasureRegion(.03f, .97f, .62f, .85f)));
        }

        boolean low() {
            return notes().stream().anyMatch(n -> n.measureIndex() == 0 && n.staffStep() == -18);
        }
    }

    @Test
    public void completeRailsAcrossHollowChordRetainLowNote() {
        assertTrue(new Page(0, false, false).low());
    }

    @Test
    public void missingInnerRailRejectsLowNote() {
        assertFalse(new Page(3, false, false).low());
    }

    @Test
    public void longLinesCannotProveLowNote() {
        assertFalse(new Page(0, true, false).low());
    }

    @Test
    public void thickBarsCannotProveLowNote() {
        assertFalse(new Page(0, false, true).low());
    }

    @Test
    public void inputPixelsUnchanged() {
        var p = new Page(0, false, false);
        var g = p.gray.clone();
        var l = p.labels.clone();
        p.notes();
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }
}

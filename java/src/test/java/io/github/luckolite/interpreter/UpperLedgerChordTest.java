// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original reflected ledger stacks; a separate oval crosses an intervening rule. */
public class UpperLedgerChordTest {
    static final int W = 640, H = 600;

    static class Page {
        byte[] gray = new byte[W * H], labels = new byte[W * H];

        Page(int missing, boolean wide, boolean thick, boolean hollow) {
            Arrays.fill(gray, (byte) 255);
            for (int top : new int[] {80, 400})
                for (int i = 0; i < 5; i++) line(20, 620, top + i * 16, 1, 4);
            for (int i = 1; i <= 5; i++)
                if (i != missing)
                    line(wide ? 20 : 130, wide ? 620 : 170, 144 + i * 16, thick ? 12 : 1, 5);
            for (int y : new int[] {176, 200, 224}) head(150, y, hollow);
            for (int y = 120; y <= 224; y++)
                for (int x = 139; x <= 140; x++) {
                    gray[y * W + x] = 0;
                    if (labels[y * W + x] != 2) labels[y * W + x] = 1;
                }
            head(450, 112, false);
            line(460, 461, 80, 33, 1);
            head(450, 432, false);
            line(460, 461, 400, 33, 1);
            for (int i = 1; i <= 5; i++)
                if (i != missing)
                    for (int x = wide ? 20 : 130; x <= (wide ? 620 : 170); x++)
                        gray[(144 + i * 16) * W + x] = 0;
            var g = gray.clone();
            var l = labels.clone();
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    gray[y * W + x] = g[(H - 1 - y) * W + x];
                    labels[y * W + x] = l[(H - 1 - y) * W + x];
                }
        }

        void line(int left, int right, int top, int height, int label) {
            for (int y = top; y < top + height; y++)
                for (int x = left; x <= right; x++) {
                    gray[y * W + x] = 0;
                    labels[y * W + x] = (byte) label;
                }
        }

        void head(int cx, int cy, boolean hollow) {
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = cx - 11; x <= cx + 11; x++) {
                    double q = Math.pow((x - cx) / 11d, 2) + Math.pow((y - cy) / 7d, 2);
                    if (q <= 1) {
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
        }

        List<ScoreNoteEvent> notes() {
            return OmrScoreInterpreter.extract(
                    labels,
                    gray,
                    W,
                    H,
                    List.of(
                            new MeasureRegion(.03f, .97f, .15f, .38f),
                            new MeasureRegion(.03f, .97f, .46f, .90f)));
        }

        boolean upper() {
            return notes().stream().anyMatch(n -> n.measureIndex() == 1 && n.staffStep() == 18);
        }
    }

    @Test
    public void completeThinRailsAcrossFilledChordRetainUpperNote() {
        assertTrue(new Page(0, false, false, false).upper());
    }

    @Test
    public void completeThinRailsAcrossHollowChordRetainUpperNote() {
        assertTrue(new Page(0, false, false, true).upper());
    }

    @Test
    public void missingInnerRuleCannotBeReplacedByChordBody() {
        assertFalse(new Page(2, false, false, false).upper());
    }

    @Test
    public void longUnderlinesDoNotProveAnUpperLedgerChain() {
        assertFalse(new Page(0, true, false, false).upper());
    }

    @Test
    public void broadBlocksDoNotProveThinLedgerFlanks() {
        assertFalse(new Page(0, false, true, false).upper());
    }

    @Test
    public void sourceArraysStayUnchanged() {
        var p = new Page(0, false, false, false);
        var g = p.gray.clone();
        var l = p.labels.clone();
        p.notes();
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }
}

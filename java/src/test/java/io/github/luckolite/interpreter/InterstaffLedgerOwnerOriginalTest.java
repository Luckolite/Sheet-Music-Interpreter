// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original paired staffs, independently short ledger rules and owned shaft. */
public class InterstaffLedgerOwnerOriginalTest {
    static final int W = 400, H = 300;

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];

        Page(String kind) {
            Arrays.fill(g, (byte) 255);
            for (int top : new int[] {40, 166})
                for (int y = top; y <= top + 56; y += 14) rect(20, y, 360, 1, 4);
            rect(20, 40, 1, 183, 1);
            if (!kind.equals("noLedger")) {
                rect(160, 138, 41, 1, 5);
                rect(160, 152, 41, 1, 5);
            }
            if (kind.equals("conflict")) {
                rect(160, 110, 41, 1, 5);
                rect(160, 124, 41, 1, 5);
            }
            if (!kind.equals("noShaft"))
                rect(
                        170,
                        kind.equals("opposed") ? 65 : 130,
                        2,
                        kind.equals("opposed") ? 66 : 67,
                        1);
            oval(180, 130);
            if (kind.equals("broken"))
                for (int y = 155; y < 164; y++)
                    for (int x = 170; x < 172; x++) g[y * W + x] = (byte) 255;
            if (kind.equals("fake"))
                for (int y = 122; y <= 138; y++)
                    for (int x = 171; x <= 190; x++)
                        if (l[y * W + x] == 2) g[y * W + x] = (byte) 255;
            oval(80, 68);
            rect(89, 25, 2, 44, 1);
            oval(300, 194);
            rect(309, 155, 2, 40, 1);
        }

        void rect(int x, int y, int ww, int hh, int k) {
            for (int yy = y; yy < y + hh; yy++)
                for (int xx = x; xx < x + ww; xx++) {
                    l[yy * W + xx] = (byte) k;
                    g[yy * W + xx] = 0;
                }
        }

        void oval(int cx, int cy) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10., 2) + Math.pow((y - cy) / 8., 2) <= 1) {
                        l[y * W + x] = 2;
                        g[y * W + x] = 0;
                    }
        }

        void mirror() {
            byte[] ll = l, gg = g;
            l = new byte[ll.length];
            g = new byte[gg.length];
            for (int i = 0; i < l.length; i++) {
                l[l.length - 1 - i] = ll[i];
                g[g.length - 1 - i] = gg[i];
            }
        }

        List<ScoreNoteEvent> target(boolean mirror) {
            float y = mirror ? (H - 1 - 130) / (float) H : 130f / H;
            return OmrScoreInterpreter.analyze(
                            l, g, W, H, List.of(new MeasureRegion(.05f, .95f, .05f, .85f)))
                    .notes()
                    .stream()
                    .filter(n -> Math.abs(n.pageY() - y) < .01f)
                    .toList();
        }
    }

    @Test
    public void oneInnerLedgerAndDownShaftRetainLowerStaffHead() {
        var n = new Page("valid").target(false);
        assertEquals(1, n.size());
        assertEquals(1, n.get(0).staffIndex());
    }

    @Test
    public void existingUpwardMirrorRemainsSupported() {
        var p = new Page("valid");
        p.mirror();
        var n = p.target(true);
        assertEquals(1, n.size());
        assertEquals(0, n.get(0).staffIndex());
    }

    @Test
    public void absentLedgerCannotSupplyOwnership() {
        assertTrue(new Page("noLedger").target(false).isEmpty());
    }

    @Test
    public void absentShaftCannotSupplyOwnership() {
        assertTrue(new Page("noShaft").target(false).isEmpty());
    }

    @Test
    public void opposedShaftCannotSupplyLowerOwnership() {
        assertTrue(new Page("opposed").target(false).isEmpty());
    }

    @Test
    public void brokenShaftCannotReachOwningStaff() {
        assertTrue(new Page("broken").target(false).isEmpty());
    }

    @Test
    public void unsupportedModelOvalCannotBorrowPrintedShaft() {
        assertTrue(new Page("fake").target(false).isEmpty());
    }

    @Test
    public void competingLedgerSeriesRetainsExistingShaftChoice() {
        var n = new Page("conflict").target(false);
        assertEquals(1, n.size());
        assertEquals(1, n.get(0).staffIndex());
    }

    @Test
    public void inputArraysAreNotMutated() {
        var p = new Page("valid");
        byte[] l = p.l.clone(), g = p.g.clone();
        p.target(false);
        assertArrayEquals(l, p.l);
        assertArrayEquals(g, p.g);
    }
}

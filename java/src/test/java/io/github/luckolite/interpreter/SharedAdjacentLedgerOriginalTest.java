// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original adjacent chord attacks whose short ledger rules meet end-to-end. */
public class SharedAdjacentLedgerOriginalTest {
    static final int W = 420, H = 340;

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];

        Page(String mode) {
            Arrays.fill(g, (byte) 255);
            for (int y = 180; y <= 236; y += 14) rect(20, y, 380, 1, 4);
            rect(20, 180, 1, 57, 1);
            for (int y : new int[] {124, 138, 152, 166})
                if (!mode.equals("missingRule") || y != 138) {
                    if (y == 124) rect(132, y, 65, 1, 5);
                    else {
                        rect(134, y, 29, 1, 5);
                        rect(166, y, 29, 1, 5);
                    }
                }
            rect(171, 110, 2, 84, 1);
            rect(139, 117, 2, 77, 1);
            oval(180, 110);
            oval(180, 159);
            oval(148, 117);
            oval(148, 166);
            if (mode.equals("missingPeer")) {
                for (int y = 109; y <= 125; y++)
                    for (int x = 139; x <= 157; x++) {
                        l[y * W + x] = 0;
                        g[y * W + x] = (byte) 255;
                    }
                rect(132, 124, 65, 1, 5);
            }
            if (mode.equals("brokenPeer"))
                for (int y = 170; y <= 178; y++)
                    for (int x = 139; x <= 140; x++) g[y * W + x] = (byte) 255;
            if (mode.equals("brokenOwn"))
                for (int y = 170; y <= 178; y++)
                    for (int x = 171; x <= 172; x++) g[y * W + x] = (byte) 255;
            if (mode.equals("longBeams"))
                for (int y : new int[] {124, 138, 152, 166}) rect(20, y, 380, 1, 5);
            if (mode.equals("broadRails"))
                for (int y : new int[] {124, 138, 152, 166}) rect(132, y - 4, 65, 9, 5);
            oval(300, 208);
            rect(309, 165, 2, 44, 1);
        }

        void rect(int x, int y, int ww, int hh, int kind) {
            for (int yy = y; yy < y + hh; yy++)
                for (int xx = x; xx < x + ww; xx++) {
                    l[yy * W + xx] = (byte) kind;
                    g[yy * W + xx] = 0;
                }
        }

        void oval(int cx, int cy) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 9; x <= cx + 9; x++)
                    if (Math.pow((x - cx) / 9., 2) + Math.pow((y - cy) / 8., 2) <= 1) {
                        l[y * W + x] = 2;
                        g[y * W + x] = 0;
                    }
        }

        List<ScoreNoteEvent> target() {
            return OmrScoreInterpreter.analyze(
                            l, g, W, H, List.of(new MeasureRegion(.04f, .96f, .05f, .9f)))
                    .notes()
                    .stream()
                    .filter(n -> Math.abs(n.pageY() - 110f / H) < .01f)
                    .toList();
        }
    }

    @Test
    public void adjacentOwnedAttackCanShareFiniteLedgerRules() {
        assertEquals(1, new Page("valid").target().size());
    }

    @Test
    public void absentPhaseRuleCannotBeInvented() {
        assertTrue(new Page("missingRule").target().isEmpty());
    }

    @Test
    public void unprintedPeerCannotJustifyLongRule() {
        assertTrue(new Page("missingPeer").target().isEmpty());
    }

    @Test
    public void brokenPeerShaftCannotOwnSharedRails() {
        assertTrue(new Page("brokenPeer").target().isEmpty());
    }

    @Test
    public void brokenOwnShaftCannotReachPhysicalStaff() {
        assertTrue(new Page("brokenOwn").target().isEmpty());
    }

    @Test
    public void existingLongParallelInputKeepsPriorOutput() {
        assertEquals(1, new Page("longBeams").target().size());
    }

    @Test
    public void broadParallelStripesCannotBeLedgerChain() {
        assertTrue(new Page("broadRails").target().isEmpty());
    }

    @Test
    public void candidateCannotMutateCallerPlanes() {
        var p = new Page("valid");
        var l = p.l.clone();
        var g = p.g.clone();
        p.target();
        assertArrayEquals(l, p.l);
        assertArrayEquals(g, p.g);
    }
}

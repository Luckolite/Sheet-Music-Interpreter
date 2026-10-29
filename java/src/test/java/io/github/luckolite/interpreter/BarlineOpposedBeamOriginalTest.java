// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original grand-staff barline beside a head with an independent opposed double-beam shaft. */
public class BarlineOpposedBeamOriginalTest {
    static final int W = 440, H = 310;

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];
        boolean rotated;

        Page(String mode) {
            Arrays.fill(g, (byte) 255);
            for (int top : new int[] {40, 166})
                for (int y = top; y <= top + 56; y += 14) rect(20, y, 400, 1, 4);
            rect(20, 40, 1, 183, 1);
            rect(291, 40, 2, 183, 1);
            if (mode.equals("brokenBar"))
                for (int y = 115; y <= 120; y++)
                    for (int x = 291; x <= 292; x++) g[y * W + x] = (byte) 255;
            rect(271, 166, 2, 60, 1);
            rect(231, 145, 2, 85, 1);
            rail(231, 271, 226, 222, 4);
            if (!mode.equals("single"))
                rail(
                        231,
                        mode.equals("partial") ? 247 : mode.equals("nearPartial") ? 268 : 271,
                        217,
                        213,
                        4);
            if (mode.equals("triple")) rail(231, 271, 208, 204, 4);
            oval(280, 166);
            oval(240, 145);
            if (mode.equals("missingPeer"))
                for (int y = 137; y <= 153; y++)
                    for (int x = 231; x <= 249; x++)
                        if (l[y * W + x] == 2) {
                            l[y * W + x] = 0;
                            g[y * W + x] = (byte) 255;
                        }
            if (mode.equals("hollowPeer"))
                for (int y = 141; y <= 149; y++)
                    for (int x = 235; x <= 245; x++) g[y * W + x] = (byte) 255;
            if (mode.equals("brokenOwn"))
                for (int y = 185; y <= 191; y++)
                    for (int x = 271; x <= 272; x++) g[y * W + x] = (byte) 255;
            if (mode.equals("axisHead")) oval(291, 80);
            oval(100, 68);
            rect(109, 25, 2, 44, 1);
            oval(365, 194);
            rect(374, 151, 2, 44, 1);
        }

        void rect(int x, int y, int w, int h, int kind) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) {
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

        void rail(int x0, int x1, int y0, int y1, int thick) {
            for (int x = x0; x <= x1; x++) {
                int y = Math.round(y0 + (y1 - y0) * (x - x0) / (float) (271 - x0));
                rect(x, y, 1, thick, 5);
            }
        }

        void rotate() {
            var ll = l;
            var gg = g;
            l = new byte[ll.length];
            g = new byte[gg.length];
            for (int i = 0; i < l.length; i++) {
                l[l.length - 1 - i] = ll[i];
                g[g.length - 1 - i] = gg[i];
            }
            rotated = true;
        }

        List<ScoreNoteEvent> target() {
            float cy = rotated ? H - 1 - 166 : 166;
            return OmrScoreInterpreter.analyze(
                            l, g, W, H, List.of(new MeasureRegion(.03f, .97f, .03f, .94f)))
                    .notes()
                    .stream()
                    .filter(n -> Math.abs(n.pageY() - cy / H) < .01f && n.staffCount() == 2)
                    .toList();
        }
    }

    @Test
    public void aPrintedBarlineCannotReplaceOwnedDoubleRailShaft() {
        var n = new Page("valid").target();
        assertEquals(1, n.size());
        assertEquals(2, n.get(0).beamCount());
    }

    @Test
    public void rotatedBarlineHasTheSameIndependentDoubleRailProof() {
        var p = new Page("valid");
        p.rotate();
        var n = p.target();
        assertEquals(1, n.size());
        assertEquals(2, n.get(0).beamCount());
    }

    @Test
    public void singleRailIsNeverPromotedToTwo() {
        assertTrue(new Page("single").target().stream().noneMatch(n -> n.beamCount() == 2));
    }

    @Test
    public void partialSecondaryCannotSupplyCompleteDoubleRail() {
        assertTrue(new Page("partial").target().stream().noneMatch(n -> n.beamCount() == 2));
    }

    @Test
    public void partialSecondaryEndingThreePixelsFromShaftStaysPartial() {
        assertTrue(new Page("nearPartial").target().stream().noneMatch(n -> n.beamCount() == 2));
    }

    @Test
    public void threePositiveRailsCannotBeRewrittenAsTwo() {
        assertTrue(new Page("triple").target().stream().noneMatch(n -> n.beamCount() == 2));
    }

    @Test
    public void unsupportedPeerCannotProveDoubleRailOwnership() {
        assertTrue(new Page("missingPeer").target().stream().noneMatch(n -> n.beamCount() == 2));
    }

    @Test
    public void hollowPeerCannotProveFilledDoubleRailOwnership() {
        assertTrue(new Page("hollowPeer").target().stream().noneMatch(n -> n.beamCount() == 2));
    }

    @Test
    public void brokenOwnShaftCannotBorrowPeerRails() {
        assertTrue(new Page("brokenOwn").target().stream().noneMatch(n -> n.beamCount() == 2));
    }

    @Test
    public void brokenGrandStaffAxisCannotBeExplainedAsBarline() {
        assertTrue(new Page("brokenBar").target().stream().noneMatch(n -> n.beamCount() == 2));
    }

    @Test
    public void existingHeadOnLongAxisRetainsItsPriorBeamCount() {
        var n = new Page("axisHead").target();
        assertEquals(1, n.size());
        assertEquals(2, n.get(0).beamCount());
    }

    @Test
    public void inputPlanesAreImmutable() {
        var p = new Page("valid");
        var l = p.l.clone();
        var g = p.g.clone();
        p.target();
        assertArrayEquals(l, p.l);
        assertArrayEquals(g, p.g);
    }
}

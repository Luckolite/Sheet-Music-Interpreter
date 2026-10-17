// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original broken outlines with a complete beam; no score pixels. */
public class SparseBeamedOvalTest {
    static class Page {
        int w = 300, h = 230;
        byte[] g = new byte[w * h], a = new byte[w * h];

        Page() {
            Arrays.fill(g, (byte) 250);
        }

        void rect(int x, int y, int ww, int hh, int gray, int label) {
            for (int yy = y; yy < y + hh; yy++)
                for (int xx = x; xx < x + ww; xx++) {
                    g[yy * w + xx] = (byte) gray;
                    a[yy * w + xx] = (byte) label;
                }
        }

        void oval(int cx, boolean recognized) {
            for (int dy = -12; dy <= 12; dy++)
                for (int dx = -12; dx <= 12; dx++) {
                    double v = dx * dx / 121.0 + (dy + .4 * dx) * (dy + .4 * dx) / 40.96;
                    if (v <= 1) {
                        int ink = v > .56 ? 60 : 224;
                        if (!recognized && dx < 0 && dy < 0 && dx > -7) ink = 250;
                        rect(cx + dx, 104 + dy, 1, 1, ink, recognized ? 2 : 0);
                    }
                }
        }

        List<ScoreNoteEvent> notes() {
            return OmrScoreInterpreter.extract(
                    a, g, w, h, List.of(new MeasureRegion(.05f, .95f, .25f, .8f)));
        }
    }

    Page fixture(int mode) {
        Page p = new Page();
        for (int y = 80; y <= 144; y += 16) p.rect(20, y, 260, 1, 40, 4);
        p.oval(100, true);
        p.oval(148, false);
        p.rect(89, 106, 3, 57, 60, 1);
        p.rect(137, 106, 3, 57, 60, mode == 3 ? 0 : 1);
        p.rect(89, 158, 51, 6, 100, 5);
        if (mode == 1) p.rect(122, 150, 4, 22, 250, 0);
        if (mode == 2) p.rect(137, 115, 3, 35, 250, 1);
        return p;
    }

    @Test
    public void brokenOvalRecoversOnOwnedBeam() {
        assertEquals(2, fixture(0).notes().size());
    }

    @Test
    public void retainedPartnerAndRecoveredHeadRemainBeamed() {
        var n = fixture(0).notes();
        assertEquals(2, n.size());
        for (var e : n) {
            assertTrue(e.beamCount() > 0);
            assertEquals(0f, e.unbeamedDurationBeats(), .001f);
        }
    }

    @Test
    public void brokenBeamDoesNotRecover() {
        assertEquals(1, fixture(1).notes().size());
    }

    @Test
    public void semanticShaftWithoutRawInkDoesNotRecover() {
        assertEquals(1, fixture(2).notes().size());
    }

    @Test
    public void rawShaftWithoutSemanticSupportDoesNotRecover() {
        assertEquals(1, fixture(3).notes().size());
    }

    @Test
    public void preservesSourceMasks() {
        var p = fixture(0);
        var g = p.g.clone();
        var a = p.a.clone();
        p.notes();
        assertArrayEquals(g, p.g);
        assertArrayEquals(a, p.a);
    }

    Page transformed(boolean mirror, int scale) {
        var p = fixture(0);
        int w = p.w, h = p.h;
        byte[] g = p.g, a = p.a;
        p.w *= scale;
        p.h *= scale;
        p.g = new byte[p.w * p.h];
        p.a = new byte[p.w * p.h];
        for (int y = 0; y < p.h; y++)
            for (int x = 0; x < p.w; x++) {
                int xx = x / scale, yy = y / scale;
                if (mirror) {
                    xx = w - 1 - xx;
                    yy = h - 1 - yy;
                }
                p.g[y * p.w + x] = g[yy * w + xx];
                p.a[y * p.w + x] = a[yy * w + xx];
            }
        return p;
    }

    @Test
    public void mirroredPairRecovers() {
        assertEquals(2, transformed(true, 1).notes().size());
    }

    @Test
    public void enlargedPairRecovers() {
        assertEquals(2, transformed(false, 2).notes().size());
    }

    @Test
    public void enlargedMirroredPairRecovers() {
        assertEquals(2, transformed(true, 2).notes().size());
    }

    @Test
    public void incompleteOvalDoesNotRecover() {
        var p = fixture(0);
        p.rect(141, 93, 20, 21, 250, 0);
        assertEquals(1, p.notes().size());
    }

    @Test
    public void rectangleOnShaftDoesNotInventHead() {
        var p = fixture(0);
        p.rect(135, 91, 27, 25, 250, 0);
        p.rect(137, 98, 23, 13, 60, 0);
        p.rect(137, 106, 3, 57, 60, 1);
        assertEquals(1, p.notes().size());
    }

    @Test
    public void arcOverShaftDoesNotInventHead() {
        var p = fixture(0);
        p.rect(135, 91, 27, 25, 250, 0);
        for (int x = 137; x <= 159; x++) {
            float t = (x - 137) / 22f;
            int y = Math.round(110 - 12 * 4 * t * (1 - t));
            p.rect(x, y, 1, 2, 60, 0);
        }
        p.rect(137, 110, 3, 53, 60, 1);
        assertEquals(1, p.notes().size());
    }

    @Test
    public void thinRuleCannotOwnRecovery() {
        var p = fixture(0);
        p.rect(92, 155, 45, 12, 250, 0);
        p.rect(92, 161, 45, 1, 60, 4);
        assertEquals(1, p.notes().size());
    }

    @Test
    public void absentNeighbourShaftCannotOwnRecovery() {
        var p = fixture(0);
        p.rect(89, 118, 3, 35, 250, 0);
        assertEquals(1, p.notes().size());
    }

    @Test
    public void washedSingleBeamDoesNotBecomeDouble() {
        var p = fixture(0);
        p.rect(89, 158, 51, 9, 100, 5);
        p.rect(109, 161, 8, 2, 215, 5);
        var n = p.notes();
        assertEquals(2, n.size());
        for (var e : n) assertEquals(1, e.beamCount());
    }

    // Two remaining three-pixel strips do not meet the existing complete-beam proof.
    @Test
    public void washoutWithoutThickRailAbstains() {
        var p = fixture(0);
        p.rect(89, 158, 51, 8, 100, 5);
        p.rect(109, 161, 8, 2, 215, 5);
        assertEquals(1, p.notes().size());
    }
}

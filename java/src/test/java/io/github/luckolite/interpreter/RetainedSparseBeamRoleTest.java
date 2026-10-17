// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural filled ovals, shafts and pale rails; no score pixels or fonts. */
public class RetainedSparseBeamRoleTest {
    static class Page {
        int w = 300, h = 230;
        byte[] g = new byte[w * h], a = new byte[w * h];

        Page() {
            Arrays.fill(g, (byte) 250);
        }

        void box(int x, int y, int ww, int hh, int ink, int label) {
            for (int yy = y; yy < y + hh; yy++)
                for (int xx = x; xx < x + ww; xx++) {
                    g[yy * w + xx] = (byte) ink;
                    a[yy * w + xx] = (byte) label;
                }
        }

        void head(int cx, int body) {
            for (int dy = -12; dy <= 12; dy++)
                for (int dx = -12; dx <= 12; dx++) {
                    double v = dx * dx / 121d + (dy + .4 * dx) * (dy + .4 * dx) / 40.96;
                    if (v <= 1) box(cx + dx, 104 + dy, 1, 1, v > .56 ? 60 : body, 2);
                }
        }

        List<ScoreNoteEvent> notes() {
            return OmrScoreInterpreter.extract(
                    a, g, w, h, List.of(new MeasureRegion(.05f, .95f, .25f, .8f)));
        }
    }

    Page fixture(int mode) {
        Page p = new Page();
        for (int y = 80; y <= 144; y += 16) p.box(20, y, 260, 1, 40, 4);
        p.head(100, mode == 3 ? 250 : 190);
        p.head(148, mode == 3 ? 250 : 190);
        p.box(89, 106, 3, 57, 60, 1);
        p.box(137, 106, 3, 57, 60, 1);
        p.box(89, 158, 51, 6, 185, 5);
        if (mode == 1) p.box(112, 155, 5, 12, 250, 0);
        if (mode == 2) {
            p.box(92, 155, 45, 12, 250, 0);
            p.box(92, 161, 45, 1, 60, 4);
        }
        if (mode == 4) p.box(137, 118, 3, 35, 250, 1);
        if (mode == 5) p.box(89, 158, 51, 6, 185, 4);
        return p;
    }

    void beamed(Page p) {
        var n = p.notes();
        assertEquals(2, n.size());
        for (var e : n) {
            assertEquals(1, e.beamCount());
            assertEquals(0f, e.unbeamedDurationBeats(), .001f);
        }
    }

    @Test
    public void bothRetainedFilledOvalsUseCompletePaleRail() {
        beamed(fixture(0));
    }

    @Test
    public void brokenRailDoesNotAssignBeam() {
        for (var e : fixture(1).notes()) assertEquals(0, e.beamCount());
    }

    @Test
    public void thinStaffRuleDoesNotAssignBeam() {
        for (var e : fixture(2).notes()) assertEquals(0, e.beamCount());
    }

    @Test
    public void genuineHollowHalvesDoNotBorrowNearbyRail() {
        for (var e : fixture(3).notes()) {
            assertEquals(0, e.beamCount());
            assertEquals(2f, e.unbeamedDurationBeats(), .001f);
        }
    }

    @Test
    public void missingPartnerShaftDoesNotAssignBeam() {
        for (var e : fixture(4).notes()) assertEquals(0, e.beamCount());
    }

    @Test
    public void staffRuleAtQuarterEndCannotOwnBeam() {
        for (var e : fixture(5).notes()) assertEquals(0, e.beamCount());
    }

    @Test
    public void preservesMasksAndPhysicalIdentity() {
        var p = fixture(0);
        var g = p.g.clone();
        var a = p.a.clone();
        var n = p.notes();
        assertArrayEquals(g, p.g);
        assertArrayEquals(a, p.a);
        assertEquals(2, n.size());
        assertNotEquals(n.get(0).positionInMeasure(), n.get(1).positionInMeasure(), .01f);
        assertEquals(n.get(0).staffStep(), n.get(1).staffStep());
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
    public void mirroredOwnedPair() {
        beamed(transformed(true, 1));
    }

    @Test
    public void largerOwnedPair() {
        beamed(transformed(false, 2));
    }

    @Test
    public void largerMirroredOwnedPair() {
        beamed(transformed(true, 2));
    }
}

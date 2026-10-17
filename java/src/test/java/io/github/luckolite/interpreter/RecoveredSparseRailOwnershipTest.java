// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural rail-ownership counterexamples; no score pixels or coordinates.
 * The complete symbolic oval pattern builds on the Apache-licensed SymbolStemHeadRecoveryTest. */
public class RecoveredSparseRailOwnershipTest {
    static final int W = 180, H = 150;

    static class Page {
        byte[] g = new byte[W * H], l = new byte[W * H];

        Page() {
            Arrays.fill(g, (byte) 250);
        }

        void pixel(int x, int y, int ink, int label) {
            g[y * W + x] = (byte) ink;
            l[y * W + x] = (byte) label;
        }
    }

    Page page(boolean symbolicRail, boolean hollowPartner) {
        Page p = new Page();
        for (int y : new int[] {70, 84, 98, 112, 126})
            for (int x = 15; x < 165; x++) p.pixel(x, y, 30, 4);
        for (int cx : new int[] {70, 130})
            for (int y = 43; y <= 57; y++)
                for (int x = cx - 10; x <= cx + 10; x++) {
                    double r = Math.pow((x - cx) / 10., 2) + Math.pow((y - 50) / 7., 2);
                    if (r <= 1)
                        p.pixel(
                                x,
                                y,
                                cx == 130 && hollowPartner && r < .56 ? 250 : cx == 70 ? 190 : 30,
                                cx == 70 ? 5 : 2);
                }
        for (int y = 56; y <= 107; y++) p.pixel(61, y, 30, 1);
        for (int y = 50; y <= 107; y++) p.pixel(120, y, 30, 1);
        for (int y = 102; y <= 107; y++)
            for (int x = 61; x <= 120; x++) p.pixel(x, y, 185, symbolicRail ? 5 : 4);
        return p;
    }

    Page genuinePage() {
        Page p = new Page();
        for (int y = 46; y <= 102; y += 14) for (int x = 15; x < 165; x++) p.pixel(x, y, 30, 4);
        for (int cx : new int[] {55, 103})
            for (int dy = -12; dy <= 12; dy++)
                for (int dx = -12; dx <= 12; dx++) {
                    double v = dx * dx / 100d + (dy + .4 * dx) * (dy + .4 * dx) / 31.36;
                    if (v <= 1) p.pixel(cx + dx, 70 + dy, v > .56 ? 60 : 190, cx == 55 ? 5 : 2);
                }
        for (int y = 72; y <= 121; y++)
            for (int dx = 0; dx < 3; dx++) {
                p.pixel(45 + dx, y, 60, 1);
                p.pixel(93 + dx, y, 60, 1);
            }
        for (int y = 116; y <= 121; y++) for (int x = 45; x <= 96; x++) p.pixel(x, y, 185, 5);
        return p;
    }

    List<ScoreNoteEvent> notes(Page p) {
        return OmrScoreInterpreter.extract(
                p.l, p.g, W, H, List.of(new MeasureRegion(.05f, .95f, .2f, .95f)));
    }

    @Test
    public void grayStaffRailCannotCreateMissingHead() {
        Page p = page(false, false);
        assertTrue(
                PrintedSingleBeamPath.connects(
                        p.g, W, H, new int[] {61, 107, 1}, new int[] {120, 107, 1}, 14));
        assertFalse(
                FilledSparseBeamHead.rootedEnds(
                        p.l, p.g, W, H, new int[] {61, 107, 1}, new int[] {120, 107, 1}, 14));
        assertEquals(1, notes(p).size());
    }

    @Test
    public void staffRailCannotTurnHollowPartnerIntoEighth() {
        Page p = page(false, true);
        var n = notes(p);
        assertEquals(1, n.size());
        assertEquals(0, n.get(0).beamCount());
        assertEquals(2, n.get(0).unbeamedDurationBeats(), .001);
    }

    @Test
    public void genuineSymbolRailStillRecoversMissingHead() {
        var n = notes(genuinePage());
        assertEquals(2, n.size());
        for (var e : n) {
            assertEquals(1, e.beamCount());
            assertEquals(0, e.unbeamedDurationBeats(), .001);
        }
    }

    @Test
    public void symbolicRailCrossingStaffInkStillRecovers() {
        Page p = genuinePage();
        for (int x = 65; x <= 80; x++) p.pixel(x, 119, 30, 4);
        var n = notes(p);
        assertEquals(2, n.size());
        for (var e : n) {
            assertEquals(1, e.beamCount());
            assertEquals(0, e.unbeamedDurationBeats(), .001);
        }
    }

    @Test
    public void brokenGenuineRailDoesNotRecover() {
        Page p = page(true, false);
        for (int y = 90; y <= 115; y++) for (int x = 87; x <= 93; x++) p.pixel(x, y, 250, 0);
        assertEquals(1, notes(p).size());
    }

    @Test
    public void sourceRastersAreImmutable() {
        Page p = page(false, true);
        byte[] g = p.g.clone(), l = p.l.clone();
        notes(p);
        assertArrayEquals(g, p.g);
        assertArrayEquals(l, p.l);
    }

    List<ScoreNoteEvent> sparseBodyPage(int mode) {
        int w = 300, h = 230;
        byte[] g = new byte[w * h], a = new byte[w * h];
        Arrays.fill(g, (byte) 250);
        for (int y = 80; y <= 144; y += 16)
            for (int x = 20; x < 280; x++) {
                g[y * w + x] = 40;
                a[y * w + x] = 4;
            }
        for (int cx : new int[] {100, 148})
            for (int dy = -12; dy <= 12; dy++)
                for (int dx = -12; dx <= 12; dx++) {
                    double v = dx * dx / 121d + (dy + .4 * dx) * (dy + .4 * dx) / 40.96;
                    if (v <= 1) {
                        int ink =
                                v > .56
                                        ? 60
                                        : cx == 100 || mode == 0
                                                ? 224
                                                : mode == 2 && dy + .4 * dx >= 0 ? 224 : 250;
                        if (cx == 148 && dx < 0 && dy < 0 && dx > -7) ink = 250;
                        g[(104 + dy) * w + cx + dx] = (byte) ink;
                        a[(104 + dy) * w + cx + dx] = (byte) (cx == 100 ? 2 : 0);
                    }
                }
        for (int x : new int[] {89, 137})
            for (int dx = 0; dx < 3; dx++)
                for (int y = 106; y < 163; y++) {
                    g[y * w + x + dx] = 60;
                    a[y * w + x + dx] = 1;
                }
        for (int y = 158; y < 164; y++)
            for (int x = 89; x < 140; x++) {
                g[y * w + x] = 100;
                a[y * w + x] = 5;
            }
        assertFalse(SymbolStemHeadRecovery.sparseOvals(a, g, w, h, 16, 50, 185).isEmpty());
        return OmrScoreInterpreter.extract(
                a, g, w, h, List.of(new MeasureRegion(.05f, .95f, .25f, .8f)));
    }

    @Test
    public void sparseHollowRimOnRealRailDoesNotInventFilledEighth() {
        assertEquals(1, sparseBodyPage(1).size());
    }

    @Test
    public void sparseHookWithPaperUpperInteriorDoesNotInventFilledEighth() {
        assertEquals(1, sparseBodyPage(2).size());
    }

    @Test
    public void oneBrokenSectorWithFaintFilledInteriorRemainsBeamed() {
        var n = sparseBodyPage(0);
        assertEquals(2, n.size());
        for (var e : n) {
            assertEquals(1, e.beamCount());
            assertEquals(0, e.unbeamedDurationBeats(), .001);
        }
    }
}

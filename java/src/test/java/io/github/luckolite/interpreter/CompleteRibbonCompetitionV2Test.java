// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent procedural competition/resource roles, frozen before successor source. */
public class CompleteRibbonCompetitionV2Test {
    static byte[] paper(int w, int h) {
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    static void rails(byte[] g, int w) {
        for (int y = 64; y <= 120; y += 14) for (int x = 0; x < w; x++) g[y * w + x] = 20;
    }

    static void ribbon(byte[] g, int w, int h, int left, int base, double radius, boolean mirror) {
        for (int i = 0; i <= 4000; i++) {
            double x = left + 200 * i / 4000.0, dx = x - left - 100, cy = base + dx * dx / 200;
            if (mirror) cy = h - cy;
            for (int y = (int) Math.floor(cy - radius); y <= Math.ceil(cy + radius); y++)
                for (int xx = (int) Math.floor(x - radius); xx <= Math.ceil(x + radius); xx++)
                    if ((xx - x) * (xx - x) + (y - cy) * (y - cy) <= radius * radius)
                        g[y * w + xx] = 0;
        }
    }

    static PrintedSlurContours.RawResult find(byte[] g, int w, int h) {
        var t =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 120, 14}, new float[] {w - 1, 120, 14}));
        return PrintedSlurContours.findWithRawEvidence(
                g, null, w, h, 40, 150, w - 41, h - 100, t, null, null);
    }

    static long whole(PrintedSlurContours.RawResult r, int left) {
        return r.result().bows().stream()
                .filter(
                        b ->
                                b.start().x() <= left + 3
                                        && b.start().x() >= left - 3
                                        && b.end().x() >= left + 197
                                        && b.end().x() <= left + 203)
                .count();
    }

    @Test
    public void twoNewOverlappingRibbonsCannotSelectFirstAcrossMirrors() {
        for (boolean mirror : new boolean[] {false, true}) {
            int w = 820, h = 1000;
            byte[] g = paper(w, h);
            rails(g, w);
            ribbon(g, w, h, 100, 190, 2.25, mirror);
            ribbon(g, w, h, 100, 370, 2.25, mirror);
            var r = find(g, w, h);
            assertFalse(r.result().limitReached());
            assertEquals(
                    "Two competing newly qualified full spans must not select the first by row order",
                    0,
                    whole(r, 100));
        }
    }

    @Test
    public void disjointNewRibbonsRemainIndependent() {
        int w = 820, h = 1000;
        byte[] g = paper(w, h);
        rails(g, w);
        ribbon(g, w, h, 100, 190, 2.25, false);
        ribbon(g, w, h, 500, 370, 2.25, false);
        var r = find(g, w, h);
        assertFalse(r.result().limitReached());
        assertEquals(1, whole(r, 100));
        assertEquals(1, whole(r, 500));
    }

    @Test
    public void legacySiblingSurvivesRejectedNewCompetition() {
        for (boolean mirror : new boolean[] {false, true}) {
            int w = 820, h = 1000;
            byte[] g = paper(w, h);
            rails(g, w);
            ribbon(g, w, h, 100, 190, 2.25, mirror);
            ribbon(g, w, h, 100, 370, 2.25, mirror);
            ribbon(g, w, h, 500, 550, 1.25, mirror);
            var r = find(g, w, h);
            assertFalse(r.result().limitReached());
            assertEquals(0, whole(r, 100));
            assertEquals(1, whole(r, 500));
        }
    }

    @Test
    public void newRunBudgetKeepsLegacyBowAndDeclaresIncomplete() {
        int w = 4000, h = 3000;
        byte[] g = paper(w, h);
        rails(g, w);
        ribbon(g, w, h, 100, 190, 1.25, false);
        for (int y = 500; y + 7 < h - 100; y += 28)
            for (int x = 50; x + 7 < w - 40; x += 10)
                for (int yy = y; yy < y + 7; yy++)
                    for (int xx = x; xx < x + 7; xx++) g[yy * w + xx] = 0;
        var r = find(g, w, h);
        assertTrue(
                "New raw-run exhaustion cannot report a complete search",
                r.result().limitReached());
        assertEquals("Already proved legacy interpretation remains available", 1, whole(r, 100));
    }

    @Test
    public void contextKeepsKnownOwnedCurveWhenNewWidthSearchExhausts() {
        var old = new OwnedCurveMezzoScene(true, false);
        int w = 12000, h = 700;
        byte[] g = paper(w, h);
        for (int y = 0; y < h; y++) System.arraycopy(old.gray, y * old.W, g, y * w, old.W);
        for (int y = 124; y <= 180; y += 14) for (int x = old.W; x < w; x++) g[y * w + x] = 20;
        for (int y = 220; y <= 226; y++) for (int x = 4000; x <= 8000; x++) g[y * w + x] = 0;
        var measures = List.of(new MeasureRegion(300f / w, 550f / w, .16f, .27f));
        var e = DynamicNotationRawContext.capture(g, w, h, old.staffs, measures, old.notes);
        assertTrue("New fallback resource uncertainty is visible", e.incomplete());
        assertFalse(
                "New uncertainty cannot discard an independently proved old curve",
                e.curves().isEmpty());
        assertEquals(Integer.valueOf(20), e.selectedContourPixelValues(g, w, h).get(202 * w + 415));
    }
}

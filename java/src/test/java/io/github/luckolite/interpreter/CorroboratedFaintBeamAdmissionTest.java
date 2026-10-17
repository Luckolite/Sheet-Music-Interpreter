// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Independent analytic ellipses, rule lines and beams; no score pixels or outlines. */
public class CorroboratedFaintBeamAdmissionTest {
    static CorroboratedFaintBeamTest.Page page(boolean quarter, int rails) {
        var p = CorroboratedFaintBeamTest.page(false);
        for (int i = 0; i < p.l.length; i++)
            if (p.l[i] == 4) {
                p.l[i] = 0;
                p.g[i] = (byte) 250;
            }
        for (int y = 89; y <= 145; y += 14)
            for (int x = 20; x < 230; x++) if (p.l[y * 300 + x] == 0) p.pixel(x, y, 160, 4);
        for (int y = 55; y <= 83; y++)
            for (int x = 77; x <= 104; x++) if (p.l[y * 300 + x] == 2) p.l[y * 300 + x] = 5;
        p.rail(80, 147, 132, 150, 215, 9);
        for (int dy = -3; dy <= 3; dy++)
            for (int dx = -5; dx <= 5; dx++)
                if (dx * dx / 25d + dy * dy / 9d <= 1) {
                    p.l[(144 + dy) * 300 + 110 + dx] = 2;
                    if (quarter) p.g[(144 + dy) * 300 + 110 + dx] = (byte) 80;
                }
        if (quarter)
            for (int y = 112; y <= 144; y++) for (int x = 114; x <= 115; x++) p.pixel(x, y, 80, 1);
        for (int r = 1; r < rails; r++)
            p.rail(132, 150 - r * 10, 182, 153 - r * 10, 80, rails == 2 ? 5 : 4);
        return p;
    }

    static CorroboratedFaintBeamTest.Page mirror(CorroboratedFaintBeamTest.Page p) {
        var q = new CorroboratedFaintBeamTest.Page();
        for (int y = 0; y < 240; y++)
            for (int x = 0; x < 300; x++) {
                int a = y * 300 + x, b = (239 - y) * 300 + 299 - x;
                q.g[b] = p.g[a];
                q.l[b] = p.l[a];
            }
        return q;
    }

    static List<ScoreNoteEvent> notes(CorroboratedFaintBeamTest.Page p) {
        return OmrScoreInterpreter.extract(
                p.l, p.g, 300, 240, List.of(new MeasureRegion(.05f, .95f, .2f, .8f)));
    }

    static void recovered(boolean mirrored) {
        var p = page(false, 1);
        var n = notes(mirrored ? mirror(p) : p);
        assertEquals(3, n.size());
        float y = (mirrored ? 169 : 70) / 240f;
        var own = n.stream().filter(a -> Math.abs(a.pageY() - y) < .01f).findFirst().orElseThrow();
        assertEquals(1, own.beamCount());
        assertEquals(0f, own.unbeamedDurationBeats(), 0);
        assertFalse(
                n.stream()
                        .anyMatch(a -> Math.abs(a.pageY() - (mirrored ? 95 : 144) / 240f) < .01f));
        if (!mirrored) assertEquals(11, own.staffStep());
    }

    @Test
    public void completeDownStemRecoveryReplacesOnlyStemlessBandArtifact() {
        recovered(false);
    }

    @Test
    public void completeMirroredUpStemRecoveryReplacesOnlyStemlessBandArtifact() {
        recovered(true);
    }

    @Test
    public void independentQuarterShaftSurvivesBandFilter() {
        var n = notes(page(true, 1));
        var q = n.stream().filter(a -> Math.abs(a.pageY() - .6f) < .01f).findFirst().orElseThrow();
        assertEquals(0, q.beamCount());
        assertEquals(1f, q.unbeamedDurationBeats(), 0);
    }

    @Test
    public void independentlyDetectedTwoBeamsAreNotReduced() {
        var n = notes(page(false, 2));
        assertEquals(2, n.stream().filter(a -> a.staffStep() == 9 || a.staffStep() == 6).count());
        assertTrue(
                n.stream()
                        .filter(a -> a.staffStep() == 9 || a.staffStep() == 6)
                        .allMatch(a -> a.beamCount() == 2));
    }

    @Test
    public void independentlyDetectedThreeBeamsAreNotReduced() {
        var n = notes(page(false, 3));
        assertTrue(
                n.stream()
                        .filter(a -> a.staffStep() == 9 || a.staffStep() == 6)
                        .allMatch(a -> a.beamCount() == 3));
    }

    @Test
    public void heldQuarterSurvivesSuccessfulNewHeadAndArtifactRecovery() {
        var p = page(false, 1);
        p.oval(230, 128, false);
        p.stem(240, 90, 128);
        var n = notes(p);
        assertEquals(4, n.size());
        assertTrue(n.stream().anyMatch(a -> a.staffStep() == 11 && a.beamCount() == 1));
        var q =
                n.stream()
                        .filter(a -> Math.abs(a.pageY() - 128 / 240f) < .01f)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, q.beamCount());
        assertEquals(1f, q.unbeamedDurationBeats(), 0);
    }

    @Test
    public void completeAnalysisPreservesInputRaster() {
        var p = page(false, 1);
        var l = p.l.clone();
        var g = p.g.clone();
        notes(p);
        assertArrayEquals(l, p.l);
        assertArrayEquals(g, p.g);
    }

    @Test
    public void onePixelProposalPhaseDoesNotChangeProductionAdmission() {
        var p = page(false, 1);
        var q = new CorroboratedFaintBeamTest.Page();
        for (int y = 0; y < 239; y++)
            for (int x = 0; x < 300; x++)
                if (p.l[y * 300 + x] != 4 && p.l[y * 300 + x] != 0)
                    q.pixel(x, y + 1, p.g[y * 300 + x] & 255, p.l[y * 300 + x]);
        for (int y = 89; y <= 145; y += 14)
            for (int x = 20; x < 230; x++) if (q.l[y * 300 + x] == 0) q.pixel(x, y, 160, 4);
        var n = notes(q);
        assertEquals(3, n.size());
        assertTrue(
                n.stream()
                        .anyMatch(
                                a -> Math.abs(a.pageY() - 71 / 240f) < .01f && a.beamCount() == 1));
    }
}

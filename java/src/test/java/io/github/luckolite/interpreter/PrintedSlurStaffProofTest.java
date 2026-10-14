// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raw staff pixels, never injected verified samples. */
public class PrintedSlurStaffProofTest {
    static final int W = 1200, H = 350;

    private byte[] page(int rules, int paper, int ink, float slope) {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) paper);
        for (int x = 30; x < W - 30; x++)
            for (int line = 0; line < rules; line++)
                g[(Math.round(210 + slope * (x - W * .5f)) - line * 10) * W + x] = (byte) ink;
        return g;
    }

    @Test
    public void straightRawFiveRulesReachTheArcScanner() {
        var g = page(5, 255, 35, 0);
        var before = g.clone();
        var t = StaffPitchTrack.forPrintedSlurs(g, W, H, 170, 210, 10);
        assertNotNull(t);
        assertTrue(t.verified());
        assertEquals(210, t.at(600)[0], .1);
        assertEquals(10, t.at(600)[1], .1);
        var f = new PrintedSlurPageCapture.Frame(0, 100, W - 1, 260, t, List.of());
        var r = PrintedSlurPageCapture.capture(9, 0, g, null, W, H, List.of(f), Set.of());
        assertFalse(r.limitReached());
        assertTrue(r.source().spans().isEmpty());
        assertArrayEquals(before, g);
    }

    @Test
    public void existingSymbolTrackPolicyAndGeometryRemainUnchanged() {
        var g = page(5, 255, 35, 0);
        var ordinary = StaffPitchTrack.detectForSymbols(g, W, H, 170, 210, 10);
        var arc = StaffPitchTrack.forPrintedSlurs(g, W, H, 170, 210, 10);
        assertNotNull(ordinary);
        assertFalse(ordinary.verified());
        for (int x : new int[] {30, 200, 600, 1000, 1170})
            assertArrayEquals(ordinary.at(x), arc.at(x), 0);
        assertNull(StaffPitchTrack.detect(g, W, H, 170, 210, 10));
        assertFalse(StaffPitchTrack.linear(W, 210, 10, 0).verified());
    }

    @Test
    public void sublineTiltUsesTheActualFiveRawRules() {
        var t = StaffPitchTrack.forPrintedSlurs(page(5, 255, 35, .007f), W, H, 170, 210, 10);
        assertNotNull(t);
        assertTrue(t.verified());
        for (int x : new int[] {200, 600, 1000})
            assertEquals(210 + .007f * (x - 600), t.at(x)[0], 1.1);
    }

    @Test
    public void shadedPaperRequiresActualContrastedRules() {
        var t = StaffPitchTrack.forPrintedSlurs(page(5, 150, 70, 0), W, H, 170, 210, 10);
        assertNotNull(t);
        assertTrue(t.verified());
    }

    @Test
    public void fourRulesCannotSupplyTheMissingFifth() {
        assertNull(StaffPitchTrack.forPrintedSlurs(page(4, 255, 35, 0), W, H, 170, 210, 10));
    }

    @Test
    public void blankPageCannotBorrowTheSeedGeometry() {
        assertNull(StaffPitchTrack.forPrintedSlurs(page(0, 255, 35, 0), W, H, 170, 210, 10));
    }

    @Test
    public void sixthRuleDoesNotChooseAnArbitraryFiveLinePhase() {
        var g = page(5, 255, 35, 0);
        for (int x = 30; x < W - 30; x++) g[220 * W + x] = 35;
        assertNull(StaffPitchTrack.forPrintedSlurs(g, W, H, 170, 210, 10));
    }

    @Test
    public void broadPageCurveUsesTheVerifiedCurvedProof() {
        var g = page(0, 255, 35, 0);
        for (int x = 30; x < W - 30; x++)
            for (int line = 0; line < 5; line++) {
                int row = Math.round(210f - 45f * x * x / (W * (float) W) - line * 10);
                g[row * W + x] = 35;
                g[(row + 1) * W + x] = 35;
            }
        var t = StaffPitchTrack.forPrintedSlurs(g, W, H, 155, 195, 10);
        assertNotNull(t);
        assertTrue(t.verified());
        var ordinary = StaffPitchTrack.detect(g, W, H, 155, 195, 10);
        assertFalse(ordinary.verified());
        for (int x : new int[] {190, 440, 720, 970}) assertArrayEquals(ordinary.at(x), t.at(x), 0);
    }

    @Test
    public void sparseLedgerGroupsCannotBecomeAStaff() {
        var g = page(0, 255, 35, 0);
        for (int center = 180; center < 1050; center += 144)
            for (int x = center - 20; x < center + 20; x++)
                for (int line = 0; line < 5; line++) g[(210 - line * 10) * W + x] = 35;
        assertNull(StaffPitchTrack.forPrintedSlurs(g, W, H, 170, 210, 10));
    }

    @Test(expected = IllegalArgumentException.class)
    public void truncatedRasterCannotBeAProof() {
        StaffPitchTrack.forPrintedSlurs(new byte[12], W, H, 170, 210, 10);
    }

    @Test
    public void provedRawStaffAndBowRetainPhysicalOwners() {
        var g = page(5, 255, 35, 0);
        for (int x = 200; x <= 350; x++) {
            double v = 2.0 * (x - 200) / 150 - 1;
            g[(int) Math.round(115 + 14 * v * v) * W + x] = 0;
        }
        for (int y = 142; y <= 148; y++) for (int x = 196; x <= 204; x++) g[y * W + x] = 0;
        for (int y = 152; y <= 158; y++) for (int x = 346; x <= 354; x++) g[y * W + x] = 0;
        var t = StaffPitchTrack.forPrintedSlurs(g, W, H, 170, 210, 10);
        assertNotNull(t);
        assertTrue(t.verified());
        var heads =
                List.of(
                        new PrintedSlurOwners.Head(0, 200, 145, 0, 1, 0, 0, 83),
                        new PrintedSlurOwners.Head(1, 350, 155, 0, 1, 0, 10080, 79));
        var frame = new PrintedSlurPageCapture.Frame(0, 90, W - 1, 260, t, heads);
        var r = PrintedSlurPageCapture.capture(9, 2, g, null, W, H, List.of(frame), Set.of());
        assertEquals(1, r.source().spans().size());
        assertEquals(new ScoreSlurPages.Anchor(9, 0), r.source().spans().get(0).start());
        assertEquals(new ScoreSlurPages.Anchor(9, 1), r.source().spans().get(0).end());
    }
}

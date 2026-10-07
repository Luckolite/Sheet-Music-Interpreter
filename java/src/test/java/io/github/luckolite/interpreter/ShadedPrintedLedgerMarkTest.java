// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original dark compact ledger and faint staff lines on photographed paper. */
public class ShadedPrintedLedgerMarkTest {
    static final int W = 1280, H = 900, G = 16;

    int mark(int rails, int spacing, int phase, boolean broad, boolean compact, int x, int paper) {
        return mark(rails, spacing, phase, broad, compact, x, paper, false, false);
    }

    int mark(
            int rails,
            int spacing,
            int phase,
            boolean broad,
            boolean compact,
            int x,
            int paper,
            boolean blurredEdge,
            boolean coveredRule) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) paper);
        if (blurredEdge)
            for (int xx = Math.max(0, x - 10); xx <= Math.min(W - 1, x + 10); xx++)
                for (int y = 281; y <= 287; y++) gray[y * W + xx] = 107;
        for (int xx = Math.max(0, x - 10); xx <= Math.min(W - 1, x + 10); xx++)
            for (int y = 282; y <= 286; y++) gray[y * W + xx] = 65;
        for (int xx = Math.max(0, x - 14); xx <= Math.min(W - 1, x + 14); xx++)
            for (int y = 299; y <= 301; y++) gray[y * W + xx] = 107;
        for (int row = 0; row < rails; row++)
            for (int xx = Math.max(0, x - (compact ? 10 : 90));
                    xx <= Math.min(W - 1, x + (compact ? 10 : 90));
                    xx++)
                for (int dy = broad ? -4 : -1; dy <= (broad ? 4 : 1); dy++)
                    gray[(316 + row * spacing + phase + dy) * W + xx] = 107;
        if (coveredRule)
            for (int xx = x + 24; xx <= x + 48; xx++)
                for (int y = 315 + 2 * spacing; y <= 317 + 2 * spacing; y++)
                    gray[y * W + xx] = (byte) paper;
        var gc = gray.clone();
        var lc = labels.clone();
        int result =
                NoteArticulationDetector.detect(
                        labels,
                        gray,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(x, 260, G, 0)))[0];
        assertArrayEquals(gc, gray);
        assertArrayEquals(lc, labels);
        return result;
    }

    @Test
    public void paleFiveRuleFrameOwnsBroadenedLedgerCore() {
        assertEquals(0, mark(5, 16, 0, false, false, 640, 140));
    }

    @Test
    public void faintBlurEdgesKeepAThinDarkLedgerCore() {
        assertEquals(0, mark(5, 16, 0, false, false, 640, 140, true, false));
    }

    @Test
    public void widerSameFrameRecoversOnePartlyCoveredRule() {
        assertEquals(0, mark(5, 16, 0, false, false, 640, 140, false, true));
    }

    @Test
    public void widerFrameStillRequiresFiveIndependentRules() {
        assertEquals(NoteArticulation.TENUTO, mark(4, 16, 0, false, false, 640, 140, false, true));
    }

    @Test
    public void fourRulesCannotErasePrintedTenuto() {
        assertEquals(NoteArticulation.TENUTO, mark(4, 16, 0, false, false, 640, 140));
    }

    @Test
    public void threeRulesCannotErasePrintedTenuto() {
        assertEquals(NoteArticulation.TENUTO, mark(3, 16, 0, false, false, 640, 140));
    }

    @Test
    public void isolatedMarkKeepsTenuto() {
        assertEquals(NoteArticulation.TENUTO, mark(0, 16, 0, false, false, 640, 140));
    }

    @Test
    public void compactDashChainCannotSubstituteForStaff() {
        assertEquals(NoteArticulation.TENUTO, mark(5, 16, 0, false, true, 640, 140));
    }

    @Test
    public void broadBandsCannotSubstituteForNarrowStaff() {
        assertEquals(NoteArticulation.TENUTO, mark(5, 16, 0, true, false, 640, 140));
    }

    @Test
    public void wrongSpacingKeepsTenuto() {
        assertEquals(NoteArticulation.TENUTO, mark(5, 20, 0, false, false, 640, 140));
    }

    @Test
    public void wrongPhaseKeepsTenuto() {
        assertEquals(NoteArticulation.TENUTO, mark(5, 16, 5, false, false, 640, 140));
    }

    @Test
    public void clippedFrameCannotErasePrintedMark() {
        assertEquals(NoteArticulation.TENUTO, mark(5, 16, 0, false, false, 20, 140));
    }

    @Test
    public void ordinaryBrightPaperKeepsPreviousPath() {
        assertEquals(NoteArticulation.TENUTO, mark(5, 16, 0, false, false, 640, 250));
    }

    @Test
    public void incompleteImageCannotProveLedger() {
        assertFalse(
                ShadedLedgerMarkOwnership.proved(
                        new byte[4], W, H, 640, 260, 16, 630, 282, 650, 286));
    }

    @Test
    public void nonfiniteAnchorCannotProveLedger() {
        assertFalse(
                ShadedLedgerMarkOwnership.proved(
                        new byte[W * H], W, H, Float.NaN, 260, 16, 630, 282, 650, 286));
    }
}

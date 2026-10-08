// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original tilted five-rule staves and required short ledgers; no score pixels. */
public final class TrackedLedgerMarkOwnershipTest {
    static final int W = 1200, H = 900, X = 630, G = 16;
    byte[] gray = new byte[W * H];
    float slope;
    int step, paper = 148, rails = 5, thickness = 3, spacing = 16, phase = 0;
    boolean lower, compact, missingShort;
    StaffPitchTrack track;

    private float bottom(int x) {
        return 430 + slope * (x - X);
    }

    private void stripe(int xx, float yy, int ink) {
        int y = Math.round(yy);
        for (int d = -thickness / 2; d <= thickness / 2; d++) gray[(y + d) * W + xx] = (byte) ink;
    }

    private void draw(float angle, boolean down) {
        slope = angle;
        lower = down;
        step = down ? -6 : 14;
        Arrays.fill(gray, (byte) paper);
        track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, bottom(0), G},
                                new float[] {W - 1, bottom(W - 1), G}));
        for (int line = 0; line < rails; line++)
            for (int x = compact ? X - 12 : 70; x <= (compact ? X + 12 : W - 70); x++)
                stripe(x, bottom(x) - line * spacing + phase, 106);
        for (int ledger = down ? -2 : 10;
                down ? ledger >= -4 : ledger <= 12;
                ledger += down ? -2 : 2) {
            if (missingShort && ledger == (down ? -2 : 10)) continue;
            for (int x = X - 13; x <= X + 13; x++) stripe(x, bottom(x) - ledger * G * .5f, 74);
        }
    }

    private boolean proof() {
        int ledger = lower ? -4 : 12;
        int y = Math.round(bottom(X) - ledger * G * .5f);
        return TrackedLedgerMarkOwnership.proved(
                gray, W, H, X, bottom(X) - step * G * .5f, G, X - 7, y - 1, X + 7, y + 1, track);
    }

    @Test
    public void risingUpperLedgerUsesThePhysicalColumns() {
        draw(-.10f, false);
        assertTrue(proof());
    }

    @Test
    public void fallingUpperLedgerUsesThePhysicalColumns() {
        draw(.10f, false);
        assertTrue(proof());
    }

    @Test
    public void risingLowerLedgerUsesThePhysicalColumns() {
        draw(-.10f, true);
        assertTrue(proof());
    }

    @Test
    public void fallingLowerLedgerUsesThePhysicalColumns() {
        draw(.10f, true);
        assertTrue(proof());
    }

    @Test
    public void flatPhysicalFrameRetainsTheSameProof() {
        draw(0, false);
        assertTrue(proof());
    }

    @Test
    public void fourRawRulesCannotBorrowVerifiedSampleMetadata() {
        rails = 4;
        draw(.1f, false);
        assertFalse(proof());
    }

    @Test
    public void threeRawRulesCannotBorrowVerifiedSampleMetadata() {
        rails = 3;
        draw(-.1f, true);
        assertFalse(proof());
    }

    @Test
    public void missingRequiredShortLedgerKeepsTheMark() {
        missingShort = true;
        draw(.1f, false);
        assertFalse(proof());
    }

    @Test
    public void compactDashChainsCannotSupplyTheStaff() {
        compact = true;
        draw(-.1f, false);
        assertFalse(proof());
    }

    @Test
    public void broadBandsCannotSupplyTheStaff() {
        thickness = 9;
        draw(.1f, true);
        assertFalse(proof());
    }

    @Test
    public void wrongRawSpacingKeepsTheMark() {
        spacing = 20;
        draw(.1f, false);
        assertFalse(proof());
    }

    @Test
    public void wrongRawPhaseKeepsTheMark() {
        phase = 5;
        draw(-.1f, false);
        assertFalse(proof());
    }

    @Test
    public void brightPaperPreservesTheOlderPathBoundary() {
        paper = 245;
        draw(.1f, false);
        assertFalse(proof());
    }

    @Test
    public void veryDarkPaperCannotProveTheShadedPath() {
        paper = 85;
        draw(-.1f, true);
        assertFalse(proof());
    }

    @Test
    public void nullTrackCannotSupplyTheStaff() {
        draw(.1f, false);
        track = null;
        assertFalse(proof());
    }

    @Test
    public void unverifiedLinearTrackCannotSupplyTheStaff() {
        draw(.1f, false);
        track = StaffPitchTrack.linear(W, 430, G, slope);
        assertFalse(proof());
    }

    @Test
    public void anotherPhysicalFrameCannotOwnTheMark() {
        draw(.1f, false);
        track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, bottom(0) + 40, G},
                                new float[] {W - 1, bottom(W - 1) + 40, G}));
        assertFalse(proof());
    }

    @Test
    public void inStaffHeadCannotOwnRequiredLedgerInk() {
        draw(.1f, false);
        step = 6;
        assertFalse(proof());
    }

    @Test
    public void markBeyondTheHeadKeepsItsArticulation() {
        draw(-.1f, false);
        step = 10;
        assertFalse(proof());
    }

    @Test
    public void ledgerSpacePhaseCannotSupplyThePrintedHead() {
        draw(.1f, false);
        assertFalse(
                TrackedLedgerMarkOwnership.proved(
                        gray,
                        W,
                        H,
                        X,
                        bottom(X) - step * G * .5f + G * .2f,
                        G,
                        X - 7,
                        333,
                        X + 7,
                        335,
                        track));
    }

    @Test
    public void genuineUpperTenutoAboveTheHighHeadStaysIndependent() {
        draw(.1f, false);
        int y = Math.round(bottom(X) - 16 * G * .5f);
        assertFalse(
                TrackedLedgerMarkOwnership.proved(
                        gray,
                        W,
                        H,
                        X,
                        bottom(X) - step * G * .5f,
                        G,
                        X - 7,
                        y - 1,
                        X + 7,
                        y + 1,
                        track));
    }

    @Test
    public void genuineLowerTenutoBelowTheLowHeadStaysIndependent() {
        draw(-.1f, true);
        int y = Math.round(bottom(X) + 8 * G * .5f);
        assertFalse(
                TrackedLedgerMarkOwnership.proved(
                        gray,
                        W,
                        H,
                        X,
                        bottom(X) - step * G * .5f,
                        G,
                        X - 7,
                        y - 1,
                        X + 7,
                        y + 1,
                        track));
    }

    @Test
    public void displacedNoteAxisCannotBorrowALedger() {
        draw(.1f, false);
        assertFalse(
                TrackedLedgerMarkOwnership.proved(
                        gray,
                        W,
                        H,
                        X + G,
                        bottom(X) - step * G * .5f,
                        G,
                        X - 7,
                        333,
                        X + 7,
                        335,
                        track));
    }

    @Test
    public void thickCandidateCannotBecomeARequiredLedger() {
        draw(.1f, false);
        assertFalse(
                TrackedLedgerMarkOwnership.proved(
                        gray,
                        W,
                        H,
                        X,
                        bottom(X) - step * G * .5f,
                        G,
                        X - 7,
                        331,
                        X + 7,
                        337,
                        track));
    }

    @Test
    public void incompleteImageCannotSupplyTheProof() {
        draw(.1f, false);
        assertFalse(
                TrackedLedgerMarkOwnership.proved(
                        new byte[4], W, H, X, 318, G, X - 7, 333, X + 7, 335, track));
    }

    @Test
    public void proofPreservesEveryOriginalPixel() {
        draw(.1f, true);
        byte[] copy = gray.clone();
        assertTrue(proof());
        assertArrayEquals(copy, gray);
    }

    private int fullMark(StaffPitchTrack physical) {
        return NoteArticulationDetector.detect(
                new byte[gray.length],
                gray,
                W,
                H,
                List.of(
                        new NoteArticulationDetector.Anchor(
                                X, bottom(X) - step * G * .5f, G, 0, physical)))[0];
    }

    @Test
    public void fullCallerCanUseTheProvedUpperRow() {
        draw(.1f, false);
        assertEquals(NoteArticulation.TENUTO, fullMark(null));
        assertEquals(0, fullMark(track));
    }

    @Test
    public void fullCallerCanUseTheProvedLowerRow() {
        draw(-.1f, true);
        assertEquals(NoteArticulation.TENUTO, fullMark(null));
        assertEquals(0, fullMark(track));
    }

    @Test
    public void fullCallerKeepsTheMarkWhenTheShortChainIsIncomplete() {
        missingShort = true;
        draw(.1f, false);
        assertEquals(NoteArticulation.TENUTO, fullMark(null));
        assertEquals(NoteArticulation.TENUTO, fullMark(track));
    }

    @Test
    public void fullCallerPreservesASeparateUpperTenuto() {
        draw(.1f, false);
        int y = Math.round(bottom(X) - 18 * G * .5f);
        for (int x = X - 7; x <= X + 7; x++) gray[y * W + x] = 30;
        assertEquals(NoteArticulation.TENUTO, fullMark(null));
        assertEquals(NoteArticulation.TENUTO, fullMark(track));
    }

    @Test
    public void fullCallerPreservesASeparateLowerTenuto() {
        draw(-.1f, true);
        int y = Math.round(bottom(X) + 10 * G * .5f);
        for (int x = X - 7; x <= X + 7; x++) gray[y * W + x] = 30;
        assertEquals(NoteArticulation.TENUTO, fullMark(null));
        assertEquals(NoteArticulation.TENUTO, fullMark(track));
    }

    @Test
    public void fullCallerPreservesASeparateStaccatoDot() {
        draw(.1f, false);
        int y = Math.round(bottom(X) - 18 * G * .5f);
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) gray[(y + dy) * W + X + dx] = 0;
        assertEquals(NoteArticulation.STACCATO | NoteArticulation.TENUTO, fullMark(null));
        assertEquals(NoteArticulation.STACCATO, fullMark(track));
    }
}

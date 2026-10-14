// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original counted-rest scenes with independent note and ledger witnesses. */
public final class CountedRestLedgerVetoTest {
    static final int W = 400, H = 320;
    static final MeasureRegion REGION = new MeasureRegion(.1f, .9f, 100f / H, 190f / H);

    record Page(byte[] labels, byte[] gray) {}

    static void rect(Page p, int left, int top, int right, int bottom, int label, int gray) {
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                p.labels[y * W + x] = (byte) label;
                p.gray[y * W + x] = (byte) gray;
            }
    }

    static Page page(boolean count) {
        var p = new Page(new byte[W * H], new byte[W * H]);
        Arrays.fill(p.gray, (byte) 255);
        for (int y = 110; y <= 174; y += 16) rect(p, 40, y, 360, y, 4, 0);
        rect(p, 165, 137, 235, 147, 1, 0);
        rect(p, 165, 130, 167, 154, 1, 0);
        rect(p, 233, 130, 235, 154, 1, 0);
        if (count) {
            rect(p, 190, 65, 209, 68, 5, 0);
            for (int y = 69; y <= 96; y++) {
                int x = 208 - (y - 69) / 2;
                rect(p, x - 2, y, x + 1, y, 5, 0);
            }
        }
        return p;
    }

    static List<MeasureNumberReconciler.NumberToken> detect(Page p) {
        return MultiMeasureRestDetector.detect(p.labels, p.gray, W, H, List.of(REGION), List.of());
    }

    static void head(Page p, int y, int shade) {
        rect(p, 115, y - 5, 130, y + 5, 2, shade);
    }

    @Test
    public void unsupportedDistantModelHeadDoesNotVetoCountedRest() {
        var p = page(true);
        head(p, 230, 215);
        var counts = detect(p);
        assertEquals(1, counts.size());
        assertEquals(7, counts.get(0).value());
    }

    @Test
    public void sameWeakHeadInsideStaffKeepsVeto() {
        var p = page(true);
        head(p, 150, 215);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void nearbyOutsideHeadKeepsVeto() {
        var p = page(true);
        head(p, 198, 215);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void distantPrintedLedgerNoteKeepsVeto() {
        var p = page(true);
        head(p, 230, 0);
        for (int y = 190; y <= 238; y += 16) rect(p, 105, y, 140, y, 4, 0);
        rect(p, 130, 170, 132, 230, 1, 0);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void fadedPrintedLedgerNoteKeepsVeto() {
        var p = page(true);
        head(p, 230, 190);
        for (int y = 190; y <= 238; y += 16) rect(p, 105, y, 140, y, 4, 190);
        rect(p, 130, 170, 132, 230, 1, 190);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void absentCountDoesNotAuthorizeDiscardingHeads() {
        var p = page(false);
        head(p, 230, 215);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void absentStaffProofKeepsVeto() {
        var p = page(true);
        for (int y = 110; y <= 174; y += 16) rect(p, 40, y, 360, y, 0, 255);
        head(p, 230, 215);
        assertTrue(detect(p).isEmpty());
    }

    @Test
    public void countedGlyphAndDecorativeHeadCannotCancelRealHeadTwice() {
        var p = page(true);
        for (int y = 65; y <= 96; y++)
            for (int x = 185; x <= 212; x++) if (p.gray[y * W + x] == 0) p.labels[y * W + x] = 2;
        head(p, 230, 215);
        rect(p, 270, 149, 281, 154, 2, 0);
        assertTrue(detect(p).isEmpty());
    }
}

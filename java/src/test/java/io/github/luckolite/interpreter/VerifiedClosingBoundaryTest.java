// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural bars and competing stems, without score-derived pixels. */
public class VerifiedClosingBoundaryTest {
    static final int W = 600, H = 220, EDGE = 450, GAP = 12;
    final byte[] labels = new byte[W * H], gray = new byte[W * H];
    final int[] rows = {80, 92, 104, 116, 128};

    public VerifiedClosingBoundaryTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y : rows)
            for (int x = 30; x <= EDGE; x++) {
                gray[y * W + x] = 40;
                labels[y * W + x] = 4;
            }
    }

    void bar(int x, int end, boolean raw, boolean semantic) {
        for (int y = 80; y <= end; y++) {
            if (raw) gray[y * W + x] = 40;
            if (semantic) labels[y * W + x] = 1;
        }
    }

    boolean read() {
        return OmrMeasurePostProcessor.verifiedClosingBoundary(
                labels, gray, W, H, EDGE, rows, GAP, 0, null);
    }

    @Test
    public void completeClosingIsRetainedBesideCompetingStem() {
        bar(EDGE - 24, 128, true, true);
        bar(EDGE, 128, true, true);
        assertTrue(read());
    }

    @Test
    public void nearbyStemCannotReplaceMissingClosing() {
        bar(EDGE - 24, 128, true, true);
        assertFalse(read());
    }

    @Test
    public void semanticBarWithoutPrintedInkIsRejected() {
        bar(EDGE, 128, false, true);
        assertFalse(read());
    }

    @Test
    public void rawBarWithoutSemanticSupportIsRejected() {
        bar(EDGE, 128, true, false);
        assertFalse(read());
    }

    @Test
    public void incompleteBarIsRejected() {
        bar(EDGE, 112, true, true);
        assertFalse(read());
    }

    @Test
    public void outerWindowEdgeIsNotAClosingWitness() {
        bar(EDGE + GAP * 4, 128, true, true);
        assertFalse(read());
    }

    @Test
    public void inputPixelsRemainUnchanged() {
        bar(EDGE, 128, true, true);
        byte[] beforeGray = gray.clone(), beforeLabels = labels.clone();
        assertTrue(read());
        assertArrayEquals(beforeGray, gray);
        assertArrayEquals(beforeLabels, labels);
    }

    @Test
    public void attachedNoteheadCannotBecomeClosingBar() {
        bar(EDGE, 128, true, true);
        for (int y = 120; y <= 128; y++)
            for (int x = EDGE; x <= EDGE + 12; x++) {
                gray[y * W + x] = 40;
                labels[y * W + x] = 2;
            }
        assertFalse(read());
    }

    @Test
    public void shiftedRowsWithoutIndependentBarProofAreRejected() {
        bar(EDGE, 128, true, true);
        int[] shifted = {104, 116, 128, 140, 152};
        assertFalse(
                OmrMeasurePostProcessor.verifiedClosingBoundary(
                        labels, gray, W, H, EDGE, shifted, GAP, 0, null));
    }
}

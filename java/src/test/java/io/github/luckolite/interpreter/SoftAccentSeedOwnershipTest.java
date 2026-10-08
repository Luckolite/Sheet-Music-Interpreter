// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete pale chevrons distinguish an actual strong stroke from unrelated specks. */
public class SoftAccentSeedOwnershipTest {
    private static final int W = 1200, H = 1000;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    private void draw(int paper) {
        Arrays.fill(gray, (byte) paper);
        Arrays.fill(labels, (byte) 0);
        for (int x = 0; x < 25; x++)
            for (int y = 0; y < 13; y++) {
                double upper = 1 + 5 * x / 24., lower = 11 - 5 * x / 24.;
                if (Math.abs(y - upper) <= 1.25 || Math.abs(y - lower) <= 1.25)
                    gray[(100 + y) * W + 100 + x] = (byte) Math.round(paper * .68f);
            }
    }

    private void stroke() {
        for (int x = 4; x <= 12; x++) gray[(100 + Math.round(1 + 5 * x / 24f)) * W + 100 + x] = 55;
    }

    private int accent() {
        return NoteArticulationDetector.detect(
                        labels,
                        gray,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(112, 150, 16, 0)))[0]
                & NoteArticulation.ACCENT;
    }

    @Test
    public void aCompletePaleShapeNeedsAnActualStrongStroke() {
        draw(140);
        assertEquals(0, accent());
    }

    @Test
    public void oneTinyDarkSpeckCannotSeedThePaleShape() {
        draw(140);
        gray[103 * W + 111] = 55;
        assertEquals(0, accent());
    }

    @Test
    public void aCompactDarkBlobCannotReplaceAStroke() {
        draw(140);
        for (int y = 101; y <= 103; y++) for (int x = 105; x <= 107; x++) gray[y * W + x] = 55;
        assertEquals(0, accent());
    }

    @Test
    public void disconnectedStrongLineDoesNotSeedThePaleShape() {
        draw(140);
        for (int x = 104; x <= 112; x++) gray[95 * W + x] = 55;
        assertEquals(0, accent());
    }

    @Test
    public void aConnectedStrongArmSegmentSeedsItsCompletePaleChevron() {
        draw(140);
        stroke();
        assertEquals(NoteArticulation.ACCENT, accent());
    }

    @Test
    public void aConnectedStrongArmSegmentWorksOnAnotherPaperTone() {
        draw(220);
        stroke();
        assertEquals(NoteArticulation.ACCENT, accent());
    }

    @Test
    public void aStrongStrokeCannotSupplyAMissingArm() {
        draw(140);
        stroke();
        for (int x = 116; x <= 124; x++)
            for (int y = 100; y <= 112; y++) if (y > 106) gray[y * W + x] = (byte) 140;
        assertEquals(0, accent());
    }

    @Test
    public void notationOwnershipStillVetoesTheSeededChevron() {
        draw(140);
        stroke();
        for (int i = 0; i < gray.length; i++)
            if ((gray[i] & 255) < 140) labels[i] = OmrMeasurePostProcessor.NOTEHEAD;
        assertEquals(0, accent());
    }

    @Test
    public void seedDiagnosticPreservesTheCallerPixels() {
        draw(140);
        stroke();
        byte[] g = gray.clone(), l = labels.clone();
        accent();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }
}

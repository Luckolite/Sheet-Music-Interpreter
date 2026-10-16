// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original unequal-width bars with an independently printed narrow measure. */
public class CountNeutralBoundaryTest {
    static final int W = 1000, H = 300;
    byte[] gray = new byte[W * H];

    static MeasureRegion bar(float l, float r) {
        return new MeasureRegion(l, r, .2f, .8f);
    }

    List<MeasureRegion> raw =
            List.of(
                    bar(.1f, .4f),
                    bar(.41f, .45f),
                    bar(.46f, .65f),
                    bar(.66f, .8f),
                    bar(.81f, .9f));
    List<MeasureRegion> fitted =
            List.of(
                    bar(.1f, .248f),
                    bar(.252f, .4f),
                    bar(.41f, .65f),
                    bar(.66f, .8f),
                    bar(.81f, .9f));

    public CountNeutralBoundaryTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 100; y <= 180; y += 20) for (int x = 90; x <= 910; x++) gray[y * W + x] = 0;
        for (int x : new int[] {405, 455, 655, 805}) line(x);
    }

    void line(int x) {
        for (int y = 100; y <= 180; y++) gray[y * W + x] = 0;
    }

    List<MeasureRegion> result() {
        return PrintedMeasureRhythmGuard.preserveCountNeutralRows(raw, fitted, gray, W, H);
    }

    @Test
    public void unsupportedReplacementCutCannotEraseNarrowPrintedBar() {
        assertEquals(raw, result());
    }

    @Test
    public void existingCorrectLayoutIsUnchanged() {
        fitted = raw;
        assertEquals(raw, result());
    }

    @Test
    public void newlyProvedSeparatorIsNotDiscarded() {
        line(250);
        assertEquals(fitted, result());
    }

    @Test
    public void anUnprovedOriginalSeparatorDoesNotOverrideFitting() {
        for (int y = 101; y < 180; y++) if (y % 20 != 0) gray[y * W + 455] = (byte) 255;
        assertEquals(fitted, result());
    }

    @Test
    public void shortNoteStemDoesNotProveReplacementCut() {
        for (int y = 120; y <= 165; y++) gray[y * W + 250] = 0;
        assertEquals(raw, result());
    }

    @Test
    public void differentLogicalCountKeepsExistingRecovery() {
        var list = new ArrayList<>(fitted);
        list.add(bar(.91f, .98f));
        fitted = list;
        assertEquals(fitted, result());
    }

    @Test
    public void noRawPixelsCannotInventBoundaryProof() {
        assertEquals(
                fitted,
                PrintedMeasureRhythmGuard.preserveCountNeutralRows(raw, fitted, null, W, H));
    }

    @Test
    public void proofPreservesInputPixels() {
        var before = gray.clone();
        result();
        assertArrayEquals(before, gray);
    }
}

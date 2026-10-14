// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic quarter contours and complete printed barline controls. */
public class PrintedQuarterRestBoundaryTest {
    private boolean rest(int gap, boolean hook, boolean zigzag, boolean stem) {
        var p = new CompactQuarterRestTest.Page(gap, hook, zigzag, stem, stem);
        var before = p.gray.clone();
        boolean found =
                PrintedQuarterRestBoundary.owns(
                        p.gray, 600, 280, 150 + gap * .35f, 100, 100 + 4 * gap, gap);
        assertArrayEquals(before, p.gray);
        return found;
    }

    private boolean rule(int thickness, boolean beam, int tone) {
        int w = 600, h = 280, gap = 16;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 235);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 580; x++) gray[y * w + x] = 40;
        for (int y = 100; y <= 164; y++)
            for (int x = 250 - thickness / 2; x <= 250 + thickness / 2; x++)
                gray[y * w + x] = (byte) tone;
        if (beam)
            for (int y = 118; y <= 121; y++) for (int x = 220; x <= 280; x++) gray[y * w + x] = 30;
        return PrintedQuarterRestBoundary.owns(gray, w, h, 250, 100, 164, gap);
    }

    @Test
    public void completeQuarterOwnsFalseBoundary() {
        assertTrue(rest(16, true, true, false));
    }

    @Test
    public void largerQuarterOwnsFalseBoundary() {
        assertTrue(rest(22, true, true, false));
    }

    @Test
    public void missingHookIsNotQuarterProof() {
        assertFalse(rest(16, false, true, false));
    }

    @Test
    public void missingZigzagIsNotQuarterProof() {
        assertFalse(rest(16, true, false, false));
    }

    @Test
    public void realStemIsNotQuarterProof() {
        assertFalse(rest(16, true, true, true));
    }

    @Test
    public void thinBarlineSurvives() {
        assertFalse(rule(1, false, 30));
    }

    @Test
    public void thickBarlineSurvives() {
        assertFalse(rule(5, false, 30));
    }

    @Test
    public void faintBarlineSurvives() {
        assertFalse(rule(2, false, 175));
    }

    @Test
    public void crossedBarlineSurvives() {
        assertFalse(rule(2, true, 30));
    }

    @Test
    public void invalidRasterCannotVeto() {
        assertFalse(PrintedQuarterRestBoundary.owns(null, 600, 280, 250, 100, 164, 16));
    }
}

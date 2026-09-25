// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class PrintedPageEvidenceTest {
    private static final int WIDTH = 600, HEIGHT = 300;
    private static final ScorePageInterpretation FALSE_MEASURE = new ScorePageInterpretation(
            List.of(new MeasureRegion(.2f, .6f, .2f, .55f)), List.of());

    private static byte[] paper() {
        byte[] pixels = new byte[WIDTH * HEIGHT];
        Arrays.fill(pixels, (byte) 255);
        return pixels;
    }

    @Test public void illustratedCoverDoesNotBecomeAnEditableMeasure() {
        byte[] gray = paper();
        for (int y = 20; y < 70; y++) for (int x = 130; x < 390; x++)
            gray[y * WIDTH + x] = 0;
        assertEquals(0, PrintedPageEvidence.rejectStafflessPage(FALSE_MEASURE,
                gray, WIDTH, HEIGHT, false).measures().size());
    }

    @Test public void printedEmptyBarAndTabRowStayAvailable() {
        byte[] gray = paper();
        for (int line = 0; line < 5; line++) for (int x = 60; x < 540; x++)
            gray[(110 + 15 * line) * WIDTH + x] = 0;
        assertSame(FALSE_MEASURE, PrintedPageEvidence.rejectStafflessPage(FALSE_MEASURE,
                gray, WIDTH, HEIGHT, false));
        assertSame(FALSE_MEASURE, PrintedPageEvidence.rejectStafflessPage(FALSE_MEASURE,
                paper(), WIDTH, HEIGHT, true));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class PrintedDirectionInkRegressionTest {
    private static final int WIDTH = 260;
    private static final int HEIGHT = 100;

    @Test
    public void requiresPeriodicContinuationFromCaptionToBarline() {
        var word = new PlayingTechniqueDetector.Word("cresc. poco a poco",
                12f / WIDTH, 20f / HEIGHT, 62f / WIDTH, 34f / HEIGHT);
        byte[] page = blank();
        for (int x = 64; x <= 244; x += 20)
            Arrays.fill(page, 28 * WIDTH + x, 28 * WIDTH + x + 8, (byte) 32);
        assertTrue(PrintedCrescendoDashes.reachesBarline(word, page, WIDTH, HEIGHT, 15, 254));
        Arrays.fill(page, 28 * WIDTH + 244, 28 * WIDTH + 252, (byte) 223);
        assertFalse(PrintedCrescendoDashes.reachesBarline(word, page, WIDTH, HEIGHT, 15, 254));
        Arrays.fill(page, 28 * WIDTH + 64, 28 * WIDTH + 252, (byte) 32);
        assertFalse(PrintedCrescendoDashes.reachesBarline(word, page, WIDTH, HEIGHT, 15, 254));
    }

    @Test
    public void miniatureTieRequiresBothReturningShoulders() {
        byte[] page = blank();
        int[] depth = {5, 6, 7, 7, 8, 8, 7, 7, 6, 5};
        for (int i = 0; i < depth.length; i++)
            for (int dy = -1; dy <= 1; dy++)
                page[(40 + depth[i] + dy) * WIDTH + 80 + i] = 32;
        assertTrue(ShadedCompactTieRidge.shortInterheadArc(page, WIDTH, HEIGHT, 80, 89, 40, 15));
        page = blank();
        for (int y = 46; y <= 48; y++)
            Arrays.fill(page, y * WIDTH + 80, y * WIDTH + 90, (byte) 32);
        assertFalse(ShadedCompactTieRidge.shortInterheadArc(page, WIDTH, HEIGHT, 80, 89, 40, 15));
    }

    private static byte[] blank() {
        byte[] page = new byte[WIDTH * HEIGHT];
        Arrays.fill(page, (byte) 223);
        return page;
    }
}

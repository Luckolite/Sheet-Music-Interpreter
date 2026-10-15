// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster: staff rules interrupted by regular black stems, no source-score pixels. */
public class NotationIsNotMarginTextureTest {
    private byte[] page(int paper, boolean mirror) {
        byte[] g = new byte[600 * 200];
        java.util.Arrays.fill(g, (byte) paper);
        for (int x = 20; x < 580; x++)
            for (int line = 0; line < 5; line++) g[(60 + line * 10) * 600 + x] = 20;
        for (int x = 30; x < 170; x += 20)
            for (int dx = 0; dx < 3; dx++) for (int y = 50; y < 115; y++) g[y * 600 + x + dx] = 20;
        if (mirror) {
            byte[] b = new byte[g.length];
            for (int y = 0; y < 200; y++)
                for (int x = 0; x < 600; x++) b[y * 600 + 599 - x] = g[y * 600 + x];
            return b;
        }
        return g;
    }

    private void check(int paper, boolean mirror) {
        byte[] g = page(paper, mirror), copy = g.clone();
        int edge =
                mirror
                        ? PrintedStaffExtent.right(
                                g, 600, 200, 19, 579, 10, (line, x) -> 60 + line * 10)
                        : PrintedStaffExtent.left(
                                g, 600, 200, 20, 580, 10, (line, x) -> 60 + line * 10);
        assertEquals(mirror ? 579 : 20, edge);
        assertArrayEquals(copy, g);
    }

    @Test
    public void blackStemsOnWhitePaperKeepLeftExtent() {
        check(255, false);
    }

    @Test
    public void blackStemsOnWhitePaperKeepRightExtent() {
        check(255, true);
    }

    @Test
    public void blackStemsOnGrayPaperKeepLeftExtent() {
        check(215, false);
    }

    @Test
    public void blackStemsOnGrayPaperKeepRightExtent() {
        check(215, true);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original open-C geometry with an asymmetric circular upper terminal. */
public class AsymmetricCommonTimeMeterTest {
    private HeaderSymbolNormalizationTest.Page page(boolean cut, int radius, int centerY) {
        var p = new HeaderSymbolNormalizationTest.Page(cut, true);
        for (int y = centerY - radius; y <= centerY + radius; y++)
            for (int x = 138 - radius; x <= 142; x++)
                if ((x - 138) * (x - 138) + (y - centerY) * (y - centerY) <= radius * radius)
                    p.gray[y * p.w + x] = 0;
        return p;
    }

    private int read(HeaderSymbolNormalizationTest.Page p) {
        return CommonTimeMeter.read(p.gray, p.w, p.h, 110, 142, 80, 16, 0);
    }

    @Test
    public void circularUpperTerminalKeepsAnOpenCommonTimeChannel() {
        for (int radius : new int[] {4, 5, 6})
            for (int centerY : new int[] {105, 106})
                assertEquals(
                        "radius=" + radius + " centerY=" + centerY,
                        4,
                        read(page(false, radius, centerY)));
    }

    @Test
    public void circularUpperTerminalKeepsAnOpenCutTimeChannel() {
        for (int radius : new int[] {4, 5, 6})
            for (int centerY : new int[] {105, 106})
                assertEquals(
                        "radius=" + radius + " centerY=" + centerY,
                        2,
                        read(page(true, radius, centerY)));
    }

    @Test
    public void terminalCannotHideAClosedRightBoundary() {
        for (boolean cut : new boolean[] {false, true}) {
            var p = page(cut, 6, 106);
            for (int y = 101; y <= 123; y++)
                for (int x = 137; x <= 142; x++) p.gray[y * p.w + x] = 0;
            assertEquals(0, read(p));
        }
    }

    @Test
    public void oneClearRowDoesNotProveAnOpening() {
        for (boolean cut : new boolean[] {false, true}) {
            var p = page(cut, 6, 106);
            for (int y = 107; y <= 117; y++)
                if (y != 116) for (int x = 137; x <= 142; x++) p.gray[y * p.w + x] = 0;
            assertEquals(0, read(p));
        }
    }

    @Test
    public void repeatedReadingPreservesGrayAndLabels() {
        var p = page(true, 6, 106);
        var gray = p.gray.clone();
        var labels = p.labels.clone();
        assertEquals(2, read(p));
        assertEquals(2, read(p));
        assertArrayEquals(gray, p.gray);
        assertArrayEquals(labels, p.labels);
    }
}

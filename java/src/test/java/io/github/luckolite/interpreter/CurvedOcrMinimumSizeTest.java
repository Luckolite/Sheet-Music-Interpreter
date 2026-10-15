// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class CurvedOcrMinimumSizeTest {
    private CurvedCoverRows.Raster raster(int glyphHeight) {
        int w = 600, h = 300;
        int[] pixels = new int[w * h];
        Arrays.fill(pixels, 0xffffffff);
        for (int n = 0; n < 12; n++) {
            int left = 110 + n * 23;
            double t = (left + 4.0) / w - .5;
            int top = 85 + (int) Math.round(80 * t * t);
            for (int y = top; y < top + glyphHeight; y++)
                for (int x = left; x < left + 8; x++) pixels[y * w + x] = 0xff151515;
        }
        return new CurvedCoverRows.Raster(w, h, pixels);
    }

    @Test
    public void shortRowsKeepPixelsAndGainOnlyWhiteBottomPadding() {
        var input = raster(16);
        int[] original = input.argb().clone();
        var rows = CurvedCoverRows.propose(input, true, -1);
        assertFalse(rows.isEmpty());
        boolean shortRow = false;
        for (var row : rows) {
            var raw = row.raw();
            var clean = row.cleaned();
            if (raw.height() >= 32) continue;
            shortRow = true;
            assertEquals(raw.width(), clean.width());
            assertEquals(32, clean.height());
            boolean ink = false;
            for (int y = 0; y < raw.height(); y++)
                for (int x = 0; x < raw.width(); x++) {
                    int value = clean.argb()[y * clean.width() + x];
                    if (value != 0xffffffff) {
                        ink = true;
                        assertEquals(raw.argb()[y * raw.width() + x], value);
                    }
                }
            assertTrue(ink);
            for (int y = raw.height(); y < clean.height(); y++)
                for (int x = 0; x < clean.width(); x++)
                    assertEquals(0xffffffff, clean.argb()[y * clean.width() + x]);
        }
        assertTrue(shortRow);
        assertArrayEquals(original, input.argb());
    }

    @Test
    public void alreadyLargeRowsKeepGeometry() {
        var rows = CurvedCoverRows.propose(raster(25), true, -1);
        assertFalse(rows.isEmpty());
        for (var row : rows) {
            assertTrue(row.raw().height() >= 32);
            assertEquals(row.raw().height(), row.cleaned().height());
            assertEquals(row.raw().width(), row.cleaned().width());
        }
    }
}

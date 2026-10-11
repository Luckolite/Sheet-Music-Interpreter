// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Procedural rectangles along a shallow curve, unrelated to any scanned cover. */
public class CurvedCoverRowsSyntheticTest {
    private static CurvedCoverRows.Raster raster() {
        int w = 600, h = 300;
        int[] pixels = new int[w * h];
        Arrays.fill(pixels, 0xffffffff);
        for (int n = 0; n < 12; n++) {
            int left = 110 + n * 25;
            double t = (left + 4.0) / w - .5;
            int top = 85 + (int) Math.round(80 * t * t);
            for (int y = top; y < top + 18; y++)
                for (int x = left; x < left + 8; x++) pixels[y * w + x] = 0xff151515;
        }
        return new CurvedCoverRows.Raster(w, h, pixels);
    }

    @Test
    public void originalCurvedComponentsProduceBoundedDeterministicRows() {
        var input = raster();
        int[] before = input.argb().clone();
        var rows = CurvedCoverRows.propose(input, true, -1);
        assertFalse(rows.isEmpty());
        var again = CurvedCoverRows.propose(input, true, -1);
        assertEquals(rows.size(), again.size());
        assertArrayEquals(before, input.argb());
        for (int n = 0; n < rows.size(); n++) {
            var row = rows.get(n);
            assertTrue(row.parts().size() >= 5);
            assertArrayEquals(row.quadratic(), again.get(n).quadratic(), 0);
            assertArrayEquals(row.cleaned().argb(), again.get(n).cleaned().argb());
            assertEquals(row.raw().width() * row.raw().height(), row.raw().argb().length);
            assertTrue(row.mapBounds()[0] >= 0 && row.mapBounds()[1] >= 0);
            assertTrue(row.mapBounds()[2] < input.width() && row.mapBounds()[3] < input.height());
        }
    }

    @Test
    public void plainOrNotationPageDoesNotRunCoverFallback() {
        assertTrue(CurvedCoverRows.propose(raster(), false, -1).isEmpty());
        assertTrue(CurvedCoverRows.propose(raster(), true, 120).isEmpty());
    }

    @Test
    public void transparentPixelsCannotBecomeInvisibleInk() {
        var source = raster();
        source.argb()[0] &= 0x00ffffff;
        assertTrue(CurvedCoverRows.propose(source, true, -1).isEmpty());
    }

    @Test
    public void blankPaperDoesNotProduceRows() {
        int[] pixels = new int[600 * 300];
        Arrays.fill(pixels, 0xffffffff);
        assertTrue(
                CurvedCoverRows.propose(new CurvedCoverRows.Raster(600, 300, pixels), true, -1)
                        .isEmpty());
    }
}

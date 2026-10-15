// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric numeral over existing synthetic beam fixture; no score-derived pixels. */
public class InclusiveFiveCapTest {
    private byte[] ink(boolean stem) {
        byte[] g = AdjacentQuintupletTest.ink(false, false, false);
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 12; x++) {
                boolean dark =
                        y < 3 && x >= 6
                                || stem && y >= 3 && y < 9 && x >= 4 && x <= 5
                                || y >= 9 && y < 12
                                || y >= 12 && y < 19 && x >= 9
                                || y >= 15 && y < 19 && x < 3
                                || y >= 19 && x >= 2 && x <= 8;
                if (dark) g[(145 + y) * 500 + 284 + x] = 0;
            }
        return g;
    }

    @Test
    public void sixPixelCapHasSixPixelWidth() {
        var r = AdjacentQuintupletTest.apply(ink(true));
        for (int i = 0; i < 9; i++) assertEquals(i < 4 ? 1 : 5, r.get(i).tupletDivisor());
        for (int i = 4; i < 9; i++) assertEquals(4, r.get(i).tupletNormalNotes());
    }

    @Test
    public void capWithoutLeftStemStillAbstains() {
        assertEquals(AdjacentQuintupletTest.notes(), AdjacentQuintupletTest.apply(ink(false)));
    }
}

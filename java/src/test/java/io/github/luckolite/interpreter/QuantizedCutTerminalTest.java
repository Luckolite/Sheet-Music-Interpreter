// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric C with a thin horizontal lower terminal. */
public class QuantizedCutTerminalTest {
    static HeaderSymbolNormalizationTest.Page page(boolean cut, int length) {
        var p = new HeaderSymbolNormalizationTest.Page(cut, true);
        for (int y = 118; y <= 134; y++)
            for (int x = 128; x <= 142; x++) p.gray[y * p.w + x] = (byte) 255;
        for (int x = 128; x < 128 + length; x++) p.gray[132 * p.w + x] = 0;
        return p;
    }

    static int read(HeaderSymbolNormalizationTest.Page p) {
        return CommonTimeMeter.read(p.gray, p.w, p.h, 110, 142, 80, 16, 0);
    }

    @Test
    public void thinCutTerminalKeepsHalfNoteMeter() {
        assertEquals(2, read(page(true, 10)));
    }

    @Test
    public void thinCommonTerminalKeepsQuarterNoteMeter() {
        assertEquals(4, read(page(false, 10)));
    }

    @Test
    public void absentTerminalRejected() {
        assertEquals(0, read(page(true, 0)));
        assertEquals(0, read(page(false, 0)));
    }

    @Test
    public void insufficientTerminalRejected() {
        assertEquals(0, read(page(true, 9)));
        assertEquals(0, read(page(false, 9)));
    }

    @Test
    public void closedRightSideRejected() {
        var p = page(true, 10);
        for (int y = 101; y <= 123; y++) for (int x = 137; x <= 142; x++) p.gray[y * p.w + x] = 0;
        assertEquals(0, read(p));
    }

    @Test
    public void sourcePreserved() {
        var p = page(true, 10);
        byte[] before = p.gray.clone();
        read(p);
        assertArrayEquals(before, p.gray);
    }
}

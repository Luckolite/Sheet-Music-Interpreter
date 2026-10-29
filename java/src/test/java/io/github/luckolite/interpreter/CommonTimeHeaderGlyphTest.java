// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural C/cut shapes, no score or font fixture. */
public class CommonTimeHeaderGlyphTest {
    private static int read(HeaderSymbolNormalizationTest.Page p) {
        return CommonTimeHeaderGlyph.read(p.gray, p.w, p.h, 110, 142, 80, 16, 0);
    }

    @Test
    public void completeCommonAndCutSignsStayDistinct() {
        assertEquals(4, read(new HeaderSymbolNormalizationTest.Page(false, true)));
        assertEquals(2, read(new HeaderSymbolNormalizationTest.Page(true, true)));
    }

    @Test
    public void incompleteArcRemainsUnproved() {
        var p = new HeaderSymbolNormalizationTest.Page(false, true);
        for (int y = 115; y <= 144; y++)
            for (int x = 110; x <= 142; x++) p.gray[y * p.w + x] = (byte) 255;
        assertEquals(0, read(p));
    }

    @Test
    public void closedOvalAndNoteheadRemainUnproved() {
        var p = new HeaderSymbolNormalizationTest.Page(false, true);
        for (int y = 101; y <= 123; y++) for (int x = 137; x <= 142; x++) p.gray[y * p.w + x] = 0;
        assertEquals(0, read(p));
        p.eraseSign();
        p.note(126, 112, 16, 12, false);
        assertEquals(0, read(p));
    }

    @Test
    public void invalidFramesAbstain() {
        assertEquals(
                0, CommonTimeHeaderGlyph.read(new byte[16], 4, 4, 0, 3, Integer.MAX_VALUE, 16, 0));
        assertEquals(0, CommonTimeHeaderGlyph.read(null, 4, 4, 0, 3, 0, 16, 0));
        assertEquals(0, CommonTimeHeaderGlyph.read(new byte[16], 4, 4, 0, 3, 0, Float.NaN, 0));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class CourtesyFigureEightRimBoundsTest {
    @Test
    public void oversizedDomainAbstainsBeforeAllocation() {
        byte[] l = new byte[300 * 1000], g = new byte[l.length];
        var p = CourtesyFigureEightRim.capture(l, g, 300, 1000, 180, 270, 16, 0, 999);
        assertFalse(p.owns(218, 96));
        assertEquals(0, p.members().length);
    }

    @Test
    public void nonFiniteScaleCannotGrantOwnership() {
        var p = MeterOnlyCourtesyTailTest.page();
        var proof =
                CourtesyFigureEightRim.capture(
                        p.labels(), p.gray(), 300, 180, 180, 270, Float.NaN, 35, 150);
        assertFalse(proof.owns(218, 96));
        assertEquals(0, proof.members().length);
    }

    @Test
    public void distantPaperCannotBorrowOnePixelFringe() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 235, 110, 1, 1, 2, 242);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }
}

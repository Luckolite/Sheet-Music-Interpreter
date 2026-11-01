// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original closed numeral counters; semantic head mistakes on paper cannot create played ink. */
public final class CourtesyClosedCounterPaperOriginalTest {
    static MeterOnlyCourtesyTailTest.Page paper(int x, int y) {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, x, y, 1, 1, 2, 255);
        return p;
    }

    @Test
    public void paperInsideUpperCounterIsNotPlayedInk() {
        assertTrue(MeterOnlyCourtesyTailTest.detect(paper(225, 98)));
    }

    @Test
    public void paperInsideLowerCounterIsNotPlayedInk() {
        assertTrue(MeterOnlyCourtesyTailTest.detect(paper(225, 112)));
    }

    @Test
    public void counterBoxDoesNotOwnExteriorPaper() {
        assertFalse(MeterOnlyCourtesyTailTest.detect(paper(238, 110)));
    }

    @Test
    public void openCounterCannotAuthenticatePaper() {
        var p = paper(225, 98);
        MeterOnlyCourtesyTailTest.rect(p, 218, 95, 5, 7, 0, 255);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void missingNumeratorCannotAuthenticateCounter() {
        var p = paper(225, 98);
        MeterOnlyCourtesyTailTest.rect(p, 200, 60, 40, 29, 0, 255);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void completePlayedHeadRetainsTail() {
        var p = paper(225, 98);
        MeterOnlyCourtesyTailTest.rect(p, 218, 100, 16, 9, 2, 0);
        MeterOnlyCourtesyTailTest.rect(p, 232, 74, 2, 36, 1, 0);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void unchangedSmallFragmentLimitStillApplies() {
        var p = paper(225, 98);
        MeterOnlyCourtesyTailTest.rect(p, 222, 95, 8, 7, 2, 255);
        MeterOnlyCourtesyTailTest.rect(p, 222, 109, 8, 7, 2, 255);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void realRestBeyondMeterStillKeepsMeasure() {
        var p = paper(225, 98);
        MeterOnlyCourtesyTailTest.rect(p, 249, 99, 5, 9, 1, 0);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void sourcePlanesRemainExact() {
        var p = paper(225, 98);
        var l = p.labels().clone();
        var g = p.gray().clone();
        MeterOnlyCourtesyTailTest.detect(p);
        assertArrayEquals(l, p.labels());
        assertArrayEquals(g, p.gray());
    }
}

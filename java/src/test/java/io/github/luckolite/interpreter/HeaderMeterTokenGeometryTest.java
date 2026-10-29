// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class HeaderMeterTokenGeometryTest {
    @Test
    public void twoDigitNumeratorHasTwoDigitWidthBudget() {
        assertTrue(HeaderMeterTokenGeometry.digitSized(12, 140, 70, 40));
        assertFalse(HeaderMeterTokenGeometry.digitSized(4, 140, 70, 40));
    }

    @Test
    public void allowedCompoundAndIrregularNumeratorsRemainNumeric() {
        for (int n : new int[] {1, 3, 4, 6, 7, 8, 10, 12, 16, 32})
            assertTrue(HeaderMeterTokenGeometry.digitSized(n, n >= 10 ? 140 : 70, 70, 40));
    }

    @Test
    public void tempoAndOutOfRangeNumeralsAreNotMeterDigits() {
        for (int n : new int[] {-1, 0, 33, 99, 128})
            assertFalse(HeaderMeterTokenGeometry.digitSized(n, 140, 70, 40));
    }

    @Test
    public void oversizedAndUndersizedMarksAreRejected() {
        assertFalse(HeaderMeterTokenGeometry.digitSized(12, 260, 70, 40));
        assertFalse(HeaderMeterTokenGeometry.digitSized(12, 140, 130, 40));
        assertFalse(HeaderMeterTokenGeometry.digitSized(12, 5, 70, 40));
    }

    @Test
    public void nonFiniteAndInvalidFramesAbstain() {
        assertFalse(HeaderMeterTokenGeometry.digitSized(12, 140, 70, 0));
        assertFalse(HeaderMeterTokenGeometry.digitSized(12, Float.NaN, 70, 40));
        assertFalse(HeaderMeterTokenGeometry.digitSized(12, 140, 70, Float.POSITIVE_INFINITY));
    }
}

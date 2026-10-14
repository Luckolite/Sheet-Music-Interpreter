// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original numeral slots with residual horizontal rails and explicit OCR disagreements. */
public class CompactMeterSlotTest {
    private int[] withRule() {
        int[] p = MeterDigitRecognizerTest.pixels();
        for (int y = 41; y < 43; y++) Arrays.fill(p, y * 30, (y + 1) * 30, 0xff000000);
        return p;
    }

    @Test
    public void thinResidualRuleDoesNotWidenDenominator() throws Exception {
        int[] p = withRule(), before = p.clone();
        assertEquals(
                "7/4", MeterDigitRecognizerTest.read(p, "4", new MeterDigitRecognizerTest.Model()));
        assertArrayEquals(before, p);
    }

    @Test
    public void railAloneCannotBecomeDigit() throws Exception {
        int[] p = withRule();
        Arrays.fill(p, 22 * 30, 41 * 30, 0xffffffff);
        assertEquals(
                "", MeterDigitRecognizerTest.read(p, "4", new MeterDigitRecognizerTest.Model()));
    }

    @Test
    public void broadInkIsNotDiscardedAsRail() throws Exception {
        int[] p = withRule();
        for (int y = 36; y < 43; y++) Arrays.fill(p, y * 30, (y + 1) * 30, 0xff000000);
        assertEquals(
                "", MeterDigitRecognizerTest.read(p, "4", new MeterDigitRecognizerTest.Model()));
    }

    @Test
    public void directNumeratorMayRecoverOneClippedPrefix() {
        assertTrue(MeterDigitRecognizer.permitsDirect("12/8", List.of("2", "", "")));
    }

    @Test
    public void repeatedContradictoryEvidenceIsRetained() {
        assertFalse(MeterDigitRecognizer.permitsDirect("12/8", List.of("2", "2", "")));
    }

    @Test
    public void unrelatedDigitIsNotAClippedPrefix() {
        assertFalse(MeterDigitRecognizer.permitsDirect("12/8", List.of("3", "", "")));
    }

    @Test
    public void conflictingWeakDigitsAbstain() {
        assertFalse(MeterDigitRecognizer.permitsDirect("12/8", List.of("2", "1", "")));
    }

    @Test
    public void matchingAndBlankEvidenceRemainCompatible() {
        assertTrue(MeterDigitRecognizer.permitsDirect("12/8", List.of("12", "", "")));
        assertTrue(MeterDigitRecognizer.permitsDirect("12/8", List.of("", "", "")));
    }
}

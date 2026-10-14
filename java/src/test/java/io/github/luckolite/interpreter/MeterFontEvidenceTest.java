// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class MeterFontEvidenceTest {
    @Test
    public void absentOcrAllowsOpticalFallback() {
        assertTrue(new MeterFontEvidence().permits("12/8"));
    }

    @Test
    public void agreeingFractionsPermitFallback() {
        var e = new MeterFontEvidence();
        e.fraction("12/8");
        e.fraction("12/8");
        assertTrue(e.permits("12/8"));
    }

    @Test
    public void conflictingNumeratorBlocksEvenWithOneAgreement() {
        var e = new MeterFontEvidence();
        e.fraction("12/8");
        e.numerator("13");
        assertFalse(e.permits("12/8"));
    }

    @Test
    public void conflictingDenominatorBlocksFallback() {
        var e = new MeterFontEvidence();
        e.denominator("4");
        assertFalse(e.permits("12/8"));
    }

    @Test
    public void partialAgreementRetainsDigitRole() {
        var e = new MeterFontEvidence();
        e.numerator("8");
        assertFalse(e.permits("12/8"));
    }

    @Test
    public void matchingPartialDenominatorPermitsFallback() {
        var e = new MeterFontEvidence();
        e.denominator("8");
        assertTrue(e.permits("12/8"));
    }

    @Test
    public void structuralVetoCannotBeOverridden() {
        var e = new MeterFontEvidence();
        e.veto();
        assertFalse(e.permits("12/8"));
    }

    @Test
    public void malformedFallbackIsRejected() {
        var e = new MeterFontEvidence();
        assertFalse(e.permits(null));
        assertFalse(e.permits("12/9"));
        assertFalse(e.permits(""));
    }
}

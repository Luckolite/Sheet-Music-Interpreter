// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original evidence controls; no private score content. */
public class MeterReflowEvidenceTest {
    MeterReflowEvidence.Witness witness(int measure, int x, int line, String text) {
        return new MeterReflowEvidence.Witness(
                measure,
                new MeterChangeDetector.Crop(x, line - 3, x + 40, line + 59, line, 14),
                text);
    }

    @Test
    public void invalidNumeratorRequiresStrongFontAndMatchingDenominator() {
        assertTrue(MeterReflowEvidence.eligible("12/8", .92f, .93f, Set.of("42"), Set.of("8")));
        assertTrue(MeterReflowEvidence.eligible("6/8", .92f, .93f, Set.of("66"), Set.of()));
    }

    @Test
    public void validNumeratorContradictionRemainsRejected() {
        for (String text : List.of("4", "2", "16", "32"))
            assertFalse(
                    MeterReflowEvidence.eligible(
                            "12/8", .99f, .99f, Set.of("42", text), Set.of("8")));
    }

    @Test
    public void denominatorContradictionRemainsRejected() {
        assertFalse(MeterReflowEvidence.eligible("12/8", .99f, .99f, Set.of("42"), Set.of("4")));
    }

    @Test
    public void weakOrNonfiniteFontIsRejected() {
        for (float score : new float[] {.89f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertFalse(
                    MeterReflowEvidence.eligible("12/8", score, .99f, Set.of("42"), Set.of("8")));
            assertFalse(
                    MeterReflowEvidence.eligible("12/8", .99f, score, Set.of("42"), Set.of("8")));
        }
    }

    @Test
    public void blankOrValidOcrDoesNotEnterRecovery() {
        assertFalse(MeterReflowEvidence.eligible("12/8", .99f, .99f, Set.of(), Set.of()));
        assertFalse(MeterReflowEvidence.eligible("12/8", .99f, .99f, Set.of("12"), Set.of("8")));
    }

    @Test
    public void twoAlignedStaffCopiesAgree() {
        assertEquals(
                Map.of(0, "12/8"),
                MeterReflowEvidence.corroborated(
                        List.of(witness(0, 100, 100, "12/8"), witness(0, 101, 240, "12/8"))));
    }

    @Test
    public void oneStaffAndShiftedRecropsCannotVoteTwice() {
        assertEquals(
                Map.of(), MeterReflowEvidence.corroborated(List.of(witness(0, 100, 100, "12/8"))));
        assertEquals(
                Map.of(),
                MeterReflowEvidence.corroborated(
                        List.of(witness(0, 100, 100, "12/8"), witness(0, 102, 105, "12/8"))));
    }

    @Test
    public void DifferentMeasuresOrHorizontalSlotsCannotCorroborate() {
        assertEquals(
                Map.of(),
                MeterReflowEvidence.corroborated(
                        List.of(witness(0, 100, 100, "12/8"), witness(1, 100, 240, "12/8"))));
        assertEquals(
                Map.of(),
                MeterReflowEvidence.corroborated(
                        List.of(witness(0, 100, 100, "12/8"), witness(0, 160, 240, "12/8"))));
    }

    @Test
    public void conflictingThirdStaffInvalidatesPair() {
        assertEquals(
                Map.of(),
                MeterReflowEvidence.corroborated(
                        List.of(
                                witness(0, 100, 100, "12/8"),
                                witness(0, 100, 240, "12/8"),
                                witness(0, 100, 380, "6/8"))));
    }
}

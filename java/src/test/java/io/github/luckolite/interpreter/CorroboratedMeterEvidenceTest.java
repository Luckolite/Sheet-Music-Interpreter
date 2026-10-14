// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original evidence controls, with no score pixels or retained OCR. */
public class CorroboratedMeterEvidenceTest {
    @Test
    public void twoAgreeingScalesSupportDigit() {
        assertEquals("3", MusicalOcr.corroboratedDigit(List.of("3", "3", "")));
    }

    @Test
    public void oneScaleDoesNotSupportDigit() {
        assertEquals("", MusicalOcr.corroboratedDigit(List.of("", "3", "")));
    }

    @Test
    public void competingScaleRejectsSupport() {
        assertEquals("", MusicalOcr.corroboratedDigit(List.of("3", "3", "9")));
    }

    @Test
    public void invalidScaleRejectsSupport() {
        assertEquals("", MusicalOcr.corroboratedDigit(List.of("3", "3", "3a")));
    }

    @Test
    public void supportsTwoDigitNumerator() {
        assertEquals("12", MusicalOcr.corroboratedDigit(List.of("12", "", "12")));
    }

    @Test
    public void emptyScalesDoNotSupportDigit() {
        assertEquals("", MusicalOcr.corroboratedDigit(List.of("", "", "")));
    }

    @Test
    public void agreeingEvidenceResolvesRunnerUpAmbiguity() {
        assertTrue(MeterFontMatcher.accepts(3, .87f, .84f, "3"));
    }

    @Test
    public void wrongEvidenceDoesNotResolveAmbiguity() {
        assertFalse(MeterFontMatcher.accepts(3, .87f, .84f, "9"));
    }

    @Test
    public void absentEvidenceKeepsMarginRequirement() {
        assertFalse(MeterFontMatcher.accepts(3, .87f, .84f, ""));
    }

    @Test
    public void weakShapeCannotUseCorroboration() {
        assertFalse(MeterFontMatcher.accepts(3, .83f, .75f, "3"));
    }

    @Test
    public void decisiveShapeKeepsExistingAcceptance() {
        assertTrue(MeterFontMatcher.accepts(3, .9f, .8f, ""));
    }

    @Test
    public void evidenceCannotPromoteRunnerUp() {
        assertFalse(MeterFontMatcher.accepts(9, .88f, .86f, "3"));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MeterOcrEvidenceTest {
    @Test
    public void reflowedDenominatorMayRetainItsGeneratedSeparator() {
        for (String denominator : List.of("/8", "|8"))
            assertEquals(
                    "12/8",
                    MeterOcrEvidence.reflowedFraction(
                            List.of(
                                    token("12", 20, 10, 40, 30),
                                    token(denominator, 100, 10, 60, 30)),
                            80,
                            110));
    }

    @Test
    public void joinedSeparatorCannotSupplyMissingOrMisplacedNumerator() {
        assertEquals(
                "",
                MeterOcrEvidence.reflowedFraction(List.of(token("/8", 120, 10, 40, 30)), 80, 110));
        assertEquals(
                "",
                MeterOcrEvidence.reflowedFraction(
                        List.of(token("/12", 20, 10, 40, 30), token("8", 130, 10, 20, 30)),
                        80,
                        110));
        assertEquals(
                "",
                MeterOcrEvidence.reflowedFraction(
                        List.of(token("12", 20, 10, 40, 30), token("1/8", 110, 10, 40, 30)),
                        80,
                        110));
    }

    @Test
    public void reflowedSlotsRecoverDigitsWhenOcrOmitsTheGeneratedSlash() {
        assertEquals(
                "12/8",
                MeterOcrEvidence.reflowedFraction(
                        List.of(token("12", 20, 10, 50, 30), token("8", 130, 12, 20, 28)),
                        80,
                        110));
    }

    @Test
    public void reflowedSlotsRejectInvalidOrCompetingNumerals() {
        for (String bad : List.of("42", "0", "x"))
            assertEquals(
                    "",
                    MeterOcrEvidence.reflowedFraction(
                            List.of(token(bad, 20, 10, 40, 30), token("8", 130, 10, 20, 30)),
                            80,
                            110));
        assertEquals(
                "",
                MeterOcrEvidence.reflowedFraction(
                        List.of(
                                token("12", 20, 10, 40, 30),
                                token("2", 30, 10, 20, 30),
                                token("8", 130, 10, 20, 30)),
                        80,
                        110));
    }

    @Test
    public void reflowedSlotsRejectMissingDenominatorAndDigitsInSeparator() {
        assertEquals(
                "",
                MeterOcrEvidence.reflowedFraction(List.of(token("12", 20, 10, 40, 30)), 80, 110));
        assertEquals(
                "",
                MeterOcrEvidence.reflowedFraction(
                        List.of(
                                token("12", 20, 10, 40, 30),
                                token("1", 90, 10, 10, 30),
                                token("8", 130, 10, 20, 30)),
                        80,
                        110));
    }

    @Test
    public void reflowedSlotsTolerateOnlyPunctuationOutsideTheNumbers() {
        assertEquals(
                "12/8",
                MeterOcrEvidence.reflowedFraction(
                        List.of(
                                token("12", 20, 10, 40, 30),
                                token("/", 90, 10, 10, 30),
                                token("8", 130, 10, 20, 30)),
                        80,
                        110));
        assertEquals("", MeterOcrEvidence.reflowedFraction(null, 80, 110));
        assertEquals("", MeterOcrEvidence.reflowedFraction(List.of(), 110, 80));
    }

    @Test
    public void singleScaleRejectsOutOfRangeMeterBeforeStaffCorroboration() {
        for (String text : List.of("0/4", "00/4", "33/4", "99/4", "4/0", "4/3", "4/64"))
            assertEquals(text, "", MeterOcrEvidence.singleReading(List.of(text)));
    }

    @Test
    public void singleScaleRetainsValidBoundaryAndCompoundMeters() {
        for (String text : List.of("1/1", "4/4", "7/8", "12/8", "32/32"))
            assertEquals(text, text, MeterOcrEvidence.singleReading(List.of(text)));
        assertEquals("4/4", MeterOcrEvidence.singleReading(List.of("04/04")));
    }

    @Test
    public void singleScaleDoesNotResolveMultipleOrMissingReadings() {
        assertEquals("", MeterOcrEvidence.singleReading(List.of()));
        assertEquals("", MeterOcrEvidence.singleReading(List.of("4/4", "3/4")));
        assertEquals("", MeterOcrEvidence.singleReading(List.of("4/4", "4/4")));
        assertEquals("", MeterOcrEvidence.singleReading(null));
        assertEquals("", MeterOcrEvidence.singleReading(java.util.Collections.singletonList(null)));
    }

    private static MeterOcrEvidence.Token token(String text, int x, int y, int width, int height) {
        return new MeterOcrEvidence.Token(text, x, y, x + width, y + height);
    }

    @Test
    public void joinsHorizontalFractionFragments() {
        assertEquals(
                "3/8",
                MeterOcrEvidence.horizontalFraction(
                        List.of(token("3", 10, 12, 30, 80), token("/8", 70, 14, 70, 78))));
        assertEquals(
                "4/4",
                MeterOcrEvidence.horizontalFraction(
                        List.of(token("4", 10, 12, 30, 80), token("|4", 60, 12, 70, 80))));
        assertEquals(
                "12/8",
                MeterOcrEvidence.horizontalFraction(
                        List.of(
                                token("12", 10, 12, 40, 80),
                                token("/", 70, 12, 10, 80),
                                token("8", 100, 12, 30, 80))));
    }

    @Test
    public void doesNotSplitATwoDigitNumeratorIntoAStackedMeter() {
        assertEquals(
                "12/8",
                MeterOcrEvidence.horizontalFraction(
                        List.of(token("12/", 10, 10, 120, 80), token("8", 170, 12, 25, 78))));
        assertEquals(
                "", MeterOcrEvidence.horizontalFraction(List.of(token("12/", 10, 10, 120, 80))));
    }

    @Test
    public void rejectsStackedDistantAndOverlappingFragments() {
        assertEquals(
                "",
                MeterOcrEvidence.horizontalFraction(
                        List.of(token("3", 10, 0, 30, 80), token("/8", 10, 90, 70, 80))));
        assertEquals(
                "",
                MeterOcrEvidence.horizontalFraction(
                        List.of(token("3", 10, 0, 30, 80), token("/8", 200, 0, 70, 80))));
        assertEquals(
                "",
                MeterOcrEvidence.horizontalFraction(
                        List.of(token("3", 10, 0, 30, 80), token("/8", 30, 0, 70, 80))));
    }

    @Test
    public void doesNotInventDigitsOrSeparators() {
        for (String text : List.of("414", "12/", "12", "B/8", "3/3", "33/4", "0/4", "4/0"))
            assertEquals(
                    text,
                    "",
                    MeterOcrEvidence.horizontalFraction(List.of(token(text, 0, 0, 100, 80))));
    }

    @Test
    public void rejectsContradictoryFractions() {
        assertEquals(
                "",
                MeterOcrEvidence.horizontalFraction(
                        List.of(token("3/8", 0, 0, 100, 80), token("8/8", 120, 0, 100, 80))));
        assertEquals("", MeterOcrEvidence.consensus(List.of("3/8", "3/8", "8/8")));
    }

    @Test
    public void requiresRepeatedValidEvidence() {
        assertEquals("", MeterOcrEvidence.consensus(List.of("", "3/8", "")));
        assertEquals("3/8", MeterOcrEvidence.consensus(List.of("", "3/8", "3/8")));
        assertEquals("4/4", MeterOcrEvidence.consensus(List.of("4|4", "4/4")));
    }

    @Test
    public void threeMatchingRenderingsCanStopFurtherOcr() {
        assertEquals("", MeterOcrEvidence.decisiveConsensus(List.of("4/4", "4/4")));
        assertEquals("4/4", MeterOcrEvidence.decisiveConsensus(List.of("4/4", "4|4", "4/4")));
        assertEquals("", MeterOcrEvidence.decisiveConsensus(List.of("4/4", "4/4", "3/4")));
    }

    @Test
    public void inkMaskMatchesCleanerThreshold() {
        assertTrue(MeterOcrEvidence.ink(0));
        assertTrue(MeterOcrEvidence.ink(134));
        assertFalse(MeterOcrEvidence.ink(135));
        assertFalse(MeterOcrEvidence.ink(255));
    }

    @Test
    public void wholeAsciiTokensKeepTrimAndSlotBoundaries() {
        assertEquals(
                "4/4",
                MeterOcrEvidence.horizontalFraction(List.of(token("\t04|04\n", 0, 0, 100, 80))));
        assertEquals("", MeterOcrEvidence.singleReading(List.of("4/4\n")));
        assertEquals("", MeterOcrEvidence.singleReading(List.of(" 4/4 ")));
        for (String bad : List.of("\uFF14/\uFF14", "\u0664/\u0664", "4/4\nx", "4/4\u00A0")) {
            assertEquals(bad, "", MeterOcrEvidence.singleReading(List.of(bad)));
            assertEquals(
                    bad,
                    "",
                    MeterOcrEvidence.horizontalFraction(List.of(token(bad, 0, 0, 100, 80))));
        }
        assertEquals(
                "12/8",
                MeterOcrEvidence.reflowedFraction(
                        List.of(
                                token("\t12\n", 20, 10, 40, 30),
                                token("|.,:", 90, 10, 10, 30),
                                token("/8\t", 130, 10, 20, 30)),
                        80,
                        110));
        assertEquals(
                "",
                MeterOcrEvidence.reflowedFraction(
                        List.of(token("12", 20, 10, 40, 30), token("\uFF18", 130, 10, 20, 30)),
                        80,
                        110));
    }

    @Test
    public void invalidEvidenceKeepsCallerOrderAndNullEntrySemantics() {
        var last = token("/8", 70, 14, 70, 78);
        var first = token("3", 10, 12, 30, 80);
        var tokens =
                java.util.Arrays.asList(
                        last,
                        null,
                        token(null, 0, 0, 10, 10),
                        token("not a meter", 20, 10, 0, 30),
                        first);
        var original = new java.util.ArrayList<>(tokens);
        assertEquals("3/8", MeterOcrEvidence.horizontalFraction(tokens));
        assertEquals(original, tokens);
        assertSame(last, tokens.get(0));
        assertSame(first, tokens.get(4));
        assertEquals("", MeterOcrEvidence.reflowedFraction(null, 80, 110));
        assertEquals(
                "",
                MeterOcrEvidence.reflowedFraction(
                        java.util.Collections.singletonList(token(null, 10, 10, 20, 30)), 80, 110));
        try {
            MeterOcrEvidence.horizontalFraction(null);
            fail("The established null-list failure must remain");
        } catch (NullPointerException expected) {
            // horizontalFraction retains its existing non-null input contract.
        }
    }
}

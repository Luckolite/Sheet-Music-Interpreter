// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural controls; no third-party score or renderer implementation. */
public class HairpinNumberingTest {
    private static ScoreDynamicChange span(int start, int end, int staff) {
        return new ScoreDynamicChange(start, .25f, staff, 2, end, .75f, 0, 1);
    }

    @Test
    public void overlappingCrossMeasureSpansHaveDistinctNumbers() {
        assertArrayEquals(
                new int[] {1, 2}, HairpinNumbering.assign(List.of(span(0, 2, 0), span(1, 2, 0))));
    }

    @Test
    public void numberOwnershipIsPartWideAcrossStaves() {
        assertArrayEquals(
                new int[] {1, 2}, HairpinNumbering.assign(List.of(span(0, 2, 0), span(1, 2, 1))));
    }

    @Test
    public void sameMeasurePairsMayReuseNumbersInDocumentOrder() {
        assertArrayEquals(
                new int[] {1, 1}, HairpinNumbering.assign(List.of(span(0, 0, 0), span(0, 0, 1))));
    }

    @Test
    public void closedNumberMayBeReusedByLaterDocumentStart() {
        assertArrayEquals(
                new int[] {1, 1}, HairpinNumbering.assign(List.of(span(0, 1, 0), span(1, 2, 1))));
    }

    @Test
    public void equalMeasureBoundaryDoesNotImplyEarlierDocumentStop() {
        assertArrayEquals(
                new int[] {2, 1}, HairpinNumbering.assign(List.of(span(1, 2, 0), span(0, 1, 1))));
    }

    @Test
    public void maximumSixteenDistinctConcurrentPairsRemainSupported() {
        var spans = new ArrayList<ScoreDynamicChange>();
        for (int i = 0; i < 16; i++) spans.add(span(0, 2, i % 2));
        assertArrayEquals(
                java.util.stream.IntStream.rangeClosed(1, 16).toArray(),
                HairpinNumbering.assign(spans));
    }

    @Test
    public void seventeenthConcurrentSpanCannotSilentlyCorruptPairing() {
        var spans = new ArrayList<ScoreDynamicChange>();
        for (int i = 0; i < 17; i++) spans.add(span(0, 2, i % 2));
        assertThrows(IllegalArgumentException.class, () -> HairpinNumbering.assign(spans));
    }

    @Test
    public void absoluteDynamicDoesNotConsumeAWedgeNumber() {
        assertArrayEquals(
                new int[] {0, 1},
                HairpinNumbering.assign(
                        List.of(new ScoreDynamicChange(0, 0, 0, 1, 0, 0, 0, 0), span(0, 2, 0))));
    }
}

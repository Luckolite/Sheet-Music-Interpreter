// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ComputedScoreAnchorTest {
    private static ScoreMeterMap meter(float beats) {
        return new ScoreMeterMap(beats, List.of());
    }

    @Test
    public void storedAnchorValidationStillRejectsOneUlpOverrun() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScoreAnchor(0, Math.nextUp(4d)).canonical(meter(4), 1));
    }

    @Test
    public void computedOneUlpOverrunResolvesToExactBoundary() {
        assertEquals(
                new ScoreAnchor(1, 0),
                ScoreAnchor.fromComputedTiming(0, Math.nextUp(4d), meter(4), 1));
    }

    @Test
    public void validInteriorOffsetRetainsExactBits() {
        double inside = Math.nextDown(4d);
        assertEquals(
                Double.doubleToRawLongBits(inside),
                Double.doubleToRawLongBits(
                        ScoreAnchor.fromComputedTiming(0, inside, meter(4), 1)
                                .quarterBeatOffset()));
    }

    @Test
    public void genuineOverrunIsNotClamped() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreAnchor.fromComputedTiming(0, 4 + 1e-8, meter(4), 1));
    }

    @Test
    public void maximumSupportedBarAllowsComputedRoundingOnly() {
        assertEquals(
                new ScoreAnchor(1, 0),
                ScoreAnchor.fromComputedTiming(0, Math.nextUp(128d), meter(128), 1));
        assertThrows(IllegalArgumentException.class, () -> new ScoreAnchor(0, Math.nextUp(128d)));
    }

    @Test
    public void scoreEndSentinelDoesNotAcceptPositiveOffset() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreAnchor.fromComputedTiming(1, Math.ulp(1d), meter(4), 1));
    }

    @Test
    public void largeAbsoluteOriginAllowsOnlyItsArithmeticPrecision() {
        var meter = meter(8f / 3);
        int measure = 10000;
        double length = meter.beatsInMeasure(measure);
        double computed = Math.nextUp(meter.startBeat(measure) + length) - meter.startBeat(measure);
        assertTrue(computed > length);
        assertEquals(
                new ScoreAnchor(measure + 1, 0),
                ScoreAnchor.fromComputedTiming(measure, computed, meter, measure + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreAnchor.fromComputedTiming(measure, length + 1e-7, meter, measure + 1));
    }

    @Test
    public void invalidTimingAndMeasureBoundsRemainRejected() {
        for (double offset : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ScoreAnchor.fromComputedTiming(0, offset, meter(4), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreAnchor.fromComputedTiming(2, 0, meter(4), 1));
    }
}

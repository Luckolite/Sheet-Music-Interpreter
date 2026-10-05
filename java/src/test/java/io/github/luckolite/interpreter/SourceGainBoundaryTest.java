// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original resolved gain curves; no score pixels, geometry or instrument assets. */
public class SourceGainBoundaryTest {
    private static ScoreMeterMap meter() {
        return ScoreMeterMap.fromPerformedDurations(List.of(1.0, 4.0, 1.0));
    }

    private static List<ScoreGainProjection.Curve> curves() {
        return List.of(
                new ScoreGainProjection.Curve("quiet", 16, 0, 1, -12, -12),
                new ScoreGainProjection.Curve("forte", 16, 1, 5, 6, 6),
                new ScoreGainProjection.Curve("closing", 16, 5, 6, -6, -6));
    }

    private static ScoreNavigationPlan plan(ScoreMeterMap meter, boolean repeat) {
        return ScoreNavigationPlan.create(
                3,
                repeat
                        ? List.of(
                                new ScorePlaybackDirection(
                                        0, ScorePlaybackDirection.Kind.REPEAT_START),
                                new ScorePlaybackDirection(
                                        3, ScorePlaybackDirection.Kind.REPEAT_END))
                        : List.of(),
                meter);
    }

    @Test
    public void pickupAndClosingLevelsKeepTheirAuthoritativeOneBeatSpans() {
        var meter = meter();
        var result =
                ScoreGainProjection.project(
                        curves(),
                        plan(meter, false),
                        meter,
                        new ScorePerformanceTimeline(60, List.of(), List.of()));
        assertEquals(6, result.performedBeats(), 0);
        assertEquals(
                List.of(0.0, 1.0, 5.0),
                result.pieces().stream().map(ScoreGainProjection.Piece::startBeat).toList());
        assertEquals(
                List.of(-12.0, 6.0, -6.0),
                result.pieces().stream().map(ScoreGainProjection.Piece::fromDb).toList());
        assertEquals(6, result.pieces().get(2).endBeat(), 0);
    }

    @Test
    public void repeatedPickupDoesNotPadEitherOccurrenceOrLeakLaterLevels() {
        var meter = meter();
        var result =
                ScoreGainProjection.project(
                        curves(),
                        plan(meter, true),
                        meter,
                        new ScorePerformanceTimeline(60, List.of(), List.of()));
        assertEquals(12, result.performedBeats(), 0);
        assertEquals(
                List.of(0.0, 1.0, 5.0, 6.0, 7.0, 11.0),
                result.pieces().stream().map(ScoreGainProjection.Piece::startBeat).toList());
        assertEquals(
                List.of(-12.0, 6.0, -6.0, -12.0, 6.0, -6.0),
                result.pieces().stream().map(ScoreGainProjection.Piece::fromDb).toList());
    }

    @Test
    public void partialBarsAndChangingTempoUseActiveTimeForClippedHairpin() {
        var meter = meter();
        var clock =
                new ScorePerformanceTimeline(
                        60,
                        List.of(
                                new ScorePerformanceTimeline.TempoSegment(0, 2, 60, 60),
                                new ScorePerformanceTimeline.TempoSegment(2, 6, 120, 120)),
                        List.of());
        var result =
                ScoreGainProjection.project(
                        List.of(new ScoreGainProjection.Curve("ramp", 16, 0, 8, -12, 4)),
                        plan(meter, false),
                        meter,
                        clock);
        assertEquals(6, result.performedBeats(), 0);
        assertEquals(-12 + 16 * 4.0 / 5.0, result.pieces().get(0).toDb(), 1e-9);
    }

    @Test
    public void nominalMeterCannotSilentlyReplaceTheRetainedPartialGrid() {
        var source = meter();
        var nominal = ScoreMeterMap.fromPerformedDurations(List.of(4.0, 4.0, 4.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScoreGainProjection.project(
                                curves(),
                                plan(source, false),
                                nominal,
                                new ScorePerformanceTimeline(60, List.of(), List.of())));
    }

    @Test
    public void duplicateCurveIdentitiesAndMalformedLanesAreRejected() {
        var meter = meter();
        var clock = new ScorePerformanceTimeline(60, List.of(), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScoreGainProjection.project(
                                List.of(curves().get(0), curves().get(0)),
                                plan(meter, false),
                                meter,
                                clock));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScoreGainProjection.Curve("bad", 17, 0, 1, 0, 0));
    }

    @Test
    public void repeatedClippedCurvePiecesKeepOriginalEndpointArithmeticExactly() {
        var grid = ScoreMeterMap.fromPerformedDurations(List.of(1.0, 4.0, 1.0));
        var clock =
                new ScorePerformanceTimeline(
                        60,
                        List.of(
                                new ScorePerformanceTimeline.TempoSegment(0, 2, 60, 90),
                                new ScorePerformanceTimeline.TempoSegment(2, 4, 90, 45),
                                new ScorePerformanceTimeline.TempoSegment(4, 6, 45, 120),
                                new ScorePerformanceTimeline.TempoSegment(6, 8, 120, 120)),
                        List.of(new ScorePerformanceTimeline.Hold("hold", 2, .3, Set.of())));
        var source =
                List.of(
                        new ScoreGainProjection.Curve("ramp", 32, 0, 8, -12, 6),
                        new ScoreGainProjection.Curve("later", 32, 3, 6, 6, -3),
                        new ScoreGainProjection.Curve("quiet", 33, 1, 5, -6, -6),
                        new ScoreGainProjection.Curve("tail", 33, 5, 9, -6, 2));
        for (boolean repeat : new boolean[] {false, true}) {
            var directions =
                    repeat
                            ? List.of(
                                    new ScorePlaybackDirection(
                                            0, ScorePlaybackDirection.Kind.REPEAT_START),
                                    new ScorePlaybackDirection(
                                            3, ScorePlaybackDirection.Kind.REPEAT_END))
                            : List.<ScorePlaybackDirection>of();
            var route = ScoreNavigationPlan.create(3, directions, grid);
            var result = ScoreGainProjection.project(source, route, grid, clock);
            assertEquals(repeat ? 12 : 6, result.performedBeats(), 0);
            assertFalse(result.pieces().isEmpty());
            for (var piece : result.pieces()) {
                var curve =
                        source.stream()
                                .filter(c -> c.id().equals(piece.sourceCurveId()))
                                .findFirst()
                                .orElse(null);
                double from = curve == null ? 0 : curve.db(piece.sourceStartBeat(), clock);
                double to = curve == null ? 0 : curve.db(piece.sourceEndBeat(), clock);
                assertEquals(
                        Double.doubleToRawLongBits(from),
                        Double.doubleToRawLongBits(piece.fromDb()));
                assertEquals(
                        Double.doubleToRawLongBits(to), Double.doubleToRawLongBits(piece.toDb()));
            }
            assertEquals(result, ScoreGainProjection.project(source, route, grid, clock));
        }
    }
}

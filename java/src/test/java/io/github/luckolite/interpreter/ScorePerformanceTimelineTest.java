// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScorePerformanceTimeline.*;

/** Original synthetic beats, scopes and occurrence identities; no score-derived fixtures. */
public class ScorePerformanceTimelineTest {
    @Test
    public void fractionalMultipleHoldBoundariesAreExactlySharedByBothDirections() {
        var random = new Random(72341);
        for (int trial = 0; trial < 100; trial++) {
            var segments = new ArrayList<TempoSegment>();
            var holds = new ArrayList<Hold>();
            for (int i = 0; i < 12; i++) {
                segments.add(
                        new TempoSegment(
                                i * 4,
                                (i + 1) * 4,
                                40 + random.nextDouble() * 160,
                                40 + random.nextDouble() * 160));
                holds.add(new Hold("pause" + i, i * 4 + 2, .2 + random.nextDouble() * 2, Set.of()));
            }
            var clock = new ScorePerformanceTimeline(120, segments, holds);
            for (var hold : holds) {
                var before =
                        clock.positionAtSeconds(clock.secondsAtBeat(hold.beat(), Boundary.BEFORE));
                var after =
                        clock.positionAtSeconds(clock.secondsAtBeat(hold.beat(), Boundary.AFTER));
                assertEquals(Optional.of(hold.occurrenceId()), before.holdId());
                assertEquals(0, before.holdProgress(), 0);
                assertEquals(hold.beat(), before.beat(), 0);
                assertTrue(after.holdId().isEmpty());
                assertEquals(hold.beat(), after.beat(), 0);
            }
        }
    }

    private ScorePerformanceTimeline ramp(double endBpm, List<Hold> holds) {
        return new ScorePerformanceTimeline(
                120, List.of(new TempoSegment(0, 4, 120, endBpm)), holds);
    }

    @Test
    public void linearBpmRampIntegratesReciprocalTempo() {
        assertEquals(4 * Math.log(2), ramp(60, List.of()).secondsAtBeat(4, Boundary.AFTER), 1e-12);
        assertEquals(2 * Math.log(2), ramp(240, List.of()).secondsAtBeat(4, Boundary.AFTER), 1e-12);
    }

    @Test
    public void rampInverseAndConstantTailRoundTrip() {
        for (double end : new double[] {60, 120, 120.000000001, 240}) {
            var clock = ramp(end, List.of());
            for (double beat = 0; beat < 12; beat += .125)
                assertEquals(
                        beat,
                        clock.positionAtSeconds(clock.secondsAtBeat(beat, Boundary.AFTER)).beat(),
                        1e-10);
        }
    }

    @Test
    public void beforeAfterAndFrozenInverseHaveExplicitBoundaries() {
        var clock = ramp(120, List.of(new Hold("pause", 4, 2, Set.of("held"))));
        assertEquals(2, clock.secondsAtBeat(4, Boundary.BEFORE), 0);
        assertEquals(4, clock.secondsAtBeat(4, Boundary.AFTER), 0);
        var start = clock.positionAtSeconds(2);
        assertEquals(Optional.of("pause"), start.holdId());
        assertEquals(0, start.holdProgress(), 0);
        var middle = clock.positionAtSeconds(3);
        assertEquals(4, middle.beat(), 0);
        assertEquals(.5, middle.holdProgress(), 0);
        assertTrue(clock.positionAtSeconds(4).holdId().isEmpty());
        assertEquals(4, clock.positionAtSeconds(4).beat(), 0);
    }

    @Test
    public void duplicateEnsembleDepictionsUnionTargetsNotDelay() {
        var clock =
                ramp(
                        120,
                        List.of(
                                new Hold("ensemble", 4, 2, Set.of("upper")),
                                new Hold("ensemble", 4, 2, Set.of("lower"))));
        assertEquals(1, clock.holds().size());
        assertEquals(Set.of("upper", "lower"), clock.holds().get(0).sustainedTargets());
        assertEquals(4, clock.secondsAtBeat(4, Boundary.AFTER), 0);
    }

    @Test
    public void twoPauseColumnsAndRepeatOccurrencesAreNotCollapsed() {
        var clock =
                ramp(
                        120,
                        List.of(
                                new Hold("rest:visit1", 2, 1, Set.of()),
                                new Hold("chord:visit1", 4, 2, Set.of("chord")),
                                new Hold("rest:visit2", 6, 1, Set.of())));
        assertEquals(8, clock.secondsAtBeat(8, Boundary.AFTER), 0);
        assertEquals(3, clock.holds().size());
        assertTrue(clock.holds().get(0).sustainedTargets().isEmpty());
    }

    @Test
    public void activeMusicalTimeFreezesDuringHoldWithoutChangingNoHoldTiming() {
        var clock = ramp(60, List.of(new Hold("pause", 2, 3, Set.of())));
        double start = clock.secondsAtBeat(2, Boundary.BEFORE);
        assertEquals(start, clock.activeSecondsAtSeconds(start + 1), 1e-12);
        assertEquals(start, clock.activeSecondsAtSeconds(start + 3), 1e-12);
        assertEquals(start + 1, clock.activeSecondsAtSeconds(start + 4), 1e-12);
        assertEquals(10, ramp(60, List.of()).activeSecondsAtSeconds(10), 0);
    }

    @Test
    public void terminalSilentHoldCountsInDuration() {
        var clock = ramp(120, List.of(new Hold("terminal", 4, 2, Set.of())));
        assertEquals(4, clock.secondsAtBeat(4, Boundary.AFTER), 0);
        assertEquals(2, clock.secondsAtBeat(4, Boundary.BEFORE), 0);
    }

    @Test
    public void numericCompatibilityIncludesOpeningChangesAndVariableMeters() {
        var meter =
                new ScoreMeterMap(
                        5, List.of(new ScoreMeterChange(1, 4, 4), new ScoreMeterChange(3, 12, 8)));
        var clock =
                numeric(
                        120,
                        meter,
                        List.of(new ScoreTempoChange(3, 0, 60), new ScoreTempoChange(4, .5f, 180)));
        assertEquals(6.5, clock.secondsAtBeat(meter.startBeat(3), Boundary.AFTER), 0);
        assertEquals(12.5, clock.secondsAtBeat(meter.startBeat(4), Boundary.AFTER), 0);
        assertEquals(
                1,
                numeric(120, meter, List.of(new ScoreTempoChange(0, 0, 60)))
                        .secondsAtBeat(1, Boundary.AFTER),
                0);
    }

    @Test
    public void numericDuplicatesUseLastAuthoritativeValueWithoutEmptySegments() {
        var clock =
                numeric(
                        120,
                        new ScoreMeterMap(4, List.of()),
                        List.of(new ScoreTempoChange(1, 0, 60), new ScoreTempoChange(1, 0, 180)));
        assertEquals(2 + 4.0 / 3, clock.secondsAtBeat(8, Boundary.AFTER), 1e-12);
    }

    @Test
    public void inputsAndSustainTargetsAreImmutable() {
        var targets = new HashSet<>(Set.of("note"));
        var holds = new ArrayList<Hold>();
        holds.add(new Hold("pause", 4, 2, targets));
        var clock = ramp(120, holds);
        targets.clear();
        holds.clear();
        assertEquals(Set.of("note"), clock.holds().get(0).sustainedTargets());
    }

    private void rejects(Runnable action) {
        try {
            action.run();
            fail("Expected invalid timeline rejection");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void malformedSegmentsRejectGapsOverlapsAndNonfiniteValues() {
        rejects(() -> new TempoSegment(0, 0, 120, 120));
        rejects(() -> new TempoSegment(0, 4, 0, 120));
        rejects(() -> new TempoSegment(0, Double.POSITIVE_INFINITY, 120, 120));
        rejects(
                () ->
                        new ScorePerformanceTimeline(
                                120, List.of(new TempoSegment(1, 4, 120, 120)), List.of()));
        rejects(
                () ->
                        new ScorePerformanceTimeline(
                                120,
                                List.of(
                                        new TempoSegment(0, 4, 120, 120),
                                        new TempoSegment(3, 5, 120, 120)),
                                List.of()));
    }

    @Test
    public void conflictingOrUnorderedSameBeatHoldsAreNotSilentlySummed() {
        rejects(
                () ->
                        ramp(
                                120,
                                List.of(
                                        new Hold("a", 4, 1, Set.of()),
                                        new Hold("a", 4, 2, Set.of()))));
        rejects(
                () ->
                        ramp(
                                120,
                                List.of(
                                        new Hold("a", 4, 1, Set.of()),
                                        new Hold("b", 4, 1, Set.of()))));
        rejects(() -> new Hold("bad", 4, Double.NaN, Set.of()));
        rejects(() -> ramp(120, List.of()).secondsAtBeat(Double.NaN, Boundary.AFTER));
    }

    @Test
    public void repeatedForwardAndInverseQueriesPreserveExactSegmentsHoldsAndCallerCurves() {
        var curves =
                new ArrayList<>(
                        List.of(
                                new TempoSegment(0, 4, 120, 120),
                                new TempoSegment(4, 8, 120, 60),
                                new TempoSegment(8, 12, 90, 90)));
        var original = List.copyOf(curves);
        var clock = new ScorePerformanceTimeline(120, curves, List.of());
        // Explicit original constant/ramp integrals; no prefix sum or cached-array oracle.
        double rampSpan = 60 * Math.log1p(-15.0 * 4 / 120) / -15;
        double rampHalf = 60 * Math.log1p(-15.0 * 2 / 120) / -15;
        double rampEnd = 2 + rampSpan;
        double curveEnd = rampEnd + 4.0 * 60 / 90;
        double partial = 2 + rampHalf, tail = curveEnd + 2;
        double partialBeat = 4 + 120 * Math.expm1((partial - 2) * -15 / 60) / -15;
        double tailBeat = 12 + Math.max(0, tail - curveEnd) * 90 / 60;
        double[] forwardBeats = {0, 2, 4, 6, 8, 12, 14, 6, 4};
        double[] forwardSeconds = {
            0,
            1,
            2,
            partial,
            rampEnd,
            curveEnd,
            curveEnd + Math.max(0, 14.0 - 12) * 60 / 90,
            partial,
            2
        };
        double[] seconds = {0, 1, 2, partial, rampEnd, curveEnd, tail, 2, 1, tail};
        double[] expected = {0, 2, 4, partialBeat, 8, 12, tailBeat, 4, 2, tailBeat};
        for (int repeat = 0; repeat < 3; repeat++) {
            for (int index = 0; index < forwardBeats.length; index++) {
                assertEquals(
                        Double.doubleToRawLongBits(forwardSeconds[index]),
                        Double.doubleToRawLongBits(clock.activeSecondsAtBeat(forwardBeats[index])));
                assertEquals(
                        Double.doubleToRawLongBits(forwardSeconds[index]),
                        Double.doubleToRawLongBits(
                                clock.secondsAtBeat(forwardBeats[index], Boundary.AFTER)));
            }
            for (int index = 0; index < seconds.length; index++) {
                var position = clock.positionAtSeconds(seconds[index]);
                assertEquals(
                        Double.doubleToRawLongBits(expected[index]),
                        Double.doubleToRawLongBits(position.beat()));
                assertEquals(Optional.empty(), position.holdId());
                assertEquals(
                        Double.doubleToRawLongBits(0),
                        Double.doubleToRawLongBits(position.holdProgress()));
            }
        }
        assertEquals(original, curves);
        assertEquals(original, clock.tempoSegments());
        curves.clear();
        assertEquals(original, clock.tempoSegments());
        assertEquals(
                Double.doubleToRawLongBits(tailBeat),
                Double.doubleToRawLongBits(clock.positionAtSeconds(tail).beat()));

        var held =
                new ScorePerformanceTimeline(
                        120,
                        original,
                        List.of(new Hold("original-pause", 6, 1.25, Set.of("original-sound"))));
        double before = held.secondsAtBeat(6, Boundary.BEFORE);
        assertEquals(Double.doubleToRawLongBits(partial), Double.doubleToRawLongBits(before));
        assertEquals(
                Double.doubleToRawLongBits(partial + 1.25),
                Double.doubleToRawLongBits(held.secondsAtBeat(6, Boundary.AFTER)));
        assertEquals(
                Double.doubleToRawLongBits(rampEnd + 1.25),
                Double.doubleToRawLongBits(held.secondsAtBeat(8, Boundary.AFTER)));
        assertEquals(
                new Position(6, Optional.of("original-pause"), 0), held.positionAtSeconds(before));
        assertEquals(
                new Position(
                        6,
                        Optional.of("original-pause"),
                        Math.min(
                                Math.nextDown(1.0),
                                ((before + .625) - before) / ((before + 1.25) - before))),
                held.positionAtSeconds(before + .625));
        assertEquals(
                new Position(6, Optional.empty(), 0),
                held.positionAtSeconds(held.secondsAtBeat(6, Boundary.AFTER)));
    }

    @Test
    public void fullSpanReuseRetainsEmptyOverflowAndInvalidCoordinateBehavior() {
        var empty = new ScorePerformanceTimeline(120, List.of(), List.of());
        assertEquals(new Position(2, Optional.empty(), 0), empty.positionAtSeconds(1));
        var huge =
                new ScorePerformanceTimeline(
                        15, List.of(new TempoSegment(0, Double.MAX_VALUE, 15, 15)), List.of());
        assertEquals(new Position(.25, Optional.empty(), 0), huge.positionAtSeconds(1));
        assertEquals(
                Double.doubleToRawLongBits(4),
                Double.doubleToRawLongBits(huge.activeSecondsAtBeat(1)));
        assertEquals(
                Double.doubleToRawLongBits(0),
                Double.doubleToRawLongBits(huge.positionAtSeconds(-0.0).beat()));
        var signed =
                new ScorePerformanceTimeline(
                        120, List.of(new TempoSegment(-0.0, 4, 120, 120)), List.of());
        assertEquals(
                Double.doubleToRawLongBits(-0.0),
                Double.doubleToRawLongBits(signed.positionAtSeconds(-0.0).beat()));
        var tiny =
                new ScorePerformanceTimeline(
                        120, List.of(new TempoSegment(0, Double.MIN_VALUE, 120, 60)), List.of());
        assertEquals(
                Double.doubleToRawLongBits(0),
                Double.doubleToRawLongBits(tiny.activeSecondsAtBeat(0)));
        assertTrue(Double.isNaN(tiny.activeSecondsAtBeat(Double.MIN_VALUE)));
        rejects(() -> tiny.positionAtSeconds(0));
        rejects(() -> huge.positionAtSeconds(Double.NaN));
        rejects(() -> huge.positionAtSeconds(Double.POSITIVE_INFINITY));
        rejects(() -> huge.positionAtSeconds(-1));
    }
}

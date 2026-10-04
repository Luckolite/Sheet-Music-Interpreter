// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScorePerformanceTimeline.*;
import static io.github.luckolite.interpreter.ScorePlaybackDirection.Kind.*;

/** Original synthetic source curves and routes, never pre-expanded expected input. */
public class ScoreNavigationPerformanceTest {
    private final ScoreMeterMap meter = new ScoreMeterMap(4, List.of());

    private ScoreNavigationPlan repeat(int count, int start, int end) {
        return ScoreNavigationPlan.create(
                count,
                List.of(
                        new ScorePlaybackDirection(start, REPEAT_START),
                        new ScorePlaybackDirection(end, REPEAT_END)),
                meter);
    }

    @Test
    public void actualRouteResumesRampPhaseAndRestoresNumericTempo() {
        var source =
                new ScorePerformanceTimeline(
                        120,
                        List.of(
                                new TempoSegment(0, 16, 120, 60),
                                new TempoSegment(16, 20, 120, 120)),
                        List.of());
        var result = ScoreNavigationPerformance.project(source, repeat(5, 2, 4), meter);
        assertEquals(28, result.performedBeats(), 0);
        assertEquals(16 * Math.log(2), result.timeline().activeSecondsAtBeat(16), 1e-10);
        assertEquals(
                16 * Math.log(2) + 16 * Math.log(1.5),
                result.timeline().activeSecondsAtBeat(24),
                1e-10);
        assertEquals(16 * Math.log(2) + 16 * Math.log(1.5) + 2, result.durationSeconds(), 1e-10);
        var returning =
                result.timeline().tempoSegments().stream()
                        .filter(s -> s.startBeat() == 16)
                        .findFirst()
                        .orElseThrow();
        assertEquals(90, returning.startBpm(), 0);
        assertEquals(
                new ScoreAnchor(2, 2),
                result.sourcePositionAtSeconds(result.timeline().activeSecondsAtBeat(18))
                        .orElseThrow()
                        .anchor());
        for (double beat = 0; beat <= 28; beat += .125)
            assertEquals(
                    beat,
                    result.timeline()
                            .positionAtSeconds(result.timeline().activeSecondsAtBeat(beat))
                            .beat(),
                    1e-10);
    }

    @Test
    public void partialFineClipsCurveWithoutRoundingToWholeBar() {
        var fine =
                new ScorePlaybackDirection(
                        0,
                        FINE,
                        new ScorePlaybackDirection.Details(
                                1.5,
                                "fine",
                                "",
                                "",
                                "",
                                2,
                                List.of(),
                                Optional.empty(),
                                ScorePlaybackDirection.AfterJumpRepeats.DEFAULT,
                                "",
                                List.of()));
        var plan =
                ScoreNavigationPlan.create(
                        3, List.of(new ScorePlaybackDirection(2, DA_CAPO_AL_FINE), fine), meter);
        var source =
                new ScorePerformanceTimeline(
                        120, List.of(new TempoSegment(0, 12, 120, 60)), List.of());
        var result = ScoreNavigationPerformance.project(source, plan, meter);
        assertEquals(9.5, result.performedBeats(), 0);
        assertEquals(
                source.activeSecondsAtBeat(8) + source.activeSecondsAtBeat(1.5),
                result.durationSeconds(),
                1e-10);
        assertEquals(
                new ScoreAnchor(0, 1.5),
                result.sourcePositionAtSeconds(result.durationSeconds()).orElseThrow().anchor());
    }

    @Test
    public void repeatedHoldGetsOccurrenceTargetsAndSourceSeekDuringPause() {
        var source =
                new ScorePerformanceTimeline(
                        120, List.of(), List.of(new Hold("fermata", 8, 2, Set.of("note"))));
        var result =
                ScoreNavigationPerformance.project(
                        source,
                        repeat(2, 0, 2),
                        meter,
                        Map.of("fermata", Boundary.BEFORE),
                        (target, occurrence) ->
                                Optional.of(target + "@" + occurrence.occurrenceId()));
        assertEquals(12, result.durationSeconds(), 0);
        assertEquals(2, result.holdOccurrences().size());
        assertNotEquals(
                result.timeline().holds().get(0).sustainedTargets(),
                result.timeline().holds().get(1).sustainedTargets());
        var position = result.sourcePositionAtSeconds(5).orElseThrow();
        assertEquals(Optional.of("fermata"), position.sourceHoldId());
        assertEquals(new ScoreAnchor(2, 0), position.anchor());
        assertEquals(.5, position.holdProgress(), 0);
        assertEquals(
                new ScoreAnchor(0, 0), result.sourcePositionAtSeconds(6).orElseThrow().anchor());
        assertEquals(10, result.timeline().secondsAtBeat(16, Boundary.BEFORE), 0);
        assertEquals(12, result.timeline().secondsAtBeat(16, Boundary.AFTER), 0);
    }

    @Test
    public void arrivalOwnershipDoesNotDelayDepartureFromAnotherSourceLocation() {
        var source =
                new ScorePerformanceTimeline(
                        120, List.of(), List.of(new Hold("breath", 0, 1, Set.of())));
        var result =
                ScoreNavigationPerformance.project(
                        source,
                        repeat(2, 0, 2),
                        meter,
                        Map.of("breath", Boundary.AFTER),
                        (target, occurrence) -> Optional.empty());
        assertEquals(10, result.durationSeconds(), 0);
        assertEquals(
                List.of(0.0, 8.0), result.timeline().holds().stream().map(Hold::beat).toList());
        assertEquals(
                new ScoreAnchor(0, 0), result.sourcePositionAtSeconds(5.5).orElseThrow().anchor());
    }

    @Test
    public void codaSkipsSourceCurvesAndHoldsWithoutAddingTime() {
        var source =
                new ScorePerformanceTimeline(
                        120,
                        List.of(
                                new TempoSegment(0, 12, 120, 120),
                                new TempoSegment(12, 32, 60, 60)),
                        List.of(new Hold("skipped", 28, 7, Set.of())));
        var plan =
                ScoreNavigationPlan.create(
                        10,
                        List.of(
                                new ScorePlaybackDirection(7, DA_CAPO_AL_CODA),
                                new ScorePlaybackDirection(3, TO_CODA),
                                new ScorePlaybackDirection(8, CODA)),
                        meter);
        var result =
                ScoreNavigationPerformance.project(
                        source,
                        plan,
                        meter,
                        Map.of("skipped", Boundary.AFTER),
                        (t, o) -> Optional.empty());
        assertEquals(48, result.performedBeats(), 0);
        assertTrue(result.timeline().holds().isEmpty());
        assertEquals(36, result.durationSeconds(), 0); // first 6+16, return 6, coda tail 8 seconds.
    }

    @Test
    public void meterMismatchAndUnmappedTargetsFailExplicitly() {
        var source =
                new ScorePerformanceTimeline(
                        120, List.of(), List.of(new Hold("h", 4, 1, Set.of("n"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreNavigationPerformance.project(source, repeat(2, 0, 2), meter));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScoreNavigationPerformance.project(
                                source,
                                repeat(2, 0, 2),
                                meter,
                                Map.of("h", Boundary.BEFORE),
                                (target, occurrence) -> Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScoreNavigationPerformance.project(
                                new ScorePerformanceTimeline(120, List.of(), List.of()),
                                repeat(2, 0, 2),
                                new ScoreMeterMap(3, List.of())));
    }

    @Test
    public void emptyRouteHasNoSourcePositionAndRejectsOutsideSeek() {
        var result =
                ScoreNavigationPerformance.project(
                        new ScorePerformanceTimeline(120, List.of(), List.of()),
                        ScoreNavigationPlan.create(0, List.of()),
                        meter);
        assertEquals(0, result.durationSeconds(), 0);
        assertTrue(result.sourcePositionAtSeconds(0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> result.sourcePositionAtSeconds(.1));
    }

    @Test
    public void simultaneousDepartingAndArrivingHoldsRequireReconciliation() {
        var source =
                new ScorePerformanceTimeline(
                        120,
                        List.of(),
                        List.of(new Hold("out", 8, 1, Set.of()), new Hold("in", 0, 1, Set.of())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScoreNavigationPerformance.project(
                                source,
                                repeat(2, 0, 2),
                                meter,
                                Map.of("out", Boundary.BEFORE, "in", Boundary.AFTER),
                                (target, occurrence) -> Optional.empty()));
    }

    @Test
    public void variableMeterReturnUsesSourceBeatBoundariesAndPhase() {
        var variable =
                new ScoreMeterMap(
                        3, List.of(new ScoreMeterChange(1, 5, 8), new ScoreMeterChange(2, 4, 4)));
        var plan =
                ScoreNavigationPlan.create(
                        3,
                        List.of(
                                new ScorePlaybackDirection(1, REPEAT_START),
                                new ScorePlaybackDirection(2, REPEAT_END)),
                        variable);
        var source =
                new ScorePerformanceTimeline(
                        120, List.of(new TempoSegment(0, 9.5, 120, 60)), List.of());
        var result = ScoreNavigationPerformance.project(source, plan, variable);
        assertEquals(12, result.performedBeats(), 0);
        assertEquals(
                source.activeSecondsAtBeat(9.5)
                        + source.activeSecondsAtBeat(5.5)
                        - source.activeSecondsAtBeat(3),
                result.durationSeconds(),
                1e-10);
        var position =
                result.sourcePositionAtSeconds(result.timeline().activeSecondsAtBeat(6.5))
                        .orElseThrow();
        assertEquals(1, position.anchor().measureIndex());
        assertEquals(1, position.anchor().quarterBeatOffset(), 1e-10);
    }

    @Test
    public void distinctSustainTargetsCannotCollapseOntoInventedSingleTarget() {
        var source =
                new ScorePerformanceTimeline(
                        120, List.of(), List.of(new Hold("h", 4, 1, Set.of("a", "b"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScoreNavigationPerformance.project(
                                source,
                                repeat(2, 0, 2),
                                meter,
                                Map.of("h", Boundary.BEFORE),
                                (target, occurrence) -> Optional.of("same")));
    }

    @Test
    public void sortedHoldRangeKeepsEndpointOwnershipAndTargetMappingOrder() {
        var input =
                List.of(
                        new Hold("end", 12, .25, Set.of("end")),
                        new Hold("inside", 5, .25, Set.of("inside")),
                        new Hold("start", -0.0, .25, Set.of("start")),
                        new Hold("loopEnd", 8, .25, Set.of("loopEnd")),
                        new Hold("tail", 10, .25, Set.of("tail")),
                        new Hold("loopStart", 4, .25, Set.of("loopStart")));
        var source = new ScorePerformanceTimeline(120, List.of(), input);
        assertEquals(
                Double.doubleToRawLongBits(-0.0),
                Double.doubleToRawLongBits(source.holds().get(0).beat()));
        var mapped = new ArrayList<String>();
        var result =
                ScoreNavigationPerformance.project(
                        source,
                        repeat(3, 1, 2),
                        meter,
                        Map.of(
                                "start",
                                Boundary.AFTER,
                                "loopStart",
                                Boundary.BEFORE,
                                "inside",
                                Boundary.AFTER,
                                "loopEnd",
                                Boundary.BEFORE,
                                "tail",
                                Boundary.AFTER,
                                "end",
                                Boundary.BEFORE),
                        (target, occurrence) -> {
                            mapped.add(target);
                            return Optional.of(target + "@" + occurrence.occurrenceId());
                        });
        var expected =
                List.of(
                        "start",
                        "loopStart",
                        "inside",
                        "loopEnd",
                        "inside",
                        "loopEnd",
                        "tail",
                        "end");
        assertEquals(expected, mapped);
        assertEquals(
                expected,
                result.holdOccurrences().stream()
                        .map(ScoreNavigationPerformance.HoldOccurrence::sourceHoldId)
                        .toList());
        assertEquals(
                List.of(0.0, 4.0, 5.0, 8.0, 9.0, 12.0, 14.0, 16.0),
                result.timeline().holds().stream().map(Hold::beat).toList());
        assertEquals(16, result.performedBeats(), 0);
        assertEquals(10, result.durationSeconds(), 0);
        assertEquals(
                List.of("end", "inside", "start", "loopEnd", "tail", "loopStart"),
                input.stream().map(Hold::occurrenceId).toList());
    }
}

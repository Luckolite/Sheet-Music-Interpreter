// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Shared beat/time mapping. Printed semantics must be resolved before constructing this timeline. */
public final class ScorePerformanceTimeline {
    public enum Boundary {
        BEFORE,
        AFTER
    }

    public record TempoSegment(double startBeat, double endBeat, double startBpm, double endBpm) {
        public TempoSegment {
            beat(startBeat);
            beat(endBeat);
            bpm(startBpm);
            bpm(endBpm);
            if (endBeat <= startBeat) throw new IllegalArgumentException("Empty tempo segment");
        }
    }

    public record Hold(
            String occurrenceId, double beat, double seconds, Set<String> sustainedTargets) {
        public Hold {
            Objects.requireNonNull(occurrenceId);
            ScorePerformanceTimeline.beat(beat);
            if (occurrenceId.isBlank() || !Double.isFinite(seconds) || seconds <= 0)
                throw new IllegalArgumentException("Invalid performance hold");
            sustainedTargets = Set.copyOf(sustainedTargets);
            if (sustainedTargets.stream().anyMatch(target -> target.isBlank()))
                throw new IllegalArgumentException("Empty sustained target");
        }
    }

    public record Position(double beat, Optional<String> holdId, double holdProgress) {
        public Position {
            ScorePerformanceTimeline.beat(beat);
            Objects.requireNonNull(holdId);
            if (!Double.isFinite(holdProgress)
                    || holdProgress < 0
                    || holdProgress >= 1
                    || !holdId.isPresent() && holdProgress != 0)
                throw new IllegalArgumentException("Invalid hold position");
        }
    }

    private record TimedHold(Hold hold, double activeStart, double start, double end) {}

    private final double openingBpm;
    private final List<TempoSegment> segments;
    private final double[] segmentSpans;
    private final double[] segmentSlopes;
    private final List<Hold> holds;
    private final List<TimedHold> timedHolds;

    public ScorePerformanceTimeline(
            double openingBpm, List<TempoSegment> segments, List<Hold> holds) {
        bpm(openingBpm);
        this.openingBpm = openingBpm;
        this.segments = List.copyOf(segments);
        double end = 0;
        for (var segment : this.segments) {
            if (segment.startBeat() != end)
                throw new IllegalArgumentException("Tempo segments must be contiguous from zero");
            end = segment.endBeat();
        }
        // Source curves are immutable; reuse full integrals without regrouping elapsed sums.
        this.segmentSpans = new double[this.segments.size()];
        this.segmentSlopes = new double[this.segments.size()];
        for (int index = 0; index < this.segments.size(); index++) {
            var segment = this.segments.get(index);
            double slope = slope(segment);
            this.segmentSlopes[index] = slope;
            this.segmentSpans[index] =
                    integral(segment, segment.endBeat() - segment.startBeat(), slope);
        }
        // Multiple depictions of one occurrence union their sustain targets, never their delay.
        var unique = new LinkedHashMap<String, Hold>();
        for (var hold : holds) {
            var old = unique.get(hold.occurrenceId());
            if (old != null) {
                if (old.beat() != hold.beat() || old.seconds() != hold.seconds())
                    throw new IllegalArgumentException("Conflicting hold realization");
                var targets = new HashSet<>(old.sustainedTargets());
                targets.addAll(hold.sustainedTargets());
                hold = new Hold(hold.occurrenceId(), hold.beat(), hold.seconds(), targets);
            }
            unique.put(hold.occurrenceId(), hold);
        }
        var ordered = new ArrayList<>(unique.values());
        ordered.sort(Comparator.comparingDouble(Hold::beat));
        var timed = new ArrayList<TimedHold>();
        double delay = 0, previous = -1;
        for (var hold : ordered) {
            if (hold.beat() == previous)
                throw new IllegalArgumentException(
                        "Distinct holds at one beat need explicit reconciliation or ordering");
            double activeStart = activeSecondsAtBeat(hold.beat()), start = activeStart + delay;
            double finish = start + hold.seconds();
            if (!Double.isFinite(finish)
                    || finish <= start
                    || !timed.isEmpty() && start < timed.get(timed.size() - 1).end())
                throw new IllegalArgumentException(
                        "Hold cannot be represented at this time coordinate");
            timed.add(new TimedHold(hold, activeStart, start, finish));
            delay += hold.seconds();
            previous = hold.beat();
        }
        this.holds = List.copyOf(ordered);
        this.timedHolds = List.copyOf(timed);
    }

    /** Compatibility path for existing rhythmic numeric-tempo records; no geometric remapping. */
    public static ScorePerformanceTimeline numeric(
            double openingBpm, ScoreMeterMap meter, List<ScoreTempoChange> changes) {
        var ordered = new ArrayList<ScoreTempoChange>();
        if (changes != null) for (var change : changes) if (change != null) ordered.add(change);
        ordered.sort(
                Comparator.comparingInt(ScoreTempoChange::measureIndex)
                        .thenComparingDouble(ScoreTempoChange::positionInMeasure));
        var segments = new ArrayList<TempoSegment>();
        double previous = 0, current = openingBpm;
        for (var change : ordered) {
            double next = meter.beatAt(change.measureIndex(), change.positionInMeasure());
            if (next > previous) segments.add(new TempoSegment(previous, next, current, current));
            previous = next;
            current = change.bpm();
        }
        // The finite segment API uses the final endpoint tempo for its unbounded tail.
        // A zero-position mark is authoritative even when there are no preceding segments.
        segments.add(new TempoSegment(previous, previous + 1, current, current));
        return new ScorePerformanceTimeline(openingBpm, segments, List.of());
    }

    public List<Hold> holds() {
        return holds;
    }

    /** Immutable source curves, before navigation creates performed occurrences. */
    public List<TempoSegment> tempoSegments() {
        return segments;
    }

    public double openingBpm() {
        return openingBpm;
    }

    public double secondsAtBeat(double target, Boundary boundary) {
        beat(target);
        Objects.requireNonNull(boundary);
        double delay = 0;
        for (var hold : timedHolds) {
            if (hold.hold().beat() == target)
                return boundary == Boundary.BEFORE ? hold.start() : hold.end();
            if (hold.hold().beat() > target) break;
            delay += hold.hold().seconds();
        }
        return activeSecondsAtBeat(target) + delay;
    }

    /** Wall time without inserted pauses; compatible hairpin progression freezes during a hold. */
    public double activeSecondsAtSeconds(double seconds) {
        time(seconds);
        double delay = 0;
        for (var hold : timedHolds) {
            if (seconds < hold.start()) break;
            if (seconds <= hold.end()) return hold.activeStart();
            delay += hold.hold().seconds();
        }
        return seconds - delay;
    }

    public Position positionAtSeconds(double seconds) {
        time(seconds);
        double delay = 0;
        for (var hold : timedHolds) {
            if (seconds < hold.start()) break;
            if (seconds < hold.end())
                return new Position(
                        hold.hold().beat(),
                        Optional.of(hold.hold().occurrenceId()),
                        Math.min(
                                Math.nextDown(1.0),
                                (seconds - hold.start()) / (hold.end() - hold.start())));
            if (seconds == hold.end()) return new Position(hold.hold().beat(), Optional.empty(), 0);
            delay += hold.hold().seconds();
        }
        return new Position(beatAtActiveSeconds(seconds - delay), Optional.empty(), 0);
    }

    public double activeSecondsAtBeat(double target) {
        beat(target);
        double seconds = 0, end = 0, current = openingBpm;
        for (int index = 0; index < segments.size(); index++) {
            var segment = segments.get(index);
            double length = Math.min(target, segment.endBeat()) - segment.startBeat();
            if (length <= 0) break;
            seconds +=
                    length == segment.endBeat() - segment.startBeat()
                            ? segmentSpans[index]
                            : integral(segment, length, segmentSlopes[index]);
            end = segment.endBeat();
            current = segment.endBpm();
            if (target <= end) return seconds;
        }
        return seconds + Math.max(0, target - end) * 60 / current;
    }

    private double beatAtActiveSeconds(double seconds) {
        double elapsed = 0, end = 0, current = openingBpm;
        for (int index = 0; index < segments.size(); index++) {
            var segment = segments.get(index);
            double span = segmentSpans[index];
            if (seconds < elapsed + span) {
                double slope = segmentSlopes[index], time = seconds - elapsed;
                double offset =
                        slope == 0
                                ? time * segment.startBpm() / 60
                                : segment.startBpm() * Math.expm1(time * slope / 60) / slope;
                return segment.startBeat() + offset;
            }
            elapsed += span;
            end = segment.endBeat();
            current = segment.endBpm();
        }
        return end + Math.max(0, seconds - elapsed) * current / 60;
    }

    private static double integral(TempoSegment segment, double length, double slope) {
        return slope == 0
                ? length * 60 / segment.startBpm()
                : 60 * Math.log1p(slope * length / segment.startBpm()) / slope;
    }

    private static double slope(TempoSegment segment) {
        return (segment.endBpm() - segment.startBpm()) / (segment.endBeat() - segment.startBeat());
    }

    private static void beat(double value) {
        time(value);
    }

    private static void time(double value) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Invalid timeline coordinate");
    }

    private static void bpm(double value) {
        if (!Double.isFinite(value) || value < 15 || value > 1600)
            throw new IllegalArgumentException("Invalid quarter BPM");
    }
}

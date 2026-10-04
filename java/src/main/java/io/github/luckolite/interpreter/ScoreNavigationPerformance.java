// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static io.github.luckolite.interpreter.ScorePerformanceTimeline.*;

/** Projects already-resolved source tempo curves and holds through the actual navigation route. */
public final class ScoreNavigationPerformance {
    private ScoreNavigationPerformance() {}

    /** A sustained source target must be proved to belong to this performed occurrence. */
    @FunctionalInterface
    public interface TargetMapper {
        Optional<String> map(String sourceTarget, ScoreNavigationTraversal.Occurrence occurrence);
    }

    public record HoldOccurrence(
            String sourceHoldId,
            String performanceHoldId,
            String navigationOccurrenceId,
            Boundary ownership,
            double sourceBeat,
            double performanceBeat) {}

    public record SourcePosition(
            String navigationOccurrenceId,
            ScoreAnchor anchor,
            Optional<String> sourceHoldId,
            double holdProgress) {}

    public static final class Result {
        private final ScorePerformanceTimeline timeline;
        private final List<ScoreNavigationTraversal.Occurrence> occurrences;
        private final List<HoldOccurrence> holds;
        private final ScoreMeterMap meter;
        private final double beats;

        private Result(
                ScorePerformanceTimeline timeline,
                List<ScoreNavigationTraversal.Occurrence> occurrences,
                List<HoldOccurrence> holds,
                ScoreMeterMap meter,
                double beats) {
            this.timeline = timeline;
            this.occurrences = List.copyOf(occurrences);
            this.holds = List.copyOf(holds);
            this.meter = meter;
            this.beats = beats;
        }

        public ScorePerformanceTimeline timeline() {
            return timeline;
        }

        public List<HoldOccurrence> holdOccurrences() {
            return holds;
        }

        public double performedBeats() {
            return beats;
        }

        public double durationSeconds() {
            return timeline.secondsAtBeat(beats, Boundary.AFTER);
        }

        /** At an ordinary jump boundary, seek selects the arriving occurrence. During a
         * hold it selects that hold's explicit owner, including a departing terminal note. */
        public Optional<SourcePosition> sourcePositionAtSeconds(double seconds) {
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > durationSeconds())
                throw new IllegalArgumentException("Seek outside performance");
            if (occurrences.isEmpty()) return Optional.empty();
            var position = timeline.positionAtSeconds(seconds);
            HoldOccurrence active = null;
            if (position.holdId().isPresent())
                for (var hold : holds)
                    if (hold.performanceHoldId().equals(position.holdId().get())) {
                        active = hold;
                        break;
                    }
            ScoreNavigationTraversal.Occurrence selected = null;
            for (var occurrence : occurrences) {
                if (active != null
                        ? occurrence.occurrenceId().equals(active.navigationOccurrenceId())
                        : position.beat() >= occurrence.performanceStartBeat()
                                && position.beat() < occurrence.performanceEndBeat()) {
                    selected = occurrence;
                    break;
                }
            }
            if (selected == null) selected = occurrences.get(occurrences.size() - 1);
            double sourceBeat =
                    active != null
                            ? active.sourceBeat()
                            : selected.start().absoluteBeat(meter)
                                    + position.beat()
                                    - selected.performanceStartBeat();
            var anchor =
                    sourceBeat >= selected.end().absoluteBeat(meter)
                            ? selected.end()
                            : new ScoreAnchor(
                                    selected.start().measureIndex(),
                                    Math.max(
                                            0,
                                            sourceBeat
                                                    - meter.startBeat(
                                                            selected.start().measureIndex())));
            return Optional.of(
                    new SourcePosition(
                            selected.occurrenceId(),
                            anchor,
                            active == null ? Optional.empty() : Optional.of(active.sourceHoldId()),
                            position.holdProgress()));
        }
    }

    /** BEFORE owns a hold at a departing segment's end; AFTER owns one at the arriving
     * segment's start. Every source hold needs this explicit choice, even if skipped.
     * Unmapped sustain targets and simultaneous distinct holds are rejected, not guessed. */
    public static Result project(
            ScorePerformanceTimeline source,
            ScoreNavigationPlan plan,
            ScoreMeterMap meter,
            Map<String, Boundary> holdOwnership,
            TargetMapper targets) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(plan);
        Objects.requireNonNull(meter);
        Objects.requireNonNull(targets);
        var ownership = Map.copyOf(holdOwnership);
        if (!plan.traversal().complete())
            throw new IllegalArgumentException("Incomplete navigation route");
        double sourceEnd = meter.startBeat(plan.sourceMeasureCount());
        var knownHolds = new HashSet<String>();
        for (var hold : source.holds()) {
            knownHolds.add(hold.occurrenceId());
            if (!ownership.containsKey(hold.occurrenceId()) || hold.beat() > sourceEnd)
                throw new IllegalArgumentException("Unowned hold or hold outside source score");
        }
        if (!knownHolds.equals(ownership.keySet()))
            throw new IllegalArgumentException("Unknown hold ownership");
        var curves = new ArrayList<TempoSegment>();
        var performedHolds = new ArrayList<Hold>();
        var identities = new ArrayList<HoldOccurrence>();
        double performedEnd = 0;
        var occurrenceIds = new HashSet<String>();
        for (var occurrence : plan.traversal().occurrences()) {
            var start = occurrence.start().canonical(meter, plan.sourceMeasureCount());
            var end = occurrence.end().canonical(meter, plan.sourceMeasureCount());
            double from = start.absoluteBeat(meter), to = end.absoluteBeat(meter);
            if (!occurrenceIds.add(occurrence.occurrenceId())
                    || to <= from
                    || occurrence.performanceStartBeat() != performedEnd
                    || Math.abs((to - from) - (occurrence.performanceEndBeat() - performedEnd))
                            > 1e-8)
                throw new IllegalArgumentException("Navigation route and source meter disagree");
            appendCurves(source, from, to, performedEnd, occurrence.performanceEndBeat(), curves);
            // The source timeline owns finite holds in immutable ascending beat order.
            var sourceHolds = source.holds();
            for (int h = firstHoldAtOrAfter(sourceHolds, from); h < sourceHolds.size(); h++) {
                var hold = sourceHolds.get(h);
                if (hold.beat() > to) break;
                var side = ownership.get(hold.occurrenceId());
                boolean owns =
                        side == Boundary.BEFORE
                                ? hold.beat() > from && hold.beat() <= to
                                : hold.beat() >= from && hold.beat() < to;
                if (!owns) continue;
                var mapped = new HashSet<String>();
                for (String target : hold.sustainedTargets()) {
                    String value =
                            Objects.requireNonNull(targets.map(target, occurrence))
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Unresolved performed sustain target: "
                                                                    + target));
                    if (value.isBlank())
                        throw new IllegalArgumentException("Empty performed sustain target");
                    if (!mapped.add(value))
                        throw new IllegalArgumentException("Distinct sustain targets alias");
                }
                // Length-prefix both identities, avoiding delimiter collisions in user/source IDs.
                String id =
                        hold.occurrenceId().length()
                                + ":"
                                + hold.occurrenceId()
                                + occurrence.occurrenceId().length()
                                + ":"
                                + occurrence.occurrenceId();
                double at =
                        hold.beat() == to
                                ? occurrence.performanceEndBeat()
                                : performedEnd + hold.beat() - from;
                performedHolds.add(new Hold(id, at, hold.seconds(), mapped));
                identities.add(
                        new HoldOccurrence(
                                hold.occurrenceId(),
                                id,
                                occurrence.occurrenceId(),
                                side,
                                hold.beat(),
                                at));
            }
            performedEnd = occurrence.performanceEndBeat();
        }
        double opening = curves.isEmpty() ? source.openingBpm() : curves.get(0).startBpm();
        return new Result(
                new ScorePerformanceTimeline(opening, curves, performedHolds),
                plan.traversal().occurrences(),
                identities,
                meter,
                performedEnd);
    }

    public static Result project(
            ScorePerformanceTimeline source, ScoreNavigationPlan plan, ScoreMeterMap meter) {
        return project(source, plan, meter, Map.of(), (target, occurrence) -> Optional.empty());
    }

    private static int firstHoldAtOrAfter(List<Hold> holds, double beat) {
        int low = 0, high = holds.size();
        while (low < high) {
            int middle = low + ((high - low) >>> 1);
            if (holds.get(middle).beat() < beat) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static void appendCurves(
            ScorePerformanceTimeline source,
            double from,
            double to,
            double outputStart,
            double outputEnd,
            List<TempoSegment> output) {
        double cursor = from, current = source.openingBpm(), written = outputStart;
        for (var segment : source.tempoSegments()) {
            if (segment.endBeat() <= cursor) {
                current = segment.endBpm();
                continue;
            }
            if (cursor >= to) break;
            double end = Math.min(to, segment.endBeat());
            double fraction =
                    (cursor - segment.startBeat()) / (segment.endBeat() - segment.startBeat());
            double endFraction =
                    (end - segment.startBeat()) / (segment.endBeat() - segment.startBeat());
            double a = segment.startBpm() + fraction * (segment.endBpm() - segment.startBpm());
            double b = segment.startBpm() + endFraction * (segment.endBpm() - segment.startBpm());
            double next = end == to ? outputEnd : outputStart + end - from;
            output.add(new TempoSegment(written, next, a, b));
            written = next;
            cursor = end;
            current = b;
        }
        if (cursor < to) output.add(new TempoSegment(written, outputEnd, current, current));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Routes resolved source gain curves, never interpreting printed text or page geometry. */
public final class ScoreGainProjection {
    private ScoreGainProjection() {}

    /** Curves are in authoritative source order; later starts override earlier curves. */
    public record Curve(
            String id, int lane, double startBeat, double endBeat, double fromDb, double toDb) {
        public Curve {
            Objects.requireNonNull(id);
            if (id.isBlank()
                    || lane / 16 < 1
                    || lane / 16 > 8
                    || lane % 16 >= lane / 16
                    || !Double.isFinite(startBeat)
                    || !Double.isFinite(endBeat)
                    || startBeat < 0
                    || endBeat <= startBeat
                    || !Double.isFinite(fromDb)
                    || !Double.isFinite(toDb)
                    || fromDb < -24
                    || fromDb > 9
                    || toDb < -24
                    || toDb > 9)
                throw new IllegalArgumentException("Invalid resolved source gain curve");
        }

        double db(double beat, ScorePerformanceTimeline clock) {
            if (beat >= endBeat) return toDb;
            double start = clock.activeSecondsAtBeat(startBeat),
                    end = clock.activeSecondsAtBeat(endBeat);
            double fraction = (clock.activeSecondsAtBeat(beat) - start) / (end - start);
            return fromDb + (toDb - fromDb) * Math.max(0, Math.min(1, fraction));
        }

        private double db(double beat, ScorePerformanceTimeline clock, ClockRange range) {
            if (beat >= endBeat) return toDb;
            double fraction =
                    (clock.activeSecondsAtBeat(beat) - range.start())
                            / (range.end() - range.start());
            return fromDb + (toDb - fromDb) * Math.max(0, Math.min(1, fraction));
        }
    }

    private record ClockRange(double start, double end) {}

    public record Piece(
            String sourceCurveId,
            String navigationRunId,
            int lane,
            double startBeat,
            double endBeat,
            double sourceStartBeat,
            double sourceEndBeat,
            double fromDb,
            double toDb) {}

    public record Result(List<Piece> pieces, double performedBeats) {
        public Result {
            pieces = List.copyOf(pieces);
        }
    }

    private static final class Run {
        final String id;
        final double from, performanceStart;
        double to;

        Run(String id, double from, double to, double performanceStart) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.performanceStart = performanceStart;
        }
    }

    public static Result project(
            List<Curve> curves,
            ScoreNavigationPlan plan,
            ScoreMeterMap meter,
            ScorePerformanceTimeline sourceClock) {
        Objects.requireNonNull(sourceClock);
        if (!plan.traversal().complete())
            throw new IllegalArgumentException("Incomplete gain route");
        var lanes = new TreeMap<Integer, List<Curve>>();
        var ids = new HashSet<String>();
        var clockRanges = new HashMap<String, ClockRange>();
        double sourceEnd = meter.startBeat(plan.sourceMeasureCount());
        for (var curve : curves) {
            if (!ids.add(curve.id()) || curve.startBeat() > sourceEnd)
                throw new IllegalArgumentException("Duplicate or out-of-score gain curve");
            double activeStart = sourceClock.activeSecondsAtBeat(curve.startBeat()),
                    activeEnd = sourceClock.activeSecondsAtBeat(curve.endBeat());
            if (!Double.isFinite(activeStart)
                    || !Double.isFinite(activeEnd)
                    || activeEnd <= activeStart)
                throw new IllegalArgumentException("Gain curve loses source clock precision");
            clockRanges.put(curve.id(), new ClockRange(activeStart, activeEnd));
            lanes.computeIfAbsent(curve.lane(), ignored -> new ArrayList<>()).add(curve);
        }
        for (var lane : lanes.values()) lane.sort(Comparator.comparingDouble(Curve::startBeat));
        var runs = new ArrayList<Run>();
        double performedEnd = 0;
        var occurrenceIds = new HashSet<String>();
        for (var occurrence : plan.traversal().occurrences()) {
            double from =
                    occurrence
                            .start()
                            .canonical(meter, plan.sourceMeasureCount())
                            .absoluteBeat(meter);
            double to =
                    occurrence
                            .end()
                            .canonical(meter, plan.sourceMeasureCount())
                            .absoluteBeat(meter);
            if (!occurrenceIds.add(occurrence.occurrenceId())
                    || to <= from
                    || occurrence.performanceStartBeat() != performedEnd
                    || Math.abs(to - from - (occurrence.performanceEndBeat() - performedEnd))
                            > 1e-8)
                throw new IllegalArgumentException("Gain route and meter disagree");
            if (!runs.isEmpty() && runs.get(runs.size() - 1).to == from)
                runs.get(runs.size() - 1).to = to;
            else runs.add(new Run(occurrence.occurrenceId(), from, to, performedEnd));
            performedEnd = occurrence.performanceEndBeat();
        }
        var result = new ArrayList<Piece>();
        for (var run : runs)
            for (var entry : lanes.entrySet()) {
                var boundaries = new TreeSet<Double>();
                boundaries.add(run.from);
                boundaries.add(run.to);
                for (var curve : entry.getValue()) {
                    if (curve.startBeat() > run.from && curve.startBeat() < run.to)
                        boundaries.add(curve.startBeat());
                    if (curve.endBeat() > run.from && curve.endBeat() < run.to)
                        boundaries.add(curve.endBeat());
                }
                var points = new ArrayList<>(boundaries);
                int cursor = -1;
                for (int i = 0; i + 1 < points.size(); i++) {
                    double from = points.get(i), to = points.get(i + 1);
                    while (cursor + 1 < entry.getValue().size()
                            && entry.getValue().get(cursor + 1).startBeat() <= from) cursor++;
                    Curve active = cursor < 0 ? null : entry.getValue().get(cursor);
                    var range = active == null ? null : clockRanges.get(active.id());
                    result.add(
                            new Piece(
                                    active == null ? "" : active.id(),
                                    run.id,
                                    entry.getKey(),
                                    run.performanceStart + from - run.from,
                                    run.performanceStart + to - run.from,
                                    from,
                                    to,
                                    active == null ? 0 : active.db(from, sourceClock, range),
                                    active == null ? 0 : active.db(to, sourceClock, range)));
                }
            }
        result.sort(Comparator.comparingDouble(Piece::startBeat).thenComparingInt(Piece::lane));
        return new Result(result, performedEnd);
    }
}

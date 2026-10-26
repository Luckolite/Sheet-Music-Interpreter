// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Projects resolved sounding intervals, without re-inferring rhythm from projected page geometry.
 * Pitches, ornaments and source ties must already be resolved by the caller. */
public final class ScoreNavigationNoteProjection {
    private ScoreNavigationNoteProjection() {}

    /** A jump into the middle of a sounding interval needs an explicit musical decision. */
    public enum EntryPolicy {
        REJECT,
        OMIT,
        REATTACK
    }

    /** sourceIndex references the caller's source-resolved pitch/voice, never a recalculated
     * destination key signature. A resolved source tie is one interval, with one attack. */
    public record SourceNote(String sourceId, int sourceIndex, double startBeat, double endBeat) {
        public SourceNote {
            Objects.requireNonNull(sourceId);
            if (sourceId.isBlank()
                    || sourceIndex < 0
                    || !Double.isFinite(startBeat)
                    || !Double.isFinite(endBeat)
                    || startBeat < 0
                    || endBeat <= startBeat)
                throw new IllegalArgumentException("Invalid resolved source note");
        }
    }

    public record PerformedNote(
            String performanceId,
            String sourceId,
            int sourceIndex,
            double startBeat,
            double endBeat,
            double sourceStartBeat,
            double sourceEndBeat,
            boolean clippedEntry,
            boolean clippedRelease,
            List<String> navigationOccurrences) {
        public PerformedNote {
            navigationOccurrences = List.copyOf(navigationOccurrences);
        }
    }

    public record Result(List<PerformedNote> notes, double performedBeats) {
        public Result {
            notes = List.copyOf(notes);
        }

        /** The numeric clock uses exactly these performed note identities for held targets. */
        public ScoreNavigationPerformance.TargetMapper targetMapper() {
            var mapping = new HashMap<String, Map<String, String>>();
            for (var note : notes)
                for (String occurrence : note.navigationOccurrences()) {
                    var bySource = mapping.computeIfAbsent(occurrence, ignored -> new HashMap<>());
                    if (bySource.put(note.sourceId(), note.performanceId()) != null)
                        throw new IllegalArgumentException("Ambiguous performed source target");
                }
            return (source, occurrence) ->
                    Optional.ofNullable(
                            mapping.getOrDefault(occurrence.occurrenceId(), Map.of()).get(source));
        }
    }

    private static final class Run {
        final double from, performedStart;
        double to;
        final List<ScoreNavigationTraversal.Occurrence> occurrences = new ArrayList<>();

        Run(
                double from,
                double to,
                double performedStart,
                ScoreNavigationTraversal.Occurrence occurrence) {
            this.from = from;
            this.to = to;
            this.performedStart = performedStart;
            occurrences.add(occurrence);
        }

        double from() {
            return from;
        }

        double to() {
            return to;
        }

        double performedStart() {
            return performedStart;
        }

        List<ScoreNavigationTraversal.Occurrence> occurrences() {
            return occurrences;
        }
    }

    public static Result project(
            List<SourceNote> source,
            ScoreNavigationPlan plan,
            ScoreMeterMap meter,
            EntryPolicy entryPolicy) {
        Objects.requireNonNull(entryPolicy);
        source = List.copyOf(source);
        plan.traversal().requirePerformanceQualified();
        double sourceEnd = meter.startBeat(plan.sourceMeasureCount());
        var identities = new HashSet<String>();
        var indices = new HashSet<Integer>();
        for (var note : source)
            if (!identities.add(note.sourceId())
                    || !indices.add(note.sourceIndex())
                    || note.endBeat() > sourceEnd)
                throw new IllegalArgumentException(
                        "Duplicate note identity or note outside source");

        // Ordinary barlines do not split/re-attack a resolved sounding event. Only source
        // discontinuities start another run, so a terminal Fine clips just the last interval.
        var runs = new ArrayList<Run>();
        var occurrenceIds = new HashSet<String>();
        double performedEnd = 0;
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
                throw new IllegalArgumentException("Route and source meter disagree");
            if (!runs.isEmpty() && runs.get(runs.size() - 1).to() == from) {
                var old = runs.get(runs.size() - 1);
                old.to = to;
                old.occurrences.add(occurrence);
            } else runs.add(new Run(from, to, performedEnd, occurrence));
            performedEnd = occurrence.performanceEndBeat();
        }

        var notes = new ArrayList<PerformedNote>();
        for (var run : runs)
            for (var note : source) {
                double from = Math.max(run.from(), note.startBeat()),
                        to = Math.min(run.to(), note.endBeat());
                if (to <= from) continue;
                boolean entered = note.startBeat() < run.from();
                if (entered && entryPolicy == EntryPolicy.REJECT)
                    throw new IllegalArgumentException(
                            "Jump enters sounding note: " + note.sourceId());
                if (entered && entryPolicy == EntryPolicy.OMIT) continue;
                var owners = new ArrayList<String>();
                // Find the first intersected bar in logarithmic time. Scanning every bar
                // for every note makes a long linear score unnecessarily quadratic.
                int low = 0, high = run.occurrences().size();
                while (low < high) {
                    int middle = (low + high) >>> 1;
                    if (run.occurrences().get(middle).end().absoluteBeat(meter) <= from)
                        low = middle + 1;
                    else high = middle;
                }
                for (int i = low; i < run.occurrences().size(); i++) {
                    var occurrence = run.occurrences().get(i);
                    if (occurrence.start().absoluteBeat(meter) >= to) break;
                    owners.add(occurrence.occurrenceId());
                }
                String first = owners.get(0);
                String id =
                        note.sourceId().length()
                                + ":"
                                + note.sourceId()
                                + first.length()
                                + ":"
                                + first;
                notes.add(
                        new PerformedNote(
                                id,
                                note.sourceId(),
                                note.sourceIndex(),
                                run.performedStart() + from - run.from(),
                                run.performedStart() + to - run.from(),
                                from,
                                to,
                                entered,
                                note.endBeat() > run.to(),
                                owners));
            }
        notes.sort(
                Comparator.comparingDouble(PerformedNote::startBeat)
                        .thenComparingInt(PerformedNote::sourceIndex));
        return new Result(notes, performedEnd);
    }
}

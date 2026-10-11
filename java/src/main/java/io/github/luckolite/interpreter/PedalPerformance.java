// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Finite pedal release gates. Callers apply these only to a piano voice, after note/tie realization. */
public final class PedalPerformance {
    public static final String POLICY = "piano-pedal-preview-v1";

    private PedalPerformance() {}

    public record Span(
            String sourceId, double startBeat, double endBeat, int staffIndex, int staffCount) {
        public Span {
            if (sourceId == null
                    || sourceId.isBlank()
                    || !Double.isFinite(startBeat)
                    || startBeat < 0
                    || !Double.isFinite(endBeat)
                    || endBeat <= startBeat
                    || staffIndex < 0
                    || staffCount <= staffIndex)
                throw new IllegalArgumentException("Invalid finite pedal span");
        }
    }

    public record Control(
            double beat, int value, int staffIndex, int staffCount, String sourceId) {}

    public record Result(List<Span> spans, List<String> diagnostics) {
        public Result {
            spans = List.copyOf(spans);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /** Only a complete, resolved pair realizes sound; contradictory pedal records stay diagnostic. */
    public static Result resolve(
            List<ScoreExpressiveEvent> events, ScoreMeterMap meter, int measures) {
        Objects.requireNonNull(events);
        Objects.requireNonNull(meter);
        if (measures < 0) throw new IllegalArgumentException("Negative score extent");
        var valid = new ArrayList<ScoreExpressiveEvent>();
        var diagnostics = new ArrayList<String>();
        var unique = new HashSet<List<Object>>();
        for (var event : events) {
            if (event.kind() != ScoreExpressiveEvent.Kind.PEDAL_DOWN
                    && event.kind() != ScoreExpressiveEvent.Kind.PEDAL_UP) continue;
            try {
                if (event.scope() != ScoreExpressiveEvent.Scope.PART || event.start().isEmpty())
                    throw new IllegalArgumentException("Unresolved piano part ownership");
                var start = event.start().get().canonical(meter, measures);
                event.end().ifPresent(end -> end.canonical(meter, measures));
                if (event.kind() == ScoreExpressiveEvent.Kind.PEDAL_DOWN
                        && start.measureIndex() == measures)
                    throw new IllegalArgumentException("Pedal down at score end");
                if (unique.add(List.of(event.kind(), start, event.end(), event.staffCount())))
                    valid.add(event);
            } catch (IllegalArgumentException invalid) {
                diagnostics.add(event.eventId() + ": " + invalid.getMessage());
            }
        }
        valid.sort(
                Comparator.comparing((ScoreExpressiveEvent e) -> e.start().orElseThrow())
                        .thenComparingInt(
                                e -> e.kind() == ScoreExpressiveEvent.Kind.PEDAL_UP ? 0 : 1));
        var held = new HashMap<Integer, ScoreExpressiveEvent>();
        var conflicts = new HashSet<Integer>();
        var spans = new ArrayList<Span>();
        for (var event : valid) {
            int part = event.staffCount();
            if (event.kind() == ScoreExpressiveEvent.Kind.PEDAL_DOWN) {
                if (conflicts.contains(part)) continue;
                var old = held.get(part);
                if (old != null
                        && old.start()
                                .orElseThrow()
                                .canonical(meter, measures)
                                .equals(event.start().orElseThrow().canonical(meter, measures))
                        && (old.end().isEmpty() || event.end().isEmpty())) {
                    // A literal cue can corroborate a bracket at the same written onset.
                    // Keep its finite endpoint, but still require a separate release event.
                    if (old.end().isEmpty() && event.end().isPresent()) held.put(part, event);
                    continue;
                }
                held.put(part, event);
                if (old != null) {
                    held.remove(part);
                    conflicts.add(part);
                    diagnostics.add(old.eventId() + ": repeated pedal down without release");
                }
                continue;
            }
            if (conflicts.remove(part)) continue;
            var down = held.remove(part);
            if (down == null) {
                diagnostics.add(event.eventId() + ": pedal release without down");
                continue;
            }
            var from = down.start().orElseThrow().canonical(meter, measures);
            var to = event.start().orElseThrow().canonical(meter, measures);
            if (from.compareTo(to) >= 0
                    || down.end().isPresent()
                            && !down.end().get().canonical(meter, measures).equals(to)) {
                diagnostics.add(down.eventId() + ": contradictory pedal release");
                continue;
            }
            spans.add(
                    new Span(
                            down.eventId(),
                            from.absoluteBeat(meter),
                            to.absoluteBeat(meter),
                            down.staffIndex(),
                            part));
        }
        for (var down : held.values()) diagnostics.add(down.eventId() + ": missing pedal release");
        spans.sort(Comparator.comparingDouble(Span::startBeat).thenComparingInt(Span::staffCount));
        return new Result(spans, diagnostics);
    }

    /** Navigation closes outgoing resonance and restores the target's printed pedal state. */
    public static List<Span> project(
            List<Span> source, ScoreNavigationPlan plan, ScoreMeterMap meter) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(plan);
        Objects.requireNonNull(meter);
        if (!plan.traversal().complete())
            throw new IllegalArgumentException("Incomplete navigation traversal");
        var result = new ArrayList<Span>();
        var occurrences = plan.traversal().occurrences();
        double extent = meter.startBeat(plan.sourceMeasureCount());
        for (var span : source) {
            if (span.endBeat() > extent)
                throw new IllegalArgumentException("Pedal beyond source extent");
            Span previous = null;
            ScoreNavigationTraversal.Occurrence prior = null;
            for (var occurrence : occurrences) {
                double from = occurrence.start().absoluteBeat(meter),
                        to = occurrence.end().absoluteBeat(meter);
                double left = Math.max(from, span.startBeat()),
                        right = Math.min(to, span.endBeat());
                if (right <= left) {
                    previous = null;
                    prior = null;
                    continue;
                }
                double begin = occurrence.performanceStartBeat() + left - from;
                double end = occurrence.performanceStartBeat() + right - from;
                if (end > occurrence.performanceEndBeat() + .000001 || end <= begin)
                    throw new IllegalArgumentException("Invalid navigation pedal clock");
                if (previous != null
                        && prior.end().equals(occurrence.start())
                        && previous.endBeat() == begin
                        && prior.performanceEndBeat() == occurrence.performanceStartBeat()) {
                    var joined =
                            new Span(
                                    previous.sourceId(),
                                    previous.startBeat(),
                                    end,
                                    span.staffIndex(),
                                    span.staffCount());
                    result.set(result.size() - 1, joined);
                    previous = joined;
                } else {
                    previous =
                            new Span(
                                    span.sourceId() + "@" + occurrence.occurrenceId(),
                                    begin,
                                    end,
                                    span.staffIndex(),
                                    span.staffCount());
                    result.add(previous);
                }
                prior = occurrence;
            }
        }
        result.sort(Comparator.comparingDouble(Span::startBeat).thenComparingInt(Span::staffCount));
        return List.copyOf(result);
    }

    /** No onset/clock changes. A key held beyond pedal-up keeps its ordinary release. */
    public static double releaseBeat(double writtenEnd, int staffCount, List<Span> spans) {
        if (!Double.isFinite(writtenEnd) || writtenEnd < 0 || staffCount < 1)
            throw new IllegalArgumentException("Invalid piano release");
        double end = writtenEnd;
        for (var span : spans)
            if (span.staffCount() == staffCount
                    && writtenEnd > span.startBeat()
                    && writtenEnd < span.endBeat()) end = Math.max(end, span.endBeat());
        return end;
    }

    /** Preserve release/redepress at a shared beat; do not union touching spans. */
    public static List<Control> controls(List<Span> spans) {
        var result = new ArrayList<Control>();
        for (var span : spans) {
            result.add(
                    new Control(
                            span.startBeat(),
                            127,
                            span.staffIndex(),
                            span.staffCount(),
                            span.sourceId()));
            result.add(
                    new Control(
                            span.endBeat(),
                            0,
                            span.staffIndex(),
                            span.staffCount(),
                            span.sourceId()));
        }
        result.sort(Comparator.comparingDouble(Control::beat).thenComparingInt(Control::value));
        return List.copyOf(result);
    }
}

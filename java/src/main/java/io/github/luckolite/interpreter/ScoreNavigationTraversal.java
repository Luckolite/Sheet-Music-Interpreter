// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static io.github.luckolite.interpreter.ScorePlaybackDirection.Kind.*;

/** Bounded traversal of immutable source segments with region-owned pass counters. */
public final class ScoreNavigationTraversal {
    public enum Phase {
        INITIAL,
        RETURN,
        FINE_ARMED,
        CODA_ARMED,
        CODA
    }

    public record Diagnostic(String eventId, String code, String message) {}

    public record Occurrence(
            String occurrenceId,
            ScoreAnchor start,
            ScoreAnchor end,
            double performanceStartBeat,
            double performanceEndBeat,
            Map<String, Integer> repeatPasses,
            Phase phase,
            String enteringEdgeId) {
        public Occurrence {
            repeatPasses = Map.copyOf(repeatPasses);
        }
    }

    public record Result(
            List<Occurrence> occurrences, List<Diagnostic> diagnostics, boolean complete) {
        public Result {
            occurrences = List.copyOf(occurrences);
            diagnostics = List.copyOf(diagnostics);
        }

        /** Termination alone can retain a diagnostic partial route. Execution needs this stricter contract. */
        public boolean performanceQualified() {
            return complete && diagnostics.isEmpty();
        }

        /** No diagnostic emitted by the current engine certifies the full intended performance. */
        public void requirePerformanceQualified() {
            if (!performanceQualified())
                throw new IllegalArgumentException(
                        "Navigation performance is unresolved; inspect traversal diagnostics");
        }

        public List<Integer> sourceMeasures() {
            return occurrences.stream().map(o -> o.start().measureIndex()).toList();
        }

        public double performedBeats() {
            return occurrences.isEmpty()
                    ? 0
                    : occurrences.get(occurrences.size() - 1).performanceEndBeat();
        }
    }

    private record Mark(ScorePlaybackDirection direction, ScoreAnchor anchor, String id) {}

    private record Repeat(
            String id,
            ScoreAnchor start,
            ScoreAnchor end,
            int plays,
            List<ScoreAnchor> closingEdges) {}

    private final int measureCount;
    private final ScoreMeterMap meter;
    private final List<Mark> marks = new ArrayList<>();
    private final List<Repeat> repeats = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<String, Integer> passes = new TreeMap<>();
    private final Set<String> executedJumps = new TreeSet<>(), states = new HashSet<>();
    private final List<Occurrence> occurrences = new ArrayList<>();
    private Phase phase = Phase.INITIAL;
    private boolean replayRepeats = true;
    private String codaTarget = "", enteringEdge = "source-start", skippedRepeatEdge = "";
    private double performanceBeat;
    private ScoreAnchor unresolvedCodaStart;

    private ScoreNavigationTraversal(int count, ScoreMeterMap meter) {
        if (count < 0 || count > 100000)
            throw new IllegalArgumentException("Invalid source measure count");
        measureCount = count;
        this.meter = Objects.requireNonNull(meter);
    }

    public static Result traverse(
            int count, ScoreMeterMap meter, List<ScorePlaybackDirection> directions) {
        var traversal = new ScoreNavigationTraversal(count, meter);
        traversal.prepare(directions);
        return traversal.run();
    }

    private void diagnostic(String id, String code, String message) {
        diagnostics.add(new Diagnostic(id, code, message));
    }

    private void prepare(List<ScorePlaybackDirection> directions) {
        if (directions == null) directions = List.of();
        var identities = new HashMap<String, Mark>();
        var conflicting = new HashSet<String>();
        for (var direction : directions) {
            if (direction == null) {
                diagnostic("", "NULL_MARK", "Null navigation record");
                continue;
            }
            String id = direction.details().eventId();
            if (id.isEmpty()) id = "legacy:" + direction.kind().wireId() + ":" + direction.anchor();
            try {
                var anchor = direction.anchor().canonical(meter, measureCount);
                if (direction.details().end().isPresent()
                        && direction
                                        .details()
                                        .end()
                                        .get()
                                        .canonical(meter, measureCount)
                                        .compareTo(anchor)
                                <= 0)
                    throw new IllegalArgumentException(
                            "Canonical navigation span must have positive length");
                var mark = new Mark(direction, anchor, id);
                var previous = identities.putIfAbsent(id, mark);
                if (previous != null) {
                    if (!sameInstruction(previous, mark)) {
                        diagnostic(
                                id,
                                "CONFLICTING_IDENTITY",
                                "One source identity has conflicting instructions");
                        conflicting.add(id);
                    }
                    continue;
                }
                if (marks.size() >= 100000) {
                    diagnostic(id, "MARK_LIMIT", "Too many navigation records");
                    break;
                }
                marks.add(mark);
            } catch (IllegalArgumentException invalid) {
                diagnostic(id, "INVALID_ANCHOR", invalid.getMessage());
            }
        }
        // Never let input order choose one of two conflicting named destinations.
        marks.removeIf(mark -> conflicting.contains(mark.id()));
        var incompatibleReturns = new HashSet<String>();
        var returnAliases = new HashSet<String>();
        for (int i = 0; i < marks.size(); i++)
            if (marks.get(i).direction().kind().jump())
                for (int j = i + 1; j < marks.size(); j++)
                    if (marks.get(j).direction().kind().jump()
                            && marks.get(i).anchor().equals(marks.get(j).anchor())) {
                        var a = marks.get(i);
                        var b = marks.get(j);
                        var x = a.direction().details();
                        var y = b.direction().details();
                        if (sameInstruction(a, b)) {
                            returnAliases.add(a.id().compareTo(b.id()) < 0 ? b.id() : a.id());
                            continue;
                        }
                        boolean exclusive =
                                !x.repeatGroupId().isEmpty()
                                        && x.repeatGroupId().equals(y.repeatGroupId())
                                        && !x.passes().isEmpty()
                                        && !y.passes().isEmpty()
                                        && Collections.disjoint(x.passes(), y.passes());
                        if (!exclusive) {
                            diagnostic(
                                    a.id(),
                                    "CONFLICTING_RETURNS",
                                    "Incompatible return controls share one source anchor");
                            incompatibleReturns.add(a.id());
                            incompatibleReturns.add(b.id());
                        }
                    }
        marks.removeIf(
                mark ->
                        incompatibleReturns.contains(mark.id())
                                || returnAliases.contains(mark.id()));
        // Backward signs must close before a new forward sign on the same boundary.
        marks.sort(
                Comparator.comparing(Mark::anchor)
                        .thenComparingInt(
                                m ->
                                        m.direction().kind() == REPEAT_END
                                                ? -1
                                                : m.direction().kind().wireId())
                        .thenComparing(Mark::id));
        var openings = new ArrayList<Mark>();
        for (var mark : marks) {
            var kind = mark.direction().kind();
            if (kind == REPEAT_START) openings.add(mark);
            if (kind == REPEAT_END) {
                String group = mark.direction().details().repeatGroupId();
                Repeat existing = null;
                for (var region : repeats)
                    if (!group.isEmpty() && region.id().equals(group)) existing = region;
                if (existing != null) {
                    if (existing.plays() != mark.direction().details().totalPlays()) {
                        diagnostic(
                                mark.id(),
                                "CONFLICTING_REPEAT_COUNT",
                                "Alternate closing edges disagree on total plays");
                        continue;
                    }
                    var edges = new ArrayList<>(existing.closingEdges());
                    if (!edges.contains(mark.anchor())) edges.add(mark.anchor());
                    repeats.remove(existing);
                    repeats.add(
                            new Repeat(
                                    existing.id(),
                                    existing.start(),
                                    existing.end().compareTo(mark.anchor()) > 0
                                            ? existing.end()
                                            : mark.anchor(),
                                    existing.plays(),
                                    List.copyOf(edges)));
                    continue;
                }
                Mark start = null;
                for (int i = openings.size() - 1; i >= 0; i--) {
                    var candidate = openings.get(i);
                    if (candidate.anchor().compareTo(mark.anchor()) < 0
                            && (group.isEmpty()
                                    || group.equals(
                                            candidate.direction().details().repeatGroupId()))) {
                        start = openings.remove(i);
                        break;
                    }
                }
                var first = start == null ? new ScoreAnchor(0, 0) : start.anchor();
                if (first.compareTo(mark.anchor()) >= 0) {
                    diagnostic(mark.id(), "EMPTY_REPEAT", "Repeat has no source segment");
                    continue;
                }
                if (group.isEmpty()) group = start == null ? mark.id() : start.id();
                String id = group;
                if (repeats.stream().anyMatch(r -> r.id().equals(id))) {
                    diagnostic(
                            mark.id(),
                            "DUPLICATE_REPEAT_GROUP",
                            "Repeat region identity is reused");
                    continue;
                }
                repeats.add(
                        new Repeat(
                                group,
                                first,
                                mark.anchor(),
                                mark.direction().details().totalPlays(),
                                List.of(mark.anchor())));
            }
            if (kind == MEASURE_REPEAT)
                diagnostic(
                        mark.id(),
                        "UNSUPPORTED_MEASURE_REPEAT",
                        "Percent shorthand requires musical-content expansion");
        }
        for (var start : openings)
            diagnostic(
                    start.id(),
                    "UNCLOSED_REPEAT",
                    "Forward repeat has no matching backward repeat");
        var crossing = new HashSet<String>();
        for (int i = 0; i < repeats.size(); i++)
            for (int j = i + 1; j < repeats.size(); j++) {
                var a = repeats.get(i);
                var b = repeats.get(j);
                if (a.start().compareTo(b.start()) < 0
                                && b.start().compareTo(a.end()) < 0
                                && a.end().compareTo(b.end()) < 0
                        || b.start().compareTo(a.start()) < 0
                                && a.start().compareTo(b.end()) < 0
                                && b.end().compareTo(a.end()) < 0) {
                    diagnostic(a.id(), "CROSSING_REPEATS", "Repeat regions cross rather than nest");
                    crossing.add(a.id());
                    crossing.add(b.id());
                }
            }
        repeats.removeIf(r -> crossing.contains(r.id()));
    }

    private boolean sameInstruction(Mark a, Mark b) {
        var x = a.direction().details();
        var y = b.direction().details();
        return a.anchor().equals(b.anchor())
                && a.direction().kind() == b.direction().kind()
                && x.targetId().equals(y.targetId())
                && x.codaTargetId().equals(y.codaTargetId())
                && x.repeatGroupId().equals(y.repeatGroupId())
                && x.totalPlays() == y.totalPlays()
                && new HashSet<>(x.passes()).equals(new HashSet<>(y.passes()))
                && x.end()
                        .map(e -> e.canonical(meter, measureCount))
                        .equals(y.end().map(e -> e.canonical(meter, measureCount)))
                && x.afterJumpRepeats() == y.afterJumpRepeats();
    }

    private Result run() {
        var cursor = new ScoreAnchor(0, 0);
        var scoreEnd = new ScoreAnchor(measureCount, 0);
        boolean complete = true;
        for (int steps = 0; ; steps++) {
            if (steps >= 400000) {
                diagnostic("", "TRAVERSAL_LIMIT", "Performance exceeds bounded traversal limit");
                complete = false;
                break;
            }
            String state =
                    cursor
                            + "|"
                            + passes
                            + "|"
                            + executedJumps
                            + "|"
                            + phase
                            + "|"
                            + replayRepeats
                            + "|"
                            + codaTarget
                            + "|"
                            + skippedRepeatEdge;
            if (!states.add(state)) {
                diagnostic("", "NAVIGATION_CYCLE", "Repeated complete navigation state");
                complete = false;
                break;
            }
            ScoreAnchor destination = repeatAt(cursor);
            if (destination != null) {
                cursor = destination;
                continue;
            }
            if (phase == Phase.FINE_ARMED && hasKind(cursor, FINE)) break;
            destination = jumpAt(cursor);
            if (destination != null) {
                cursor = destination;
                continue;
            }
            // A missing return destination does not authorize a first-pass coda.
            // Retain the diagnostic and omit only the unambiguously identified section.
            if (unresolvedCodaStart != null && cursor.compareTo(unresolvedCodaStart) >= 0) break;
            if (cursor.compareTo(scoreEnd) >= 0) break;
            for (var repeat : repeats)
                if (repeat.start().equals(cursor))
                    passes.putIfAbsent(repeat.id(), replayRepeats ? 1 : repeat.plays());
            destination = endingAt(cursor);
            if (destination != null) {
                cursor = destination;
                continue;
            }
            var next = new ScoreAnchor(cursor.measureIndex() + 1, 0);
            for (var mark : marks)
                if (mark.anchor().compareTo(cursor) > 0 && mark.anchor().compareTo(next) < 0)
                    next = mark.anchor();
            double duration = next.absoluteBeat(meter) - cursor.absoluteBeat(meter);
            // A sign is a decision point, not necessarily a new visit to the printed bar.
            // Keep real jumps and state changes separate, but merge unchanged contiguous
            // coverage so an unarmed Fine/To Coda does not invent a repeated measure.
            Occurrence previous =
                    occurrences.isEmpty() ? null : occurrences.get(occurrences.size() - 1);
            if (previous != null
                    && enteringEdge.equals("source-continuation")
                    && previous.start().measureIndex() == cursor.measureIndex()
                    && previous.end().equals(cursor)
                    && previous.phase() == phase
                    && previous.repeatPasses().equals(passes)) {
                occurrences.set(
                        occurrences.size() - 1,
                        new Occurrence(
                                previous.occurrenceId(),
                                previous.start(),
                                next,
                                previous.performanceStartBeat(),
                                performanceBeat + duration,
                                passes,
                                phase,
                                previous.enteringEdgeId()));
                performanceBeat += duration;
                cursor = next;
                continue;
            }
            if (occurrences.size() >= 100000) {
                diagnostic("", "TRAVERSAL_LIMIT", "Performance exceeds bounded occurrence limit");
                complete = false;
                break;
            }
            occurrences.add(
                    new Occurrence(
                            "occurrence:" + occurrences.size(),
                            cursor,
                            next,
                            performanceBeat,
                            performanceBeat + duration,
                            passes,
                            phase,
                            enteringEdge));
            performanceBeat += duration;
            enteringEdge = "source-continuation";
            cursor = next;
        }
        return new Result(occurrences, diagnostics, complete);
    }

    private boolean hasKind(ScoreAnchor anchor, ScorePlaybackDirection.Kind kind) {
        return marks.stream()
                .anyMatch(m -> m.anchor().equals(anchor) && m.direction().kind() == kind);
    }

    private ScoreAnchor repeatAt(ScoreAnchor cursor) {
        String skipped = skippedRepeatEdge;
        skippedRepeatEdge = "";
        var endings =
                repeats.stream()
                        .filter(r -> r.closingEdges().contains(cursor) && !r.id().equals(skipped))
                        .sorted(Comparator.comparing(Repeat::start).reversed())
                        .toList();
        for (var repeat : endings) {
            int pass = passes.getOrDefault(repeat.id(), replayRepeats ? 1 : repeat.plays());
            if (replayRepeats && pass < repeat.plays()) {
                passes.put(repeat.id(), pass + 1);
                for (var inner : repeats)
                    if (!inner.id().equals(repeat.id())
                            && inner.start().compareTo(repeat.start()) >= 0
                            && inner.end().compareTo(repeat.end()) <= 0) passes.remove(inner.id());
                enteringEdge = "repeat:" + repeat.id();
                return repeat.start();
            }
            passes.put(repeat.id(), repeat.plays());
        }
        return null;
    }

    private ScoreAnchor endingAt(ScoreAnchor cursor) {
        for (var mark : marks)
            if (mark.anchor().equals(cursor) && mark.direction().kind() == ENDING) {
                String group = mark.direction().details().repeatGroupId();
                var owners =
                        repeats.stream()
                                .filter(
                                        r ->
                                                group.isEmpty()
                                                        ? r.start().compareTo(cursor) <= 0
                                                                && cursor.compareTo(r.end()) <= 0
                                                        : r.id().equals(group))
                                .toList();
                if (owners.size() != 1) {
                    diagnostic(mark.id(), "ENDING_OWNER", "Ending has no unambiguous repeat owner");
                    continue;
                }
                var repeat = owners.get(0);
                int pass = passes.getOrDefault(repeat.id(), replayRepeats ? 1 : repeat.plays());
                if (!mark.direction().details().passes().contains(pass)) {
                    enteringEdge = "skip-ending:" + mark.id();
                    skippedRepeatEdge = repeat.id();
                    return mark.direction()
                            .details()
                            .end()
                            .orElseThrow()
                            .canonical(meter, measureCount);
                }
            }
        return null;
    }

    private Mark target(ScorePlaybackDirection.Kind kind, String identity, String caller) {
        var candidates =
                marks.stream()
                        .filter(
                                m ->
                                        m.direction().kind() == kind
                                                && (identity.isEmpty() || m.id().equals(identity)))
                        .toList();
        if (candidates.size() != 1) {
            diagnostic(
                    caller,
                    "AMBIGUOUS_TARGET",
                    "Expected one " + kind + " destination, found " + candidates.size());
            return null;
        }
        var destination = candidates.get(0);
        if (destination.anchor().measureIndex() == measureCount) {
            diagnostic(caller, "EMPTY_DESTINATION", "Destination contains no music");
            return null;
        }
        return destination;
    }

    private ScoreAnchor jumpAt(ScoreAnchor cursor) {
        for (var mark : marks)
            if (mark.anchor().equals(cursor)) {
                var direction = mark.direction();
                var kind = direction.kind();
                if (kind == TO_CODA && phase == Phase.CODA_ARMED) {
                    String requested = direction.details().targetId();
                    if (!requested.isEmpty()
                            && !codaTarget.isEmpty()
                            && !requested.equals(codaTarget)) continue;
                    var destination =
                            target(CODA, requested.isEmpty() ? codaTarget : requested, mark.id());
                    if (destination != null) {
                        phase = Phase.CODA;
                        enteringEdge = mark.id();
                        return destination.anchor();
                    }
                }
                if (!kind.jump() || executedJumps.contains(mark.id())) continue;
                if (!direction.details().passes().isEmpty()) {
                    String group = direction.details().repeatGroupId();
                    var owner = repeats.stream().filter(r -> r.id().equals(group)).findFirst();
                    if (owner.isEmpty()) {
                        diagnostic(
                                mark.id(),
                                "RETURN_PASS_OWNER",
                                "Conditional return has no repeat owner");
                        executedJumps.add(mark.id());
                        continue;
                    }
                    int pass = passes.getOrDefault(group, replayRepeats ? 1 : owner.get().plays());
                    if (!direction.details().passes().contains(pass)) continue;
                }
                executedJumps.add(mark.id());
                boolean segno =
                        kind == DAL_SEGNO || kind == DAL_SEGNO_AL_FINE || kind == DAL_SEGNO_AL_CODA;
                var destination = new ScoreAnchor(0, 0);
                if (segno) {
                    var found = target(SEGNO, direction.details().targetId(), mark.id());
                    if (found == null) {
                        if (kind == DAL_SEGNO_AL_CODA) {
                            var coda = target(CODA, direction.details().codaTargetId(), mark.id());
                            if (coda != null && coda.anchor().compareTo(cursor) >= 0)
                                unresolvedCodaStart = coda.anchor();
                        }
                        continue;
                    }
                    destination = found.anchor();
                }
                if (destination.compareTo(cursor) >= 0) {
                    diagnostic(
                            mark.id(),
                            "INVALID_RETURN",
                            "DC/DS return must precede its instruction");
                    continue;
                }
                boolean coda = kind == DAL_SEGNO_AL_CODA || kind == DA_CAPO_AL_CODA;
                if (coda) {
                    var found = target(CODA, direction.details().codaTargetId(), mark.id());
                    var start = destination;
                    boolean hasTrigger =
                            marks.stream()
                                    .anyMatch(
                                            m ->
                                                    m.direction().kind() == TO_CODA
                                                            && m.anchor().compareTo(start) > 0
                                                            && m.anchor().compareTo(cursor) <= 0
                                                            && (direction
                                                                            .details()
                                                                            .codaTargetId()
                                                                            .isEmpty()
                                                                    || m.direction()
                                                                            .details()
                                                                            .targetId()
                                                                            .isEmpty()
                                                                    || direction
                                                                            .details()
                                                                            .codaTargetId()
                                                                            .equals(
                                                                                    m.direction()
                                                                                            .details()
                                                                                            .targetId())));
                    if (found == null || !hasTrigger) {
                        diagnostic(
                                mark.id(),
                                "MISSING_CODA_TRIGGER",
                                "Return needs a reachable To Coda instruction");
                        continue;
                    }
                    codaTarget = found.id();
                }
                if (direction.details().afterJumpRepeats()
                        == ScorePlaybackDirection.AfterJumpRepeats.UNKNOWN) {
                    diagnostic(
                            mark.id(),
                            "UNKNOWN_REPEAT_POLICY",
                            "Printed return-repeat policy is unresolved");
                    continue;
                }
                replayRepeats =
                        direction.details().afterJumpRepeats()
                                == ScorePlaybackDirection.AfterJumpRepeats.PLAY;
                passes.clear();
                if (!replayRepeats)
                    for (var repeat : repeats) passes.put(repeat.id(), repeat.plays());
                phase =
                        coda
                                ? Phase.CODA_ARMED
                                : kind == DA_CAPO_AL_FINE || kind == DAL_SEGNO_AL_FINE
                                        ? Phase.FINE_ARMED
                                        : Phase.RETURN;
                enteringEdge = mark.id();
                return destination;
            }
        return null;
    }
}

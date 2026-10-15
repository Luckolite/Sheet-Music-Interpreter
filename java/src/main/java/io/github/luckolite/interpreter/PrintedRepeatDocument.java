// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Joins optical navigation evidence across proved consecutive page numbers. */
final class PrintedRepeatDocument {
    record Result(List<ScorePlaybackDirection> directions, Set<String> resolvedIds) {}

    private record Witness(ScoreExpressiveEvent event, int page, int pageStart, int pageEnd) {}

    /** Keep authored/local navigation intact; admit each recovered repeat only after joint traversal. */
    static Result validated(
            List<ScorePageInterpretation> pages,
            List<ScorePlaybackDirection> existing,
            int count,
            ScoreMeterMap meter) {
        var candidate = assemble(pages);
        var groups = new LinkedHashMap<String, List<ScorePlaybackDirection>>();
        for (var direction : candidate.directions())
            groups.computeIfAbsent(
                            direction.details().repeatGroupId(), ignored -> new ArrayList<>())
                    .add(direction);
        var accepted = new ArrayList<ScorePlaybackDirection>();
        var ids = new HashSet<String>();
        var combined = new ArrayList<>(existing);
        var prior = ScoreNavigationTraversal.traverse(count, meter, combined);
        for (var group : groups.values()) {
            var current = combined;
            if (group.stream()
                    .anyMatch(
                            n ->
                                    current.stream()
                                            .anyMatch(
                                                    old ->
                                                            old.kind() == n.kind()
                                                                    && old.anchor()
                                                                            .equals(n.anchor()))))
                continue;
            var attempt = new ArrayList<>(combined);
            attempt.addAll(group);
            var checked = ScoreNavigationTraversal.traverse(count, meter, attempt);
            if (!checked.complete() || !prior.diagnostics().containsAll(checked.diagnostics()))
                continue;
            accepted.addAll(group);
            combined = attempt;
            prior = checked;
            for (var direction : group)
                for (var proof : direction.details().evidence())
                    for (String id : candidate.resolvedIds())
                        if (proof.sourceId().endsWith(id)) ids.add(id);
        }
        return new Result(List.copyOf(accepted), Set.copyOf(ids));
    }

    static Result assemble(List<ScorePageInterpretation> pages) {
        var directions = new ArrayList<ScorePlaybackDirection>();
        var resolved = new HashSet<String>();
        int offset = 0;
        var group = new ArrayList<Witness>();
        ScorePageInterpretation previous = null;
        for (int page = 0; page < pages.size(); page++) {
            var score = pages.get(page);
            if (previous != null
                    && (previous.measures().isEmpty()
                            || score.measures().isEmpty()
                            || score.firstMeasureNumber()
                                    != previous.firstMeasureNumber()
                                            + previous.measures().size())) {
                flush(group, directions, resolved);
                group.clear();
            }
            for (var event : score.expressiveEvents()) {
                if (event.kind() == ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION
                        && (event.eventId().startsWith("unresolved-printed-repeat:")
                                || event.eventId().startsWith("unresolved-printed-ending:")))
                    group.add(
                            new Witness(
                                    event.offset(offset),
                                    page,
                                    offset,
                                    offset + score.measures().size()));
            }
            offset += score.measures().size();
            previous = score;
        }
        flush(group, directions, resolved);
        return new Result(List.copyOf(directions), Set.copyOf(resolved));
    }

    private static boolean validEnding(Witness w) {
        return w.event().evidence().stream()
                .allMatch(
                        e ->
                                e.sourceId()
                                        .matches(
                                                "printed-ending-span:v1:(true|false):(true|false):unresolved-printed-ending:.*"));
    }

    private static boolean source(Witness w, String prefix) {
        return w.event().evidence().stream().allMatch(e -> e.sourceId().startsWith(prefix));
    }

    private static void flush(
            List<Witness> group, List<ScorePlaybackDirection> directions, Set<String> resolved) {
        if (group.isEmpty()) return;
        var repeats = new ArrayList<PrintedRepeatGlyphs.Mark>();
        var endings = new ArrayList<PrintedEndingBrackets.Mark>();
        for (var w : group) {
            var event = w.event();
            if (event.start().isEmpty() || event.start().get().quarterBeatOffset() != 0) return;
            int start = event.start().get().measureIndex();
            if (start < w.pageStart()
                    || start > w.pageEnd()
                    || event.staffCount() != group.get(0).event().staffCount()) return;
            var proof = event.evidence().get(0);
            if (source(w, "printed-repeat-start:v1:") || source(w, "printed-repeat-end:v1:")) {
                repeats.add(
                        new PrintedRepeatGlyphs.Mark(
                                start,
                                source(w, "printed-repeat-end:v1:"),
                                proof.visualX(),
                                proof.staffIndex(),
                                proof.staffCount()));
            } else if (source(w, "printed-ending-span:v1:")) {
                if (!validEnding(w)) return;
                if (event.end().isEmpty() || event.end().get().quarterBeatOffset() != 0) return;
                int end = event.end().get().measureIndex();
                if (end <= start || end > w.pageEnd()) return;
                var passes = PrintedEndingBrackets.passes(proof.printedText());
                if (passes.isEmpty()) return;
                if (source(w, "printed-ending-span:v1:true:")
                        && end == w.pageEnd()
                        && passes.equals(List.of(1))) {
                    var next =
                            group.stream()
                                    .filter(
                                            n ->
                                                    n.page() == w.page() + 1
                                                            && validEnding(n)
                                                            && n.event()
                                                                    .evidence()
                                                                    .get(0)
                                                                    .sourceId()
                                                                    .split(":", 6)[3]
                                                                    .equals("true")
                                                            && PrintedEndingBrackets.passes(
                                                                            n.event()
                                                                                    .evidence()
                                                                                    .get(0)
                                                                                    .printedText())
                                                                    .equals(List.of(2))
                                                            && n.event().start().isPresent()
                                                            && n.event()
                                                                            .start()
                                                                            .get()
                                                                            .measureIndex()
                                                                    >= w.pageEnd())
                                    .toList();
                    if (next.size() != 1) return;
                    var following = next.get(0);
                    if (following.event().staffCount() != event.staffCount()) return;
                    end = following.event().start().get().measureIndex();
                    final int closing = end;
                    long outgoing =
                            group.stream()
                                    .filter(
                                            n ->
                                                    source(n, "printed-repeat-end:v1:")
                                                            && n.event().start().isPresent()
                                                            && n.event()
                                                                            .start()
                                                                            .get()
                                                                            .measureIndex()
                                                                    == closing)
                                    .count();
                    if (outgoing != 1) return;
                }
                endings.add(
                        new PrintedEndingBrackets.Mark(
                                start,
                                end,
                                passes,
                                proof.printedText(),
                                proof.visualX(),
                                proof.staffIndex(),
                                proof.staffCount()));
            } else return; // Old caches have no span proof; keep their unresolved evidence intact.
        }
        var found = PrintedRepeatNavigation.assemble(repeats, endings, group.get(0).page());
        if (!found.unresolved().isEmpty()) return;
        for (var direction : found.directions()) {
            var proof = new ArrayList<ScoreExpressiveEvent.Evidence>();
            for (var w : group) {
                if (w.event().start().get().measureIndex() != direction.measureBoundary()) continue;
                boolean match =
                        switch (direction.kind()) {
                            case REPEAT_START -> source(w, "printed-repeat-start:v1:");
                            case REPEAT_END -> source(w, "printed-repeat-end:v1:");
                            case ENDING -> source(w, "printed-ending-span:v1:");
                            default -> false;
                        };
                if (match) {
                    proof.addAll(w.event().evidence());
                    resolved.add(w.event().eventId());
                }
            }
            var d = direction.details();
            directions.add(
                    new ScorePlaybackDirection(
                            direction.measureBoundary(),
                            direction.kind(),
                            new ScorePlaybackDirection.Details(
                                    d.quarterBeatOffset(),
                                    "document:" + d.eventId(),
                                    d.targetId(),
                                    d.codaTargetId(),
                                    "document:" + d.repeatGroupId(),
                                    d.totalPlays(),
                                    d.passes(),
                                    d.end(),
                                    d.afterJumpRepeats(),
                                    d.printedText(),
                                    proof)));
        }
    }
}

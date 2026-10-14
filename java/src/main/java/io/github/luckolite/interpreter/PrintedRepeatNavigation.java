// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Converts proved local optical marks only when repeat ownership and pass coverage agree. */
final class PrintedRepeatNavigation {
    record Result(List<ScorePlaybackDirection> directions, List<String> unresolved) {}

    private record Region(int start, int end, String id) {}

    private PrintedRepeatNavigation() {}

    static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height,
            int physicalPage) {
        var repeats = PrintedRepeatGlyphs.detect(gray, width, height, staffs, score.measures());
        var endings =
                PrintedEndingBrackets.detect(gray, width, height, staffs, score.measures(), words);
        var found = assemble(repeats, endings, Math.max(0, physicalPage));
        if (found.directions().isEmpty() && found.unresolved().isEmpty()) return score;
        var directions = new ArrayList<>(score.playbackDirections());
        directions.addAll(found.directions());
        var expressions = new ArrayList<>(score.expressiveEvents());
        for (var mark : repeats) {
            var kind =
                    mark.outgoing()
                            ? ScorePlaybackDirection.Kind.REPEAT_END
                            : ScorePlaybackDirection.Kind.REPEAT_START;
            if (found.directions().stream()
                    .anyMatch(d -> d.kind() == kind && d.measureBoundary() == mark.boundary()))
                continue;
            String id =
                    "unresolved-printed-repeat:"
                            + physicalPage
                            + ":"
                            + mark.boundary()
                            + ":"
                            + mark.outgoing();
            expressions.add(
                    unresolved(
                            id,
                            "Printed repeat needs document-level ownership",
                            evidence(
                                    id,
                                    physicalPage,
                                    mark.x(),
                                    mark.staffIndex(),
                                    mark.staffCount(),
                                    "")));
        }
        for (var mark : endings) {
            if (found.directions().stream()
                    .anyMatch(
                            d ->
                                    d.kind() == ScorePlaybackDirection.Kind.ENDING
                                            && d.measureBoundary() == mark.start())) continue;
            String id =
                    "unresolved-printed-ending:"
                            + physicalPage
                            + ":"
                            + mark.start()
                            + ":"
                            + mark.text();
            expressions.add(
                    unresolved(
                            id,
                            "Printed ending needs unambiguous repeat ownership: " + mark.text(),
                            evidence(
                                    id,
                                    physicalPage,
                                    mark.x(),
                                    mark.staffIndex(),
                                    mark.staffCount(),
                                    mark.text())));
        }
        return score.withPlaybackDirections(directions).withExpressiveEvents(expressions);
    }

    private static ScoreExpressiveEvent unresolved(
            String id, String text, ScoreExpressiveEvent.Evidence evidence) {
        return new ScoreExpressiveEvent(
                id,
                ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                Optional.empty(),
                Optional.empty(),
                ScoreExpressiveEvent.Scope.UNRESOLVED,
                evidence.staffIndex(),
                evidence.staffCount(),
                Optional.empty(),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                text,
                List.of(evidence));
    }

    private static ScoreExpressiveEvent.Evidence evidence(
            String id, int page, float x, int staff, int count, String text) {
        return new ScoreExpressiveEvent.Evidence(id, Math.max(0, page), x, staff, count, text);
    }

    static Result assemble(
            List<PrintedRepeatGlyphs.Mark> repeats,
            List<PrintedEndingBrackets.Mark> endings,
            int pageIndex) {
        var directions = new ArrayList<ScorePlaybackDirection>();
        var unresolved = new ArrayList<String>();
        var ordered = new ArrayList<>(repeats);
        ordered.sort(
                Comparator.comparingInt(PrintedRepeatGlyphs.Mark::boundary)
                        .thenComparing(m -> !m.outgoing()));
        var seen = new HashSet<String>();
        var starts = new ArrayDeque<PrintedRepeatGlyphs.Mark>();
        var regions = new ArrayList<Region>();
        var closingEdges = new HashMap<String, List<Integer>>();
        for (var mark : ordered) {
            if (!seen.add(mark.boundary() + ":" + mark.outgoing())) continue;
            if (!mark.outgoing()) {
                starts.push(mark);
                continue;
            }
            var continuation = new ArrayList<Region>();
            for (var region : regions) {
                var edges = closingEdges.get(region.id());
                int prior = edges.get(edges.size() - 1);
                boolean initialEnding =
                        endings.stream()
                                .anyMatch(
                                        e ->
                                                e.start() >= region.start()
                                                        && e.start() < region.end()
                                                        && e.end() == region.end());
                boolean linkedEnding =
                        endings.stream()
                                .anyMatch(e -> e.start() == prior && e.end() == mark.boundary());
                if (initialEnding
                        && linkedEnding
                        && (starts.isEmpty() || starts.peek().boundary() < region.start()))
                    continuation.add(region);
            }
            if (continuation.size() == 1) {
                closingEdges.get(continuation.get(0).id()).add(mark.boundary());
                continue;
            }
            if (continuation.size() > 1) {
                unresolved.add("Ambiguous alternate backward edge at " + mark.boundary());
                continue;
            }
            if (starts.isEmpty()) {
                unresolved.add("Repeat end at " + mark.boundary() + " has no proved local start");
                continue;
            }
            var start = starts.pop();
            if (start.boundary() >= mark.boundary()) {
                unresolved.add("Empty repeat at " + mark.boundary());
                continue;
            }
            var region =
                    new Region(
                            start.boundary(),
                            mark.boundary(),
                            "printed-repeat:"
                                    + pageIndex
                                    + ":"
                                    + start.boundary()
                                    + ":"
                                    + mark.boundary());
            regions.add(region);
            closingEdges.put(region.id(), new ArrayList<>(List.of(mark.boundary())));
        }
        for (var start : starts)
            unresolved.add(
                    "Repeat start at " + start.boundary() + " continues outside local proof");
        var uniqueEndings = new ArrayList<PrintedEndingBrackets.Mark>();
        for (var ending : endings)
            if (uniqueEndings.stream()
                    .noneMatch(
                            e ->
                                    e.start() == ending.start()
                                            && e.end() == ending.end()
                                            && e.passes().equals(ending.passes())))
                uniqueEndings.add(ending);
        var used = new HashSet<PrintedEndingBrackets.Mark>();
        regions.sort(Comparator.comparingInt(r -> r.end() - r.start()));
        for (var region : regions) {
            var owned = new ArrayList<PrintedEndingBrackets.Mark>();
            for (var ending : uniqueEndings)
                if (!used.contains(ending)
                        && ending.start() >= region.start()
                        && ending.start() < region.end()
                        && ending.end() == region.end()) owned.add(ending);
            if (owned.size() > 1) {
                unresolved.add("Conflicting closing endings for " + region.id());
                continue;
            }
            int plays = 2;
            if (!owned.isEmpty()) {
                int next = region.end();
                boolean conflict = false;
                while (true) {
                    final int boundary = next;
                    var following =
                            uniqueEndings.stream()
                                    .filter(e -> !used.contains(e) && e.start() == boundary)
                                    .toList();
                    if (following.isEmpty()) break;
                    if (following.size() != 1) {
                        conflict = true;
                        break;
                    }
                    var ending = following.get(0);
                    owned.add(ending);
                    next = ending.end();
                }
                var passes = new HashSet<Integer>();
                for (var ending : owned)
                    for (int pass : ending.passes()) if (!passes.add(pass)) conflict = true;
                plays = passes.stream().mapToInt(Integer::intValue).max().orElse(0);
                for (int pass = 1; pass <= plays; pass++)
                    if (!passes.contains(pass)) conflict = true;
                if (plays < 2
                        || owned.size() < 2
                        || !owned.get(owned.size() - 1).passes().contains(plays)) conflict = true;
                var edges = closingEdges.get(region.id());
                if (edges.size() != owned.size() - 1) conflict = true;
                for (int i = 0; i < owned.size(); i++) {
                    var ending = owned.get(i);
                    if (i == owned.size() - 1) {
                        if (!ending.passes().equals(List.of(plays)) || edges.contains(ending.end()))
                            conflict = true;
                    } else if (!edges.contains(ending.end()) || ending.passes().contains(plays))
                        conflict = true;
                }
                if (conflict) {
                    unresolved.add("Incomplete or conflicting ending coverage for " + region.id());
                    continue;
                }
            } else if (uniqueEndings.stream()
                    .anyMatch(
                            e ->
                                    !used.contains(e)
                                            && e.start() >= region.start()
                                            && e.start() <= region.end())) {
                unresolved.add("Ending span has no proved closing edge for " + region.id());
                continue;
            }
            directions.add(
                    direction(
                            region.start(),
                            ScorePlaybackDirection.Kind.REPEAT_START,
                            region.id() + ":start",
                            region.id(),
                            plays,
                            List.of(),
                            Optional.empty(),
                            ""));
            for (int edge : closingEdges.get(region.id()))
                directions.add(
                        direction(
                                edge,
                                ScorePlaybackDirection.Kind.REPEAT_END,
                                region.id() + ":end" + (edge == region.end() ? "" : ":" + edge),
                                region.id(),
                                plays,
                                List.of(),
                                Optional.empty(),
                                ""));
            for (var ending : owned) {
                directions.add(
                        direction(
                                ending.start(),
                                ScorePlaybackDirection.Kind.ENDING,
                                region.id() + ":ending:" + ending.start(),
                                region.id(),
                                plays,
                                ending.passes(),
                                Optional.of(new ScoreAnchor(ending.end(), 0)),
                                ending.text()));
                used.add(ending);
            }
        }
        for (var ending : uniqueEndings)
            if (!used.contains(ending))
                unresolved.add(
                        "Unresolved printed ending at " + ending.start() + ": " + ending.text());
        for (int i = 0; i < directions.size(); i++) {
            var direction = directions.get(i);
            var details = direction.details();
            ScoreExpressiveEvent.Evidence proof;
            if (direction.kind() == ScorePlaybackDirection.Kind.ENDING) {
                var mark =
                        uniqueEndings.stream()
                                .filter(
                                        e ->
                                                e.start() == direction.measureBoundary()
                                                        && e.passes().equals(details.passes()))
                                .findFirst()
                                .orElseThrow();
                proof =
                        evidence(
                                details.eventId(),
                                pageIndex,
                                mark.x(),
                                mark.staffIndex(),
                                mark.staffCount(),
                                mark.text());
            } else {
                boolean outgoing = direction.kind() == ScorePlaybackDirection.Kind.REPEAT_END;
                var mark =
                        ordered.stream()
                                .filter(
                                        m ->
                                                m.boundary() == direction.measureBoundary()
                                                        && m.outgoing() == outgoing)
                                .findFirst()
                                .orElseThrow();
                proof =
                        evidence(
                                details.eventId(),
                                pageIndex,
                                mark.x(),
                                mark.staffIndex(),
                                mark.staffCount(),
                                "");
            }
            directions.set(
                    i,
                    new ScorePlaybackDirection(
                            direction.measureBoundary(),
                            direction.kind(),
                            new ScorePlaybackDirection.Details(
                                    details.quarterBeatOffset(),
                                    details.eventId(),
                                    details.targetId(),
                                    details.codaTargetId(),
                                    details.repeatGroupId(),
                                    details.totalPlays(),
                                    details.passes(),
                                    details.end(),
                                    details.afterJumpRepeats(),
                                    details.printedText(),
                                    List.of(proof))));
        }
        directions.sort(
                Comparator.comparingInt(ScorePlaybackDirection::measureBoundary)
                        .thenComparingInt(d -> d.kind().wireId()));
        return new Result(List.copyOf(directions), List.copyOf(unresolved));
    }

    private static ScorePlaybackDirection direction(
            int boundary,
            ScorePlaybackDirection.Kind kind,
            String id,
            String group,
            int plays,
            List<Integer> passes,
            Optional<ScoreAnchor> end,
            String text) {
        return new ScorePlaybackDirection(
                boundary,
                kind,
                new ScorePlaybackDirection.Details(
                        0,
                        id,
                        "",
                        "",
                        group,
                        plays,
                        passes,
                        end,
                        ScorePlaybackDirection.AfterJumpRepeats.DEFAULT,
                        text,
                        List.of()));
    }
}

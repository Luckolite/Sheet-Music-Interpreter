// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Connects proved text, extension ink, and numeric endpoints without converting pixels to beats. */
final class PrintedTempoSpans {
    private static final String SOURCE = "printed-tempo-dash-span";
    static final String OPEN_HINT = "printed-tempo-dash-page-edge-v1:";
    static final String END_HINT = "printed-tempo-dash-end-v1:";

    static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height) {
        // Multipart ownership and open page continuations need document-level evidence.
        if (staffs.isEmpty()
                || staffs.stream().anyMatch(s -> s.count() != 1)
                || width <= 0
                || height <= 0) return score;
        var sizes = staffs.stream().map(PlayingTechniqueDetector.Staff::gap).sorted().toList();
        float gap = sizes.get(sizes.size() / 2), gx = gap / width, gy = gap / height;
        boolean eligible =
                score.expressiveEvents().stream()
                        .anyMatch(e -> (gradual(e.kind()) || bareFaster(e)) && e.end().isEmpty());
        if (!eligible) return score;
        var runs = PrintedDashChains.detect(gray, width, height, gap);
        if (runs.isEmpty()) return score;
        var systems = new ArrayList<PrintedDashSpan.SystemBand>();
        int[] row = new int[score.measures().size()];
        for (int i = 0; i < score.measures().size(); i++) {
            var m = score.measures().get(i);
            if (systems.isEmpty()
                    || Math.abs(m.top() - systems.get(systems.size() - 1).top()) > gy * 2)
                systems.add(
                        new PrintedDashSpan.SystemBand(
                                m.left(), m.right(), m.top(), m.bottom(), Float.NaN));
            else {
                var s = systems.remove(systems.size() - 1);
                systems.add(
                        new PrintedDashSpan.SystemBand(
                                Math.min(s.left(), m.left()),
                                Math.max(s.right(), m.right()),
                                Math.min(s.top(), m.top()),
                                Math.max(s.bottom(), m.bottom()),
                                s.lastColumn()));
            }
            row[i] = systems.size() - 1;
        }
        for (var n : score.notes()) {
            var m = score.measures().get(n.measureIndex());
            int at = row[n.measureIndex()];
            var s = systems.get(at);
            float x = m.left() + n.positionInMeasure() * (m.right() - m.left());
            systems.set(
                    at,
                    new PrintedDashSpan.SystemBand(
                            s.left(),
                            s.right(),
                            s.top(),
                            s.bottom(),
                            Float.isFinite(s.lastColumn()) ? Math.max(s.lastColumn(), x) : x));
        }
        var events = new ArrayList<ScoreExpressiveEvent>();
        for (var event : score.expressiveEvents()) {
            if ((!gradual(event.kind()) && !bareFaster(event)) || event.end().isPresent()) {
                events.add(event);
                continue;
            }
            var measure = ScoreExpressionDetector.measureOf(event);
            if (measure.isEmpty()) {
                events.add(event);
                continue;
            }
            // This zero is an ordering sentinel only; unresolved starts remain absent in output.
            ScoreAnchor start = event.start().orElse(new ScoreAnchor(measure.getAsInt(), 0));
            ScoreAnchor endpoint = null;
            boolean conflict = false;
            float endpointX = Float.NaN;
            boolean open = false;
            for (var word : words) {
                if (event.evidence().stream()
                        .noneMatch(
                                e ->
                                        e.sourceId().equals("printed-expression-word")
                                                && e.printedText().equals(word.text()))) continue;
                var span =
                        PrintedDashSpan.follow(
                                runs,
                                systems,
                                new PrintedDashSpan.WordBox(
                                        word.left(), word.right(), word.top(), word.bottom()),
                                gx,
                                gy);
                if (span.isEmpty()
                        || start.measureIndex() >= row.length
                        || row[start.measureIndex()] != span.get().firstSystem()) continue;
                if (span.get().end() == PrintedDashSpan.End.PAGE_EDGE) {
                    open = true;
                    endpointX = span.get().runs().get(span.get().runs().size() - 1).right();
                    continue;
                }
                var end =
                        PrintedDashEndpoint.numeric(
                                span.get(),
                                systems,
                                score.measures(),
                                score.tempoChanges(),
                                start,
                                gx,
                                gy);
                if (end.isEmpty()) continue;
                if (endpoint != null && !endpoint.equals(end.get())) {
                    conflict = true;
                    break;
                }
                endpoint = end.get();
                endpointX = span.get().runs().get(span.get().runs().size() - 1).right();
            }
            if (open && endpoint == null && !conflict) {
                var evidence = new ArrayList<>(event.evidence());
                var first = evidence.get(0);
                evidence.add(
                        new Evidence(
                                OPEN_HINT + (score.measures().size() - measure.getAsInt()),
                                first.pageIndex(),
                                endpointX,
                                event.staffIndex(),
                                event.staffCount(),
                                first.printedText()));
                events.add(
                        new ScoreExpressiveEvent(
                                event.eventId(),
                                event.kind(),
                                event.start(),
                                Optional.empty(),
                                Scope.UNRESOLVED,
                                event.staffIndex(),
                                event.staffCount(),
                                event.targetEventId(),
                                event.strength(),
                                event.qualifierText(),
                                evidence));
                continue;
            }
            if (endpoint == null || conflict || open || bareFaster(event)) {
                events.add(event);
                continue;
            }
            var evidence = new ArrayList<>(event.evidence());
            var first = evidence.get(0);
            evidence.add(
                    new Evidence(
                            SOURCE,
                            first.pageIndex(),
                            endpointX,
                            event.staffIndex(),
                            event.staffCount(),
                            first.printedText()));
            // Versioned, relative evidence survives page offsets without inventing an onset.
            evidence.add(
                    new Evidence(
                            END_HINT + (endpoint.measureIndex() - measure.getAsInt()),
                            first.pageIndex(),
                            endpointX,
                            event.staffIndex(),
                            event.staffCount(),
                            first.printedText()));
            events.add(
                    new ScoreExpressiveEvent(
                            event.eventId(),
                            event.kind(),
                            event.start(),
                            event.start().isPresent() ? Optional.of(endpoint) : Optional.empty(),
                            event.scope(),
                            event.staffIndex(),
                            event.staffCount(),
                            event.targetEventId(),
                            event.strength(),
                            event.qualifierText(),
                            evidence));
        }
        return score.withExpressiveEvents(events);
    }

    static Optional<ScoreAnchor> preserveEnd(
            ScoreExpressiveEvent event,
            ScoreAnchor start,
            ScoreMeterMap meter,
            int measures,
            List<ScoreTempoChange> tempos) {
        if (!gradual(event.kind())
                || event.start().filter(a -> !a.equals(start)).isPresent()
                || event.evidence().stream().noneMatch(e -> e.sourceId().equals(SOURCE)))
            return Optional.empty();
        try {
            if (event.end().isPresent())
                return event.end()
                        .map(e -> e.canonical(meter, measures))
                        .filter(e -> e.compareTo(start) > 0);
            Integer delta = null;
            for (var evidence : event.evidence()) {
                if (!evidence.sourceId().startsWith(END_HINT)) continue;
                String value = evidence.sourceId().substring(END_HINT.length());
                if (!value.matches("[1-9][0-9]{0,3}")) return Optional.empty();
                int parsed = Integer.parseInt(value);
                if (delta != null && delta != parsed) return Optional.empty();
                delta = parsed;
            }
            if (delta == null) return Optional.empty();
            int target = Math.addExact(start.measureIndex(), delta);
            if (target >= measures) return Optional.empty();
            var marks =
                    tempos.stream()
                            .filter(t -> t.measureIndex() == target && t.positionInMeasure() == 0)
                            .toList();
            if (marks.isEmpty()
                    || marks.stream().map(ScoreTempoChange::bpm).distinct().count() != 1)
                return Optional.empty();
            return Optional.of(new ScoreAnchor(target, 0).canonical(meter, measures));
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return Optional.empty();
        }
    }

    static boolean hasOpen(ScoreExpressiveEvent event) {
        return event.evidence().stream().anyMatch(e -> e.sourceId().startsWith(OPEN_HINT));
    }

    static boolean bareFaster(ScoreExpressiveEvent event) {
        return event.kind() == Kind.UNRESOLVED_DIRECTION
                && event.evidence().stream()
                        .anyMatch(
                                e ->
                                        e.sourceId().equals("printed-expression-word")
                                                && e.printedText()
                                                        .matches(
                                                                "(?i)\\s*(?:[mpf]{1,3}\\s+)?faster\\s*[-.!]*\\s*"));
    }

    private static boolean gradual(Kind kind) {
        return kind == Kind.ACCELERANDO || kind == Kind.RITARDANDO || kind == Kind.RALLENTANDO;
    }
}

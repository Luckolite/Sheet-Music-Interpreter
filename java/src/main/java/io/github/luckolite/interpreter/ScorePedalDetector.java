// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Preserves proved hook columns until final inherited meter is available, without page rectangles. */
public final class ScorePedalDetector {
    private static final String SOURCE = "printed-pedal-bracket", TARGET = "pedal-hooks-v1:";

    private ScorePedalDetector() {}

    private record Columns(
            PedalBracketAnchors.Hook start, PedalBracketAnchors.Hook end, int staff, int count) {
        Columns {
            if (staff < 0 || count <= staff || start.measure() > end.measure())
                throw new IllegalArgumentException("Invalid pedal columns");
        }

        String encode() {
            return TARGET
                    + start.measure()
                    + ":"
                    + start.kind()
                    + ":"
                    + bits(start.position())
                    + ":"
                    + bits(start.tolerance())
                    + ":"
                    + end.measure()
                    + ":"
                    + end.kind()
                    + ":"
                    + bits(end.position())
                    + ":"
                    + bits(end.tolerance())
                    + ":"
                    + staff
                    + ":"
                    + count;
        }

        Columns offset(int measures) {
            return new Columns(
                    new PedalBracketAnchors.Hook(
                            Math.addExact(start.measure(), measures),
                            start.kind(),
                            start.position(),
                            start.tolerance()),
                    new PedalBracketAnchors.Hook(
                            Math.addExact(end.measure(), measures),
                            end.kind(),
                            end.position(),
                            end.tolerance()),
                    staff,
                    count);
        }
    }

    private static int bits(float value) {
        return Float.floatToIntBits(value);
    }

    private static Columns decode(String target) {
        if (!target.startsWith(TARGET)) return null;
        try {
            String[] fields = target.substring(TARGET.length()).split(":");
            if (fields.length != 10) return null;
            int[] v = new int[10];
            for (int i = 0; i < 10; i++) v[i] = Integer.parseInt(fields[i]);
            return new Columns(
                    new PedalBracketAnchors.Hook(
                            v[0], v[1], Float.intBitsToFloat(v[2]), Float.intBitsToFloat(v[3])),
                    new PedalBracketAnchors.Hook(
                            v[4], v[5], Float.intBitsToFloat(v[6]), Float.intBitsToFloat(v[7])),
                    v[8],
                    v[9]);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    static boolean owns(ScoreExpressiveEvent event) {
        return ownedColumns(event) != null;
    }

    private static Columns ownedColumns(ScoreExpressiveEvent event) {
        var columns = event.targetEventId().map(ScorePedalDetector::decode).orElse(null);
        return columns != null
                        && (event.kind() == ScoreExpressiveEvent.Kind.PEDAL_DOWN
                                || event.kind() == ScoreExpressiveEvent.Kind.PEDAL_UP)
                        && columns.staff() == event.staffIndex()
                        && columns.count() == event.staffCount()
                        && event.evidence().stream()
                                .anyMatch(
                                        e ->
                                                e.sourceId().equals(SOURCE)
                                                        && e.staffIndex() == event.staffIndex()
                                                        && e.staffCount() == event.staffCount())
                ? columns
                : null;
    }

    static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs) {
        return withBrackets(
                score,
                PedalBracketDetector.detect(gray, width, height, staffs),
                staffs,
                width,
                height);
    }

    static ScorePageInterpretation withBrackets(
            ScorePageInterpretation score,
            List<PedalBracketDetector.Bracket> brackets,
            List<PlayingTechniqueDetector.Staff> staffs,
            int width,
            int height) {
        Objects.requireNonNull(score);
        if (score.measures().isEmpty() || width <= 0 || height <= 0) return score;
        var events = new ArrayList<>(score.expressiveEvents());
        var ids = new HashSet<String>();
        for (var event : events) ids.add(event.eventId());
        for (var bracket : brackets) {
            var hooks = PedalBracketAnchors.locate(score, bracket, staffs, width, height);
            if (hooks.size() != 2) continue;
            var target =
                    new Columns(
                                    hooks.get(0),
                                    hooks.get(1),
                                    bracket.staffIndex(),
                                    bracket.staffCount())
                            .encode();
            for (boolean down : new boolean[] {true, false}) {
                String id = "printed-pedal:" + target + ":" + (down ? "down" : "up");
                if (!ids.add(id)) continue;
                events.add(
                        new ScoreExpressiveEvent(
                                id,
                                down
                                        ? ScoreExpressiveEvent.Kind.PEDAL_DOWN
                                        : ScoreExpressiveEvent.Kind.PEDAL_UP,
                                Optional.empty(),
                                Optional.empty(),
                                ScoreExpressiveEvent.Scope.UNRESOLVED,
                                bracket.staffIndex(),
                                bracket.staffCount(),
                                Optional.of(target),
                                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                                "continuous bracket",
                                List.of(
                                        new ScoreExpressiveEvent.Evidence(
                                                SOURCE,
                                                0,
                                                (down ? bracket.left() : bracket.right()) / width,
                                                bracket.staffIndex(),
                                                bracket.staffCount(),
                                                down ? "pedal down" : "pedal up"))));
            }
        }
        return score.withExpressiveEvents(events);
    }

    static ScoreExpressiveEvent offsetEvidence(ScoreExpressiveEvent event, int offset, int page) {
        if (!owns(event)) return event.offset(offset);
        if (page < 0) throw new IllegalArgumentException("Negative evidence page");
        var columns = decode(event.targetEventId().orElseThrow());
        return new ScoreExpressiveEvent(
                "page:" + page + "/" + event.eventId(),
                event.kind(),
                event.start().map(a -> a.offset(offset)),
                event.end().map(a -> a.offset(offset)),
                event.scope(),
                event.staffIndex(),
                event.staffCount(),
                Optional.of(columns.offset(offset).encode()),
                event.strength(),
                event.qualifierText(),
                event.evidence().stream()
                        .map(
                                e ->
                                        new ScoreExpressiveEvent.Evidence(
                                                e.sourceId(),
                                                page,
                                                e.visualX(),
                                                e.staffIndex(),
                                                e.staffCount(),
                                                e.printedText()))
                        .toList());
    }

    public static ScorePageInterpretation resolve(
            ScorePageInterpretation score, float openingBeats) {
        if (!Float.isFinite(openingBeats)) {
            var first =
                    score.meterChanges().stream().filter(m -> m.measureIndex() == 0).findFirst();
            if (first.isEmpty()) return resolve(score, (ScoreMeterMap) null);
            openingBeats = first.get().quarterBeats();
        }
        return resolve(
                score,
                openingBeats > 0 ? new ScoreMeterMap(openingBeats, score.meterChanges()) : null);
    }

    /** Null meter means unresolved; cached anchors never substitute for the final score clock. */
    public static ScorePageInterpretation resolve(
            ScorePageInterpretation score, ScoreMeterMap meter) {
        Objects.requireNonNull(score);
        var events = new ArrayList<ScoreExpressiveEvent>();
        try (var session = ScoreNoteTiming.beginTimingSession()) {
            Columns previousColumns = null;
            Optional<ScoreAnchor> previousStart = Optional.empty();
            Optional<ScoreAnchor> previousEnd = Optional.empty();
            for (var event : score.expressiveEvents()) {
                var columns = ownedColumns(event);
                if (columns == null) {
                    events.add(event);
                    continue;
                }
                Optional<ScoreAnchor> start;
                Optional<ScoreAnchor> end;
                if (meter != null && columns.equals(previousColumns)) {
                    start = previousStart;
                    end = previousEnd;
                } else {
                    start =
                            meter == null
                                    ? Optional.<ScoreAnchor>empty()
                                    : PedalBracketAnchors.anchor(
                                            columns.start(),
                                            score,
                                            columns.staff(),
                                            columns.count(),
                                            meter);
                    end =
                            meter == null
                                    ? Optional.<ScoreAnchor>empty()
                                    : PedalBracketAnchors.anchor(
                                            columns.end(),
                                            score,
                                            columns.staff(),
                                            columns.count(),
                                            meter);
                    previousColumns = columns;
                    previousStart = start;
                    previousEnd = end;
                }
                boolean proved =
                        start.isPresent()
                                && end.isPresent()
                                && start.get().compareTo(end.get()) < 0;
                events.add(
                        new ScoreExpressiveEvent(
                                event.eventId(),
                                event.kind(),
                                !proved
                                        ? Optional.empty()
                                        : event.kind() == ScoreExpressiveEvent.Kind.PEDAL_DOWN
                                                ? start
                                                : end,
                                proved && event.kind() == ScoreExpressiveEvent.Kind.PEDAL_DOWN
                                        ? end
                                        : Optional.empty(),
                                proved
                                        ? ScoreExpressiveEvent.Scope.PART
                                        : ScoreExpressiveEvent.Scope.UNRESOLVED,
                                event.staffIndex(),
                                event.staffCount(),
                                event.targetEventId(),
                                event.strength(),
                                event.qualifierText(),
                                event.evidence()));
            }
        }
        return score.withExpressiveEvents(events);
    }
}

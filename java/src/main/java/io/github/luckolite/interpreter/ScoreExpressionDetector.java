// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** OCR directions attached to proved written columns; optical fractions never become beats. */
public final class ScoreExpressionDetector {
    private static final String SOURCE = "printed-expression-word";
    private static final String SYMBOL = "printed-expression-symbol";
    private static final String TARGET = "expression-column:";
    private static final Set<Kind> KINDS =
            EnumSet.of(
                    Kind.RITARDANDO,
                    Kind.RALLENTANDO,
                    Kind.RITENUTO,
                    Kind.METRIC_MODULATION,
                    Kind.ACCELERANDO,
                    Kind.A_TEMPO,
                    Kind.TEMPO_PRIMO,
                    Kind.SAME_TEMPO,
                    Kind.SFORZANDO,
                    Kind.SFORZATO,
                    Kind.SFORZANDO_PIANO,
                    Kind.BREATH,
                    Kind.CAESURA,
                    Kind.UNRESOLVED_DIRECTION);

    private ScoreExpressionDetector() {}

    static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height,
            PrintedExpressionGlyphs glyphs) {
        var combined = new ArrayList<>(words);
        var symbols =
                glyphs.detect(score, staffs, gray, width, height).stream()
                        .map(m -> m.word(width, height))
                        .toList();
        combined.addAll(symbols);
        return apply(score, combined, staffs, gray, width, height, Set.copyOf(symbols));
    }

    static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height) {
        return apply(score, words, staffs, gray, width, height, Set.of());
    }

    private static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height,
            Set<PlayingTechniqueDetector.Word> symbols) {
        var events = new ArrayList<>(score.expressiveEvents());
        for (var word : words) {
            var directions =
                    ExpressiveDirectionText.parse(word.text()).stream()
                            .filter(d -> KINDS.contains(d.kind()))
                            .toList();
            if (directions.isEmpty()) continue;
            boolean release =
                    directions.stream()
                            .allMatch(d -> d.kind() == Kind.BREATH || d.kind() == Kind.CAESURA);
            float x = word.left() * width;
            var staff =
                    PrintedDirectionStaff.at(
                            staffs,
                            score.measures(),
                            score.notes(),
                            gray,
                            width,
                            height,
                            x,
                            word.top() * height,
                            word.bottom() * height);
            if (staff == null) continue;
            ScoreNoteEvent closest = null;
            float distance = Float.MAX_VALUE;
            for (var note : score.notes()) {
                if (note.staffIndex() != staff.index() || note.staffCount() != staff.count())
                    continue;
                var region = score.measures().get(note.measureIndex());
                float center = (staff.top() + staff.bottom()) * .5f / height;
                if (center < region.top() - staff.gap() / height
                        || center > region.bottom() + staff.gap() / height) continue;
                float nx =
                        (region.left()
                                        + note.positionInMeasure()
                                                * (region.right() - region.left()))
                                * width;
                if (release && nx > x + staff.gap() * .2f) continue;
                float d = Math.abs(nx - x);
                if (d < distance && d <= staff.gap() * 3) {
                    closest = note;
                    distance = d;
                }
            }
            if (closest == null) continue;
            String target =
                    target(
                            closest.measureIndex(),
                            closest.staffIndex(),
                            closest.staffCount(),
                            closest.positionInMeasure());
            for (var direction : directions) {
                String id =
                        "printed-expression:"
                                + direction.kind()
                                + ":"
                                + target.substring(TARGET.length());
                if (direction.kind() == Kind.UNRESOLVED_DIRECTION)
                    id += ":word:" + direction.printedText().trim();
                String eventId = id;
                if (events.stream().anyMatch(e -> e.eventId().equals(eventId))) continue;
                events.add(
                        new ScoreExpressiveEvent(
                                id,
                                direction.kind(),
                                Optional.empty(),
                                Optional.empty(),
                                Scope.UNRESOLVED,
                                closest.staffIndex(),
                                closest.staffCount(),
                                Optional.of(target),
                                direction.strength(),
                                direction.qualifierText(),
                                List.of(
                                        new Evidence(
                                                symbols.contains(word) ? SYMBOL : SOURCE,
                                                0,
                                                word.left(),
                                                closest.staffIndex(),
                                                closest.staffCount(),
                                                direction.printedText()))));
            }
        }
        return resolve(score.withExpressiveEvents(events), Float.NaN);
    }

    private record Column(int measure, int staff, int count, float position) {}

    private static String target(int measure, int staff, int count, float position) {
        return TARGET + measure + ":" + staff + ":" + count + ":" + Float.floatToIntBits(position);
    }

    private static Optional<Column> column(ScoreExpressiveEvent event) {
        if (!KINDS.contains(event.kind())
                || event.evidence().stream()
                        .noneMatch(e -> e.sourceId().equals(SOURCE) || e.sourceId().equals(SYMBOL))
                || event.targetEventId().isEmpty()
                || !event.targetEventId().get().startsWith(TARGET)) return Optional.empty();
        try {
            var fields = event.targetEventId().get().substring(TARGET.length()).split(":");
            if (fields.length != 4) return Optional.empty();
            var value =
                    new Column(
                            Integer.parseInt(fields[0]),
                            Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]),
                            Float.intBitsToFloat(Integer.parseInt(fields[3])));
            if (value.measure() < 0
                    || value.staff() != event.staffIndex()
                    || value.count() != event.staffCount()
                    || !Float.isFinite(value.position())
                    || value.position() < 0
                    || value.position() > 1) return Optional.empty();
            return Optional.of(value);
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    static boolean owns(ScoreExpressiveEvent event) {
        return column(event).isPresent();
    }

    static ScoreExpressiveEvent offsetEvidence(ScoreExpressiveEvent event, int offset, int page) {
        var c = column(event).orElseThrow();
        return new ScoreExpressiveEvent(
                "page:" + page + "/" + event.eventId(),
                event.kind(),
                event.start().map(a -> a.offset(offset)),
                event.end().map(a -> a.offset(offset)),
                event.scope(),
                event.staffIndex(),
                event.staffCount(),
                Optional.of(
                        target(
                                Math.addExact(c.measure(), offset),
                                c.staff(),
                                c.count(),
                                c.position())),
                event.strength(),
                event.qualifierText(),
                event.evidence().stream()
                        .map(
                                e ->
                                        new Evidence(
                                                e.sourceId(),
                                                page,
                                                e.visualX(),
                                                e.staffIndex(),
                                                e.staffCount(),
                                                e.printedText()))
                        .toList());
    }

    /** Called again after page concatenation, so continuation pages can use the inherited meter. */
    public static ScorePageInterpretation resolve(
            ScorePageInterpretation score, float openingBeats) {
        if (!Float.isFinite(openingBeats)) {
            var first =
                    score.meterChanges().stream().filter(m -> m.measureIndex() == 0).findFirst();
            if (first.isEmpty()) return score;
            openingBeats = first.get().numerator() * 4f / first.get().denominator();
        }
        if (openingBeats <= 0) return score;
        var meter = new ScoreMeterMap(openingBeats, score.meterChanges());
        var events = new ArrayList<ScoreExpressiveEvent>();
        Map<NoteLane, List<Integer>> noteLanes = null;
        Deque<Optional<Column>> parsedAhead = null;
        boolean firstColumn = true, indexDecision = false;
        int nextEventIndex = 0;
        try (var session = ScoreNoteTiming.beginTimingSession()) {
            for (var event : score.expressiveEvents()) {
                nextEventIndex++;
                var c =
                        parsedAhead == null || parsedAhead.isEmpty()
                                ? column(event)
                                : parsedAhead.removeFirst();
                if (c.isEmpty()) {
                    events.add(event);
                    continue;
                }
                List<Integer> indices;
                if (!firstColumn && !indexDecision) {
                    indexDecision = true;
                    if (score.expressiveEvents().size() - nextEventIndex >= 3) {
                        parsedAhead = new ArrayDeque<>();
                        if (hasThreeMoreColumns(
                                score.expressiveEvents(), nextEventIndex, parsedAhead))
                            noteLanes = indexNoteLanes(score);
                    }
                }
                indices =
                        noteLanes == null
                                ? targetIndicesForColumn(score, c.get())
                                : targetIndicesForColumn(score, c.get(), noteLanes);
                firstColumn = false;
                double start = -1, release = -1;
                boolean proved = !indices.isEmpty();
                for (int index : indices) {
                    var note = score.notes().get(index);
                    float beats = meter.beatsInMeasure(note.measureIndex());
                    double onset = ScoreNoteTiming.beatInMeasure(note, score.notes(), beats);
                    double end =
                            onset
                                    + ScoreNoteTiming.resolvedWrittenDurationBeats(
                                            note, score.notes(), beats);
                    end = ScoreAnchor.computedOffset(note.measureIndex(), end, meter);
                    if (!Double.isFinite(onset)
                            || !Double.isFinite(end)
                            || end <= onset
                            || end > beats
                            || start >= 0
                                    && (Math.abs(onset - start) > 1e-7
                                            || Math.abs(end - release) > 1e-7)) proved = false;
                    start = onset;
                    release = end;
                }
                boolean breath = event.kind() == Kind.BREATH || event.kind() == Kind.CAESURA;
                boolean attack =
                        event.kind() == Kind.SFORZANDO
                                || event.kind() == Kind.SFORZATO
                                || event.kind() == Kind.SFORZANDO_PIANO;
                events.add(
                        new ScoreExpressiveEvent(
                                event.eventId(),
                                event.kind(),
                                proved
                                        ? Optional.of(
                                                new ScoreAnchor(
                                                                c.get().measure(),
                                                                breath ? release : start)
                                                        .canonical(meter, score.measures().size()))
                                        : Optional.empty(),
                                Optional.empty(),
                                proved
                                        ? (attack ? Scope.NOTE : breath ? Scope.PART : Scope.SCORE)
                                        : Scope.UNRESOLVED,
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

    public static List<Integer> targetIndices(
            ScorePageInterpretation score, ScoreExpressiveEvent event) {
        var c = column(event);
        if (c.isEmpty()) return List.of();
        return targetIndicesForColumn(score, c.get());
    }

    private static List<Integer> targetIndicesForColumn(ScorePageInterpretation score, Column a) {
        var result = new ArrayList<Integer>();
        for (int i = 0; i < score.notes().size(); i++) {
            var note = score.notes().get(i);
            if (note.measureIndex() == a.measure()
                    && note.staffIndex() == a.staff()
                    && note.staffCount() == a.count()
                    && Math.abs(note.positionInMeasure() - a.position()) <= .018f) result.add(i);
        }
        return List.copyOf(result);
    }

    private record NoteLane(int measure, int staff, int count) {}

    /** Build only when at least four queries remain to share the allocation cost. */
    private static boolean hasThreeMoreColumns(
            List<ScoreExpressiveEvent> events, int from, Deque<Optional<Column>> parsedAhead) {
        int found = 0;
        for (int i = from; i < events.size(); i++) {
            var value = column(events.get(i));
            parsedAhead.addLast(value);
            if (value.isPresent() && ++found == 3) return true;
        }
        return false;
    }

    /** Original indices stay ordered; this index belongs only to one resolve call. */
    private static Map<NoteLane, List<Integer>> indexNoteLanes(ScorePageInterpretation score) {
        Map<NoteLane, List<Integer>> result = new HashMap<>();
        for (int i = 0; i < score.notes().size(); i++) {
            var note = score.notes().get(i);
            var lane = new NoteLane(note.measureIndex(), note.staffIndex(), note.staffCount());
            result.computeIfAbsent(lane, unused -> new ArrayList<>()).add(i);
        }
        return result;
    }

    private static List<Integer> targetIndicesForColumn(
            ScorePageInterpretation score, Column a, Map<NoteLane, List<Integer>> lanes) {
        var result = new ArrayList<Integer>();
        var lane = new NoteLane(a.measure(), a.staff(), a.count());
        for (int i : lanes.getOrDefault(lane, List.of())) {
            var note = score.notes().get(i);
            if (note.measureIndex() == a.measure()
                    && note.staffIndex() == a.staff()
                    && note.staffCount() == a.count()
                    && Math.abs(note.positionInMeasure() - a.position()) <= .018f) result.add(i);
        }
        return List.copyOf(result);
    }
}

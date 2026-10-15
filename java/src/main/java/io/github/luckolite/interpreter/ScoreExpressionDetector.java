// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** OCR directions attached to proved written columns; optical fractions never become beats. */
public final class ScoreExpressionDetector {

    private static boolean coveredPedalWord(
            PlayingTechniqueDetector.Word word, List<PlayingTechniqueDetector.Word> words) {
        String token = word.text().trim().toLowerCase(Locale.ROOT);
        if (!token.matches("ped(?:ale|\\.?)")) return false;
        float height = word.bottom() - word.top();
        if (!(height > 0) || !(word.right() > word.left())) return false;
        for (var other : words) {
            String phrase = other.text().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (!phrase.matches("(?:con|senza) ped(?:ale|\\.?)")) continue;
            if (!phrase.endsWith(" " + token)) continue;
            float otherHeight = other.bottom() - other.top();
            float overlap =
                    Math.min(word.bottom(), other.bottom()) - Math.max(word.top(), other.top());
            if (otherHeight > 0
                    && overlap >= .8f * Math.min(height, otherHeight)
                    && word.left() >= other.left()
                    && word.right() <= other.right()
                    && other.right() - other.left() > word.right() - word.left()) return true;
        }
        return false;
    }

    private static final String SOURCE = "printed-expression-word";
    private static final String SYMBOL = "printed-expression-symbol";
    private static final String TARGET = "expression-column:";
    private static final String REST_TARGET = "expression-rest-column:";
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
                    Kind.UNRESOLVED_DIRECTION,
                    Kind.PEDAL_DOWN,
                    Kind.PEDAL_UP);

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
            if (coveredPedalWord(word, words)) continue;
            var caption = PrintedTempoCaption.from(word, score);
            String directionText = caption == null ? word.text() : caption.text();
            var directions =
                    ExpressiveDirectionText.parse(directionText).stream()
                            .filter(d -> KINDS.contains(d.kind()))
                            .toList();
            if (directions.isEmpty() && caption != null)
                directions =
                        List.of(
                                new ExpressiveDirectionText.Direction(
                                        Kind.UNRESOLVED_DIRECTION,
                                        Strength.UNSPECIFIED,
                                        caption.text(),
                                        ""));
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
            boolean metric = directions.stream().allMatch(d -> d.kind() == Kind.METRIC_MODULATION);
            if (metric && staff == null)
                staff =
                        PrintedDirectionStaff.at(
                                staffs,
                                score.measures(),
                                score.notes(),
                                gray,
                                width,
                                height,
                                word.right() * width,
                                word.top() * height,
                                word.bottom() * height);
            if (staff == null && word.right() > word.left())
                staff =
                        PrintedDirectionWordStaff.find(
                                staffs, score.measures(), score.notes(), gray, width, height, word);
            ScoreNoteEvent closest =
                    staff == null ? null : closestDirectionNote(score, staff, width, height, x, x);
            boolean metricBoundary = false;
            if (metric && staff != null) {
                float center = (staff.top() + staff.bottom()) * .5f / height;
                int next = -1;
                float boundary = -1;
                int boundaryCount = 0;
                for (int i = 0; i < score.measures().size(); i++) {
                    var region = score.measures().get(i);
                    float bx = region.left() * width;
                    if (center < region.top() - staff.gap() / height
                            || center > region.bottom() + staff.gap() / height) continue;
                    if (bx > x && bx < word.right() * width) {
                        boundaryCount++;
                        if (bx > boundary) {
                            boundary = bx;
                            next = i;
                        }
                    }
                }
                if (boundaryCount > 1) continue;
                if (next >= 0) {
                    metricBoundary = true;
                    closest = null;
                    float first = Float.POSITIVE_INFINITY;
                    for (var note : score.notes())
                        if (!note.crossStaffBeam()
                                && note.measureIndex() == next
                                && note.staffIndex() == staff.index()
                                && note.staffCount() == staff.count()) {
                            var region = score.measures().get(next);
                            float nx =
                                    (region.left()
                                                    + note.positionInMeasure()
                                                            * (region.right() - region.left()))
                                            * width;
                            if (nx >= boundary && nx - boundary <= staff.gap() * 8 && nx < first) {
                                first = nx;
                                closest = note;
                            }
                        }
                }
            }
            if (metricBoundary && closest == null) continue;
            if (closest == null && staff != null && word.right() > word.left())
                closest =
                        closestDirectionNote(score, staff, width, height, x, word.right() * width);
            var rest =
                    closest == null
                            ? PrintedRestDirection.find(score, staffs, word, gray, width, height)
                            : null;
            if (closest == null && rest == null) continue;
            if (caption != null
                    && (closest != null ? closest.measureIndex() : rest.measureIndex())
                            != caption.measureIndex()) continue;
            var anchor =
                    closest != null
                            ? new Column(
                                    closest.measureIndex(),
                                    closest.staffIndex(),
                                    closest.staffCount(),
                                    closest.positionInMeasure(),
                                    false)
                            : new Column(
                                    rest.measureIndex(),
                                    rest.staffIndex(),
                                    rest.staffCount(),
                                    rest.positionInMeasure(),
                                    true);
            String target =
                    target(
                            anchor.measure(),
                            anchor.staff(),
                            anchor.count(),
                            anchor.position(),
                            anchor.rest());
            for (var direction : directions) {
                if (anchor.rest() && !PrintedRestDirection.allows(direction.kind())) continue;
                String id =
                        "printed-expression:"
                                + direction.kind()
                                + ":"
                                + (anchor.rest() ? "rest:" : "")
                                + target.substring(
                                        anchor.rest() ? REST_TARGET.length() : TARGET.length());
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
                                anchor.staff(),
                                anchor.count(),
                                Optional.of(target),
                                direction.strength(),
                                direction.qualifierText(),
                                List.of(
                                        new Evidence(
                                                symbols.contains(word) ? SYMBOL : SOURCE,
                                                0,
                                                word.left(),
                                                anchor.staff(),
                                                anchor.count(),
                                                direction.printedText()))));
            }
        }
        return resolve(score.withExpressiveEvents(events), Float.NaN);
    }

    private record Column(int measure, int staff, int count, float position, boolean rest) {}

    private static ScoreNoteEvent closestDirectionNote(
            ScorePageInterpretation score,
            PlayingTechniqueDetector.Staff staff,
            int width,
            int height,
            float left,
            float right) {
        ScoreNoteEvent closest = null;
        float distance = Float.MAX_VALUE;
        for (var note : score.notes()) {
            if (note.staffIndex() != staff.index() || note.staffCount() != staff.count()) continue;
            var region = score.measures().get(note.measureIndex());
            float center = (staff.top() + staff.bottom()) * .5f / height;
            if (center < region.top() - staff.gap() / height
                    || center > region.bottom() + staff.gap() / height) continue;
            float nx =
                    (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                            * width;
            float d = Math.max(0, Math.max(left - nx, nx - right));
            if (d < distance && d <= staff.gap() * 3) {
                closest = note;
                distance = d;
            }
        }
        return closest;
    }

    private static String target(int measure, int staff, int count, float position, boolean rest) {
        return (rest ? REST_TARGET : TARGET)
                + measure
                + ":"
                + staff
                + ":"
                + count
                + ":"
                + Float.floatToIntBits(position);
    }

    private static Optional<Column> column(ScoreExpressiveEvent event) {
        if (!KINDS.contains(event.kind())
                || event.evidence().stream()
                        .noneMatch(e -> e.sourceId().equals(SOURCE) || e.sourceId().equals(SYMBOL))
                || event.targetEventId().isEmpty()
                || !(event.targetEventId().get().startsWith(TARGET)
                        || event.targetEventId().get().startsWith(REST_TARGET)))
            return Optional.empty();
        try {
            boolean rest = event.targetEventId().get().startsWith(REST_TARGET);
            var fields =
                    event.targetEventId()
                            .get()
                            .substring(rest ? REST_TARGET.length() : TARGET.length())
                            .split(":");
            if (fields.length != 4) return Optional.empty();
            var value =
                    new Column(
                            Integer.parseInt(fields[0]),
                            Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]),
                            Float.intBitsToFloat(Integer.parseInt(fields[3])),
                            rest);
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
                                c.position(),
                                c.rest())),
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
                if (c.get().rest()) indices = List.of();
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
                if (c.get().rest()) {
                    start =
                            PrintedRestDirection.onset(
                                    score,
                                    c.get().measure(),
                                    c.get().staff(),
                                    c.get().count(),
                                    c.get().position(),
                                    meter.beatsInMeasure(c.get().measure()));
                    proved =
                            PrintedRestDirection.allows(event.kind())
                                    && Double.isFinite(start)
                                    && start >= 0
                                    && start < meter.beatsInMeasure(c.get().measure());
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
                                        ? (attack
                                                ? Scope.NOTE
                                                : breath
                                                                || event.kind() == Kind.PEDAL_DOWN
                                                                || event.kind() == Kind.PEDAL_UP
                                                        ? Scope.PART
                                                        : Scope.SCORE)
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
        if (c.isEmpty() || c.get().rest()) return List.of();
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

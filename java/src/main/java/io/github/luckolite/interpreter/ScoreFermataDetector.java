// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Preserve proved fermata symbols; resolve quarter-beat ownership only with musical meter. */
public final class ScoreFermataDetector {
    private static final String SOURCE = "fermata-raw-ink";
    private static final String TARGET = "printed-attack:";
    private static final String EVENT = "printed-fermata:";
    private static final float COLUMN = .018f;

    private ScoreFermataDetector() {}

    static ScorePageInterpretation withFermatas(
            ScorePageInterpretation score,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs) {
        if (score.measures().isEmpty()) return score;
        var anchors = new ArrayList<NoteArticulationDetector.Anchor>();
        for (var note : score.notes()) {
            var measure = score.measures().get(note.measureIndex());
            float x =
                    (measure.left() + note.positionInMeasure() * (measure.right() - measure.left()))
                            * width;
            int best = -1;
            float distance = Float.MAX_VALUE;
            for (int i = 0; i < staffs.size(); i++) {
                var staff = staffs.get(i);
                if (staff.index() != note.staffIndex()) continue;
                float next = Math.abs(note.pageY() * height - (staff.top() + staff.bottom()) * .5f);
                if (next < distance) {
                    distance = next;
                    best = i;
                }
            }
            anchors.add(
                    new NoteArticulationDetector.Anchor(
                            x,
                            note.pageY() * height,
                            best < 0 ? 12 : staffs.get(best).gap(),
                            best));
        }
        var events = new ArrayList<>(score.expressiveEvents());
        anchors.addAll(ScoreRestFermataDetector.anchors(score, staffs, width, height));
        for (var mark :
                NoteArticulationDetector.fermataMarks(labels, gray, width, height, anchors)) {
            if (mark.noteIndex() >= score.notes().size()) {
                var event =
                        ScoreRestFermataDetector.event(
                                score.rests().get(mark.noteIndex() - score.notes().size()),
                                mark,
                                width);
                if (events.stream().noneMatch(e -> e.eventId().equals(event.eventId())))
                    events.add(event);
                continue;
            }
            var note = score.notes().get(mark.noteIndex());
            String target =
                    target(
                            note.measureIndex(),
                            note.staffIndex(),
                            note.staffCount(),
                            note.positionInMeasure());
            String identity = EVENT + target.substring(TARGET.length()) + ":" + mark.inverted();
            if (events.stream().anyMatch(e -> e.eventId().equals(identity))) continue;
            events.add(
                    new ScoreExpressiveEvent(
                            identity,
                            ScoreExpressiveEvent.Kind.FERMATA,
                            Optional.empty(),
                            Optional.empty(),
                            ScoreExpressiveEvent.Scope.UNRESOLVED,
                            note.staffIndex(),
                            note.staffCount(),
                            Optional.of(target),
                            ScoreExpressiveEvent.Strength.UNSPECIFIED,
                            mark.inverted() ? "fermata inverted" : "fermata",
                            List.of(
                                    new ScoreExpressiveEvent.Evidence(
                                            SOURCE,
                                            0,
                                            mark.x() / width,
                                            note.staffIndex(),
                                            note.staffCount(),
                                            "fermata"))));
        }
        return resolve(score.withExpressiveEvents(events), Float.NaN);
    }

    private record Attack(int measure, int staff, int count, float position) {}

    private static String target(int measure, int staff, int count, float position) {
        return TARGET + measure + ":" + staff + ":" + count + ":" + Float.floatToIntBits(position);
    }

    private static Optional<Attack> attack(ScoreExpressiveEvent event) {
        if (event.kind() != ScoreExpressiveEvent.Kind.FERMATA
                || event.evidence().stream().noneMatch(e -> e.sourceId().equals(SOURCE))
                || event.targetEventId().isEmpty()) return Optional.empty();
        String id = event.targetEventId().get();
        if (!id.startsWith(TARGET)) return Optional.empty();
        try {
            String[] fields = id.substring(TARGET.length()).split(":");
            if (fields.length != 4) return Optional.empty();
            var a =
                    new Attack(
                            Integer.parseInt(fields[0]),
                            Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]),
                            Float.intBitsToFloat(Integer.parseInt(fields[3])));
            if (a.measure < 0
                    || a.staff != event.staffIndex()
                    || a.count != event.staffCount()
                    || !Float.isFinite(a.position)
                    || a.position < 0
                    || a.position > 1) return Optional.empty();
            return Optional.of(a);
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    static boolean owns(ScoreExpressiveEvent event) {
        return attack(event).isPresent();
    }

    /** Only detector-owned identities are rebased; independently authored semantics stay intact. */
    static ScoreExpressiveEvent offsetEvidence(ScoreExpressiveEvent event, int offset, int page) {
        var attack = attack(event);
        if (attack.isEmpty()) return event.offset(offset);
        var a = attack.get();
        return new ScoreExpressiveEvent(
                "page:" + page + "/" + event.eventId(),
                event.kind(),
                event.start().map(anchor -> anchor.offset(offset)),
                event.end().map(anchor -> anchor.offset(offset)),
                event.scope(),
                event.staffIndex(),
                event.staffCount(),
                Optional.of(target(Math.addExact(a.measure, offset), a.staff, a.count, a.position)),
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

    /** A continuation page does not independently prove its inherited meter. */
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
        try (var session = ScoreNoteTiming.beginTimingSession()) {
            for (var event : score.expressiveEvents()) {
                var attack = attack(event);
                if (attack.isEmpty()) {
                    events.add(event);
                    continue;
                }
                var a = attack.get();
                var indices = targetIndices(score, a);
                double start = -1, end = -1;
                boolean proved = !indices.isEmpty();
                for (int index : indices) {
                    var note = score.notes().get(index);
                    float beats = meter.beatsInMeasure(note.measureIndex());
                    double onset = ScoreNoteTiming.beatInMeasure(note, score.notes(), beats);
                    double finish =
                            onset
                                    + ScoreNoteTiming.resolvedWrittenDurationBeats(
                                            note, score.notes(), beats);
                    finish = ScoreAnchor.computedOffset(note.measureIndex(), finish, meter);
                    if (!Double.isFinite(onset)
                            || !Double.isFinite(finish)
                            || finish <= onset
                            || finish > beats
                            || start >= 0
                                    && (Math.abs(start - onset) > 1e-7
                                            || Math.abs(end - finish) > 1e-7)) proved = false;
                    start = onset;
                    end = finish;
                }
                var scope =
                        proved
                                ? (indices.size() == 1
                                        ? ScoreExpressiveEvent.Scope.NOTE
                                        : ScoreExpressiveEvent.Scope.VOICE)
                                : ScoreExpressiveEvent.Scope.UNRESOLVED;
                events.add(
                        new ScoreExpressiveEvent(
                                event.eventId(),
                                event.kind(),
                                proved
                                        ? Optional.of(new ScoreAnchor(a.measure, start))
                                        : Optional.empty(),
                                proved
                                        ? Optional.of(
                                                new ScoreAnchor(a.measure, end)
                                                        .canonical(meter, score.measures().size()))
                                        : Optional.empty(),
                                scope,
                                event.staffIndex(),
                                event.staffCount(),
                                event.targetEventId(),
                                event.strength(),
                                event.qualifierText(),
                                event.evidence()));
            }
        }
        return ScoreRestFermataDetector.resolve(score.withExpressiveEvents(events), openingBeats);
    }

    /** Written members of one detector-owned column, before renderer tie/unison aliases. */
    public static List<Integer> targetIndices(
            ScorePageInterpretation score, ScoreExpressiveEvent event) {
        var attack = attack(event);
        if (attack.isEmpty()) return List.of();
        return targetIndices(score, attack.get());
    }

    private static List<Integer> targetIndices(ScorePageInterpretation score, Attack a) {
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < score.notes().size(); i++) {
            var note = score.notes().get(i);
            if (note.measureIndex() == a.measure
                    && note.staffIndex() == a.staff
                    && note.staffCount() == a.count
                    && Math.abs(note.positionInMeasure() - a.position) <= COLUMN) indices.add(i);
        }
        return List.copyOf(indices);
    }
}

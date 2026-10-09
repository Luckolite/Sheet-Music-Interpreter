// Copyright 2026 Luckolite

// SPDX-License-Identifier: Apache-2.0

package io.github.luckolite.interpreter;

import java.util.*;

import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Rest holds require an identified rest and an exactly accounted silent slot, never an optical beat. */
public final class ScoreRestFermataDetector {

    private static final String SOURCE = "fermata-rest-raw-ink", TARGET = "printed-rest:";

    private record Ref(int measure, int staff, int count, float position) {}

    private ScoreRestFermataDetector() {}

    static List<NoteArticulationDetector.Anchor> anchors(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs,
            int width,
            int height) {

        var result = new ArrayList<NoteArticulationDetector.Anchor>();

        for (var rest : score.rests()) {

            if (rest.measureIndex() < 0
                    || rest.measureIndex() >= score.measures().size()
                    || !Float.isFinite(rest.positionInMeasure())
                    || !Float.isFinite(rest.pageY())) {

                result.add(new NoteArticulationDetector.Anchor(0, 0, 12, -1));
                continue;
            }

            var m = score.measures().get(rest.measureIndex());

            float x = (m.left() + rest.positionInMeasure() * (m.right() - m.left())) * width;

            int best = -1;
            float distance = Float.MAX_VALUE;

            for (int i = 0; i < staffs.size(); i++) {

                var s = staffs.get(i);
                float center = (s.top() + s.bottom()) * .5f;

                if (s.index() != rest.staffIndex()
                        || s.count() != rest.staffCount()
                        || center / height < m.top()
                        || center / height > m.bottom()) continue;

                float d = Math.abs(rest.pageY() * height - center);

                if (d < distance) {
                    distance = d;
                    best = i;
                }
            }

            result.add(
                    new NoteArticulationDetector.Anchor(
                            x,
                            rest.pageY() * height,
                            best < 0 ? 12 : staffs.get(best).gap(),
                            best));
        }

        return List.copyOf(result);
    }

    private static String target(Ref r) {
        return TARGET
                + r.measure
                + ":"
                + r.staff
                + ":"
                + r.count
                + ":"
                + Float.floatToIntBits(r.position);
    }

    static ScoreExpressiveEvent event(
            ScoreRestEvent rest, NoteArticulationDetector.FermataMark mark, int width) {

        String target =
                target(
                        new Ref(
                                rest.measureIndex(),
                                rest.staffIndex(),
                                rest.staffCount(),
                                rest.positionInMeasure()));

        return new ScoreExpressiveEvent(
                "printed-rest-fermata:" + target.substring(TARGET.length()) + ":" + mark.inverted(),
                Kind.FERMATA,
                Optional.empty(),
                Optional.empty(),
                Scope.UNRESOLVED,
                rest.staffIndex(),
                rest.staffCount(),
                Optional.of(target),
                Strength.UNSPECIFIED,
                mark.inverted() ? "fermata inverted" : "fermata",
                List.of(
                        new Evidence(
                                SOURCE,
                                0,
                                mark.x() / width,
                                rest.staffIndex(),
                                rest.staffCount(),
                                "fermata")));
    }

    private static Optional<Ref> ref(ScoreExpressiveEvent e) {

        if (e.kind() != Kind.FERMATA
                || e.evidence().stream().noneMatch(x -> x.sourceId().equals(SOURCE))
                || e.targetEventId().isEmpty()
                || !e.targetEventId().get().startsWith(TARGET)) return Optional.empty();

        try {

            var f = e.targetEventId().get().substring(TARGET.length()).split(":");
            if (f.length != 4) return Optional.empty();

            var r =
                    new Ref(
                            Integer.parseInt(f[0]),
                            Integer.parseInt(f[1]),
                            Integer.parseInt(f[2]),
                            Float.intBitsToFloat(Integer.parseInt(f[3])));

            return r.measure >= 0
                            && r.staff == e.staffIndex()
                            && r.count == e.staffCount()
                            && Float.isFinite(r.position)
                            && r.position >= 0
                            && r.position <= 1
                    ? Optional.of(r)
                    : Optional.empty();

        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    static boolean owns(ScoreExpressiveEvent event) {
        return ref(event).isPresent();
    }

    static ScoreExpressiveEvent offsetEvidence(ScoreExpressiveEvent e, int offset, int page) {

        var r = ref(e).orElseThrow();

        return new ScoreExpressiveEvent(
                "page:" + page + "/" + e.eventId(),
                e.kind(),
                e.start().map(a -> a.offset(offset)),
                e.end().map(a -> a.offset(offset)),
                e.scope(),
                e.staffIndex(),
                e.staffCount(),
                Optional.of(
                        target(
                                new Ref(
                                        Math.addExact(r.measure, offset),
                                        r.staff,
                                        r.count,
                                        r.position))),
                e.strength(),
                e.qualifierText(),
                e.evidence().stream()
                        .map(
                                x ->
                                        new Evidence(
                                                x.sourceId(),
                                                page,
                                                x.visualX(),
                                                x.staffIndex(),
                                                x.staffCount(),
                                                x.printedText()))
                        .toList());
    }

    public static List<Integer> targetIndices(
            ScorePageInterpretation score, ScoreExpressiveEvent event) {

        var r = ref(event);
        if (r.isEmpty()) return List.of();

        return targetIndices(score, r.get());
    }

    private static List<Integer> targetIndices(ScorePageInterpretation score, Ref a) {

        var indices = new ArrayList<Integer>();

        for (int i = 0; i < score.rests().size(); i++) {

            var rest = score.rests().get(i);

            if (rest.measureIndex() == a.measure
                    && rest.staffIndex() == a.staff
                    && rest.staffCount() == a.count
                    && Math.abs(rest.positionInMeasure() - a.position) <= .018f) indices.add(i);
        }

        return List.copyOf(indices);
    }

    static ScorePageInterpretation resolve(ScorePageInterpretation score, float openingBeats) {

        var meter = new ScoreMeterMap(openingBeats, score.meterChanges());
        var result = new ArrayList<ScoreExpressiveEvent>();

        try (var timing = ScoreNoteTiming.beginTimingSession()) {

            for (var e : score.expressiveEvents()) {

                var r = ref(e);
                if (r.isEmpty()) {
                    result.add(e);
                    continue;
                }

                var indices = targetIndices(score, r.get());
                double onset = Double.NaN, finish = Double.NaN;

                if (indices.size() == 1 && r.get().measure < score.measures().size()) {

                    var rest = score.rests().get(indices.get(0));

                    onset =
                            provedOnset(
                                    rest,
                                    score.rests(),
                                    score.notes(),
                                    meter.beatsInMeasure(rest.measureIndex()),
                                    true);

                    finish =
                            onset
                                    + rest.resolvedDurationBeats(
                                            meter.beatsInMeasure(rest.measureIndex()));
                }

                boolean proved =
                        Double.isFinite(onset)
                                && Double.isFinite(finish)
                                && onset >= 0
                                && finish > onset;

                result.add(
                        new ScoreExpressiveEvent(
                                e.eventId(),
                                e.kind(),
                                proved
                                        ? Optional.of(new ScoreAnchor(r.get().measure, onset))
                                        : Optional.empty(),
                                proved
                                        ? Optional.of(
                                                new ScoreAnchor(r.get().measure, finish)
                                                        .canonical(meter, score.measures().size()))
                                        : Optional.empty(),
                                proved ? Scope.REST : Scope.UNRESOLVED,
                                e.staffIndex(),
                                e.staffCount(),
                                e.targetEventId(),
                                e.strength(),
                                e.qualifierText(),
                                e.evidence()));
            }
        }

        return score.withExpressiveEvents(result);
    }

    static double provedOnset(
            ScoreRestEvent target,
            List<ScoreRestEvent> rests,
            List<ScoreNoteEvent> notes,
            float beats) {

        return provedOnset(target, rests, notes, beats, false);
    }

    private static double provedOnset(
            ScoreRestEvent target,
            List<ScoreRestEvent> rests,
            List<ScoreNoteEvent> notes,
            float beats,
            boolean captureLane) {

        if (target.isFullMeasure())
            return Double.isFinite(target.resolvedDurationBeats(beats)) ? 0 : Double.NaN;
        if (!Double.isFinite(target.durationBeats())
                || target.durationBeats() <= 0
                || target.durationBeats() > beats) return Double.NaN;

        var witnesses = captureLane ? new ArrayList<ScoreNoteEvent>() : null;

        float before = -1, after = 2;

        for (var note : notes)
            if (note.measureIndex() == target.measureIndex()
                    && note.staffIndex() == target.staffIndex()
                    && note.staffCount() == target.staffCount()) {

                if (witnesses != null) witnesses.add(note);

                if (Math.abs(note.positionInMeasure() - target.positionInMeasure()) <= .018f)
                    return Double.NaN;

                if (note.positionInMeasure() < target.positionInMeasure())
                    before = Math.max(before, note.positionInMeasure());
                else after = Math.min(after, note.positionInMeasure());
            }

        var scanNotes = witnesses == null ? notes : witnesses;

        double start = 0, end = beats;

        for (var note : scanNotes)
            if (note.measureIndex() == target.measureIndex()
                    && note.staffIndex() == target.staffIndex()
                    && note.staffCount() == target.staffCount()) {

                double a = ScoreNoteTiming.beatInMeasure(note, notes, beats),
                        d = ScoreNoteTiming.resolvedWrittenDurationBeats(note, notes, beats);

                if (!Double.isFinite(a) || !Double.isFinite(d) || d <= 0) return Double.NaN;

                if (note.positionInMeasure() == before) start = Math.max(start, a + d);

                if (note.positionInMeasure() == after) end = Math.min(end, a);
            }

        double prior = 0, total = 0;

        for (var rest : rests)
            if (!rest.isFullMeasure()
                    && rest.measureIndex() == target.measureIndex()
                    && rest.staffIndex() == target.staffIndex()
                    && rest.staffCount() == target.staffCount()
                    && rest.positionInMeasure() > before
                    && rest.positionInMeasure() < after) {

                if (!Double.isFinite(rest.durationBeats()) || rest.durationBeats() <= 0)
                    return Double.NaN;

                total += rest.durationBeats();
                if (rest.positionInMeasure() < target.positionInMeasure())
                    prior += rest.durationBeats();
            }

        if (Math.abs(end - start - total) > 1e-7) return Double.NaN;

        for (var note : scanNotes)
            if (note.measureIndex() == target.measureIndex()
                    && note.staffIndex() == target.staffIndex()
                    && note.staffCount() == target.staffCount()) {

                double a = ScoreNoteTiming.beatInMeasure(note, notes, beats),
                        b = a + ScoreNoteTiming.resolvedWrittenDurationBeats(note, notes, beats);

                if (a < end - 1e-7 && b > start + 1e-7) return Double.NaN;
            }

        return start + prior;
    }
}

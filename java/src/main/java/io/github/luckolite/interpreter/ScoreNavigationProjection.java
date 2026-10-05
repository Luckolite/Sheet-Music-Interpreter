// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure source-to-performance projection. Printed geometry remains owned by the source score. */
public final class ScoreNavigationProjection {
    private ScoreNavigationProjection() {}

    /** Opening user/document state, before the first printed changes. BPM is quarter notes/minute. */
    public record Defaults(int fifths, double bpm, int numerator, int denominator) {
        public Defaults {
            new ScoreKeyChange(0, fifths);
            new ScoreTempoChange(0, 0, bpm);
            new ScoreMeterChange(0, numerator, denominator);
        }
    }

    private record Lane(int staff, int count) {}

    private record Run(int source, int end, int playback) {
        int length() {
            return end - source;
        }

        int map(int measure) {
            return playback + measure - source;
        }
    }

    public static ScorePageInterpretation project(
            ScorePageInterpretation source, ScoreNavigationPlan plan, Defaults defaults) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(plan);
        Objects.requireNonNull(defaults);
        if (!plan.traversal().complete())
            throw new IllegalArgumentException("Navigation traversal is incomplete");
        if (plan.sourceMeasureCount() != source.measures().size())
            throw new IllegalArgumentException("Plan does not describe this source score");
        if (!plan.navigationApplied()) {
            if (plan.measureCount() != source.measures().size())
                throw new IllegalArgumentException("Plan does not describe this source score");
            return source;
        }
        // Whole-bar projection must not silently turn a partial-bar prefix into a complete bar.
        for (var occurrence : plan.traversal().occurrences())
            if (occurrence.start().quarterBeatOffset() != 0
                    || !occurrence
                            .end()
                            .equals(new ScoreAnchor(occurrence.start().measureIndex() + 1, 0)))
                throw new IllegalArgumentException(
                        "Partial-bar navigation needs segment projection");
        var runs = new ArrayList<Run>();
        for (int p = 0; p < plan.measureCount(); ) {
            int start = plan.sourceMeasure(p), length = 1;
            if (start < 0 || start >= source.measures().size())
                throw new IllegalArgumentException("Route outside source");
            while (p + length < plan.measureCount()
                    && plan.sourceMeasure(p + length) == start + length) length++;
            if (start + length > source.measures().size())
                throw new IllegalArgumentException("Route outside source");
            runs.add(new Run(start, start + length, p));
            p += length;
        }
        var measures = new ArrayList<MeasureRegion>();
        var notes = new ArrayList<ScoreNoteEvent>();
        var rests = new ArrayList<ScoreRestEvent>();
        var keys = new ArrayList<ScoreKeyChange>();
        var tempos = new ArrayList<ScoreTempoChange>();
        var meters = new ArrayList<ScoreMeterChange>();
        var techniques = new ArrayList<ScoreTechniqueChange>();
        var dynamics = new ArrayList<ScoreDynamicChange>();
        var expressions = new ArrayList<ScoreExpressiveEvent>();
        var expressionSources = new HashMap<String, String>();
        for (var event : source.expressiveEvents())
            if (event.start().isEmpty()) expressions.add(event);
        var lanes = lanes(source);
        var orderedKeys =
                source.keyChanges().stream()
                        .sorted(Comparator.comparingInt(ScoreKeyChange::measureIndex))
                        .toList();
        var orderedTempos =
                source.tempoChanges().stream()
                        .sorted(
                                Comparator.comparingInt(ScoreTempoChange::measureIndex)
                                        .thenComparingDouble(ScoreTempoChange::positionInMeasure))
                        .toList();
        var orderedMeters =
                source.meterChanges().stream()
                        .sorted(Comparator.comparingInt(ScoreMeterChange::measureIndex))
                        .toList();
        var orderedTechniques =
                source.techniqueChanges().stream()
                        .sorted(
                                Comparator.comparingInt(ScoreTechniqueChange::measureIndex)
                                        .thenComparingDouble(
                                                ScoreTechniqueChange::positionInMeasure))
                        .toList();
        try (var timing = ScoreNoteTiming.beginTimingSession()) {
            var dynamicSource = new DynamicSource(source, defaults, orderedTempos);
            for (var run : runs) {
                var end = new ScoreAnchor(run.end(), 0);
                for (var event : source.expressiveEvents())
                    if (event.start().isPresent()
                            && (inside(event.start().get().measureIndex(), run)
                                    // Terminal printed releases occur on reaching the source
                                    // endpoint, before any return, and again on the final visit.
                                    || run.end() == source.measures().size()
                                            && event.start().get().equals(end))) {
                        var finish = event.end().map(a -> a.compareTo(end) > 0 ? end : a);
                        String suffix = "@occurrence:" + run.playback();
                        expressionSources.put(event.eventId() + suffix, event.eventId());
                        expressions.add(
                                new ScoreExpressiveEvent(
                                        event.eventId() + suffix,
                                        event.kind(),
                                        event.start()
                                                .map(a -> a.offset(run.playback() - run.source())),
                                        finish.map(a -> a.offset(run.playback() - run.source())),
                                        event.scope(),
                                        event.staffIndex(),
                                        event.staffCount(),
                                        event.targetEventId().map(id -> id + suffix),
                                        event.strength(),
                                        event.qualifierText(),
                                        event.evidence()));
                    }
                for (int m = run.source(); m < run.end(); m++)
                    measures.add(source.measures().get(m));
                for (var n : source.notes())
                    if (inside(n.measureIndex(), run))
                        notes.add(
                                noteAt(
                                        n,
                                        run.map(n.measureIndex()),
                                        n.tiedFromPrevious()
                                                && !(run.playback() > 0
                                                        && n.measureIndex() == run.source()
                                                        && !hasEarlierPitch(source.notes(), n))));
                for (var r : source.rests())
                    if (inside(r.measureIndex(), run))
                        rests.add(
                                new ScoreRestEvent(
                                        run.map(r.measureIndex()),
                                        r.positionInMeasure(),
                                        r.pageY(),
                                        r.pageHeight(),
                                        r.staffIndex(),
                                        r.staffCount(),
                                        r.durationBeats()));
                int fifths = defaults.fifths(),
                        num = defaults.numerator(),
                        den = defaults.denominator();
                double bpm = defaults.bpm(), unit = 1;
                for (var k : orderedKeys) if (k.measureIndex() < run.source()) fifths = k.fifths();
                for (var t : orderedTempos)
                    if (t.measureIndex() < run.source()) {
                        bpm = t.bpm();
                        unit = t.beatUnit();
                    }
                for (var m : orderedMeters)
                    if (m.measureIndex() < run.source()) {
                        num = m.numerator();
                        den = m.denominator();
                    }
                keys.add(new ScoreKeyChange(run.playback(), fifths));
                // Opening-tempo selection anchors to the first printed mark at 0/0.
                // Do not hide that anchor behind a synthetic user default.
                if (run.playback() != 0
                        || orderedTempos.stream()
                                .noneMatch(
                                        t -> t.measureIndex() == 0 && t.positionInMeasure() == 0))
                    tempos.add(new ScoreTempoChange(run.playback(), 0, bpm, unit));
                meters.add(new ScoreMeterChange(run.playback(), num, den));
                for (var k : orderedKeys)
                    if (inside(k.measureIndex(), run))
                        keys.add(new ScoreKeyChange(run.map(k.measureIndex()), k.fifths()));
                for (var t : orderedTempos)
                    if (inside(t.measureIndex(), run))
                        tempos.add(
                                new ScoreTempoChange(
                                        run.map(t.measureIndex()),
                                        t.positionInMeasure(),
                                        t.bpm(),
                                        t.beatUnit()));
                for (var m : orderedMeters)
                    if (inside(m.measureIndex(), run))
                        meters.add(
                                new ScoreMeterChange(
                                        run.map(m.measureIndex()), m.numerator(), m.denominator()));
                // Instrument technique and expressive instruction are independent persistent
                // states.
                for (var lane : lanes) {
                    int technique = ScoreTechniqueChange.ARCO,
                            expression = ScoreTechniqueChange.ORDINARIO;
                    for (var t : orderedTechniques)
                        if (t.staffIndex() == lane.staff() && t.measureIndex() < run.source()) {
                            if (t.technique() <= ScoreTechniqueChange.PIZZICATO)
                                technique = t.technique();
                            else expression = t.technique();
                        }
                    techniques.add(
                            new ScoreTechniqueChange(
                                    run.playback(), 0, lane.staff(), lane.count(), technique));
                    techniques.add(
                            new ScoreTechniqueChange(
                                    run.playback(), 0, lane.staff(), lane.count(), expression));
                    dynamicSource.project(run, lane, dynamics);
                }
                for (var t : orderedTechniques)
                    if (inside(t.measureIndex(), run))
                        techniques.add(
                                new ScoreTechniqueChange(
                                        run.map(t.measureIndex()),
                                        t.positionInMeasure(),
                                        t.staffIndex(),
                                        t.staffCount(),
                                        t.technique()));
            }
        }
        // An a-tempo checkpoint can refer to a ramp on an earlier source run. Select its
        // latest preceding performed occurrence, not a nonexistent current-run identity.
        var sourceExpressions = new HashMap<String, ScoreExpressiveEvent>();
        for (var event : source.expressiveEvents()) sourceExpressions.put(event.eventId(), event);
        for (int i = 0; i < expressions.size(); i++) {
            var event = expressions.get(i);
            var original = sourceExpressions.get(expressionSources.get(event.eventId()));
            if (original == null || original.targetEventId().isEmpty()) continue;
            String target = original.targetEventId().get();
            ScoreExpressiveEvent best = null;
            if (sourceExpressions.containsKey(target))
                for (var candidate : expressions)
                    if (target.equals(expressionSources.get(candidate.eventId()))
                            && candidate.start().isPresent()
                            && event.start().isPresent()
                            && candidate.start().get().compareTo(event.start().get()) <= 0
                            && (best == null
                                    || candidate.start().get().compareTo(best.start().orElseThrow())
                                            >= 0)) best = candidate;
            // Note/rest identities need the segment renderer's ownership map. Preserve the
            // source target but keep realization unresolved until that map has proved it.
            var scope = best == null ? ScoreExpressiveEvent.Scope.UNRESOLVED : event.scope();
            expressions.set(
                    i,
                    new ScoreExpressiveEvent(
                            event.eventId(),
                            event.kind(),
                            event.start(),
                            event.end(),
                            scope,
                            event.staffIndex(),
                            event.staffCount(),
                            java.util.Optional.of(best == null ? target : best.eventId()),
                            event.strength(),
                            event.qualifierText(),
                            event.evidence()));
        }
        notes.sort(
                Comparator.comparingInt(ScoreNoteEvent::measureIndex)
                        .thenComparingDouble(ScoreNoteEvent::positionInMeasure));
        rests.sort(
                Comparator.comparingInt(ScoreRestEvent::measureIndex)
                        .thenComparingDouble(ScoreRestEvent::positionInMeasure));
        techniques.sort(
                Comparator.comparingInt(ScoreTechniqueChange::measureIndex)
                        .thenComparingDouble(ScoreTechniqueChange::positionInMeasure));
        dynamics.sort(
                Comparator.comparingInt(ScoreDynamicChange::measureIndex)
                        .thenComparingDouble(ScoreDynamicChange::positionInMeasure)
                        .thenComparingInt(c -> c.direction() == 0 ? 0 : 1));
        // Directions have already been consumed: projecting this result cannot repeat the route
        // again.
        return new ScorePageInterpretation(
                measures,
                notes,
                source.firstMeasureNumber(),
                keys,
                tempos,
                meters,
                rests,
                techniques,
                dynamics,
                List.of(),
                expressions);
    }

    private static boolean inside(int measure, Run run) {
        return measure >= run.source() && measure < run.end();
    }

    private static Set<Lane> lanes(ScorePageInterpretation source) {
        var result = new LinkedHashSet<Lane>();
        for (var n : source.notes()) result.add(new Lane(n.staffIndex(), n.staffCount()));
        for (var r : source.rests()) result.add(new Lane(r.staffIndex(), r.staffCount()));
        for (var t : source.techniqueChanges())
            result.add(new Lane(t.staffIndex(), t.staffCount()));
        for (var d : source.dynamicChanges()) {
            if (d.sharedStaffs())
                for (int s = 0; s < d.staffCount(); s++) result.add(new Lane(s, d.staffCount()));
            else result.add(new Lane(d.staffIndex(), d.staffCount()));
        }
        return result;
    }

    private static boolean hasEarlierPitch(List<ScoreNoteEvent> notes, ScoreNoteEvent note) {
        if (note.kind() != ScoreNoteEvent.Kind.PITCHED) return false;
        for (var prior : notes)
            if (prior.kind() == ScoreNoteEvent.Kind.PITCHED
                    && prior.measureIndex() == note.measureIndex()
                    && prior.staffIndex() == note.staffIndex()
                    && prior.diatonicPitchIdentity() == note.diatonicPitchIdentity()
                    && prior.octaveShift() == note.octaveShift()
                    && prior.positionInMeasure() < note.positionInMeasure() - .00001f) return true;
        return false;
    }

    private static ScoreNoteEvent noteAt(ScoreNoteEvent n, int m, boolean tied) {
        return new ScoreNoteEvent(
                        m,
                        n.positionInMeasure(),
                        n.staffStep(),
                        n.staffIndex(),
                        n.staffCount(),
                        n.pageY(),
                        tied,
                        n.augmentationDots(),
                        n.beamCount(),
                        n.writtenAccidental(),
                        n.unbeamedDurationBeats(),
                        n.tupletDivisor(),
                        n.followingRestBeats(),
                        n.articulations(),
                        n.clefBottomDiatonic(),
                        n.crossStaffBeam(),
                        n.leadingRestBeats(),
                        n.compactOpening(),
                        n.octaveShift(),
                        n.boundaryTies(),
                        n.tupletNormalNotes())
                .withStemDirection(n.stemDirection())
                .withKind(n.kind());
    }

    // Source dynamics are evaluated below in musical time, not page distance or bar count.
    private static final class DynamicSource {
        private record Curve(
                ScoreDynamicChange change, double start, double end, double from, double to) {
            double db(double time) {
                return time >= end
                        ? to
                        : from
                                + (to - from)
                                        * Math.max(0, time - start)
                                        / Math.max(.0000001, end - start);
            }
        }

        private final ScorePageInterpretation source;
        private final Defaults defaults;
        private final ScoreMeterMap meter;
        private final List<ScoreTempoChange> tempos;
        private final ScorePerformanceTimeline timeline;
        private final java.util.Map<Lane, List<Curve>> cachedCurves = new HashMap<>();

        DynamicSource(
                ScorePageInterpretation source, Defaults defaults, List<ScoreTempoChange> tempos) {
            this.source = source;
            this.defaults = defaults;
            this.tempos = tempos;
            this.meter =
                    new ScoreMeterMap(
                            defaults.numerator() * 4f / defaults.denominator(),
                            source.meterChanges());
            this.timeline = ScorePerformanceTimeline.numeric(defaults.bpm(), this.meter, tempos);
        }

        void project(Run run, Lane lane, List<ScoreDynamicChange> out) {
            var curves = cachedCurves.computeIfAbsent(lane, this::curves);
            double start = seconds(meter.startBeat(run.source())),
                    end = seconds(meter.startBeat(run.end()));
            Curve active = null;
            boolean startsWithLevel = false;
            for (var curve : curves) {
                if (curve.start() <= start + .0000001) active = curve;
                if (inside(curve.change().measureIndex(), run)
                        && curve.change().direction() == 0
                        && Math.abs(curve.start() - start) < .0000001) startsWithLevel = true;
            }
            if (!startsWithLevel) {
                // A printed level is persistent state; its 8 ms de-click ramp is not a new
                // source instruction to freeze forever when entering exactly at a barline.
                float db =
                        (float)
                                (active == null
                                        ? 0
                                        : active.change().direction() == 0
                                                ? active.to()
                                                : active.db(start));
                out.add(
                        new ScoreDynamicChange(
                                run.playback(),
                                0,
                                lane.staff(),
                                lane.count(),
                                run.playback(),
                                0,
                                db,
                                0,
                                false,
                                true));
            }
            // A return into an already active wedge resumes only its remaining gain, not another 6
            // dB.
            if (active != null
                    && active.change().direction() != 0
                    && active.change().measureIndex() < run.source()
                    && active.end() > start)
                hairpin(run, lane, active, run.playback(), 0, end, out);
            for (var curve : curves)
                if (inside(curve.change().measureIndex(), run)) {
                    var c = curve.change();
                    int measure = run.map(c.measureIndex());
                    if (c.direction() == 0)
                        out.add(
                                new ScoreDynamicChange(
                                        measure,
                                        c.positionInMeasure(),
                                        lane.staff(),
                                        lane.count(),
                                        measure,
                                        c.positionInMeasure(),
                                        c.decibels(),
                                        0,
                                        false,
                                        true,
                                        c.sharedTiming()));
                    else hairpin(run, lane, curve, measure, c.positionInMeasure(), end, out);
                }
        }

        private void hairpin(
                Run run,
                Lane lane,
                Curve curve,
                int measure,
                float position,
                double runEnd,
                List<ScoreDynamicChange> out) {
            var c = curve.change();
            boolean clipped = curve.end() >= runEnd;
            int endMeasure =
                    clipped ? run.playback() + run.length() - 1 : run.map(c.endMeasureIndex());
            float endPosition = clipped ? 1 : c.endPosition();
            if (endMeasure < measure || endMeasure == measure && endPosition <= position) return;
            float target = (float) curve.db(Math.min(curve.end(), runEnd));
            out.add(
                    new ScoreDynamicChange(
                            measure,
                            position,
                            lane.staff(),
                            lane.count(),
                            endMeasure,
                            endPosition,
                            target,
                            c.direction(),
                            false,
                            true,
                            c.sharedTiming()));
        }

        private List<Curve> curves(Lane lane) {
            var changes =
                    source.dynamicChanges().stream()
                            .filter(
                                    c ->
                                            c.measureIndex() < source.measures().size()
                                                    && (c.sharedStaffs()
                                                            ? c.staffCount() == lane.count()
                                                            : c.staffIndex() == lane.staff()
                                                                    && (c.staffCount()
                                                                                    == lane.count()
                                                                            || !c.fixedTarget()
                                                                                    && c
                                                                                                    .staffIndex()
                                                                                            == 0)))
                            .sorted(
                                    Comparator.comparingInt(ScoreDynamicChange::measureIndex)
                                            .thenComparingDouble(
                                                    ScoreDynamicChange::positionInMeasure)
                                            .thenComparingInt(c -> c.direction() == 0 ? 0 : 1))
                            .toList();
            var result = new ArrayList<Curve>();
            for (int i = 0; i < changes.size(); i++) {
                var c = changes.get(i);
                double start = at(c.measureIndex(), c.positionInMeasure(), c);
                Curve previous = result.isEmpty() ? null : result.get(result.size() - 1);
                double from = previous == null ? 0 : previous.db(start),
                        to = c.decibels(),
                        end = start + .008;
                if (c.direction() == 0) {
                    if (previous != null
                            && previous.change().measureIndex() == c.measureIndex()
                            && Math.abs(previous.start() - start) < .0000001
                            && previous.end() - start <= .010
                            && to < previous.to()) {
                        start += .075;
                        end = start + .008;
                        from = previous.to();
                    }
                    if (start == 0) from = to;
                } else {
                    if (previous != null && Math.abs(previous.start() - start) < .0000001)
                        from = previous.to();
                    end = at(c.endMeasureIndex(), c.endPosition(), c);
                    if (end <= start) continue;
                    to =
                            c.fixedTarget()
                                    ? c.decibels()
                                    : Math.max(-24, Math.min(9, from + c.direction() * 6));
                    for (int j = i + 1; !c.fixedTarget() && j < changes.size(); j++) {
                        var next = changes.get(j);
                        if (next.direction() != 0) continue;
                        double target = at(next.measureIndex(), next.positionInMeasure(), next);
                        if (target >= end
                                && target - end <= .4
                                && (next.decibels() - from) * c.direction() > 0)
                            to = next.decibels();
                        break;
                    }
                }
                result.add(new Curve(c, start, end, from, to));
            }
            return result;
        }

        private double at(int measure, float position, ScoreDynamicChange c) {
            if (position >= 1) return seconds(meter.startBeat(measure + 1));
            ScoreNoteEvent before = null, after = null;
            for (var n : source.notes())
                if (n.measureIndex() == measure
                        && (c.sharedTiming()
                                ? n.staffCount() == c.staffCount()
                                : n.staffIndex() == c.staffIndex())) {
                    if (n.positionInMeasure() <= position
                            && (before == null
                                    || n.positionInMeasure() > before.positionInMeasure()))
                        before = n;
                    if (n.positionInMeasure() >= position
                            && (after == null || n.positionInMeasure() < after.positionInMeasure()))
                        after = n;
                }
            if (before == null) return seconds(meter.startBeat(measure));
            float beats = meter.beatsInMeasure(measure);
            double a = ScoreNoteTiming.beatInMeasure(before, source.notes(), beats);
            double b =
                    after == null
                            ? beats
                            : ScoreNoteTiming.beatInMeasure(after, source.notes(), beats);
            float x = before.positionInMeasure(), y = after == null ? 1 : after.positionInMeasure();
            double beat =
                    y <= x ? a : a + (b - a) * Math.max(0, Math.min(1, (position - x) / (y - x)));
            return seconds(meter.startBeat(measure) + beat);
        }

        private double seconds(double beat) {
            return timeline.activeSecondsAtBeat(beat);
        }
    }
}

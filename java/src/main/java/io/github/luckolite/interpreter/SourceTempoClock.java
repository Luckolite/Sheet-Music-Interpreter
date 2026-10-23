// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Written tempo clocks supplied by the same proved source column as an inline caption. */
public final class SourceTempoClock {
    private SourceTempoClock() {}

    public static OptionalDouble onset(
            ScorePageInterpretation score,
            ScoreTempoChange tempo,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> retained,
            float beats) {
        Objects.requireNonNull(score);
        Objects.requireNonNull(tempo);
        if (!score.tempoChanges().contains(tempo)) return OptionalDouble.empty();
        int m = tempo.measureIndex();
        if (m < 0 || m >= score.measures().size() || !Float.isFinite(beats) || beats <= 0)
            return OptionalDouble.empty();
        var region = score.measures().get(m);
        float x = region.left() + tempo.positionInMeasure() * (region.right() - region.left());
        float tolerance = Math.ulp(x) * 4;
        if (!Float.isFinite(x) || x < 0 || x > 1) return OptionalDouble.empty();
        if (score.tempoChanges().stream()
                        .filter(
                                t ->
                                        t.measureIndex() == m
                                                && Math.abs(
                                                                region.left()
                                                                        + t.positionInMeasure()
                                                                                * (region.right()
                                                                                        - region
                                                                                                .left())
                                                                        - x)
                                                        <= tolerance)
                        .count()
                != 1) return OptionalDouble.empty();
        var checked = ScorePlacementMap.validatedWritten(retained, score.notes());
        var local = score.notes().stream().filter(n -> n.measureIndex() == m).toList();
        Double result = null;
        try (var session = ScoreNoteTiming.beginTimingSession(score)) {
            for (var event : score.expressiveEvents()) {
                if (event.kind() != ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION
                        || event.scope() != ScoreExpressiveEvent.Scope.SCORE
                                && event.scope() != ScoreExpressiveEvent.Scope.UNRESOLVED
                        || !ScoreExpressionDetector.owns(event)) continue;
                var words =
                        event.evidence().stream()
                                .filter(e -> e.sourceId().equals("printed-expression-word"))
                                .toList();
                if (words.size() != 1) continue;
                var word = words.get(0);
                if (word.staffIndex() != event.staffIndex()
                        || word.staffCount() != event.staffCount()
                        || Math.abs(word.visualX() - x) > tolerance
                        || word.printedText().isBlank()) continue;
                var owners = ScoreExpressionDetector.targetIndices(score, event);
                if (owners.isEmpty()) continue;
                // Preserve the existing inline-caption eligibility guard. It identifies
                // the same printed instruction; it never supplies the resulting clock.
                double anchor = Double.NaN;
                if (event.start().isPresent()) {
                    if (event.start().get().measureIndex() != m) continue;
                    anchor = event.start().get().quarterBeatOffset();
                } else {
                    for (int index : owners) {
                        var original =
                                ScoreNoteTiming.writtenPlacement(
                                                score.notes().get(index), local, beats)
                                        .orElse(null);
                        if (original == null
                                || original.grace()
                                || Double.isFinite(anchor) && anchor != original.onsetBeats()) {
                            anchor = Double.NaN;
                            break;
                        }
                        anchor = original.onsetBeats();
                    }
                }
                if (!Double.isFinite(anchor)
                        || Math.abs(anchor - beats * tempo.positionInMeasure()) > 1.0 / 32)
                    continue;
                Double at = null;
                for (int index : owners) {
                    var note = score.notes().get(index);
                    if (note.measureIndex() != m
                            || note.staffIndex() != event.staffIndex()
                            || note.staffCount() != event.staffCount())
                        return OptionalDouble.empty();
                    var p = checked.get(index);
                    if (p == null)
                        p = ScoreNoteTiming.writtenPlacement(note, local, beats).orElse(null);
                    if (p == null
                            || p.grace()
                            || !Double.isFinite(p.onsetBeats())
                            || p.onsetBeats() < 0
                            || p.onsetBeats() >= beats
                            || at != null && at.doubleValue() != p.onsetBeats())
                        return OptionalDouble.empty();
                    at = p.onsetBeats();
                }
                if (result != null) return OptionalDouble.empty();
                result = at;
            }
        }
        return result == null ? OptionalDouble.empty() : OptionalDouble.of(result);
    }

    /** Project source geometry once, at a source boundary; numeric timeline APIs stay rhythmic. */
    public static List<ScoreTempoChange> rhythmicChanges(
            ScorePageInterpretation score,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> retained,
            ScoreMeterMap meter) {
        var result = new ArrayList<ScoreTempoChange>();
        for (var tempo : score.tempoChanges()) {
            float beats = meter.beatsInMeasure(tempo.measureIndex());
            var onset = onset(score, tempo, retained, beats);
            result.add(
                    onset.isEmpty()
                            ? tempo
                            : new ScoreTempoChange(
                                    tempo.measureIndex(),
                                    (float) (onset.getAsDouble() / beats),
                                    tempo.bpm(),
                                    tempo.beatUnit()));
        }
        return List.copyOf(result);
    }

    /** Only arrangement boundaries may shorten a page's printed first/last bar clock. */
    public static ScoreMeterMap sourceMeter(
            ScorePageInterpretation score, float nominal, boolean firstPage, boolean lastPage) {
        var meter = new ScoreMeterMap(nominal, score.meterChanges());
        if (score.measures().isEmpty()) return meter;
        var boundaries = ScoreBoundaryClock.resolve(score, nominal);
        return meter.withBoundaryQuarterBeats(
                firstPage ? boundaries.openingQuarterBeats() : 0,
                lastPage ? boundaries.closingQuarterBeats() : 0,
                score.measures().size());
    }
}

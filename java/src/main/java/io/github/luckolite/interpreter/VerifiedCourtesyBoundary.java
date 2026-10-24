// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A proven courtesy-only final region has no duration; its signatures belong to the next boundary. */
final class VerifiedCourtesyBoundary {
    /** Remove at most one proven terminal courtesy region before retaining slur-source geometry. */
    static ScorePageInterpretation apply(
            ScorePageInterpretation score, byte[] labels, byte[] gray, int width, int height) {
        if (score == null || score.measures().size() < 2) return score;
        int last = score.measures().size() - 1;
        if (score.keyChanges().stream().noneMatch(k -> k.measureIndex() == last)
                || score.meterChanges().stream().noneMatch(m -> m.measureIndex() == last))
            return score;
        for (var crop :
                MeterChangeDetector.candidates(
                        labels, gray, width, height, score.measures(), score.notes())) {
            var trimmed = trim(score, crop, labels, gray, width, height);
            if (trimmed != score) return trimmed;
        }
        return score;
    }

    static ScorePageInterpretation trim(
            ScorePageInterpretation s,
            MeterChangeDetector.Crop crop,
            byte[] labels,
            byte[] gray,
            int width,
            int height) {
        int last = s.measures().size() - 1;
        if (last < 1 || crop == null) return s;
        if (s.keyChanges().stream().noneMatch(k -> k.measureIndex() == last)
                || s.meterChanges().stream().noneMatch(m -> m.measureIndex() == last)) return s;
        if (s.notes().stream().anyMatch(n -> n.measureIndex() >= last)
                || s.rests().stream().anyMatch(r -> r.measureIndex() >= last)
                || s.tempoChanges().stream().anyMatch(t -> t.measureIndex() >= last)
                || s.techniqueChanges().stream().anyMatch(t -> t.measureIndex() >= last)
                || s.dynamicChanges().stream()
                        .anyMatch(d -> d.measureIndex() >= last || d.endMeasureIndex() >= last)
                || s.playbackDirections().stream().anyMatch(d -> d.measureBoundary() >= last)
                || s.expressiveEvents().stream()
                        .anyMatch(
                                e ->
                                        e.start().isEmpty()
                                                || e.start().get().measureIndex() >= last
                                                || e.end().isPresent()
                                                        && e.end().get().measureIndex() >= last))
            return s;
        var r = s.measures().get(last);
        var previous = s.measures().get(last - 1);
        float cy = (crop.top() + crop.bottom()) * .5f / height;
        if (cy < r.top()
                || cy > r.bottom()
                || cy < previous.top()
                || cy > previous.bottom()
                || crop.left() < r.left() * width
                || crop.right() > r.right() * width + crop.gap() * .3f) return s;
        if (!CourtesyBoundaryInk.matches(
                labels,
                gray,
                width,
                height,
                Math.round(r.left() * width),
                Math.round(r.right() * width),
                crop.firstLine(),
                crop.gap())) return s;
        return new ScorePageInterpretation(
                List.copyOf(s.measures().subList(0, last)),
                s.notes(),
                s.firstMeasureNumber(),
                s.keyChanges(),
                s.tempoChanges(),
                s.meterChanges(),
                s.rests(),
                s.techniqueChanges(),
                s.dynamicChanges(),
                s.playbackDirections(),
                s.expressiveEvents(),
                s.slurSource(),
                s.openingPickup(),
                s.tremoloSource(),
                PrintedMultiRestGroups.remap(
                        s.multiRestGroups(),
                        java.util.stream.IntStream.range(0, last).boxed().toList())).withBeamSource(s.beamSource());
    }
}

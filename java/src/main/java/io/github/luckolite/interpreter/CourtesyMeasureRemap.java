// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Collapses one independently proven empty courtesy region, preserving note identities. */
final class CourtesyMeasureRemap {
    static int map(int index, int cut) {
        return index > cut ? index - 1 : index;
    }

    static ScoreAnchor anchor(ScoreAnchor a, int cut) {
        return new ScoreAnchor(map(a.measureIndex(), cut), a.quarterBeatOffset());
    }

    static ScorePageInterpretation remove(ScorePageInterpretation s, int cut) {
        if (cut < 0
                || cut >= s.measures().size()
                || s.notes().stream().anyMatch(n -> n.measureIndex() == cut)
                || s.rests().stream().anyMatch(r -> r.measureIndex() == cut))
            throw new IllegalArgumentException("Region contains timed content");
        var measures = new ArrayList<>(s.measures());
        measures.remove(cut);
        var notes =
                s.notes().stream()
                        .map(
                                n ->
                                        new ScoreNoteEvent(
                                                map(n.measureIndex(), cut),
                                                n.positionInMeasure(),
                                                n.staffStep(),
                                                n.staffIndex(),
                                                n.staffCount(),
                                                n.pageY(),
                                                n.tiedFromPrevious(),
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
                                                n.tupletNormalNotes(),
                                                n.stemDirection(),
                                                n.kind()))
                        .toList();
        var keys =
                s.keyChanges().stream()
                        .map(k -> new ScoreKeyChange(map(k.measureIndex(), cut), k.fifths()))
                        .distinct()
                        .toList();
        var tempos =
                s.tempoChanges().stream()
                        .map(
                                t ->
                                        new ScoreTempoChange(
                                                map(t.measureIndex(), cut),
                                                t.positionInMeasure(),
                                                t.bpm(),
                                                t.beatUnit()))
                        .toList();
        var meters =
                s.meterChanges().stream()
                        .map(
                                m ->
                                        new ScoreMeterChange(
                                                map(m.measureIndex(), cut),
                                                m.numerator(),
                                                m.denominator()))
                        .distinct()
                        .toList();
        var rests =
                s.rests().stream()
                        .map(r -> r.withMeasureIndex(map(r.measureIndex(), cut)))
                        .toList();
        var techniques =
                s.techniqueChanges().stream()
                        .map(
                                t ->
                                        new ScoreTechniqueChange(
                                                map(t.measureIndex(), cut),
                                                t.positionInMeasure(),
                                                t.staffIndex(),
                                                t.staffCount(),
                                                t.technique()))
                        .toList();
        var dynamics =
                s.dynamicChanges().stream()
                        .map(
                                d ->
                                        new ScoreDynamicChange(
                                                map(d.measureIndex(), cut),
                                                d.positionInMeasure(),
                                                d.staffIndex(),
                                                d.staffCount(),
                                                map(d.endMeasureIndex(), cut),
                                                d.endPosition(),
                                                d.decibels(),
                                                d.direction(),
                                                d.sharedStaffs(),
                                                d.fixedTarget(),
                                                d.sharedTiming()))
                        .toList();
        var directions =
                s.playbackDirections().stream()
                        .map(
                                d -> {
                                    var x = d.details();
                                    return new ScorePlaybackDirection(
                                            map(d.measureBoundary(), cut),
                                            d.kind(),
                                            new ScorePlaybackDirection.Details(
                                                    x.quarterBeatOffset(),
                                                    x.eventId(),
                                                    x.targetId(),
                                                    x.codaTargetId(),
                                                    x.repeatGroupId(),
                                                    x.totalPlays(),
                                                    x.passes(),
                                                    x.end().map(a -> anchor(a, cut)),
                                                    x.afterJumpRepeats(),
                                                    x.printedText(),
                                                    x.evidence()));
                                })
                        .toList();
        var expressions =
                s.expressiveEvents().stream()
                        .map(
                                e ->
                                        new ScoreExpressiveEvent(
                                                e.eventId(),
                                                e.kind(),
                                                e.start().map(a -> anchor(a, cut)),
                                                e.end().map(a -> anchor(a, cut)),
                                                e.scope(),
                                                e.staffIndex(),
                                                e.staffCount(),
                                                ScorePedalDetector.remapTargetMeasures(
                                                        e, i -> map(i, cut)),
                                                e.strength(),
                                                e.qualifierText(),
                                                e.evidence()))
                        .toList();
        return new ScorePageInterpretation(
                measures,
                notes,
                s.firstMeasureNumber(),
                keys,
                tempos,
                meters,
                rests,
                techniques,
                dynamics,
                directions,
                expressions,
                s.slurSource(),
                s.openingPickup(),
                s.tremoloSource(),
                PrintedMultiRestGroups.remap(
                        s.multiRestGroups(),
                        java.util.stream.IntStream.range(0, s.measures().size())
                                .filter(i -> i != cut)
                                .boxed()
                                .toList()));
    }
}

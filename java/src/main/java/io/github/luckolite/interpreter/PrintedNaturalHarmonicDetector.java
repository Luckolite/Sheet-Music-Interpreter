// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative note-owned ring annotation stage for use after final note decoding. */
public final class PrintedNaturalHarmonicDetector {
    private PrintedNaturalHarmonicDetector() {}

    /**
     * Materializes only when a real opening meter is supplied. Ring geometry selects no beat;
     * written timing is resolved from the exact score note inside ScoreNoteTiming's session.
     */
    public static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<PlayingTechniqueDetector.Word> recognizedText,
            int physicalPage,
            float openingBeats) {
        long pixels = (long) width * height;
        if (score == null
                || labels == null
                || gray == null
                || width <= 0
                || height <= 0
                || pixels != gray.length
                || pixels != labels.length
                || staffs == null
                || recognizedText == null
                || physicalPage < 0
                || Float.isInfinite(openingBeats)
                || openingBeats == 0
                || openingBeats < 0
                || score.measures().isEmpty()
                || score.notes().isEmpty()) return score;
        var meter =
                Float.isNaN(openingBeats)
                        ? null
                        : new ScoreMeterMap(openingBeats, score.meterChanges());
        if (score.notes().size() > 4096 || recognizedText.size() > 10_000) return score;
        List<NaturalHarmonicRingProof.NoteIdentity> identities = new ArrayList<>();
        for (ScoreNoteEvent note : score.notes()) {
            if (note.measureIndex() >= score.measures().size()) continue;
            String target = ScoreNaturalHarmonicNotation.target(note);
            if (target == null) continue;
            var region = score.measures().get(note.measureIndex());
            var staff =
                    NaturalHarmonicStaffBinding.uniqueForMeasure(
                            staffs, note.staffIndex(), note.staffCount(), region, height);
            if (staff == null) continue;
            var point =
                    NaturalHarmonicRingProof.reconstruct(
                            note.positionInMeasure(),
                            note.pageY(),
                            new NaturalHarmonicRingProof.MeasureBounds(
                                    region.left(), region.right()),
                            width,
                            height);
            identities.add(
                    new NaturalHarmonicRingProof.NoteIdentity(
                            target,
                            note.measureIndex(),
                            note.staffIndex(),
                            note.staffCount(),
                            note.staffStep(),
                            point.x(),
                            point.y(),
                            staff.gap(),
                            staff.top(),
                            point.xError(),
                            point.yError()));
        }
        if (identities.isEmpty()) return score;
        var searches = new ArrayList<NaturalHarmonicRingProof.Search>();
        for (var note : identities) {
            int radiusX = Math.max(8, (int) Math.ceil(note.staffGap() * 1.3f));
            int radiusY = Math.max(8, (int) Math.ceil(note.staffGap() * 4.5f));
            int l = Math.max(0, (int) Math.floor(note.centerX() - radiusX));
            int r = Math.min(width - 1, (int) Math.ceil(note.centerX() + radiusX));
            int t = Math.max(0, (int) Math.floor(note.centerY() - radiusY));
            int b = Math.min(height - 1, (int) Math.ceil(note.centerY() + radiusY));
            var roi = new NaturalHarmonicRingProof.Box(l, t, r, b);
            int minD = Math.max(5, (int) Math.floor(note.staffGap() * .4f));
            int maxD = Math.max(minD, (int) Math.ceil(note.staffGap() * 1.35f));
            searches.add(new NaturalHarmonicRingProof.Search(roi, minD, maxD));
        }
        NaturalHarmonicRingProof.WorkPlan workPlan;
        NaturalHarmonicRingProof.Budget budget;
        try {
            workPlan = NaturalHarmonicRingProof.pageWorkPlan(width, height, searches, identities);
            budget = new NaturalHarmonicRingProof.Budget(workPlan.workLimit(), 64 * 1024 * 1024);
            budget.consume(identities.size(), Math.multiplyExact(identities.size(), 64));
        } catch (IllegalArgumentException | ArithmeticException boundedRefusal) {
            return score;
        }
        List<NaturalHarmonicRingProof.Box> textBoxes = new ArrayList<>();
        for (var word : recognizedText) {
            try {
                budget.consume(1, 48);
            } catch (IllegalArgumentException exhausted) {
                return score;
            }
            int l = clamp((int) Math.floor(word.left() * width), 0, width - 1);
            int r = clamp((int) Math.ceil(word.right() * width), 0, width - 1);
            int t = clamp((int) Math.floor(word.top() * height), 0, height - 1);
            int b = clamp((int) Math.ceil(word.bottom() * height), 0, height - 1);
            if (r >= l && b >= t) textBoxes.add(new NaturalHarmonicRingProof.Box(l, t, r, b));
        }
        List<NaturalHarmonicRingProof.Ring> found;
        try {
            found =
                    NaturalHarmonicRingProof.ringsInUnion(
                            gray, width, height, searches, 180, 3, 64, budget);
            if (found.isEmpty()) return score;
            List<ScoreExpressiveEvent> events = new ArrayList<>(score.expressiveEvents());
            Set<String> ids = new HashSet<>();
            for (var existing : events) ids.add(existing.eventId());
            Map<String, List<NaturalHarmonicRingProof.OwnedRing>> ringsByNote = new HashMap<>();
            try (var timing = ScoreNoteTiming.beginTimingSession(score)) {
                for (var ring : found) {
                    var owned =
                            NaturalHarmonicRingProof.uniqueOwner(
                                    ring,
                                    identities,
                                    gray,
                                    labels,
                                    width,
                                    height,
                                    OmrMeasurePostProcessor.NOTEHEAD,
                                    textBoxes,
                                    budget);
                    if (owned == null) continue;
                    ringsByNote
                            .computeIfAbsent(owned.note().target(), ignored -> new ArrayList<>())
                            .add(owned);
                }
                for (var entry : ringsByNote.entrySet()) {
                    // Multiple complete rings competing for one exact note are ambiguous.
                    if (entry.getValue().size() != 1) continue;
                    var owned = entry.getValue().get(0);
                    var note =
                            score.notes().stream()
                                    .filter(
                                            n ->
                                                    entry.getKey()
                                                            .equals(
                                                                    ScoreNaturalHarmonicNotation
                                                                            .target(n)))
                                    .findFirst()
                                    .orElse(null);
                    if (note == null || note.kind() != ScoreNoteEvent.Kind.PITCHED) continue;
                    String id = "printed-natural-harmonic:" + owned.note().target();
                    if (!ids.add(id)) continue;
                    float visualX = Math.max(0, Math.min(1, owned.ring().centerX() / width));
                    ScoreAnchor anchor = null;
                    if (meter != null) {
                        float beats = meter.beatsInMeasure(note.measureIndex());
                        double onset = ScoreNoteTiming.beatInMeasure(note, score.notes(), beats);
                        if (!Double.isFinite(onset) || onset < 0 || onset >= beats) continue;
                        anchor =
                                ScoreAnchor.fromComputedTiming(
                                        note.measureIndex(), onset, meter, score.measures().size());
                    }
                    events.add(
                            new ScoreExpressiveEvent(
                                    id,
                                    ScoreExpressiveEvent.Kind.NATURAL_HARMONIC,
                                    java.util.Optional.ofNullable(anchor),
                                    java.util.Optional.empty(),
                                    meter == null
                                            ? ScoreExpressiveEvent.Scope.UNRESOLVED
                                            : ScoreExpressiveEvent.Scope.NOTE,
                                    note.staffIndex(),
                                    note.staffCount(),
                                    java.util.Optional.of(owned.note().target()),
                                    ScoreExpressiveEvent.Strength.UNSPECIFIED,
                                    "",
                                    List.of(
                                            new ScoreExpressiveEvent.Evidence(
                                                    ScoreNaturalHarmonicNotation.SOURCE,
                                                    physicalPage,
                                                    visualX,
                                                    note.staffIndex(),
                                                    note.staffCount(),
                                                    "○"))));
                }
            }
            return events.size() == score.expressiveEvents().size()
                    ? score
                    : score.withExpressiveEvents(events);
        } catch (IllegalArgumentException boundedRefusal) {
            // An exhausted or invalid local proof cannot emit a partial semantic interpretation.
            return score;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

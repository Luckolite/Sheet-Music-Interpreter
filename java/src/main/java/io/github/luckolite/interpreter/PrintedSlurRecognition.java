// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Final page-slot orchestration; physical page and written meter are explicit caller context. */
final class PrintedSlurRecognition {
    record Result(
            ScorePageInterpretation score,
            Map<PrintedSlurOwners.Status, Integer> statuses,
            Set<Integer> unboundOwners,
            int unprovedStaffs,
            boolean limitReached,
            boolean alreadyCaptured) {
        Result {
            Objects.requireNonNull(score);
            statuses = Map.copyOf(statuses);
            unboundOwners = Set.copyOf(unboundOwners);
        }
    }

    private PrintedSlurRecognition() {}

    private record TieEnd(int staffIndex, int staffCount, int pitch, long tick) {}

    /** Use only a page's actually retained opening meter; never invent a four-beat context. */
    static Optional<Result> captureObserved(
            ScorePageInterpretation score,
            int physicalPage,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs) {
        Objects.requireNonNull(score);
        if (physicalPage < 0) throw new IllegalArgumentException("Missing physical source page");
        ScoreMeterChange opening = null;
        for (var change : score.meterChanges()) if (change.measureIndex() == 0) opening = change;
        if (opening == null) {
            if (!score.slurSource().spans().isEmpty() || !score.slurSource().candidates().isEmpty())
                return Optional.of(new Result(score, Map.of(), Set.of(), 0, false, true));
            var heads =
                    PrintedSlurContext.geometryHeads(
                            score.notes(), score.measures(), width, height);
            var prepared =
                    PrintedSlurFrames.prepare(gray, width, height, staffs, score.notes(), heads);
            labels =
                    OmrScoreInterpreter.printedSlurLabels(
                            labels, gray, width, height, score.measures(), heads);
            var captured =
                    PrintedSlurPageCapture.captureGeometry(
                            physicalPage,
                            score.notes().size(),
                            gray,
                            labels,
                            width,
                            height,
                            prepared.frames());
            var result =
                    captured.limitReached() || captured.source().candidates().isEmpty()
                            ? score
                            : score.withSlurSource(captured.source());
            return Optional.of(
                    new Result(
                            result,
                            captured.statuses(),
                            prepared.unboundOwners(),
                            prepared.unprovedStaffs(),
                            captured.limitReached(),
                            false));
        }
        return Optional.of(
                capture(
                        score,
                        physicalPage,
                        gray,
                        labels,
                        width,
                        height,
                        staffs,
                        new ScoreMeterMap(opening.quarterBeats(), score.meterChanges()),
                        score.keyChanges()));
    }

    static Result capture(
            ScorePageInterpretation score,
            int physicalPage,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            ScoreMeterMap meter,
            List<ScoreKeyChange> keys) {
        Objects.requireNonNull(score);
        Objects.requireNonNull(meter);
        Objects.requireNonNull(keys);
        if (physicalPage < 0
                || width <= 0
                || height <= 0
                || gray == null
                || gray.length != (long) width * height
                || labels != null && labels.length != gray.length)
            throw new IllegalArgumentException("Invalid explicit printed page source");
        // A retained or edited source already owns its immutable spans. Never replace it
        // by reinterpreting the same pixels with a new selection order or musical context.
        if (!score.slurSource().spans().isEmpty() || !score.slurSource().candidates().isEmpty())
            return new Result(score, Map.of(), Set.of(), 0, false, true);
        var written = ScorePlacementMap.resolveWritten(score.notes(), meter);
        var pitches = new HashMap<Integer, Integer>();
        for (int i = 0; i < score.notes().size(); i++) {
            // The decoder has already applied printed accidental state to retained notes.
            // Reuse its existing final tie-guard pitch policy; do not infer an unknown key.
            int pitch = ScoreTiePitchGuard.midi(score.notes().get(i), keys);
            if (pitch >= 0 && pitch <= 127) pitches.put(i, pitch);
        }
        var heads =
                PrintedSlurContext.heads(
                        score.notes(),
                        score.measures(),
                        written,
                        meter,
                        Map.of(),
                        pitches,
                        width,
                        height);
        var prepared = PrintedSlurFrames.prepare(gray, width, height, staffs, score.notes(), heads);
        var ties = retainedTies(score.notes(), written, heads);
        labels =
                OmrScoreInterpreter.printedSlurLabels(
                        labels, gray, width, height, score.measures(), heads);
        var captured =
                PrintedSlurPageCapture.capture(
                        physicalPage,
                        score.notes().size(),
                        gray,
                        labels,
                        width,
                        height,
                        prepared.frames(),
                        ties);
        // The bounded scanner discards all partial spans if its admission/run cap is reached.
        if (captured.limitReached())
            return new Result(
                    score,
                    captured.statuses(),
                    prepared.unboundOwners(),
                    prepared.unprovedStaffs(),
                    true,
                    false);
        var result =
                captured.source().spans().isEmpty() && captured.source().candidates().isEmpty()
                        ? score
                        : score.withSlurSource(captured.source());
        return new Result(
                result,
                captured.statuses(),
                prepared.unboundOwners(),
                prepared.unprovedStaffs(),
                false,
                false);
    }

    private static Set<PrintedSlurOwners.Tie> retainedTies(
            List<ScoreNoteEvent> notes,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> written,
            List<PrintedSlurOwners.Head> heads) {
        // Index exact ordinary endings once; a long page must not compare every pair.
        // A collision preserves ambiguity rather than choosing a voice or note owner.
        var endings = new HashMap<TieEnd, Integer>();
        for (var head : heads) {
            var placement = written.get(head.owner());
            if (head.soundingPitch() < 0 || placement.grace()) continue;
            long duration = Math.round(placement.durationBeats() * 10080);
            if (duration <= 0 || head.attack() > Long.MAX_VALUE - duration) continue;
            var key =
                    new TieEnd(
                            head.staffIndex(),
                            head.staffCount(),
                            head.soundingPitch(),
                            head.attack() + duration);
            endings.merge(key, head.owner(), (a, b) -> -1);
        }
        var ties = new HashSet<PrintedSlurOwners.Tie>();
        for (var current : heads) {
            if (!notes.get(current.owner()).tiedFromPrevious()
                    || current.soundingPitch() < 0
                    || written.get(current.owner()).grace()) continue;
            int prior =
                    endings.getOrDefault(
                            new TieEnd(
                                    current.staffIndex(),
                                    current.staffCount(),
                                    current.soundingPitch(),
                                    current.attack()),
                            -1);
            if (prior >= 0 && prior != current.owner())
                ties.add(new PrintedSlurOwners.Tie(prior, current.owner()));
        }
        return Set.copyOf(ties);
    }
}

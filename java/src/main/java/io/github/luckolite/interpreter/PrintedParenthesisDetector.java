// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Captures a complete printed pair as one note group, with no inferred performance effect. */
public final class PrintedParenthesisDetector {
    private PrintedParenthesisDetector() {}

    public static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<PlayingTechniqueDetector.Word> words,
            int physicalPage) {
        return apply(
                score,
                gray,
                width,
                height,
                staffs,
                words,
                physicalPage,
                128000000,
                128000000,
                128000000,
                128000000);
    }

    static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<PlayingTechniqueDetector.Word> words,
            int physicalPage,
            long ordinaryAllowance,
            long referenceAllowance,
            long calibratedAllowance,
            long bindingAllowance) {
        if (score == null
                || gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || physicalPage < 0
                || staffs == null
                || words == null
                || score.notes().size() > 4096
                || staffs.size() > 128
                || words.size() > 10000
                || score.measures().isEmpty()) return score;
        List<PrintedSlurPageCapture.Frame<PrintedSlurOwners.GeometryHead>> frames;
        try {
            if (staffs.stream().anyMatch(Objects::isNull)
                    || words.stream().anyMatch(Objects::isNull)) return score;
            for (var word : words)
                if (!Float.isFinite(word.left() + word.right() + word.top() + word.bottom())
                        || word.left() < 0
                        || word.top() < 0
                        || word.right() > 1
                        || word.bottom() > 1
                        || word.left() > word.right()
                        || word.top() > word.bottom()) return score;
            var heads =
                    PrintedSlurContext.geometryHeads(
                            score.notes(), score.measures(), width, height);
            frames =
                    PrintedSlurFrames.prepare(gray, width, height, staffs, score.notes(), heads)
                            .frames();
        } catch (IllegalArgumentException invalidContext) {
            return score;
        }
        var ordinary = new PrintedParenthesisGroups.Budget(ordinaryAllowance);
        var references = new LiteralStaffContrast.CatalogBudget(referenceAllowance);
        var calibrated = new PrintedParenthesisGroups.Budget(calibratedAllowance);
        var binding = new PrintedParenthesisGroups.Budget(bindingAllowance);
        var first =
                PrintedParenthesisGroups.discover(
                        gray, width, height, frames, false, references, ordinary);
        if (ordinary.refused()) return score;
        var second =
                PrintedParenthesisGroups.discover(
                        gray, width, height, frames, true, references, calibrated);
        if (references.refused() || calibrated.refused()) return score;
        var groups =
                PrintedParenthesisGroups.bind(
                        first, second, frames, gray, width, height, score.notes(), words, binding);
        if (binding.refused() || groups.isEmpty()) return score;
        var physicalHeads = new ArrayList<PrintedSlurOwners.Location>();
        for (var frame : frames) physicalHeads.addAll(frame.heads());
        var events = new ArrayList<>(score.expressiveEvents());
        var ids = new HashSet<String>();
        for (var mark : events) ids.add(mark.eventId());
        for (var group : groups) {
            ScoreParenthesizedNoteGroup.Pair pair;
            try {
                pair = ScoreParenthesizedNoteGroup.capture(group, score.notes(), physicalHeads);
            } catch (IllegalArgumentException invalidOwnership) {
                continue;
            }
            String id = "printed-parenthesis:" + physicalPage + ":" + pair.target();
            if (!ids.add(id)) continue;
            var note = score.notes().get(group.members().get(0));
            events.add(
                    new ScoreExpressiveEvent(
                            id,
                            ScoreExpressiveEvent.Kind.PARENTHESIZED_NOTE_GROUP,
                            Optional.empty(),
                            Optional.empty(),
                            ScoreExpressiveEvent.Scope.UNRESOLVED,
                            note.staffIndex(),
                            note.staffCount(),
                            Optional.of(pair.target()),
                            ScoreExpressiveEvent.Strength.UNSPECIFIED,
                            pair.qualifier(),
                            List.of(
                                    new ScoreExpressiveEvent.Evidence(
                                            ScoreParenthesizedNoteGroup.SOURCE,
                                            physicalPage,
                                            Math.max(
                                                    0, Math.min(1, group.left().centerX() / width)),
                                            note.staffIndex(),
                                            note.staffCount(),
                                            "( note group )"))));
        }
        return events.size() == score.expressiveEvents().size()
                ? score
                : score.withExpressiveEvents(events);
    }
}

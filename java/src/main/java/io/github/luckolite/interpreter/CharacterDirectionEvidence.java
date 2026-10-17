// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Shared geometric musical-role proof. Callers supply independently verified literal words,
 * never scalar dynamic events or unverified OCR tokens. Acquisition retains its typed proof. */
final class CharacterDirectionEvidence {
    private CharacterDirectionEvidence() {}

    record Evidence(
            PlayingTechniqueDetector.Word word,
            PlayingTechniqueDetector.Word literal,
            int measureIndex,
            int staffIndex,
            int staffCount,
            float writtenColumn,
            OcrText.Grouping grouping) {}

    static List<Evidence> collect(
            OcrText text,
            float scale,
            int top,
            List<PlayingTechniqueDetector.Word> literals,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        if (text == null
                || text.grouping() == OcrText.Grouping.UNKNOWN
                || scale <= 0
                || width <= 0
                || height <= 0
                || gray == null
                || gray.length != (long) width * height) return List.of();
        var out = new ArrayList<Evidence>();
        for (var block : text.blocks())
            for (var line : block.lines()) {
                if (!EnglishCharacterOcrContext.character(line.text())
                        || EnglishCharacterOcrContext.ownedByProse(text, block, line)) continue;
                var b = line.box();
                if (b == null || b.width() <= 0 || b.height() <= 0) continue;
                var word =
                        new PlayingTechniqueDetector.Word(
                                line.text(),
                                b.left / scale / width,
                                (top + b.top / scale) / height,
                                b.right / scale / width,
                                (top + b.bottom / scale) / height);
                if (!ScoreDynamicsDetector.containsInk(word, gray, width, height)) continue;
                var candidates = new ArrayList<Evidence>();
                for (var staff : staffs) {
                    float gap = staff.gap(), cy = (word.top() + word.bottom()) * height * .5f;
                    if (gap < 4 || cy < staff.top() - 5.5f * gap || cy > staff.top() - .25f * gap)
                        continue;
                    for (int m = 0; m < measures.size(); m++) {
                        var region = measures.get(m);
                        float left = word.left() * width;
                        if (left < region.left() * width - gap * .6f
                                || left > region.right() * width) continue;
                        var columns = new TreeSet<Float>();
                        for (var n : notes)
                            if (n.measureIndex() == m
                                    && n.staffIndex() == staff.index()
                                    && n.staffCount() == staff.count()
                                    && n.pageY() * height >= staff.top() - 2 * gap
                                    && n.pageY() * height <= staff.bottom() + 3 * gap) {
                                float x =
                                        (region.left()
                                                        + n.positionInMeasure()
                                                                * (region.right() - region.left()))
                                                * width;
                                if (Math.abs(x - left) <= gap * 2.5f) columns.add(x);
                            }
                        if (columns.size() != 1) continue;
                        float column = columns.first();
                        for (var literal : literals) {
                            var lw = literal;
                            float lx = (lw.left() + lw.right()) * width * .5f;
                            // Bracket the actual upper staff: direction above, independently
                            // matched dynamic below.
                            if (lw.top() * height < staff.bottom() + gap * .25f
                                    || lw.bottom() * height > staff.bottom() + gap * 5.5f) continue;
                            if (Math.abs(lx - column) > gap * 1.5f) continue;
                            // Actual dynamic detector supplies musical scope from the independently
                            // verified literal.
                            var detected =
                                    ScoreDynamicsDetector.detectWithEvidence(
                                            List.of(lw),
                                            staffs,
                                            measures,
                                            notes,
                                            gray,
                                            width,
                                            height);
                            for (var d : detected.changes())
                                if (d.direction() == 0
                                        && d.measureIndex() == m
                                        && d.staffCount() == staff.count()
                                        && (d.staffIndex() == staff.index() || d.sharedStaffs())
                                        && Math.abs(
                                                        (region.left()
                                                                                + d
                                                                                                .positionInMeasure()
                                                                                        * (region
                                                                                                        .right()
                                                                                                - region
                                                                                                        .left()))
                                                                        * width
                                                                - column)
                                                <= gap * 1.5f) {
                                    var admitted =
                                            new PlayingTechniqueDetector.Word(
                                                    word.text(),
                                                    word.left(),
                                                    word.top(),
                                                    word.right(),
                                                    word.bottom(),
                                                    PlayingTechniqueDetector.WordAdmission
                                                            .CONTEXT_QUALIFIED);
                                    candidates.add(
                                            new Evidence(
                                                    admitted,
                                                    literal,
                                                    m,
                                                    staff.index(),
                                                    staff.count(),
                                                    column,
                                                    text.grouping()));
                                }
                        }
                    }
                }
                // A shared grandstaff literal may contain RH; two different actual written
                // columns/staves abstain.
                var owners = new HashSet<String>();
                for (var e : candidates)
                    owners.add(
                            e.measureIndex()
                                    + ":"
                                    + e.staffIndex()
                                    + ":"
                                    + e.staffCount()
                                    + ":"
                                    + e.writtenColumn());
                if (owners.size() == 1 && !candidates.isEmpty()) out.add(candidates.get(0));
            }
        return List.copyOf(out);
    }
}

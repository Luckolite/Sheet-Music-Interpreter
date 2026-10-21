// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** A beamed note head inside a purported letter box is independent notation evidence. */
final class PrintedDynamicNoteOwnership {
    private PrintedDynamicNoteOwnership() {}

    static boolean containsBeamedHead(
            PlayingTechniqueDetector.Word word,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {
        if (word.right() <= word.left() || word.bottom() <= word.top()) return false;
        for (var note : notes) {
            if (note.beamCount() <= 0
                    || note.measureIndex() < 0
                    || note.measureIndex() >= measures.size()) continue;
            var measure = measures.get(note.measureIndex());
            float x =
                    measure.left() + (measure.right() - measure.left()) * note.positionInMeasure();
            if (x >= word.left()
                    && x <= word.right()
                    && note.pageY() >= word.top()
                    && note.pageY() <= word.bottom()) return true;
        }
        return false;
    }

    /** A raw two-cycle symbol already owned as this note's mordent is not a second dynamic. */
    static boolean containsOwnedMordent(
            PlayingTechniqueDetector.Word word,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        if (!word.text().trim().equalsIgnoreCase("m")
                || gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || !Float.isFinite(word.left() + word.top() + word.right() + word.bottom())
                || word.left() < 0
                || word.top() < 0
                || word.right() > 1
                || word.bottom() > 1
                || word.right() <= word.left()
                || word.bottom() <= word.top()) return false;
        int left = Math.round(word.left() * width),
                right = Math.round(word.right() * width),
                top = Math.round(word.top() * height),
                bottom = Math.round(word.bottom() * height);
        int il = right, it = bottom, ir = left, ib = top;
        for (int y = top; y < bottom; y++)
            for (int x = left; x < right; x++)
                if ((gray[y * width + x] & 255) < 180) {
                    il = Math.min(il, x);
                    ir = Math.max(ir, x + 1);
                    it = Math.min(it, y);
                    ib = Math.max(ib, y + 1);
                }
        if (il >= ir
                || it >= ib
                || !(MordentContour.matches(
                                gray,
                                width,
                                new PortableNoteOrnaments.Bounds(left, top, right, bottom))
                        || MordentContour.matches(
                                gray, width, new PortableNoteOrnaments.Bounds(il, it, ir, ib))))
            return false;
        for (var note : notes) {
            if (NoteOrnament.type(note.articulations()) != NoteOrnament.MORDENT
                    || note.measureIndex() < 0
                    || note.measureIndex() >= measures.size()) continue;
            var measure = measures.get(note.measureIndex());
            float x =
                    (measure.left() + (measure.right() - measure.left()) * note.positionInMeasure())
                            * width;
            if (x < il || x >= ir) continue;
            for (var staff : staffs) {
                if (staff.index() != note.staffIndex()
                        || !Float.isFinite(staff.gap())
                        || staff.gap() <= 0) continue;
                float gap = staff.gap(), dy = (note.pageY() * height - (it + ib) * .5f) / gap;
                if (ib <= staff.top() + gap * .1f
                        && ib >= staff.top() - gap * 5.5f
                        && dy >= .65f
                        && dy <= 10
                        && note.pageY() * height >= staff.top() - gap * 2.5f
                        && note.pageY() * height <= staff.bottom() + gap * 2.5f) return true;
            }
        }
        return false;
    }
}

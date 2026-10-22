// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** A short printed rest inside one complete attached beam belongs to that moving voice. */
final class BeamedRestVoice {
    private BeamedRestVoice() {}

    static boolean owns(
            ScoreRestEvent rest,
            ScoreNoteEvent before,
            List<ScoreNoteEvent> notes,
            MeasureRegion region,
            byte[] gray,
            int width,
            int height,
            float gap) {
        if (rest == null
                || before == null
                || notes == null
                || region == null
                || gray == null
                || gray.length != (long) width * height
                || width < 3
                || height < 3
                || !Float.isFinite(gap)
                || gap < 4
                || gap > 64
                || rest.kind() != ScoreRestEvent.Kind.LITERAL
                || rest.durationBeats() < .125
                || rest.durationBeats() > .5
                || rest.measureIndex() != before.measureIndex()
                || rest.staffIndex() != before.staffIndex()
                || rest.staffCount() != before.staffCount()
                || before.beamCount() < 1
                || before.crossStaffBeam()
                || (before.articulations() & NoteOrnament.GRACE) != 0
                || rest.positionInMeasure() <= before.positionInMeasure()) return false;
        ScoreNoteEvent after = null;
        for (var n : notes) {
            if (n == null
                    || n.measureIndex() != before.measureIndex()
                    || n.staffIndex() != before.staffIndex()
                    || n.staffCount() != before.staffCount()
                    || (n.articulations() & NoteOrnament.GRACE) != 0) continue;
            if (n.positionInMeasure() > before.positionInMeasure() + .018f
                    && (after == null || n.positionInMeasure() < after.positionInMeasure()))
                after = n;
        }
        if (after == null
                || after.positionInMeasure() <= rest.positionInMeasure()
                || after.beamCount() < 1
                || after.crossStaffBeam()) return false;
        float span = region.right() - region.left();
        float ax = (region.left() + before.positionInMeasure() * span) * width,
                bx = (region.left() + after.positionInMeasure() * span) * width;
        float rx = (region.left() + rest.positionInMeasure() * span) * width;
        if (!Float.isFinite(ax)
                || !Float.isFinite(bx)
                || !Float.isFinite(rx)
                || bx - ax < gap * 2
                || bx - ax > gap * 18
                || rx - ax < gap * .8f
                || bx - rx < gap * .8f) return false;
        int direction =
                PrintedStemDirection.detect(gray, width, height, ax, before.pageY() * height, gap);
        if (direction == 0
                || PrintedStemDirection.detect(gray, width, height, bx, after.pageY() * height, gap)
                        != direction) return false;
        if (before.stemDirection() != 0 && before.stemDirection() != direction
                || after.stemDirection() != 0 && after.stemDirection() != direction) return false;
        for (var n : notes) {
            if (n == before
                    || n == after
                    || n.measureIndex() != before.measureIndex()
                    || n.staffIndex() != before.staffIndex()
                    || n.staffCount() != before.staffCount()) continue;
            if (Math.abs(n.positionInMeasure() - before.positionInMeasure()) <= .018f
                    || Math.abs(n.positionInMeasure() - after.positionInMeasure()) <= .018f) {
                float x = (region.left() + n.positionInMeasure() * span) * width;
                int d =
                        n.stemDirection() != 0
                                ? n.stemDirection()
                                : PrintedStemDirection.detect(
                                        gray, width, height, x, n.pageY() * height, gap);
                if (d != 0 && d != direction) return false;
            }
        }
        return PrintedTupletBeamOwner.connectedHeads(
                gray,
                width,
                height,
                ax,
                before.pageY() * height,
                bx,
                after.pageY() * height,
                gap,
                direction);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A printed level immediately following a proved wedge supplies its semantic arrival column. */
final class PrintedHairpinArrival {
    private PrintedHairpinArrival() {}

    record Level(
            PlayingTechniqueDetector.Word word,
            PlayingTechniqueDetector.Staff owner,
            int measure,
            float position,
            boolean actualColumn) {}

    record Point(int measure, float position) {}

    record Arrival(Optional<Point> column, boolean observed) {}

    static Arrival resolve(
            List<Level> levels,
            PlayingTechniqueDetector.Staff owner,
            float right,
            int endMeasure,
            float endPosition,
            List<MeasureRegion> measures,
            int width,
            int height) {
        if (levels == null
                || owner == null
                || measures == null
                || width < 1
                || height < 1
                || !Float.isFinite(right + owner.gap() + owner.top() + owner.bottom() + endPosition)
                || owner.gap() < 3
                || owner.bottom() <= owner.top()
                || endMeasure < 0
                || endMeasure >= measures.size()) return new Arrival(Optional.empty(), false);
        Level first = null;
        float firstLeft = Float.POSITIVE_INFINITY;
        boolean ambiguous = false;
        boolean observed = false, overlapping = false;
        for (var level : levels) {
            if (level == null
                    || level.word == null
                    || level.owner == null
                    || !Float.isFinite(level.owner.top() + level.owner.bottom() + level.owner.gap())
                    || level.owner.index() != owner.index()
                    || level.owner.count() != owner.count()
                    || Math.abs(
                                    (level.owner.top()
                                                    + level.owner.bottom()
                                                    - owner.top()
                                                    - owner.bottom())
                                            * .5f)
                            > owner.gap() * 1.5f
                    || level.measure < endMeasure
                    || level.measure > endMeasure + 1
                    || level.measure >= measures.size()
                    || level.position < 0
                    || level.position > 1
                    || level.measure == endMeasure && level.position < endPosition) continue;
            var word = level.word;
            var region = measures.get(level.measure);
            float left = word.left() * width,
                    centerY = (owner.top() + owner.bottom()) * .5f / height;
            if (!Float.isFinite(left + word.right() + word.top() + word.bottom() + level.position)
                    || word.right() <= word.left()
                    || word.bottom() <= word.top()
                    || centerY < region.top()
                    || centerY > region.bottom()
                    || word.right() * width < right
                    || left - right > owner.gap() * 4
                    || (region.left() + level.position * (region.right() - region.left())) * width
                            < right) continue;
            observed = true;
            if (left < right - owner.gap() * .35f) {
                overlapping = true;
                continue;
            }
            if (left < firstLeft) {
                first = level;
                firstLeft = left;
                ambiguous = false;
            } else if (left == firstLeft
                    && first != null
                    && (first.measure != level.measure || first.position != level.position))
                ambiguous = true;
        }
        return new Arrival(
                first == null || ambiguous || overlapping || !first.actualColumn
                        ? Optional.empty()
                        : Optional.of(new Point(first.measure, first.position)),
                observed);
    }
}

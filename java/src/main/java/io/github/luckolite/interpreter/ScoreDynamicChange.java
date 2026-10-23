// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Staff-local absolute dynamic (direction 0) or hairpin (+1/-1). Positions are score geometry. */
public record ScoreDynamicChange(
        int measureIndex,
        float positionInMeasure,
        int staffIndex,
        int staffCount,
        int endMeasureIndex,
        float endPosition,
        float decibels,
        int direction,
        boolean sharedStaffs,
        boolean fixedTarget,
        boolean sharedTiming) {
    /** Timing may remain shared while a projected hairpin has a distinct target on each staff. */
    public ScoreDynamicChange(
            int measureIndex,
            float positionInMeasure,
            int staffIndex,
            int staffCount,
            int endMeasureIndex,
            float endPosition,
            float decibels,
            int direction,
            boolean sharedStaffs,
            boolean fixedTarget) {
        this(
                measureIndex,
                positionInMeasure,
                staffIndex,
                staffCount,
                endMeasureIndex,
                endPosition,
                decibels,
                direction,
                sharedStaffs,
                fixedTarget,
                sharedStaffs);
    }

    /** Fixed-target records bind the exact staff topology; hairpins end at decibels, not a relative change. */
    public ScoreDynamicChange(
            int measureIndex,
            float positionInMeasure,
            int staffIndex,
            int staffCount,
            int endMeasureIndex,
            float endPosition,
            float decibels,
            int direction,
            boolean sharedStaffs) {
        this(
                measureIndex,
                positionInMeasure,
                staffIndex,
                staffCount,
                endMeasureIndex,
                endPosition,
                decibels,
                direction,
                sharedStaffs,
                false);
    }

    public ScoreDynamicChange(
            int measureIndex,
            float positionInMeasure,
            int staffIndex,
            int staffCount,
            int endMeasureIndex,
            float endPosition,
            float decibels,
            int direction) {
        this(
                measureIndex,
                positionInMeasure,
                staffIndex,
                staffCount,
                endMeasureIndex,
                endPosition,
                decibels,
                direction,
                false);
    }

    public ScoreDynamicChange {
        sharedTiming |= sharedStaffs;
        if (measureIndex < 0
                || endMeasureIndex < measureIndex
                || staffCount < 1
                || staffCount > 8
                || staffIndex < 0
                || staffIndex >= staffCount
                || !Float.isFinite(positionInMeasure)
                || !Float.isFinite(endPosition)
                || positionInMeasure < 0
                || positionInMeasure > 1
                || endPosition < 0
                || endPosition > 1
                || !Float.isFinite(decibels)
                || decibels < -24
                || decibels > 9
                || Math.abs(direction) > 1
                || endMeasureIndex == measureIndex && endPosition < positionInMeasure)
            throw new IllegalArgumentException("Invalid dynamic change");
    }

    /** Half-open overlap in score geometry; a bar's end1 equals the next bar's start0. */
    public boolean overlapsScoreSpan(ScoreDynamicChange other) {
        java.util.Objects.requireNonNull(other);
        return comparePosition(measureIndex, positionInMeasure, endMeasureIndex, endPosition) < 0
                && comparePosition(
                                other.measureIndex,
                                other.positionInMeasure,
                                other.endMeasureIndex,
                                other.endPosition)
                        < 0
                && comparePosition(
                                measureIndex,
                                positionInMeasure,
                                other.endMeasureIndex,
                                other.endPosition)
                        < 0
                && comparePosition(
                                other.measureIndex,
                                other.positionInMeasure,
                                endMeasureIndex,
                                endPosition)
                        < 0;
    }

    private static int comparePosition(int a, float x, int b, float y) {
        long am = (long) a + (x == 1 ? 1 : 0), bm = (long) b + (y == 1 ? 1 : 0);
        int bars = Long.compare(am, bm);
        return bars != 0 ? bars : Float.compare(x == 0 || x == 1 ? 0 : x, y == 0 || y == 1 ? 0 : y);
    }

    public ScoreDynamicChange offset(int measures) {
        return new ScoreDynamicChange(
                measureIndex + measures,
                positionInMeasure,
                staffIndex,
                staffCount,
                endMeasureIndex + measures,
                endPosition,
                decibels,
                direction,
                sharedStaffs,
                fixedTarget,
                sharedTiming);
    }
}

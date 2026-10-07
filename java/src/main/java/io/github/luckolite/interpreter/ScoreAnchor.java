// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A musical quarter-beat offset, never an engraving fraction. */
public record ScoreAnchor(int measureIndex, double quarterBeatOffset)
        implements Comparable<ScoreAnchor> {
    public ScoreAnchor {
        if (measureIndex < 0
                || !Double.isFinite(quarterBeatOffset)
                || quarterBeatOffset < 0
                || quarterBeatOffset > 128)
            throw new IllegalArgumentException("Invalid musical anchor");
    }

    /** Normalizes arithmetic roundoff past a bar end; genuine overruns remain unchanged. */
    public static double computedOffset(int measureIndex, double offset, ScoreMeterMap meter) {
        if (!Double.isFinite(offset) || offset < 0 || measureIndex < 0) return offset;
        double length = meter.beatsInMeasure(measureIndex);
        double scale = Math.max(length, Math.abs(meter.startBeat(measureIndex) + length));
        double rounding = 4 * Math.ulp(scale);
        return offset > length && offset - length <= rounding ? length : offset;
    }

    /** Builds an anchor from computed time while keeping stored-anchor validation strict. */
    public static ScoreAnchor fromComputedTiming(
            int measureIndex, double offset, ScoreMeterMap meter, int measureCount) {
        if (measureIndex >= 0 && measureIndex < measureCount)
            offset = computedOffset(measureIndex, offset, meter);
        return new ScoreAnchor(measureIndex, offset).canonical(meter, measureCount);
    }

    public ScoreAnchor canonical(ScoreMeterMap meter, int measureCount) {
        if (measureCount < 0
                || measureIndex > measureCount
                || measureIndex == measureCount && quarterBeatOffset != 0)
            throw new IllegalArgumentException("Anchor outside score");
        if (measureIndex == measureCount) return this;
        double length = meter.beatsInMeasure(measureIndex);
        if (quarterBeatOffset > length)
            throw new IllegalArgumentException("Anchor beyond bar duration");
        return quarterBeatOffset == length ? new ScoreAnchor(measureIndex + 1, 0) : this;
    }

    public double absoluteBeat(ScoreMeterMap meter) {
        if (quarterBeatOffset > meter.beatsInMeasure(measureIndex))
            throw new IllegalArgumentException("Anchor beyond bar duration");
        return meter.startBeat(measureIndex) + quarterBeatOffset;
    }

    public ScoreAnchor offset(int measures) {
        return new ScoreAnchor(Math.addExact(measureIndex, measures), quarterBeatOffset);
    }

    @Override
    public int compareTo(ScoreAnchor other) {
        int order = Integer.compare(measureIndex, other.measureIndex);
        return order != 0 ? order : Double.compare(quarterBeatOffset, other.quarterBeatOffset);
    }
}

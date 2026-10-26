// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Converts logical bars to cumulative quarter-note beats without assuming equal bar lengths. */
public final class ScoreMeterMap {
    private final float opening;
    private final List<ScoreMeterChange> changes;
    private final double[] performedStarts;
    private final boolean exactAnchorMode;
    private final double[] exactSpans;

    public ScoreMeterMap(float opening, List<ScoreMeterChange> changes) {
        this.opening = Float.isFinite(opening) ? Math.max(.125f, Math.min(128, opening)) : 4;
        ArrayList<ScoreMeterChange> ordered = new ArrayList<>();
        if (changes != null)
            for (ScoreMeterChange change : changes) if (change != null) ordered.add(change);
        ordered.sort(Comparator.comparingInt(ScoreMeterChange::measureIndex));
        this.changes = List.copyOf(ordered);
        this.performedStarts = null;
        this.exactAnchorMode = false;
        this.exactSpans = null;
    }

    private ScoreMeterMap(double[] starts) {
        this(starts, null);
    }

    private ScoreMeterMap(double[] starts, double[] exactSpans) {
        this.opening = starts.length > 1 ? (float) (starts[1] - starts[0]) : 4;
        this.changes = List.of();
        this.performedStarts = starts;
        this.exactAnchorMode = exactSpans != null;
        this.exactSpans = exactSpans;
    }

    /** Exact performed segment lengths, not newly recognized printed time signatures. */
    public static ScoreMeterMap fromPerformedDurations(List<Double> durations) {
        if (durations.size() > 1000000)
            throw new IllegalArgumentException("Too many performed segments");
        var starts = new double[durations.size() + 1];
        for (int i = 0; i < durations.size(); i++) {
            double value = durations.get(i);
            if (!Double.isFinite(value) || value <= 0 || value > 128)
                throw new IllegalArgumentException("Invalid performed segment length");
            starts[i + 1] = starts[i] + value;
            if (!Double.isFinite(starts[i + 1]) || starts[i + 1] <= starts[i])
                throw new IllegalArgumentException("Performed segment loses clock precision");
        }
        return new ScoreMeterMap(starts);
    }

    public java.util.OptionalInt performedMeasureCount() {
        return performedStarts == null
                ? java.util.OptionalInt.empty()
                : java.util.OptionalInt.of(performedStarts.length - 1);
    }

    /** Replace a proved partial opening span; printed meter changes remain caller-owned. */
    public ScoreMeterMap withOpeningQuarterBeats(double duration, int measureCount) {
        if (measureCount < 1
                || measureCount > 1000000
                || !Double.isFinite(duration)
                || duration <= 0
                || duration > quarterBeatsInMeasure(0))
            throw new IllegalArgumentException("Invalid opening span");
        return withBoundaryQuarterBeats(duration, 0, measureCount);
    }

    /** Zero means unproved: retain the nominal boundary span. */
    public ScoreMeterMap withBoundaryQuarterBeats(
            double openingSpan, double closingSpan, int measureCount) {
        if (measureCount < 1
                || measureCount > 1000000
                || !Double.isFinite(openingSpan)
                || !Double.isFinite(closingSpan)
                || openingSpan < 0
                || closingSpan < 0
                || openingSpan > quarterBeatsInMeasure(0)
                || closingSpan > quarterBeatsInMeasure(measureCount - 1)
                || (measureCount == 1
                        && openingSpan > 0
                        && closingSpan > 0
                        && Math.abs(openingSpan - closingSpan) > .001))
            throw new IllegalArgumentException("Invalid boundary span");
        List<Double> spans = new ArrayList<>(measureCount);
        for (int i = 0; i < measureCount; i++) {
            double span = quarterBeatsInMeasure(i);
            if (i == 0 && openingSpan > 0) span = openingSpan;
            if (i == measureCount - 1 && closingSpan > 0) span = closingSpan;
            spans.add(span);
        }
        return fromPerformedDurations(spans);
    }

    public double quarterBeatsInMeasure(int measure) {
        if (performedStarts == null) return beatsInMeasure(measure);
        if (performedStarts.length == 1) return 4;
        int index = Math.max(0, Math.min(performedStarts.length - 2, measure));
        return exactSpans != null
                ? exactSpans[index]
                : performedStarts[index + 1] - performedStarts[index];
    }

    public float beatsInMeasure(int measure) {
        if (performedStarts != null)
            return Math.max(Float.MIN_VALUE, (float) quarterBeatsInMeasure(measure));
        float beats = opening;
        for (ScoreMeterChange change : changes) {
            if (change.measureIndex() > measure) break;
            beats = change.quarterBeats();
        }
        return beats;
    }

    public double startBeat(int measure) {
        if (performedStarts != null) {
            int target = Math.max(0, measure), last = performedStarts.length - 1;
            return target <= last
                    ? performedStarts[target]
                    : performedStarts[last] + (target - last) * quarterBeatsInMeasure(last);
        }
        int target = Math.max(0, measure), previous = 0;
        double total = 0;
        float beats = opening;
        for (ScoreMeterChange change : changes) {
            if (change.measureIndex() > target) break;
            total += (change.measureIndex() - previous) * (double) beats;
            previous = change.measureIndex();
            beats = change.quarterBeats();
        }
        return total + (target - previous) * (double) beats;
    }

    public double beatAt(int measure, double fraction) {
        return startBeat(measure)
                + Math.max(0, Math.min(1, fraction)) * quarterBeatsInMeasure(measure);
    }

    /** Integer part is the logical measure; fractional part is progress within that measure. */
    public double measurePosition(double beat) {
        double target = Double.isFinite(beat) ? Math.max(0, beat) : 0;
        if (performedStarts != null) {
            int last = performedStarts.length - 1;
            if (target >= performedStarts[last])
                return last + (target - performedStarts[last]) / quarterBeatsInMeasure(last);
            int index = java.util.Arrays.binarySearch(performedStarts, target);
            if (index >= 0) return index;
            index = -index - 2;
            return index + (target - performedStarts[index]) / quarterBeatsInMeasure(index);
        }
        int previous = 0;
        double total = 0;
        float beats = opening;
        for (ScoreMeterChange change : changes) {
            double next = total + (change.measureIndex() - previous) * (double) beats;
            if (target < next) return previous + (target - total) / beats;
            total = next;
            previous = change.measureIndex();
            beats = change.quarterBeats();
        }
        return previous + (target - total) / beats;
    }

    /** Explicit audio frame only; legacy factories and float note inference stay unchanged. */
    public ScoreMeterMap withExactBoundaryQuarterBeats(double opening, double closing, int count) {
        var frame = withBoundaryQuarterBeats(opening, closing, count);
        var spans = new double[count];
        for (int i = 0; i < count; i++) {
            double span = quarterBeatsInMeasure(i);
            if (i == 0 && opening > 0) span = opening;
            if (i == count - 1 && closing > 0) span = closing;
            spans[i] = span;
        }
        return new ScoreMeterMap(frame.performedStarts, spans);
    }

    public static ScoreMeterMap fromExactPerformedDurations(List<Double> durations) {
        var snapshot = List.copyOf(durations);
        var frame = fromPerformedDurations(snapshot);
        var spans = new double[snapshot.size()];
        for (int i = 0; i < spans.length; i++) spans[i] = snapshot.get(i);
        return new ScoreMeterMap(frame.performedStarts, spans);
    }

    public double anchorQuarterBeatsInMeasure(int measure) {
        return exactAnchorMode ? quarterBeatsInMeasure(measure) : beatsInMeasure(measure);
    }

    public boolean exactAnchorMode() {
        return exactAnchorMode;
    }
}

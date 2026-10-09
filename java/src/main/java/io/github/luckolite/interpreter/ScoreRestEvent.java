// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Printed duration and full-measure silence have distinct, retained semantics. */
public record ScoreRestEvent(
        int measureIndex,
        float positionInMeasure,
        float pageY,
        float pageHeight,
        int staffIndex,
        int staffCount,
        double durationBeats,
        Kind kind) {
    public enum Kind {
        LITERAL,
        FULL_MEASURE
    }

    public ScoreRestEvent {
        if (kind == null) kind = Kind.LITERAL; // Legacy serialized records omit the kind field.
        if (kind == Kind.FULL_MEASURE && durationBeats != 4)
            throw new IllegalArgumentException(
                    "Full-measure glyph must retain the undotted whole-rest base");
    }

    public ScoreRestEvent(int m, float x, float y, float h, int staff, int count, double duration) {
        this(m, x, y, h, staff, count, duration, Kind.LITERAL);
    }

    public ScoreRestEvent(int m, float x, float y, float h, int staff, int count) {
        this(m, x, y, h, staff, count, .25, Kind.LITERAL);
    }

    public static ScoreRestEvent fullMeasure(
            int m, float x, float y, float h, int staff, int count) {
        return new ScoreRestEvent(m, x, y, h, staff, count, 4, Kind.FULL_MEASURE);
    }

    public boolean isFullMeasure() {
        return kind == Kind.FULL_MEASURE;
    }

    /** Caller supplies the actual bar span, including proved pickups and meter changes. */
    public double resolvedDurationBeats(double actualMeasureBeats) {
        return !isFullMeasure()
                ? durationBeats
                : Double.isFinite(actualMeasureBeats) && actualMeasureBeats > 0
                        ? actualMeasureBeats
                        : Double.NaN;
    }

    public ScoreRestEvent withMeasureIndex(int value) {
        return new ScoreRestEvent(
                value,
                positionInMeasure,
                pageY,
                pageHeight,
                staffIndex,
                staffCount,
                durationBeats,
                kind);
    }
}

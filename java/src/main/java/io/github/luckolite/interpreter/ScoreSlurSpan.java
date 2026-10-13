// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Objects;

/** Explicit source note ownership for a printed phrase arc, independent of sounding ties. */
public record ScoreSlurSpan(
        String id,
        int startOwner,
        int endOwner,
        Placement placement,
        LineType lineType,
        List<ArcEvidence> evidence) {
    public enum Placement {
        AUTO,
        ABOVE,
        BELOW
    }

    public enum LineType {
        SOLID,
        DASHED,
        DOTTED
    }

    /** Original page geometry survives reflow; x may decrease across a system break. */
    public record ArcEvidence(int pageIndex, float startX, float startY, float endX, float endY) {
        public ArcEvidence {
            if (pageIndex < 0) throw new IllegalArgumentException("Invalid slur evidence page");
            for (float value : new float[] {startX, startY, endX, endY})
                if (!Float.isFinite(value) || value < 0 || value > 1)
                    throw new IllegalArgumentException("Invalid slur evidence geometry");
        }
    }

    public ScoreSlurSpan {
        Objects.requireNonNull(id);
        Objects.requireNonNull(placement);
        Objects.requireNonNull(lineType);
        evidence = List.copyOf(evidence);
        if (id.isBlank()
                || startOwner < 0
                || endOwner < 0
                || startOwner == endOwner
                || evidence.isEmpty())
            throw new IllegalArgumentException("Invalid slur source ownership");
    }

    /** Page concatenation offsets source owners, never printed geometry or span identity. */
    public ScoreSlurSpan offsetOwners(int offset) {
        return new ScoreSlurSpan(
                id,
                Math.addExact(startOwner, offset),
                Math.addExact(endOwner, offset),
                placement,
                lineType,
                evidence);
    }
}

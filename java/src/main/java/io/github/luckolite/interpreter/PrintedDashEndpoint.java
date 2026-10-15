// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A closed optical extension may end at an independently resolved numeric bar-start tempo. */
final class PrintedDashEndpoint {
    static Optional<ScoreAnchor> numeric(
            PrintedDashSpan.Span span,
            List<PrintedDashSpan.SystemBand> systems,
            List<MeasureRegion> measures,
            List<ScoreTempoChange> tempos,
            ScoreAnchor start,
            float gapX,
            float gapY) {
        if (span == null
                || (span.end() != PrintedDashSpan.End.CLOSED
                        && span.end() != PrintedDashSpan.End.MISSING_CONTINUATION)
                || span.runs().isEmpty()
                || span.lastSystem() < 0
                || span.lastSystem() >= systems.size()
                || !Float.isFinite(gapX)
                || !Float.isFinite(gapY)
                || gapX <= 0
                || gapY <= 0) return Optional.empty();
        boolean nextSystem = span.end() == PrintedDashSpan.End.MISSING_CONTINUATION;
        if (nextSystem && span.lastSystem() + 1 >= systems.size()) return Optional.empty();
        var system = systems.get(span.lastSystem() + (nextSystem ? 1 : 0));
        float edge = span.runs().get(span.runs().size() - 1).right();
        ScoreAnchor found = null;
        double bpm = 0;
        for (var tempo : tempos) {
            if (tempo.positionInMeasure() != 0 || tempo.measureIndex() >= measures.size()) continue;
            var region = measures.get(tempo.measureIndex());
            if (Math.abs(region.top() - system.top()) > gapY
                    || Math.abs(region.bottom() - system.bottom()) > gapY) continue;
            float distance = region.left() - (nextSystem ? system.left() : edge);
            if (nextSystem
                    ? Math.abs(distance) > gapX * .5f
                    : distance < -gapX * .5f || distance > gapX * 3) continue;
            var candidate = new ScoreAnchor(tempo.measureIndex(), 0);
            if (candidate.compareTo(start) <= 0) continue;
            if (found != null && (!found.equals(candidate) || bpm != tempo.bpm()))
                return Optional.empty();
            found = candidate;
            bpm = tempo.bpm();
        }
        return Optional.ofNullable(found);
    }
}

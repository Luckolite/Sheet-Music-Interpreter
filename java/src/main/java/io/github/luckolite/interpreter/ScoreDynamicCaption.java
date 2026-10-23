// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Pair a retained complete sudden label with its own proved absolute dynamic column. */
public final class ScoreDynamicCaption {
    public record Caption(String eventId, SuddenDynamicText.Mark text) {}

    private ScoreDynamicCaption() {}

    public static Map<Integer, Caption> plan(
            ScorePageInterpretation score,
            int measure,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> written,
            float beats) {
        var result = new LinkedHashMap<Integer, Caption>();
        for (int i = 0; i < score.dynamicChanges().size(); i++) {
            var d = score.dynamicChanges().get(i);
            if (d.measureIndex() != measure
                    || d.direction() != 0
                    || ScoreDynamicClock.onset(d, score.notes(), written, beats).isEmpty())
                continue;
            Caption found = null;
            boolean ambiguous = false;
            for (var e : score.expressiveEvents()) {
                if (e.kind() != ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION
                        || (e.scope() != ScoreExpressiveEvent.Scope.PART
                                && e.scope() != ScoreExpressiveEvent.Scope.UNRESOLVED)
                        || e.staffIndex() != d.staffIndex()
                        || e.staffCount() != d.staffCount()
                        || !ScoreExpressionDetector.owns(e)
                        || !e.targetEventId().orElse("").startsWith("expression-column:")) continue;
                var owners = ScoreExpressionDetector.targetIndices(score, e);
                if (owners.isEmpty()
                        || owners.stream()
                                .anyMatch(
                                        index -> {
                                            var n = score.notes().get(index);
                                            return n.measureIndex() != d.measureIndex()
                                                    || n.staffIndex() != d.staffIndex()
                                                    || n.staffCount() != d.staffCount()
                                                    || n.positionInMeasure()
                                                            != d.positionInMeasure();
                                        })) continue;
                SuddenDynamicText.Mark literal = null;
                boolean conflict = false;
                for (var evidence : e.evidence()) {
                    if (!evidence.sourceId().equals("printed-expression-word")
                            || evidence.staffIndex() != e.staffIndex()
                            || evidence.staffCount() != e.staffCount()) continue;
                    var parsed = SuddenDynamicText.parse(evidence.printedText());
                    if (parsed.isEmpty()
                            || ScoreDynamicsDetector.level(parsed.get().level()) != d.decibels())
                        continue;
                    if (literal != null && !literal.equals(parsed.get())) {
                        conflict = true;
                        break;
                    }
                    literal = parsed.get();
                }
                if (conflict || literal == null) continue;
                if (found != null) {
                    ambiguous = true;
                    break;
                }
                found = new Caption(e.eventId(), literal);
            }
            if (found != null && !ambiguous) result.put(i, found);
        }
        var claims = new HashMap<String, Integer>();
        for (var caption : result.values()) claims.merge(caption.eventId(), 1, Integer::sum);
        result.entrySet().removeIf(e -> claims.get(e.getValue().eventId()) != 1);
        return Map.copyOf(result);
    }
}

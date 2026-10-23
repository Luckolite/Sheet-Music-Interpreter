// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Selects staff-owned instructions while preserving separately printed reiterations. */
public final class ScoreTechniqueSelection {
    private ScoreTechniqueSelection() {}

    /** Selection preserves order and part evidence; omitted instructions are never reassigned. */
    public static List<ScoreTechniqueChange> upperStaff(List<ScoreTechniqueChange> changes) {
        return changes.stream().filter(change -> change.staffIndex() == 0).toList();
    }

    /** Keep printed reiterations; collapse only identical instructions on one source column. */
    public static List<ScoreTechniqueChange> distinctInstructions(
            List<ScoreTechniqueChange> changes, List<ScoreNoteEvent> notes) {
        record Column(int measure, int staff, int count, int column) {}
        record Site(Column column, int technique) {}
        var sites = new java.util.ArrayList<Site>();
        var kinds = new java.util.HashMap<Column, java.util.Set<Integer>>();
        for (var change : changes) {
            if (ScoreTechniqueChange.isBow(change.technique())) {
                sites.add(null);
                continue;
            }
            float first = Float.POSITIVE_INFINITY;
            boolean foreignTopology = false;
            for (var note : notes) {
                if (note.measureIndex() != change.measureIndex()
                        || note.staffIndex() != change.staffIndex()
                        || change.positionInMeasure() > note.positionInMeasure() + .001f) continue;
                if (note.staffCount() != change.staffCount()) foreignTopology = true;
                first = Math.min(first, note.positionInMeasure());
            }
            // Ambiguous/unowned labels retain their exact printed position. Never guess a clock.
            float column =
                    Float.isFinite(first) && !foreignTopology ? first : change.positionInMeasure();
            var key =
                    new Column(
                            change.measureIndex(),
                            change.staffIndex(),
                            change.staffCount(),
                            Float.floatToIntBits(column == 0 ? 0 : column));
            sites.add(new Site(key, change.technique()));
            kinds.computeIfAbsent(key, ignored -> new java.util.HashSet<>())
                    .add(change.technique());
        }
        var seen = new java.util.HashSet<Site>();
        var result = new java.util.ArrayList<ScoreTechniqueChange>();
        for (int i = 0; i < changes.size(); i++) {
            var site = sites.get(i);
            // Contradictory evidence is left intact, including its original order.
            if (site == null || kinds.get(site.column()).size() > 1 || seen.add(site))
                result.add(changes.get(i));
        }
        return List.copyOf(result);
    }
}

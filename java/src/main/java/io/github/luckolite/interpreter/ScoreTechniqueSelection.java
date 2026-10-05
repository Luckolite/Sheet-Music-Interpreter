// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Keeps playing instructions belonging to the surviving upper staff of an extraction. */
public final class ScoreTechniqueSelection {
    private ScoreTechniqueSelection() {}

    /** Selection preserves order and part evidence; omitted instructions are never reassigned. */
    public static List<ScoreTechniqueChange> upperStaff(List<ScoreTechniqueChange> changes) {
        return changes.stream().filter(change -> change.staffIndex() == 0).toList();
    }
}

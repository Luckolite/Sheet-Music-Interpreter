// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** One positively recognized printed H-bar; does not change per-bar time or rest values. */
public record ScoreMultiRestGroup(
        int firstMeasureIndex, int measureCount, int staffIndex, int staffCount) {
    public ScoreMultiRestGroup {
        if (firstMeasureIndex < 0
                || measureCount < 2
                || measureCount > 32
                || firstMeasureIndex > Integer.MAX_VALUE - measureCount
                || staffCount < 1
                || staffCount > 32
                || staffIndex < 0
                || staffIndex >= staffCount)
            throw new IllegalArgumentException("Invalid printed multi-rest group");
    }
}

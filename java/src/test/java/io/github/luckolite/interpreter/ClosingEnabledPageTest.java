// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ClosingEnabledPageTest {
    @Test
    public void filteredTrailingPagesKeepTheActualLastMusicPage() {
        int[] counts = {2, 0, 0, 3, 0};
        int[] starts = {1, 0, 0, 1, 0};
        assertEquals(
                0,
                ScorePageTimeline.lastEnabledPage(
                        counts, ScorePageTimeline.selectedArrangement(counts, starts, 0)));
        assertEquals(
                3,
                ScorePageTimeline.lastEnabledPage(
                        counts, ScorePageTimeline.selectedArrangement(counts, starts, 3)));
        assertEquals(
                2,
                ScorePageTimeline.lastEnabledPage(
                        new int[] {2, 0, 1}, new ScorePageTimeline.PageRange(0, 3)));
        assertEquals(
                -1,
                ScorePageTimeline.lastEnabledPage(
                        new int[] {0, 0}, new ScorePageTimeline.PageRange(0, 2)));
    }
}

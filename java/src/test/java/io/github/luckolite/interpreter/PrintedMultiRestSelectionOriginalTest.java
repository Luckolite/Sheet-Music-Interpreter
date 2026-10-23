// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PrintedMultiRestSelectionOriginalTest {
    private final List<ScoreMultiRestGroup> groups = List.of(new ScoreMultiRestGroup(1, 4, 0, 1));

    @Test
    public void completeSelectionRetainsPrintedCountAndOffsets() {
        assertEquals(
                List.of(new ScoreMultiRestGroup(0, 4, 0, 1)),
                PrintedMultiRestGroups.remap(groups, List.of(1, 2, 3, 4)));
    }

    @Test
    public void selectionOfOnlyThreeBarsDoesNotClaimFour() {
        assertTrue(PrintedMultiRestGroups.remap(groups, List.of(1, 2, 3)).isEmpty());
    }

    @Test
    public void wholeBlockMayMoveAfterOtherMusic() {
        assertEquals(
                List.of(new ScoreMultiRestGroup(2, 4, 0, 1)),
                PrintedMultiRestGroups.remap(groups, List.of(8, 9, 1, 2, 3, 4)));
    }

    @Test
    public void reorderedGroupDoesNotRemainOnePrintedHBar() {
        assertTrue(PrintedMultiRestGroups.remap(groups, List.of(1, 3, 2, 4)).isEmpty());
    }

    @Test
    public void duplicatedMeasureOwnerMakesGroupAmbiguous() {
        assertTrue(PrintedMultiRestGroups.remap(groups, List.of(1, 2, 3, 4, 1)).isEmpty());
    }

    @Test
    public void ordinaryFourWholeRestsStayUngrouped() {
        assertTrue(PrintedMultiRestGroups.remap(List.of(), List.of(0, 1, 2, 3)).isEmpty());
    }
}

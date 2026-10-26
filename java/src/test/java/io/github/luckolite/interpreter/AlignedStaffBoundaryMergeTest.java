// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class AlignedStaffBoundaryMergeTest {
    @Test
    public void eachAcceptedCloseBoundarySurvivesBothIdenticalStaves() throws Exception {
        var source = List.of(0, 200, 222, 500);
        assertEquals(source, merge(source, source));
    }

    @Test
    public void crossStaffDriftMatchesOneToOneAndMissingBarsRemain() throws Exception {
        assertEquals(
                List.of(0, 202, 224, 500),
                merge(List.of(0, 200, 222, 500), List.of(0, 204, 226, 500)));
        assertEquals(
                List.of(0, 200, 400, 500), merge(List.of(0, 200, 400, 500), List.of(0, 400, 500)));
        assertEquals(
                List.of(0, 200, 400, 500), merge(List.of(0, 400, 500), List.of(0, 200, 400, 500)));
    }

    @Test
    public void aBoundaryCannotConsumeTwoWitnessesFromTheSameStave() throws Exception {
        assertEquals(4, merge(List.of(0, 200, 222, 500), List.of(0, 211, 500)).size());
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> merge(List<Integer> first, List<Integer> second) throws Exception {
        var method =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "mergeBoundaries", List.class, List.class, int.class, float.class);
        method.setAccessible(true);
        return (List<Integer>) method.invoke(null, first, second, 1000, 6f);
    }
}

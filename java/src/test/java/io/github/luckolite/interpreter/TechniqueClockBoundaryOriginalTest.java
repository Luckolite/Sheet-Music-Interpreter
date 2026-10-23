// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** A metrical note cannot borrow the following measure's boundary as its own attack. */
public final class TechniqueClockBoundaryOriginalTest {
    @Test
    public void nonGraceAttackAtTheNextBarBoundaryDoesNotProveThisBarClock() {
        var notes = List.of(TechniqueWrittenClockOriginalTest.notes().get(0));
        var p = Map.of(0, new ScoreNoteTiming.WrittenPlacement(4, 1, -1, -1, false, 0));
        assertTrue(
                ScoreTechniqueClock.onset(
                                TechniqueWrittenClockOriginalTest.change(.03f), notes, p, 4)
                        .isEmpty());
    }
}

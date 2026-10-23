// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Physical order cannot override a contradictory retained musical clock. */
public final class TechniqueClockOrderOriginalTest {
    @Test
    public void laterAffectedColumnBeforeTheFirstInTimeDoesNotAcquireAClock() {
        var p =
                Map.of(
                        0, new ScoreNoteTiming.WrittenPlacement(2, 2, -1, -1, false, 0),
                        1, new ScoreNoteTiming.WrittenPlacement(0, 2, -1, -1, false, 0));
        assertTrue(
                ScoreTechniqueClock.onset(
                                TechniqueWrittenClockOriginalTest.change(.03f),
                                TechniqueWrittenClockOriginalTest.notes(),
                                p,
                                4)
                        .isEmpty());
    }
}

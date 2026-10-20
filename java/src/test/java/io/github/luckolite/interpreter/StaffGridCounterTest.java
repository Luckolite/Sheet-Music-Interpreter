// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Independent staff-grid counter, not an oval and not an extra written note. */
public class StaffGridCounterTest {
    @Test
    public void lastStemAndFullBarlineDoNotCreateHollowHead() {
        var p = new UnbeamedHollowRecoveryTest();
        p.oval(330, 214, false, true);
        p.shaft(344, 150, 216);
        for (int x = 280; x <= 345; x++) for (int y = 150; y <= 154; y++) p.ink(x, y, 25, 5);
        for (int x = 280; x <= 345; x++) for (int y = 160; y <= 164; y++) p.ink(x, y, 25, 5);
        for (int x = 367; x <= 369; x++) for (int y = 120; y <= 200; y++) p.ink(x, y, 25, 1);
        var n = p.notes();
        assertTrue(
                n.stream()
                        .noneMatch(
                                x ->
                                        Math.abs(x.pageY() * UnbeamedHollowRecoveryTest.H - 191) < 9
                                                && x.positionInMeasure() > .6f));
        assertEquals(1, p.near(n, 214).size());
    }

    @Test
    public void labelledLongShaftHalfStaysARealHead() {
        var p = new UnbeamedHollowRecoveryTest();
        p.oval(250, 134, true, true);
        p.shaft(236, 110, 210);
        var n = p.near(p.notes(), 134);
        assertEquals(1, n.size());
        assertEquals(2, n.get(0).unbeamedDurationBeats(), .001);
    }

    @Test
    public void unlabelledHalfWithShaftEndingBeforeStaffBottomStillRecovers() {
        var p = new UnbeamedHollowRecoveryTest();
        p.oval(250, 134, true, false);
        p.shaft(236, 132, 194);
        var n = p.near(p.notes(), 134);
        assertEquals(1, n.size());
        assertEquals(2, n.get(0).unbeamedDurationBeats(), .001);
    }
}

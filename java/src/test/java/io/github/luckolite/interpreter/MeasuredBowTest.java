// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent complete cap/leg profiles and attack/staff ownership companions. */
public class MeasuredBowTest extends BowDirectionTest {
    void cap(int scale, int phase, boolean shortLegs) {
        int left = 250 - 10 * scale, top = 72 + phase;
        for (int y = top; y < top + 10 * scale; y++)
            for (int x = left; x <= left + 20 * scale; x++) g[y * W + x] = 0;
        int length = shortLegs ? 5 * scale : 12 * scale;
        for (int y = top + 10 * scale; y < top + 10 * scale + length; y++)
            for (int dx = 0; dx < 3 * scale; dx++) {
                g[y * W + left + dx] = 0;
                g[y * W + left + 20 * scale - dx] = 0;
            }
    }

    @Test
    public void thickCompleteCapWithFiniteLongLegs() {
        cap(1, 0, false);
        assertEquals(7, read(List.of(note(250, 0)), 20).get(0).technique());
    }

    @Test
    public void scaledAndShiftedThickCap() {
        cap(2, 1, false);
        assertEquals(7, read(List.of(note(250, 0)), 24).get(0).technique());
    }

    @Test
    public void capWithShortStubsIsNotBow() {
        cap(1, 0, true);
        assertTrue(read(List.of(note(250, 0)), 20).isEmpty());
    }

    @Test
    public void thickCapForeignInteriorBranchIsNotBow() {
        cap(1, 0, false);
        for (int x = 240; x <= 260; x++) g[90 * W + x] = 0;
        assertTrue(read(List.of(note(250, 0)), 20).isEmpty());
    }

    @Test
    public void aboveStaffBowCanBelongToLowStaffNote() throws Exception {
        font('\uE612', 80, 0);
        var n =
                new ScoreNoteEvent(
                        0,
                        .5f,
                        -5,
                        0,
                        1,
                        240f / H,
                        false,
                        0,
                        0,
                        2,
                        1,
                        1,
                        0,
                        0,
                        30,
                        false,
                        0,
                        false,
                        0,
                        0,
                        1,
                        1,
                        ScoreNoteEvent.Kind.PITCHED);
        assertEquals(6, read(List.of(n), 20).get(0).technique());
    }

    @Test
    public void capWithMiddleLegIsNotBow() {
        cap(1, 0, false);
        for (int y = 82; y <= 93; y++) g[y * W + 250] = 0;
        assertTrue(read(List.of(note(250, 0)), 20).isEmpty());
    }
}

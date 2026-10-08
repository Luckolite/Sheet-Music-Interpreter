// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original half-rest rectangles with dots in the printed space above the supporting rule. */
public class PolyphonicHalfRestsDotPlacementTest {
    @Test
    public void normalPlateRetainsItsDotInTheStaffSpace() {
        var page = new PolyphonicHalfRestsTest.Page(true, true);
        page.oval(336, 92, 3, 3);
        var r = page.target();
        assertEquals(1, r.size());
        assertEquals(3, r.get(0).durationBeats(), 0);
    }

    @Test
    public void thinnerPlateRetainsItsDotInTheSameStaffSpace() {
        var page = new PolyphonicHalfRestsTest.Page(true, true);
        for (int y = 93; y <= 94; y++)
            for (int x = 300; x <= 318; x++) page.gray[y * 640 + x] = (byte) 245;
        page.oval(336, 92, 3, 3);
        var r = page.target();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(3, r.get(0).durationBeats(), 0);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** An independently printed high head cannot compete without intersecting any feasible sector. */
public class CurvedUpperStaffCompetitionTest {
    static int read(boolean near) {
        var s = FiniteCurvedApproachTest.scene(18, "hook");
        byte[] g = s.gray().clone();
        int cx = 106, cy = 117;
        for (int y = 103; y <= 111; y++)
            for (int x = cx - 7; x <= cx + 7; x++) g[y * 360 + x] = (byte) 255;
        int otherY = near ? 115 : 40;
        for (int yy : new int[] {cy, otherY})
            for (int y = yy - 4; y <= yy + 4; y++)
                for (int x = cx - 7; x <= cx + 7; x++) {
                    double q = (x - cx) * (x - cx) / 49. + (y - yy) * (y - yy) / 16.;
                    if (q <= 1 && q >= .3) g[y * 360 + x] = 0;
                }
        for (int x = 8; x < 352; x++)
            for (int line = 0; line < 5; line++) g[(20 + 18 * line) * 360 + x] = 0;
        var heads =
                List.of(
                        new NoteSlideDetector.Head(cx, cy, 18, 0, 0),
                        new NoteSlideDetector.Head(cx, otherY, 18, 0, 0));
        return NoteSlideDetector.detect(
                        g, 360, 260, List.of(new NoteSlideDetector.Staff(20, 92, 18)), heads)
                .size();
    }

    @Test
    public void farHigherHeadHasNoFeasiblePhysicalBodyContact() {
        assertEquals(1, read(false));
    }

    @Test
    public void genuinelyCompetingNearbyBodyRemainsUnknown() {
        assertEquals(0, read(true));
    }
}

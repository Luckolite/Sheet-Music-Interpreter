// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** A raw supported principal has extent; its centre need not lie inside the approach sector. */
public class SupportedCurvedDestinationTest {
    static int read(String kind) {
        var s = FiniteCurvedApproachTest.scene(18, "hook");
        byte[] g = s.gray().clone();
        int cx = 106, oldY = 107, cy = kind.equals("exteriorInk") ? 120 : 117;
        for (int y = oldY - 4; y <= oldY + 4; y++)
            for (int x = cx - 7; x <= cx + 7; x++) g[y * 360 + x] = (byte) 255;
        if (!kind.equals("noBody"))
            for (int y = cy - 4; y <= cy + 4; y++)
                for (int x = cx - 7; x <= cx + 7; x++) {
                    double q = (x - cx) * (x - cx) / 49. + (y - cy) * (y - cy) / 16.;
                    if (q <= 1 && q >= .3) g[y * 360 + x] = 0;
                }
        if (kind.equals("exteriorInk")) g[110 * 360 + 105] = 0;
        if (!kind.equals("noRawStaff"))
            for (int x = 8; x < 352; x++)
                for (int line = 0; line < 5; line++) g[(20 + 18 * line) * 360 + x] = 0;
        var heads = new ArrayList<NoteSlideDetector.Head>();
        heads.add(new NoteSlideDetector.Head(cx, cy, 18, 0, 0));
        if (kind.equals("ambiguous"))
            heads.add(new NoteSlideDetector.Head(cx + 1, cy + 1, 18, 0, 0));
        byte[] before = g.clone();
        var result =
                NoteSlideDetector.detect(
                        g, 360, 260, List.of(new NoteSlideDetector.Staff(20, 92, 18)), heads);
        assertArrayEquals(before, g);
        return result.size();
    }

    @Test
    public void supportedHollowBodyIntersectsMeasuredSector() {
        assertEquals(1, read("body"));
    }

    @Test
    public void missingActualBodyCannotBeBorrowed() {
        assertEquals(0, read("noBody"));
    }

    @Test
    public void exteriorInkDoesNotExtendBody() {
        assertEquals(0, read("exteriorInk"));
    }

    @Test
    public void competingAttackStillAbstains() {
        assertEquals(0, read("ambiguous"));
    }

    @Test
    public void nominalStaffDoesNotIssueRawBodyProof() {
        assertEquals(0, read("noRawStaff"));
    }
}

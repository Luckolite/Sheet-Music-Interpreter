// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original complete one-note staff, with independently drawn head, shaft and optional curve. */
public class SingleHeadApproachOriginalsTest {
    static AttachedCurvedApproachTest.Scene scene(int scale, String kind) {
        var s = new AttachedCurvedApproachTest.Scene(scale, true);
        // Retain the original analytical drawing helpers, then draw a new complete one-head scene.
        Arrays.fill(s.gray, (byte) 255);
        s.heads.clear();
        for (int y : new int[] {80, 94, 108, 122, 136}) s.box(12, y, 427, y);
        s.oval(214, 87, 9, 6);
        s.box(205, 87, 206, kind.equals("shortStem") ? 146 : 166);
        s.heads.add(new NoteSlideDetector.Head(214 * scale, 87 * scale, 14 * scale, 0, 0));
        if (!kind.equals("plain"))
            for (int i = 1; i <= 120; i++) {
                double t = (i - 1) * Math.PI / 240, u = i * Math.PI / 240;
                double ax = 181 + 24 * Math.sin(t),
                        ay = 94 + 24 * Math.cos(t),
                        bx = 181 + 24 * Math.sin(u),
                        by = 94 + 24 * Math.cos(u);
                if (kind.equals("returning")) {
                    ay += 20 * Math.sin(2 * t);
                    by += 20 * Math.sin(2 * u);
                }
                s.line(ax, ay, bx, by, 1.2);
            }
        return s;
    }

    private static void positive(AttachedCurvedApproachTest.Scene s) {
        var r = s.found();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(0, r.get(0).noteIndex());
        assertEquals(1, r.get(0).direction());
        assertFalse(r.get(0).connected());
    }

    @Test
    public void oneCompleteHeadAndFreeShaft() {
        positive(scene(1, "hook"));
    }

    @Test
    public void doubledCompleteSingleHead() {
        positive(scene(2, "hook"));
    }

    @Test
    public void tripledCompleteSingleHead() {
        positive(scene(3, "hook"));
    }

    @Test
    public void shorterRawShaftStillClosesItsRole() {
        positive(scene(1, "shortStem"));
    }

    @Test
    public void bareQuarterNoteDoesNotInventApproach() {
        scene(1, "plain").negative();
    }

    @Test
    public void returningBowIsNotAnApproach() {
        scene(1, "returning").negative();
    }

    @Test
    public void unexplainedCurveBranchRefusesComponent() {
        var s = scene(1, "hook");
        s.line(193, 113, 193, 101, 1);
        s.negative();
    }

    @Test
    public void duplicatePhysicalDestinationRefuses() {
        var s = scene(1, "hook");
        s.heads.add(s.heads.get(0));
        s.negative();
    }

    @Test
    public void unknownPhysicalStaffRefuses() {
        var s = scene(1, "hook");
        var n = s.heads.get(0);
        s.heads.set(0, new NoteSlideDetector.Head(n.x(), n.y(), n.gap(), -1, 0));
        s.negative();
    }

    @Test
    public void absentRawHeadCannotBeReplacedByMetadata() {
        var s = scene(1, "hook");
        s.erase(205, 81, 223, 93);
        s.box(205, 87, 206, 166);
        s.negative();
    }

    @Test
    public void absentRawShaftCannotBeReplacedByMetadata() {
        var s = scene(1, "hook");
        s.erase(204, 110, 207, 160);
        s.negative();
    }

    @Test
    public void untypedShaftSpurRemainsUnexplained() {
        var s = scene(1, "hook");
        s.line(201, 145, 206, 145, .9);
        s.negative();
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric curve crossing a complete staff and a separate returning flag. */
public class FlagRuleApproachOriginalsTest {
    static AttachedCurvedApproachTest.Scene scene(int scale, boolean flag, boolean approach) {
        var s = new AttachedCurvedApproachTest.Scene(scale, true);
        Arrays.fill(s.gray, (byte) 255);
        s.heads.clear();
        for (int y : new int[] {80, 94, 108, 122, 136}) s.box(12, y, 427, y);
        s.oval(214, 94, 9, 6);
        s.box(205, 94, 206, 142);
        s.heads.add(new NoteSlideDetector.Head(214 * scale, 94 * scale, 14 * scale, 0, 0));
        if (approach)
            for (int i = 1; i <= 120; i++) {
                double t = (i - 1) * Math.PI / 240, u = i * Math.PI / 240;
                s.line(
                        182 + 20 * Math.sin(t),
                        100 + 20 * Math.cos(t),
                        182 + 20 * Math.sin(u),
                        100 + 20 * Math.cos(u),
                        1.1);
            }
        if (flag)
            for (int i = 1; i <= 120; i++) {
                double t = (i - 1) / 120., u = i / 120.;
                s.line(bx(t), by(t), bx(u), by(u), 1.2);
            }
        return s;
    }

    static double bx(double t) {
        double u = 1 - t;
        return u * u * u * 205 + 3 * u * u * t * 221 + 3 * u * t * t * 227 + t * t * t * 218;
    }

    static double by(double t) {
        double u = 1 - t;
        return u * u * u * 140 + 3 * u * u * t * 137 + 3 * u * t * t * 114 + t * t * t * 99;
    }

    static void positive(AttachedCurvedApproachTest.Scene s) {
        var v = s.found();
        assertEquals(v.toString(), 1, v.size());
        assertEquals(0, v.get(0).noteIndex());
        assertEquals(1, v.get(0).direction());
        assertFalse(v.get(0).connected());
    }

    @Test
    public void entireUnflaggedCurveSurvivesStaffBridge() {
        positive(scene(1, false, true));
    }

    @Test
    public void entireFlaggedCurveSurvivesStaffBridge() {
        positive(scene(1, true, true));
    }

    @Test
    public void doubledFlagAndCurve() {
        positive(scene(2, true, true));
    }

    @Test
    public void tripledFlagAndCurve() {
        positive(scene(3, true, true));
    }

    @Test
    public void aFlagAloneDoesNotBecomeApproach() {
        scene(1, true, false).negative();
    }

    @Test
    public void duplicateOwnerRefuses() {
        var s = scene(1, true, true);
        s.heads.add(s.heads.get(0));
        s.negative();
    }

    @Test
    public void missingRawHeadRefuses() {
        var s = scene(1, true, true);
        s.erase(205, 88, 223, 100);
        s.box(205, 94, 206, 142);
        s.negative();
    }

    @Test
    public void missingShaftRefuses() {
        var s = scene(1, true, true);
        s.erase(204, 102, 207, 135);
        s.negative();
    }

    @Test
    public void unknownCurveBranchRefuses() {
        var s = scene(1, true, true);
        s.line(194, 115, 184, 102, 1);
        s.negative();
    }

    @Test
    public void unknownFlagBranchRefuses() {
        var s = scene(1, true, true);
        s.line(222, 117, 236, 108, 1);
        s.negative();
    }
}

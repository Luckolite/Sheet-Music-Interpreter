// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original full staff with a separately drawn flag and antialiased short approach. */
public class StaffJoinedCompactOriginalsTest {
    static AttachedCurvedApproachTest.Scene scene(int scale, String kind) {
        return scene(scale, kind, new int[] {220, 120, 60, 120, 220});
    }

    static AttachedCurvedApproachTest.Scene scene(int scale, String kind, int[] profile) {
        var s = new AttachedCurvedApproachTest.Scene(scale, true);
        Arrays.fill(s.gray, (byte) 255);
        s.heads.clear();
        for (int line : new int[] {80, 94, 108, 122, 136})
            for (int yy = -2; yy <= 2; yy++)
                for (int y = (line + yy) * scale; y < (line + yy + 1) * scale; y++)
                    for (int x = 12 * scale; x < 428 * scale; x++)
                        s.gray[y * s.w + x] = (byte) profile[yy + 2];
        s.oval(170, 115, 9, 6);
        s.box(178, 67, 179, 115);
        s.oval(214, 94, 9, 6);
        s.box(222, 53, 223, 94);
        s.heads.add(new NoteSlideDetector.Head(170 * scale, 115 * scale, 14 * scale, 0, 0));
        s.heads.add(new NoteSlideDetector.Head(214 * scale, 94 * scale, 14 * scale, 0, 0));
        for (int i = 1; i <= 120; i++) {
            double a = (i - 1) / 120., b = i / 120.;
            s.line(fx(a), fy(a), fx(b), fy(b), 1.1);
        }
        if (!kind.equals("plain"))
            aa(s, 195, kind.equals("fall") ? 106 : 112, 199, kind.equals("fall") ? 112 : 106, 1.05);
        return s;
    }

    static double fx(double t) {
        double u = 1 - t;
        return u * u * u * 179 + 3 * u * u * t * 195 + 3 * u * t * t * 198 + t * t * t * 190;
    }

    static double fy(double t) {
        double u = 1 - t;
        return u * u * u * 69 + 3 * u * u * t * 70 + 3 * u * t * t * 100 + t * t * t * 110;
    }

    static void aa(
            AttachedCurvedApproachTest.Scene s,
            double ax,
            double ay,
            double bx,
            double by,
            double radius) {
        for (int y = (int) Math.floor((Math.min(ay, by) - radius - 1) * s.scale);
                y <= (Math.max(ay, by) + radius + 1) * s.scale;
                y++)
            for (int x = (int) Math.floor((Math.min(ax, bx) - radius - 1) * s.scale);
                    x <= (Math.max(ax, bx) + radius + 1) * s.scale;
                    x++) {
                int hits = 0;
                double dx = bx - ax, dy = by - ay;
                for (int sy = 0; sy < 8; sy++)
                    for (int sx = 0; sx < 8; sx++) {
                        double xx = (x + (sx + .5) / 8.) / s.scale,
                                yy = (y + (sy + .5) / 8.) / s.scale;
                        double u =
                                Math.max(
                                        0,
                                        Math.min(
                                                1,
                                                ((xx - ax) * dx + (yy - ay) * dy)
                                                        / (dx * dx + dy * dy)));
                        if (Math.hypot(xx - ax - u * dx, yy - ay - u * dy) <= radius) hits++;
                    }
                int at = y * s.w + x;
                s.gray[at] = (byte) Math.round((s.gray[at] & 255) * (1 - hits / 64.));
            }
    }

    static void positive(AttachedCurvedApproachTest.Scene s) {
        var r = s.found();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(1, r.get(0).noteIndex());
        assertEquals(1, r.get(0).direction());
        assertFalse(r.get(0).connected());
    }

    @Test
    public void completeGrayStaffApproach() {
        positive(scene(1, "rise"));
    }

    @Test
    public void doubledGrayStaffApproach() {
        positive(scene(2, "rise"));
    }

    @Test
    public void tripledGrayStaffApproach() {
        positive(scene(3, "rise"));
    }

    @Test
    public void bareFlagCannotInventApproach() {
        scene(1, "plain").negative();
    }

    @Test
    public void oppositeSlopeIsNotAnIncomingRise() {
        scene(1, "fall").negative();
    }

    @Test
    public void duplicatePhysicalDestinationRefuses() {
        var s = scene(1, "rise");
        s.heads.add(s.heads.get(1));
        s.negative();
    }

    @Test
    public void missingRawDestinationRefuses() {
        var s = scene(1, "rise");
        s.erase(205, 88, 223, 100);
        s.box(222, 53, 223, 94);
        s.negative();
    }

    @Test
    public void untypedCurveForkRefuses() {
        var s = scene(1, "rise");
        s.line(197, 109, 208, 114, 1);
        s.negative();
    }

    @Test
    public void untypedCurveForkAtDoubleDensityRefuses() {
        var s = scene(2, "rise");
        s.line(197, 109, 208, 114, 1);
        s.negative();
    }

    @Test
    public void unknownDestinationStaffRefuses() {
        var s = scene(1, "rise");
        var n = s.heads.get(1);
        s.heads.set(1, new NoteSlideDetector.Head(n.x(), n.y(), n.gap(), -1, 0));
        s.negative();
    }

    @Test
    public void incompatibleBilateralStaffBackgroundRefuses() {
        var s = scene(1, "rise");
        for (int y = 105; y <= 111; y++) s.box(240, y, 275, y);
        s.negative();
    }

    @Test
    public void wrongFiveRuleFrameCannotOwnInk() {
        var s = scene(1, "rise");
        assertTrue(
                NoteSlideDetector.detect(
                                s.gray,
                                s.w,
                                s.h,
                                List.of(new NoteSlideDetector.Staff(60, 116, 14)),
                                s.heads)
                        .isEmpty());
    }

    @Test
    public void inputHeadPermutationPreservesPhysicalOwner() {
        var s = scene(1, "rise");
        Collections.reverse(s.heads);
        var r = s.found();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(0, r.get(0).noteIndex());
    }

    @Test
    public void exhaustedRuleBudgetCannotReturnStroke() {
        var s = scene(1, "rise");
        var budget = new AttachedApproachBranches.Budget(0);
        assertNull(
                StaffJoinedCompactApproach.find(
                        s.gray,
                        s.w,
                        s.h,
                        new int[40],
                        40,
                        160,
                        200,
                        67,
                        121,
                        s.staffs,
                        s.heads,
                        new HashMap<>(),
                        budget));
        assertTrue(budget.refused);
    }

    @Test
    public void destinationOutsideCompleteStrokeRayRefusesAtDoubleDensity() {
        var s = scene(2, "rise");
        s.erase(205, 88, 223, 100);
        s.oval(214, 101, 9, 6);
        s.box(222, 53, 223, 101);
        s.heads.set(1, new NoteSlideDetector.Head(428, 202, 28, 0, 0));
        s.negative();
    }

    @Test
    public void alternateIndependentStaffGrayProfile() {
        positive(scene(1, "rise", new int[] {235, 160, 85, 160, 235}));
    }

    @Test
    public void alternateGrayProfileAtDoubleDensity() {
        positive(scene(2, "rise", new int[] {235, 160, 85, 160, 235}));
    }

    @Test
    public void opaqueStaffCannotInventItsHiddenStrokePixels() {
        scene(1, "rise", new int[] {0, 0, 0, 0, 0}).negative();
    }
}

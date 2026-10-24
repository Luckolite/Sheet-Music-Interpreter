// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original abstract geometry; no commercial score pixels or fixed song coordinates. */
public class ScoopAttachmentRolesOriginalsTest {
    static AttachedCurvedApproachTest.Scene scene(
            int scale, boolean hollow, boolean barline, boolean opposed) {
        var s = new AttachedCurvedApproachTest.Scene(scale, true);
        if (barline || opposed) {
            Arrays.fill(s.gray, (byte) 255);
            s.heads.clear();
            for (int y : new int[] {80, 94, 108, 122, 136}) s.box(12, y, 427, y);
            s.oval(145, 108, 9, 6);
            s.box(136, 108, 137, 166);
            s.heads.add(new NoteSlideDetector.Head(145 * scale, 108 * scale, 14 * scale, 0, 0));
            s.oval(214, 87, 9, 6);
            if (opposed) s.box(222, 38, 223, 87);
            else s.box(205, 87, 206, 166);
            s.heads.add(
                    new NoteSlideDetector.Head(
                            214 * scale, 87 * scale, 14 * scale, 0, barline ? 1 : 0));
            if (barline) s.box(180, 80, 182, 136);
            for (int i = 1; i <= 120; i++) {
                double a = (i - 1) * Math.PI / 240, b = i * Math.PI / 240;
                s.line(
                        181 + 24 * Math.sin(a),
                        94 + 24 * Math.cos(a),
                        181 + 24 * Math.sin(b),
                        94 + 24 * Math.cos(b),
                        1.2);
            }
        }
        if (hollow)
            for (int y = 82 * scale; y <= 92 * scale; y++)
                for (int x = 206 * scale; x <= 222 * scale; x++)
                    if (Math.pow((x - 214 * scale) / (9.0 * scale), 2)
                                    + Math.pow((y - 87 * scale) / (6.0 * scale), 2)
                            < .28) s.gray[y * s.w + x] = (byte) 255;
        return s;
    }

    static void positive(AttachedCurvedApproachTest.Scene s) {
        var found = s.found();
        assertEquals(found.toString(), 1, found.size());
        assertEquals(1, found.get(0).noteIndex());
        assertFalse(found.get(0).connected());
    }

    @Test
    public void hollowBodySuppliesNecessaryOwnerDespiteWhiteCenter() {
        for (int scale : new int[] {1, 2}) {
            var s = scene(scale, true, false, false);
            var target = s.heads.get(1);
            assertEquals(255, s.gray[Math.round(target.y()) * s.w + Math.round(target.x())] & 255);
            var pixels = new ArrayList<Integer>();
            for (int y = 81 * scale; y <= 94 * scale; y++)
                for (int x = 204 * scale; x <= 224 * scale; x++)
                    if ((s.gray[y * s.w + x] & 255) < 150) pixels.add(y * s.w + x);
            int[] component = pixels.stream().mapToInt(Integer::intValue).toArray();
            assertTrue(
                    ScoopAttachmentRoles.componentOwner(
                            s.gray,
                            s.w,
                            s.h,
                            component,
                            component.length,
                            s.staffs,
                            List.of(target),
                            List.of(0),
                            new HashMap<>(),
                            new AttachedApproachBranches.Budget(64000000)));
        }
    }

    @Test
    public void closedHollowTopologyStillFailsClosed() {
        for (int scale : new int[] {1, 2})
            assertTrue(scene(scale, true, false, false).found().isEmpty());
    }

    @Test
    public void completeBarlineAndRuleContactsDoNotEraseScoop() {
        positive(scene(2, false, true, false));
    }

    @Test
    public void unsupportedBarlineTopologiesStillFailClosed() {
        for (int scale : new int[] {1, 3})
            assertTrue(scene(scale, false, true, false).found().isEmpty());
    }

    @Test
    public void lowerRawShaftContactSurvivesPreflightWithOffComponentRival() {
        for (int scale : new int[] {1, 2}) {
            var s = scene(scale, false, false, false);
            var target = s.heads.get(1);
            var all = List.copyOf(s.heads);
            int[] component = {115 * scale * s.w + 205 * scale, 116 * scale * s.w + 205 * scale};
            byte[] before = s.gray.clone();
            assertTrue(
                    ScoopAttachmentRoles.componentOwner(
                            s.gray,
                            s.w,
                            s.h,
                            component,
                            component.length,
                            s.staffs,
                            s.heads,
                            List.of(1),
                            new HashMap<>(),
                            new AttachedApproachBranches.Budget(64000000)));
            assertArrayEquals(before, s.gray);
            assertEquals(all, s.heads);
            assertTrue(target.y() + target.gap() * .8f < 115 * scale);
        }
    }

    @Test
    public void unrelatedRawIslandCannotBorrowDistantHeadOrShaft() {
        for (int scale : new int[] {1, 2}) {
            var s = scene(scale, false, false, false);
            s.box(350, 200, 360, 204);
            int[] component = {200 * scale * s.w + 350 * scale, 201 * scale * s.w + 350 * scale};
            assertFalse(
                    ScoopAttachmentRoles.componentOwner(
                            s.gray,
                            s.w,
                            s.h,
                            component,
                            component.length,
                            s.staffs,
                            s.heads,
                            List.of(0, 1, 2),
                            new HashMap<>(),
                            new AttachedApproachBranches.Budget(64000000)));
        }
    }

    @Test
    public void preflightRefusalRemainsSticky() {
        var s = scene(1, false, false, false);
        int[] component = {115 * s.w + 205};
        var budget = new AttachedApproachBranches.Budget(1);
        assertFalse(
                ScoopAttachmentRoles.componentOwner(
                        s.gray,
                        s.w,
                        s.h,
                        component,
                        1,
                        s.staffs,
                        s.heads,
                        List.of(1),
                        new HashMap<>(),
                        budget));
        assertTrue(budget.refused);
        budget.take(0);
        assertTrue(budget.refused);
    }

    @Test
    public void rawFlagShaftRoleCannotConsumeForeignHead() {
        for (int scale : new int[] {1, 2}) {
            var s = FlagRuleApproachOriginalsTest.scene(scale, true, true);
            var path = new ArrayList<Integer>();
            for (int y = 120 * scale; y <= 141 * scale; y++) path.add(y * s.w + 205 * scale);
            assertTrue(
                    ScoopAttachmentRoles.shaftPath(
                            s.gray,
                            s.w,
                            s.h,
                            path,
                            p -> p,
                            s.heads,
                            List.of(0),
                            Set.of(0),
                            new AttachedApproachBranches.Budget(64000000)));
            s.heads.add(new NoteSlideDetector.Head(205 * scale, 125 * scale, 14 * scale, 0, 0));
            assertFalse(
                    ScoopAttachmentRoles.shaftPath(
                            s.gray,
                            s.w,
                            s.h,
                            path,
                            p -> p,
                            s.heads,
                            List.of(0, 1),
                            Set.of(0),
                            new AttachedApproachBranches.Budget(64000000)));
        }
    }

    @Test
    public void opposedUpstemCannotSupplyAnAttachedScoop() {
        for (int scale : new int[] {1, 2})
            assertTrue(scene(scale, false, false, true).found().isEmpty());
    }

    @Test
    public void overlappingDestinationRemainsAmbiguous() {
        for (int scale : new int[] {1, 2}) {
            var s = scene(scale, true, false, false);
            s.heads.add(new NoteSlideDetector.Head(215 * scale, 88 * scale, 14 * scale, 0, 0));
            assertTrue(s.found().isEmpty());
        }
    }

    @Test
    public void metadataRivalAtBarlineCannotBePrunedAsBackground() {
        for (int scale : new int[] {1, 2}) {
            var s = scene(scale, false, true, false);
            s.heads.add(new NoteSlideDetector.Head(181 * scale, 100 * scale, 14 * scale, 0, 1));
            assertTrue(s.found().isEmpty());
        }
    }

    @Test
    public void sameMeasureVerticalMarkCannotBorrowBarlineRole() {
        for (int scale : new int[] {1, 2}) {
            var s = scene(scale, false, true, false);
            var n = s.heads.get(1);
            s.heads.set(1, new NoteSlideDetector.Head(n.x(), n.y(), n.gap(), n.staff(), 0));
            assertTrue(s.found().isEmpty());
        }
    }

    @Test
    public void shortenedVerticalMarkCannotBorrowBarlineRole() {
        for (int scale : new int[] {1, 2}) {
            var s = scene(scale, false, true, false);
            s.erase(179, 80, 183, 90);
            assertTrue(s.found().isEmpty());
        }
    }

    @Test
    public void nonbodyTargetCannotTypeAnAttachedGraph() {
        for (int scale : new int[] {1, 2}) {
            var s = scene(scale, true, false, false);
            s.erase(205, 81, 223, 93);
            assertTrue(s.found().isEmpty());
        }
    }
}

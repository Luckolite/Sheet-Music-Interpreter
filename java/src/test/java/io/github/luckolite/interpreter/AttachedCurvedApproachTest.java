// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric approach attached to a raw-proved beamed head/stem tree. */
public class AttachedCurvedApproachTest {
    static final class Scene {
        final int scale, w, h;
        final byte[] gray;
        final List<NoteSlideDetector.Head> heads = new ArrayList<>();
        final List<NoteSlideDetector.Staff> staffs;

        Scene() {
            this(1, true);
        }

        Scene(int scale, boolean curved) {
            this.scale = scale;
            w = 440 * scale;
            h = 280 * scale;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            staffs = List.of(new NoteSlideDetector.Staff(80 * scale, 136 * scale, 14 * scale));
            for (int y : new int[] {80, 94, 108, 122, 136}) box(12, y, 427, y);
            int[] xs = {145, 214, 287}, ys = {108, 87, 94};
            for (int i = 0; i < xs.length; i++) {
                oval(xs[i], ys[i], 9, 6);
                box(xs[i] - 9, ys[i], xs[i] - 8, 166);
                heads.add(
                        new NoteSlideDetector.Head(xs[i] * scale, ys[i] * scale, 14 * scale, 0, 0));
            }
            box(136, 163, 279, 167);
            for (int i = 1; i <= 120; i++) {
                double t0 = (i - 1) * Math.PI / 240, t1 = i * Math.PI / 240;
                double x0 = 181 + 24 * Math.sin(t0),
                        y0 = 94 + 24 * Math.cos(t0),
                        x1 = 181 + 24 * Math.sin(t1),
                        y1 = 94 + 24 * Math.cos(t1);
                if (!curved) {
                    x0 = 181 + 24 * (i - 1) / 120.;
                    y0 = 118 - 24 * (i - 1) / 120.;
                    x1 = 181 + 24 * i / 120.;
                    y1 = 118 - 24 * i / 120.;
                }
                line(x0, y0, x1, y1, 1.2);
            }
        }

        void box(int l, int t, int r, int b) {
            for (int y = Math.min(t, b) * scale; y < (Math.max(t, b) + 1) * scale; y++)
                for (int x = Math.min(l, r) * scale; x < (Math.max(l, r) + 1) * scale; x++)
                    gray[y * w + x] = 0;
        }

        void erase(int l, int t, int r, int b) {
            for (int y = t * scale; y < (b + 1) * scale; y++)
                for (int x = l * scale; x < (r + 1) * scale; x++) gray[y * w + x] = (byte) 255;
        }

        void oval(int cx, int cy, int rx, int ry) {
            for (int y = (cy - ry) * scale; y <= (cy + ry) * scale; y++)
                for (int x = (cx - rx) * scale; x <= (cx + rx) * scale; x++)
                    if (Math.pow((x - cx * scale) / (double) (rx * scale), 2)
                                    + Math.pow((y - cy * scale) / (double) (ry * scale), 2)
                            <= 1) gray[y * w + x] = 0;
        }

        void line(double ax, double ay, double bx, double by, double radius) {
            for (int y = (int) Math.floor((Math.min(ay, by) - radius) * scale);
                    y <= (Math.max(ay, by) + radius) * scale;
                    y++)
                for (int x = (int) Math.floor((Math.min(ax, bx) - radius) * scale);
                        x <= (Math.max(ax, bx) + radius) * scale;
                        x++) {
                    double dx = bx - ax,
                            dy = by - ay,
                            u =
                                    Math.max(
                                            0,
                                            Math.min(
                                                    1,
                                                    ((x / (double) scale - ax) * dx
                                                                    + (y / (double) scale - ay)
                                                                            * dy)
                                                            / (dx * dx + dy * dy)));
                    if (Math.hypot(
                                    x / (double) scale - ax - u * dx,
                                    y / (double) scale - ay - u * dy)
                            <= radius) gray[y * w + x] = 0;
                }
        }

        List<NoteSlideDetector.Stroke> found() {
            byte[] copy = gray.clone();
            var result = NoteSlideDetector.detect(gray, w, h, staffs, heads);
            assertArrayEquals(copy, gray);
            return result;
        }

        void positive() {
            var r = found();
            assertEquals(r.toString(), 1, r.size());
            assertEquals(1, r.get(0).noteIndex());
            assertEquals(1, r.get(0).direction());
            assertFalse(r.get(0).connected());
        }

        void negative() {
            assertTrue(found().toString(), found().isEmpty());
        }
    }

    @Test
    public void completeAttachedQuarterBowIsAnUnpitchedApproach() {
        new Scene().positive();
    }

    @Test
    public void doubledDensityPreservesTheApproachAndOwner() {
        new Scene(2, true).positive();
    }

    @Test
    public void aStraightDiagonalDoesNotBecomeAnAdditionalCurvedApproach() {
        new Scene(1, false).negative();
    }

    @Test
    public void anUnrecognizedHeadDoesNotLeaveAClosedComponentRole() {
        Scene s = new Scene();
        s.heads.remove(0);
        s.negative();
    }

    @Test
    public void missingTargetDoesNotBorrowTheNextHead() {
        Scene s = new Scene();
        s.heads.remove(1);
        s.negative();
    }

    @Test
    public void ambiguousCoincidentTargetCannotOwnTheBranch() {
        Scene s = new Scene();
        s.heads.add(s.heads.get(1));
        s.negative();
    }

    @Test
    public void detachedMetadataWithoutRawTargetShaftDoesNotProveTheRole() {
        Scene s = new Scene();
        s.erase(203, 101, 208, 160);
        s.negative();
    }

    @Test
    public void anUnownedTextBranchVetoesTheWholeAttachedTree() {
        Scene s = new Scene();
        s.line(205, 130, 187, 142, 1.1);
        s.negative();
    }

    @Test
    public void anotherPhysicalLaneCannotBeDiscardedDuringClosure() {
        Scene s = new Scene();
        s.heads.set(0, new NoteSlideDetector.Head(145, 108, 14, 1, 0));
        s.negative();
    }

    @Test
    public void aClosedAttachedCounterCannotBeOpenedIntoAnApproach() {
        Scene s = new Scene();
        s.line(181, 118, 205, 94, 1.2);
        s.negative();
    }

    @Test
    public void aSquareAttachedCornerIsNotAQuarterBow() {
        Scene s = new Scene();
        s.erase(175, 93, 204, 120);
        s.line(181, 118, 205, 118, 1.2);
        s.negative();
    }

    @Test
    public void exhaustedOptionalBudgetCannotProduceAReclassifiedBranch() {
        Scene s = new Scene();
        byte[] before = s.gray.clone(),
                clean = NoteSlideDetector.removeStaffLines(s.gray, s.w, s.h, s.staffs);
        boolean[] seen = new boolean[clean.length];
        int[] queue = new int[clean.length];
        int count = 1, cursor = 0;
        queue[0] = 118 * s.w + 181;
        seen[queue[0]] = true;
        while (cursor < count) {
            int at = queue[cursor++], x = at % s.w, y = at / s.w;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= s.w || yy < 0 || yy >= s.h) continue;
                    int p = yy * s.w + xx;
                    if (!seen[p] && (clean[p] & 255) < 150) {
                        seen[p] = true;
                        queue[count++] = p;
                    }
                }
        }
        var budget = new AttachedApproachBranches.Budget(0);
        assertTrue(
                AttachedApproachBranches.find(
                                s.gray,
                                s.w,
                                s.h,
                                queue,
                                count,
                                s.staffs,
                                s.heads,
                                new HashMap<Integer, StaffPitchTrack>(),
                                budget)
                        .isEmpty());
        assertTrue(budget.refused);
        assertArrayEquals(before, s.gray);
    }
}

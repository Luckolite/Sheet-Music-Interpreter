// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original analytical slashes, heads and competing notation; no score pixels. */
public class CompactApproachStrokesOriginalsTest {
    private static final int W = 480, H = 360;

    private record Scene(
            byte[] raw, List<NoteSlideDetector.Staff> staffs, List<NoteSlideDetector.Head> heads) {}

    private static void segment(
            byte[] raw, double ax, double ay, double bx, double by, double radius) {
        for (int y = Math.max(0, (int) Math.floor(Math.min(ay, by) - radius));
                y < H && y <= Math.max(ay, by) + radius;
                y++)
            for (int x = Math.max(0, (int) Math.floor(Math.min(ax, bx) - radius));
                    x < W && x <= Math.max(ax, bx) + radius;
                    x++) {
                double dx = bx - ax,
                        dy = by - ay,
                        u =
                                Math.max(
                                        0,
                                        Math.min(
                                                1,
                                                ((x - ax) * dx + (y - ay) * dy)
                                                        / (dx * dx + dy * dy)));
                if (Math.hypot(x - ax - u * dx, y - ay - u * dy) <= radius) raw[y * W + x] = 0;
            }
    }

    private static Scene scene(int g, String kind) {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        float x = 14 * g, y = 9.5f * g;
        var staffs = List.of(new NoteSlideDetector.Staff(5 * g, 9 * g, g));
        for (int rail = 0; rail < 5; rail++)
            segment(raw, 20, (5 + rail) * g, W - 20, (5 + rail) * g, .6);
        var heads = new ArrayList<NoteSlideDetector.Head>();
        heads.add(new NoteSlideDetector.Head(x, y, g, 0, 0));
        if (kind.equals("ambiguous"))
            heads.add(new NoteSlideDetector.Head(x + g * .1f, y, g, 0, 0));
        if (kind.equals("foreign"))
            heads.add(new NoteSlideDetector.Head(x - g * 1.3f, y + g, g, 0, 0));
        if (!kind.equals("missingBody")) {
            for (int iy = (int) (y - g * .4); iy <= y + g * .4; iy++)
                for (int ix = (int) (x - g * .7); ix <= x + g * .7; ix++)
                    if (Math.pow((ix - x) / (g * .65), 2) + Math.pow((iy - y) / (g * .35), 2) <= 1)
                        raw[iy * W + ix] = 0;
            segment(raw, x - g * .6, y, x - g * .6, y + g * 3, .7);
        }
        double ax = x - g * 1.3, ay = y + g * 1.05, bx = x - g * .9, by = y + g * .4;
        if (kind.equals("descending")) {
            ay = y + g * .4;
            by = y + g * 1.05;
        }
        if (kind.equals("flat")) ay = by = y + g * .6;
        if (kind.equals("vertical")) ax = bx = x - g;
        segment(raw, ax, ay, bx, by, Math.max(.75, g * .06));
        if (kind.equals("joinedShaft")) segment(raw, ax, ay, ax, y - g * 2, 1);
        if (kind.equals("branch")) segment(raw, (ax + bx) * .5, (ay + by) * .5, ax - g, ay, 1);
        if (kind.equals("noHead")) heads.clear();
        if (kind.equals("wrongStaff")) heads.set(0, new NoteSlideDetector.Head(x, y, g, -1, 0));
        if (kind.equals("wrongSide"))
            heads.set(0, new NoteSlideDetector.Head(x - g * 3, y, g, 0, 0));
        return new Scene(raw, staffs, heads);
    }

    private static List<NoteSlideDetector.Stroke> found(int gap, String kind) {
        var s = scene(gap, kind);
        byte[] before = s.raw.clone();
        var result = NoteSlideDetector.detect(s.raw, W, H, s.staffs, s.heads);
        assertArrayEquals("Original grayscale must remain unchanged", before, s.raw);
        return result;
    }

    private static void positive(int g) {
        var result = found(g, "approach");
        assertEquals(result.toString(), 1, result.size());
        assertEquals(0, result.get(0).noteIndex());
        assertEquals(1, result.get(0).direction());
        assertFalse(result.get(0).connected());
    }

    @Test
    public void compactAt12() {
        positive(12);
    }

    @Test
    public void compactAt16() {
        positive(16);
    }

    @Test
    public void compactAt20() {
        positive(20);
    }

    @Test
    public void compactAt24() {
        positive(24);
    }

    @Test
    public void duplicateDestinationAbstains() {
        assertTrue(found(16, "ambiguous").isEmpty());
    }

    @Test
    public void foreignHeadOwnsSlash() {
        assertTrue(found(16, "foreign").isEmpty());
    }

    @Test
    public void metadataWithoutRawBodyAbstains() {
        assertTrue(found(16, "missingBody").isEmpty());
    }

    @Test
    public void unownedSlashAbstains() {
        assertTrue(found(16, "noHead").isEmpty());
    }

    @Test
    public void unknownPhysicalStaffAbstains() {
        assertTrue(found(16, "wrongStaff").isEmpty());
    }

    @Test
    public void destinationOnWrongSideAbstains() {
        assertTrue(found(16, "wrongSide").isEmpty());
    }

    @Test
    public void smallDescendingMarkAbstains() {
        assertTrue(found(16, "descending").isEmpty());
    }

    @Test
    public void flatMarkAbstains() {
        assertTrue(found(16, "flat").isEmpty());
    }

    @Test
    public void verticalMarkAbstains() {
        assertTrue(found(16, "vertical").isEmpty());
    }

    @Test
    public void joinedShaftIsNotIsolated() {
        assertTrue(found(16, "joinedShaft").isEmpty());
    }

    @Test
    public void unclosedBranchAbstains() {
        assertTrue(found(16, "branch").isEmpty());
    }

    @Test
    public void cleanedRasterCannotHideRawFlagAttachment() {
        var s = scene(16, "approach");
        byte[] clean = NoteSlideDetector.removeStaffLines(s.raw, W, H, s.staffs);
        // Simulate cleanup cutting an attached flag down to the otherwise valid short slash.
        segment(
                s.raw,
                14 * 16 - 16 * 1.3,
                9.5 * 16 + 16 * 1.05,
                14 * 16 - 16 * 1.3,
                9.5 * 16 - 32,
                1);
        assertTrue(
                NoteSlideDetector.detectWithRemovedStaffLines(s.raw, W, H, s.staffs, s.heads, clean)
                        .isEmpty());
    }

    @Test
    public void invalidPhysicalFrameCannotProveDestination() {
        var s = scene(16, "approach");
        assertTrue(
                NoteSlideDetector.detect(
                                s.raw,
                                W,
                                H,
                                List.of(new NoteSlideDetector.Staff(80, 144, Float.NaN)),
                                s.heads)
                        .isEmpty());
    }
}

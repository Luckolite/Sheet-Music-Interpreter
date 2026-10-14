// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural printed staff and irregular semantic texture controls. */
public class IrregularInteriorStaffTest {
    static final int W = 1600, H = 400;
    byte[] gray = new byte[W * H];
    RawStaffLineDetector.StaffLines a = staff(50, 17),
            b = new RawStaffLineDetector.StaffLines(new int[] {140, 154, 162, 180, 192}, 13),
            c = staff(260, 17);

    static RawStaffLineDetector.StaffLines staff(int top, int gap) {
        return new RawStaffLineDetector.StaffLines(
                new int[] {top, top + gap, top + 2 * gap, top + 3 * gap, top + 4 * gap}, gap);
    }

    public IrregularInteriorStaffTest() {
        Arrays.fill(gray, (byte) 255);
        draw(a, 20, 780, 0);
        draw(c, 20, 780, 0);
    }

    void draw(RawStaffLineDetector.StaffLines s, int left, int right, int value) {
        for (int y : s.rows()) for (int x = left; x < right; x++) gray[y * W + x] = (byte) value;
    }

    int remaining() throws Exception {
        var list = new ArrayList<>(List.of(a, b, c));
        var m =
                RawStaffLineDetector.class.getDeclaredMethod(
                        "rejectUnsupportedAliases", List.class, byte[].class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(null, list, gray, W, H);
        return list.size();
    }

    @Test
    public void compressedIrregularTextureBetweenPrintedStaffsIsRejected() throws Exception {
        for (int y : new int[] {140, 153, 166, 179})
            for (int x = 500; x < 650; x++) gray[y * W + x] = 80;
        assertEquals(2, remaining());
    }

    @Test
    public void regularUnsupportedProjectionIsPreserved() throws Exception {
        b = staff(140, 13);
        assertEquals(3, remaining());
    }

    @Test
    public void sameScaleIrregularProjectionIsPreserved() throws Exception {
        a = staff(50, 13);
        c = staff(260, 13);
        draw(a, 20, 780, 0);
        draw(c, 20, 780, 0);
        assertEquals(3, remaining());
    }

    @Test
    public void paleLongRulesPreserveIrregularProjection() throws Exception {
        draw(b, 20, 780, 220);
        assertEquals(3, remaining());
    }

    @Test
    public void shortFiveRulesPreserveIrregularProjection() throws Exception {
        draw(staff(140, 13), 500, 620, 220);
        assertEquals(3, remaining());
    }

    @Test
    public void faintShortFiveRulesPreserveIrregularProjection() throws Exception {
        draw(staff(140, 13), 500, 620, 245);
        assertEquals(3, remaining());
    }

    @Test
    public void unsupportedNeighborCannotAuthorizeRejection() throws Exception {
        for (int y : c.rows()) Arrays.fill(gray, y * W, (y + 1) * W, (byte) 255);
        assertEquals(3, remaining());
    }

    @Test
    public void missingRasterPreservesProjection() throws Exception {
        gray = null;
        assertEquals(3, remaining());
    }

    @Test
    public void rasterIsUnchanged() throws Exception {
        byte[] before = gray.clone();
        remaining();
        assertArrayEquals(before, gray);
    }
}

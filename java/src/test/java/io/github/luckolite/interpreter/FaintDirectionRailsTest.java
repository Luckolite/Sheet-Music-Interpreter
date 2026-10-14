// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;

public final class FaintDirectionRailsTest {
    static int checked;

    static float[] read(
            int rails,
            int paper,
            int ink,
            int halfThickness,
            boolean leftOnly,
            boolean erase,
            boolean shifted,
            float slope) {
        int w = 600, h = 350;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) paper);
        for (int line = 0; line < rails; line++)
            for (int x = 100; x < 500; x++) {
                if (leftOnly && x > 300) continue;
                if (erase && line == 4 && x > 310 && x < 336) continue;
                int y = 200 - line * 14 + (shifted ? 14 : 0) + Math.round((x - 300) * slope);
                for (int dy = -halfThickness; dy <= halfThickness; dy++)
                    gray[(y + dy) * w + x] = (byte) ink;
            }
        byte[] before = gray.clone();
        var result = PrintedDirectionStaff.printedRules(gray, w, h, 300, 200, 14);
        if (!Arrays.equals(before, gray)) throw new AssertionError("source mutation");
        return result;
    }

    static void check(String name, boolean expected, float[] found) {
        checked++;
        if ((found != null) != expected)
            throw new AssertionError(name + " got " + Arrays.toString(found));
        if (found != null && (Math.abs(found[0] - 200) > 1 || Math.abs(found[1] - 14) > .5))
            throw new AssertionError(name + " phase");
    }

    @Test
    public void faintRailsOnShade() {
        check("faint rails on shade", true, read(5, 198, 180, 0, false, false, false, 0));
    }

    @Test
    public void faintRailsOnWhite() {
        check("faint rails on white", true, read(5, 255, 180, 0, false, false, false, 0));
    }

    @Test
    public void occludedTopRail() {
        check("occluded top rail", true, read(5, 198, 180, 0, false, true, false, 0));
    }

    @Test
    public void tiltedFaintRails() {
        check("tilted faint rails", true, read(5, 198, 180, 0, false, false, false, .04f));
    }

    @Test
    public void fourRails() {
        check("four rails", false, read(4, 198, 180, 0, false, false, false, 0));
    }

    @Test
    public void threeRails() {
        check("three rails", false, read(3, 198, 180, 0, false, false, false, 0));
    }

    @Test
    public void noRails() {
        check("no rails", false, read(0, 198, 180, 0, false, false, false, 0));
    }

    @Test
    public void weakContrast() {
        check("weak contrast", false, read(5, 190, 180, 0, false, false, false, 0));
    }

    @Test
    public void bilateralMissing() {
        check("bilateral missing", false, read(5, 198, 180, 0, true, false, false, 0));
    }

    @Test
    public void broadShade() {
        check("broad shade", false, read(5, 198, 180, 5, false, false, false, 0));
    }

    @Test
    public void wrongPhase() {
        check("wrong phase", false, read(5, 198, 180, 0, false, false, true, 0));
    }

    @Test
    public void existingDarkRails() {
        check("existing dark rails", true, read(5, 255, 30, 0, false, false, false, 0));
    }

    @Test
    public void malformedPlaneCannotSupplyRails() {
        org.junit.Assert.assertNull(
                PrintedDirectionStaff.printedRules(null, 600, 350, 300, 200, 14));
        org.junit.Assert.assertNull(
                PrintedDirectionStaff.printedRules(new byte[4], 600, 350, 300, 200, 14));
    }

    @Test
    public void nonfiniteGeometryCannotSupplyRails() {
        byte[] gray = new byte[600 * 350];
        org.junit.Assert.assertNull(
                PrintedDirectionStaff.printedRules(gray, 600, 350, Float.NaN, 200, 14));
        org.junit.Assert.assertNull(
                PrintedDirectionStaff.printedRules(
                        gray, 600, 350, 300, Float.POSITIVE_INFINITY, 14));
        org.junit.Assert.assertNull(
                PrintedDirectionStaff.printedRules(gray, 600, 350, 300, 200, Float.NaN));
    }

    @Test
    public void unboundedWindowCannotSupplyRails() {
        org.junit.Assert.assertNull(
                FaintDirectionRails.read(
                        new byte[600 * 350], 600, 350, 300, 200, 14, Float.MAX_VALUE));
    }
}

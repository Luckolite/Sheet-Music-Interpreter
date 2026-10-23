// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original single observed fringe contact between two measured opaque arm bodies. */
public class HairpinApertureJoinOriginalTest {
    static byte[] image() {
        byte[] g = new byte[80 * 70];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i <= 50; i++) {
            int x = 10 + i, up = (int) Math.round(30 - .1 * i), lo = (int) Math.round(40 + .1 * i);
            for (int y = up; y < up + 6; y++) g[y * 80 + x] = 0;
            for (int y = lo - 5; y <= lo; y++) g[y * 80 + x] = 0;
        }
        for (int y = 29; y <= 41; y++) g[y * 80 + 10] = 0;
        g[35 * 80 + 10] = (byte) 190;
        return g;
    }

    static Optional<CompleteHairpinAperture.Proof> proof(byte[] g) {
        var copy = g.clone();
        var r =
                CompleteHairpinAperture.prove(
                        g,
                        80,
                        70,
                        10,
                        60,
                        new double[] {-.1, 30},
                        new double[] {.1, 40},
                        new boolean[51],
                        14,
                        new CompleteHairpinAperture.Budget(100_000));
        assertArrayEquals(copy, g);
        return r;
    }

    @Test
    public void oneGrayJoinRetainsTheActualPixelAndBothCoreOwners() {
        var p = proof(image()).orElseThrow();
        assertEquals(1, p.fringeJoins().size());
        var j = p.fringeJoins().get(0);
        assertEquals(10, j.x());
        assertEquals(35, j.row());
        assertEquals(190, j.shade());
        assertTrue(j.upperCore() < j.row());
        assertTrue(j.lowerCore() > j.row());
    }

    @Test
    public void anOpaqueExtraInteriorPixelIsNotAFringeJoin() {
        var g = image();
        g[35 * 80 + 10] = 0;
        assertTrue(proof(g).isEmpty());
    }

    @Test
    public void aGrayContactWithoutItsUpperOpaqueCoreCannotBorrowAnArm() {
        var g = image();
        for (int y = 29; y < 35; y++) g[y * 80 + 10] = (byte) 190;
        assertTrue(proof(g).isEmpty());
    }

    @Test
    public void aTwoPixelWideExtraContactCannotBorrowTheOnePixelRole() {
        var g = image();
        g[35 * 80 + 10] = g[36 * 80 + 10] = (byte) 190;
        for (int y = 37; y <= 42; y++) g[y * 80 + 10] = 0;
        assertTrue(proof(g).isEmpty());
    }
}

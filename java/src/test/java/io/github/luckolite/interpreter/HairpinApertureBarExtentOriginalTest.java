// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** A real long neighboring bar cannot exempt ink beyond its own measured extent. */
public final class HairpinApertureBarExtentOriginalTest {
    private static HairpinApertureOriginalTest.Scene scene(
            int top, int bottom, int extraTop, int extraBottom) {
        var s = FringedHairpinApertureOriginalTest.scene(1, false);
        for (int y = 0; y < s.h; y++) s.gray[y * s.w + 450] = (byte) 255;
        for (int y = top; y <= bottom; y++) s.gray[y * s.w + 450] = 0;
        for (int y = extraTop; y <= extraBottom; y++) s.gray[y * s.w + 450] = 0;
        return s;
    }

    private static Optional<CompleteHairpinAperture.Proof> prove(
            HairpinApertureOriginalTest.Scene s) {
        var before = s.gray.clone();
        var bars = new boolean[751];
        bars[350] = true;
        var out =
                HairpinApertureSafetyOriginalTest.proof(
                        s, bars, new CompleteHairpinAperture.Budget(4_000_000));
        assertArrayEquals(before, s.gray);
        return out;
    }

    @Test
    public void completeBarOwnsItsEntireExaminedInterval() {
        var p = prove(scene(50, 300, 1, 0)).orElseThrow();
        var c = p.columns().stream().filter(v -> v.x() == 450).findFirst().orElseThrow();
        assertEquals(CompleteHairpinAperture.Role.BAR, c.role());
        assertTrue(c.barExtent().first() <= c.firstExamined());
        assertTrue(c.barExtent().last() >= c.lastExamined());
    }

    @Test
    public void lowerDetachedBandCannotBorrowALongBarEndingInsideTheAperture() {
        assertTrue(prove(scene(50, 171, 177, 179)).isEmpty());
    }

    @Test
    public void croppedLowerBandCannotBorrowTheSameBarAuthority() {
        assertTrue(prove(scene(50, 171, 177, 180)).isEmpty());
    }

    @Test
    public void upperDetachedBandCannotBorrowALongBarStartingInsideTheAperture() {
        assertTrue(prove(scene(169, 300, 161, 164)).isEmpty());
    }
}

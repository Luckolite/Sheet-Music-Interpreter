// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural inter-staff hairpins; no score or font pixels. */
public class InterstaffBarHairpinOriginalTest {
    final int w = 360, h = 280;
    final byte[] gray = new byte[w * h];
    final List<PlayingTechniqueDetector.Staff> staffs =
            List.of(
                    new PlayingTechniqueDetector.Staff(20, 84, 16, 1, 3),
                    new PlayingTechniqueDetector.Staff(180, 244, 16, 2, 3));

    public InterstaffBarHairpinOriginalTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void wedge() {
        for (int x = 40; x <= 280; x++) {
            int u = Math.round(112 - 4 * (x - 40) / 240f),
                    l = Math.round(120 + 8 * (x - 40) / 240f);
            gray[u * w + x] = 0;
            gray[(u + 1) * w + x] = 0;
            gray[l * w + x] = 0;
            gray[(l + 1) * w + x] = 0;
            if (x >= 70 && x <= 91)
                for (int y = u + 3; y < l - 2; y++) gray[y * w + x] = (byte) 210;
        }
    }

    void stroke(int top, int bottom, int width) {
        for (int y = top; y <= bottom; y++)
            for (int x = 150; x < 150 + width; x++) gray[y * w + x] = 0;
    }

    boolean read() {
        return OpenHairpinPairs.detect(gray, w, h, staffs).stream()
                .anyMatch(s -> s.staff().index() == 1);
    }

    @Test
    public void completeInterstaffBarAccountsOnlyItsCore() {
        wedge();
        stroke(20, 244, 4);
        byte[] before = gray.clone();
        assertTrue(read());
        assertArrayEquals(before, gray);
    }

    @Test
    public void ordinaryShortShaftCannotAccountForInterior() {
        wedge();
        stroke(90, 150, 4);
        assertFalse(read());
    }

    @Test
    public void incompleteInterstaffBarCannotAccountForInterior() {
        wedge();
        stroke(45, 244, 4);
        assertFalse(read());
    }

    @Test
    public void broadVerticalObjectCannotAccountForInterior() {
        wedge();
        stroke(20, 244, 7);
        assertFalse(read());
    }

    @Test
    public void unrelatedStaffIdentityCannotSupplyBarAuthority() {
        wedge();
        stroke(20, 244, 4);
        var wrong = List.of(staffs.get(0), new PlayingTechniqueDetector.Staff(180, 244, 16, 0, 1));
        assertTrue(OpenHairpinPairs.detect(gray, w, h, wrong).isEmpty());
    }

    @Test
    public void missingRailIsNotRepairedByBar() {
        stroke(20, 244, 4);
        for (int x = 40; x <= 280; x++) gray[112 * w + x] = 0;
        assertFalse(read());
    }

    @Test
    public void filledInteriorRemainsUnexplained() {
        wedge();
        stroke(20, 244, 4);
        for (int x = 100; x <= 135; x++) for (int y = 114; y <= 120; y++) gray[y * w + x] = 0;
        assertFalse(read());
    }

    @Test
    public void paperWedgeWithoutBarStillWorks() {
        wedge();
        assertTrue(read());
    }
}

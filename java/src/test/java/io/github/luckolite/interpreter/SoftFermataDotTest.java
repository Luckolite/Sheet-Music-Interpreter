// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated antialias halo and compact roof, no private score pixels. */
public class SoftFermataDotTest {
    static final int W = 500, H = 400;
    byte[] gray = new byte[W * H], labels = new byte[W * H];

    public SoftFermataDotTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void dot(boolean core) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -3; dx <= 3; dx++)
                if (!(Math.abs(dx) == 3 && Math.abs(dy) == 2))
                    gray[(200 + dy) * W + 250 + dx] = (byte) 190;
        if (core)
            for (int dy = 0; dy < 2; dy++)
                for (int dx = -2; dx < 2; dx++) gray[(200 + dy) * W + 250 + dx] = (byte) 140;
    }

    void roof(int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            int y =
                    200
                            - (int)
                                    Math.round(
                                            3
                                                    + 10
                                                            * Math.sqrt(
                                                                    Math.max(
                                                                            0,
                                                                            1
                                                                                    - dx
                                                                                            * dx
                                                                                            / (double)
                                                                                                    (radius
                                                                                                            * radius))));
            gray[y * W + 250 + dx] = 100;
            gray[(y + 1) * W + 250 + dx] = 100;
        }
    }

    List<NoteArticulationDetector.FermataMark> marks() {
        return NoteArticulationDetector.fermataMarks(
                labels, gray, W, H, List.of(new NoteArticulationDetector.Anchor(250, 230, 16, 0)));
    }

    @Test
    public void darkCoreWithHaloAndRoof() {
        dot(true);
        roof(12);
        assertEquals(1, marks().size());
    }

    @Test
    public void noRoofNoFermata() {
        dot(true);
        assertTrue(marks().isEmpty());
    }

    @Test
    public void noDarkCoreNoFermata() {
        dot(false);
        roof(12);
        assertTrue(marks().isEmpty());
    }

    @Test
    public void longSlurDoesNotOwn() {
        dot(true);
        roof(35);
        assertTrue(marks().isEmpty());
    }

    @Test
    public void scatteredRoofSpecksDoNotOwn() {
        dot(true);
        for (int x : new int[] {238, 244, 250, 256, 262}) gray[187 * W + x] = 100;
        assertTrue(marks().isEmpty());
    }

    @Test
    public void connectedGrayStripeRejected() {
        dot(true);
        roof(12);
        for (int x = 220; x <= 280; x++) gray[201 * W + x] = (byte) 190;
        assertTrue(marks().isEmpty());
    }

    @Test
    public void pixelsPreserved() {
        dot(true);
        roof(12);
        var old = gray.clone();
        marks();
        assertArrayEquals(old, gray);
    }

    @Test
    public void invertedHaloFermata() {
        dot(true);
        roof(12);
        var old = gray.clone();
        for (int y = 0; y < H; y++) System.arraycopy(old, y * W, gray, (H - 1 - y) * W, W);
        assertEquals(
                1,
                NoteArticulationDetector.fermataMarks(
                                labels,
                                gray,
                                W,
                                H,
                                List.of(new NoteArticulationDetector.Anchor(250, 169, 16, 0)))
                        .size());
    }
}

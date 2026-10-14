// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster dots and parabolic arches. */
public class FermataDotRoundingTest {
    private ConnectedFermataRoofTest page(boolean roof, boolean broad) {
        var p = new ConnectedFermataRoofTest();
        Arrays.fill(p.g, (byte) 255);
        for (int y = 497; y <= 504; y++)
            for (int x = 496; x <= (broad ? 505 : 504); x++) p.g[y * p.W + x] = 0;
        if (roof) p.arc(488, 512, 500, 482, 8);
        return p;
    }

    private int count(ConnectedFermataRoofTest p) {
        return NoteArticulationDetector.fermataMarks(
                        p.labels,
                        p.g,
                        p.W,
                        p.H,
                        List.of(new NoteArticulationDetector.Anchor(500, 530, 14, 0)))
                .size();
    }

    @Test
    public void roundedOuterPixelPreservesProvedFermata() {
        assertEquals(1, count(page(true, false)));
    }

    @Test
    public void roofStillRequired() {
        assertEquals(0, count(page(false, false)));
    }

    @Test
    public void extraPixelBeyondCeilingRejected() {
        assertEquals(0, count(page(true, true)));
    }

    @Test
    public void longSlurCannotSupplyRoof() {
        var p = page(false, false);
        p.arc(450, 514, 482, 480, 16);
        assertEquals(0, count(p));
    }

    @Test
    public void offCenterRoofRejected() {
        var p = page(false, false);
        p.arc(476, 500, 488, 482, 8);
        assertEquals(0, count(p));
    }

    @Test
    public void staccatoClassifierUnchanged() {
        var p = page(false, false);
        assertEquals(
                0,
                NoteArticulationDetector.detect(
                        p.labels,
                        p.g,
                        p.W,
                        p.H,
                        List.of(new NoteArticulationDetector.Anchor(500, 530, 14, 0)))[0]);
    }

    @Test
    public void inputUnchanged() {
        var p = page(true, false);
        var before = p.g.clone();
        count(p);
        assertArrayEquals(before, p.g);
    }
}

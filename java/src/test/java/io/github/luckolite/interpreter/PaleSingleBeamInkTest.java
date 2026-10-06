// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original light stems, independent thick beams, slurs and bow strokes. */
public class PaleSingleBeamInkTest {
    static final int W = 260, H = 220;
    final byte[] g = new byte[W * H];
    final int[] stem = {100, 80, -1};

    public PaleSingleBeamInkTest() {
        Arrays.fill(g, (byte) 255);
    }

    void rect(int l, int r, int t, int b, int value) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = (byte) value;
    }

    boolean proof() {
        return PaleSingleBeamInk.supports(g, W, H, stem, 16);
    }

    @Test
    public void thickStraightBeamIsIndependentProof() {
        rect(100, 160, 80, 86, 40);
        assertTrue(proof());
    }

    @Test
    public void slopingBeamRetainsProof() {
        for (int x = 100; x <= 160; x++) {
            int y = 80 + Math.round((x - 100) * .3f);
            rect(x, x, y, y + 5, 40);
        }
        assertTrue(proof());
    }

    @Test
    public void thinStaffRuleCannotSupplyBeam() {
        rect(30, 200, 80, 82, 40);
        assertFalse(proof());
    }

    @Test
    public void shortTenutoCannotSupplyBeam() {
        rect(100, 119, 80, 85, 40);
        assertFalse(proof());
    }

    @Test
    public void broadFaintPaperCannotSupplyBeam() {
        rect(20, 220, 60, 110, 190);
        assertFalse(proof());
    }

    @Test
    public void detachedUpBowIsNotHorizontalBeam() {
        for (int x = 83; x <= 117; x++) {
            int y = 80 + Math.round(Math.abs(x - 100) * -1.5f);
            rect(x, x, y, y + 4, 40);
        }
        assertFalse(proof());
    }

    @Test
    public void thinCurvedSlurCannotSupplyBeam() {
        for (int x = 70; x <= 170; x++) {
            int y = 80 + Math.round(12 * (float) Math.sin(Math.PI * (x - 70) / 100));
            rect(x, x, y, y + 1, 40);
        }
        assertFalse(proof());
    }

    @Test
    public void blankPaperIsRejected() {
        assertFalse(proof());
    }

    @Test
    public void invalidInputsAreRejected() {
        assertFalse(PaleSingleBeamInk.supports(new byte[2], W, H, stem, 16));
        assertFalse(PaleSingleBeamInk.supports(g, W, H, null, 16));
        assertFalse(PaleSingleBeamInk.supports(g, W, H, stem, Float.NaN));
    }

    @Test
    public void inputPixelsAndStemArePreserved() {
        rect(100, 160, 80, 86, 40);
        byte[] before = g.clone();
        int[] s = stem.clone();
        proof();
        assertArrayEquals(before, g);
        assertArrayEquals(s, stem);
    }

    int decoded(int beamCount, int shaft) throws Exception {
        byte[] labels = new byte[W * H];
        rect(109, 111, 80, 150, shaft);
        for (int beam = 0; beam < beamCount; beam++)
            rect(110, 180, 80 + beam * 12, 86 + beam * 12, 35);
        var hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = hc.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object head = ctor.newInstance(180, 90, 110, 144, 156, 100f, 150f);
        var sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var st = sc.getDeclaredConstructor(float.class, float.class, float.class);
        st.setAccessible(true);
        Object staff = st.newInstance(70f, 134f, 16f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        hc,
                        sc,
                        List.class);
        m.setAccessible(true);
        return (int) m.invoke(null, labels, g, W, H, head, staff, List.of(head));
    }

    @Test
    public void veryPaleShaftCanReachSingleThickBeam() throws Exception {
        assertEquals(1, decoded(1, 240));
    }

    @Test
    public void genuineDoubleBeamIsNotReduced() throws Exception {
        assertEquals(2, decoded(2, 225));
    }

    @Test
    public void paleUnbeamedQuarterStaysUnbeamed() throws Exception {
        assertEquals(0, decoded(0, 240));
    }

    @Test
    public void bothBeamSidesAndStemDirectionsRetainTheInkBoundary() {
        for (int direction : new int[] {-1, 1})
            for (int side : new int[] {-1, 1}) {
                Arrays.fill(g, (byte) 255);
                stem[2] = direction;
                rect(side < 0 ? 40 : 100, side < 0 ? 100 : 160, 80, 86, 164);
                byte[] before = g.clone();
                int[] originalStem = stem.clone();
                assertTrue(proof());
                assertTrue(proof());
                assertArrayEquals(before, g);
                assertArrayEquals(originalStem, stem);
                rect(side < 0 ? 40 : 100, side < 0 ? 100 : 160, 80, 86, 165);
                before = g.clone();
                assertFalse(proof());
                assertArrayEquals(before, g);
                assertArrayEquals(originalStem, stem);
            }
    }

    @Test
    public void changedStemAndClearedRasterDoNotReuseEarlierBeamEvidence() {
        rect(100, 160, 80, 86, 40);
        byte[] before = g.clone();
        int[] originalStem = stem.clone();
        assertTrue(proof());
        assertArrayEquals(before, g);
        assertArrayEquals(originalStem, stem);
        stem[1] = 180;
        originalStem = stem.clone();
        assertFalse(proof());
        assertArrayEquals(before, g);
        assertArrayEquals(originalStem, stem);
        stem[1] = 80;
        originalStem = stem.clone();
        assertTrue(proof());
        assertArrayEquals(before, g);
        assertArrayEquals(originalStem, stem);
        Arrays.fill(g, (byte) 255);
        before = g.clone();
        assertFalse(proof());
        assertArrayEquals(before, g);
        assertArrayEquals(originalStem, stem);
    }
}

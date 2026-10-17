// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

/** Original complete ledger chains contested by a single unrelated short stroke. */
public class ContestedCompleteLedgerOwnerTest {
    static RemoteLedgerEvidenceTest.Page page(int mode) {
        var p = TwoInnerLedgerOwnerTest.page(true, false, 2, false);
        // End the original short shaft, then give this head its own continuous shaft to the staff.
        for (int y = 80; y < 128; y++)
            for (int x = 94; x <= 95; x++) {
                p.labels[y * 480 + x] = 0;
                p.gray[y * 480 + x] = (byte) 255;
            }
        for (int y : new int[] {80, 96, 112}) p.rule(86, 124, y, 5);
        p.rule(92, 118, 56, 5);
        if (mode != 1) p.stem(94, mode == 2 ? 92 : 72, mode == 3 ? 104 : 145);
        if (mode == 4)
            for (int x = 86; x <= 124; x++) {
                p.gray[96 * 480 + x] = (byte) 255;
                p.labels[96 * 480 + x] = 0;
            }
        if (mode == 5) p.rule(92, 118, 64, 5);
        if (mode == 6) {
            for (int y = 80; y <= 145; y++)
                for (int x = 94; x <= 95; x++) {
                    p.gray[y * 480 + x] = (byte) 255;
                    p.labels[y * 480 + x] = 0;
                }
            for (int y : new int[] {80, 96, 112}) p.rule(86, 124, y, 5);
            p.stem(116, 72, 30);
        }
        return p;
    }

    @Test
    public void completeChainAndOwnedShaftBeatOneIncidentalRule() {
        var n = page(0).remote();
        assertNotNull(n);
        assertEquals(15, n.staffStep());
    }

    @Test
    public void noShaftCannotResolveTheContestedChain() {
        var n = page(1).remote();
        assertTrue(n == null || n.staffStep() != 15);
    }

    @Test
    public void detachedShaftCannotResolveTheContestedChain() {
        var n = page(2).remote();
        assertTrue(n == null || n.staffStep() != 15);
    }

    @Test
    public void shaftStoppingBeforeStaffCannotResolveTheContestedChain() {
        var n = page(3).remote();
        assertTrue(n == null || n.staffStep() != 15);
    }

    @Test
    public void incompleteChainCannotWin() {
        var n = page(4).remote();
        assertTrue(n == null || n.staffStep() != 15);
    }

    @Test
    public void twoCompleteChainsRemainAmbiguous() {
        var n = page(5).remote();
        assertTrue(n == null || n.staffStep() != 15);
    }

    @Test
    public void shaftPointingToOtherStaffCannotPromoteLower() {
        var n = page(6).remote();
        assertTrue(n == null || n.staffStep() != 15);
    }

    @Test
    public void pixelsRemainUnchanged() {
        var p = page(0);
        var l = p.labels.clone();
        var g = p.gray.clone();
        p.remote();
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }

    static ScoreNoteEvent transformed(boolean mirror, int scale, int dx, int dy) {
        var p = page(0);
        int w = 480 * scale + dx + 12, h = 384 * scale + dy + 12;
        byte[] l = new byte[w * h], g = new byte[w * h];
        java.util.Arrays.fill(g, (byte) 255);
        for (int y = 0; y < 384; y++)
            for (int x = 0; x < 480; x++)
                for (int sy = 0; sy < scale; sy++)
                    for (int sx = 0; sx < scale; sx++) {
                        int yy = (mirror ? 383 - y : y) * scale + dy + sy, xx = x * scale + dx + sx;
                        l[yy * w + xx] = p.labels[y * 480 + x];
                        g[yy * w + xx] = p.gray[y * 480 + x];
                    }
        return OmrScoreInterpreter.analyze(
                        l, g, w, h, java.util.List.of(new MeasureRegion(0, 1, 0, 1)))
                .notes()
                .stream()
                .filter(
                        n ->
                                Math.abs(
                                                n.positionInMeasure() * w
                                                        - (105 * scale + dx + (scale - 1) * .5f))
                                        < 3 * scale)
                .findFirst()
                .orElse(null);
    }

    @Test
    public void completeUpperChainUsesOwnedUpwardShaft() throws Exception {
        byte[] g = new byte[400 * 420];
        java.util.Arrays.fill(g, (byte) 255);
        for (int y : new int[] {153, 171, 221}) for (int x = 166; x <= 194; x++) g[y * 400 + x] = 0;
        for (int y = 180; y <= 198; y++)
            for (int x = 169; x <= 191; x++)
                if ((x - 180) * (x - 180) / 121f + (y - 189) * (y - 189) / 81f <= 1)
                    g[y * 400 + x] = 0;
        for (int y = 120; y <= 189; y++) for (int x = 190; x <= 191; x++) g[y * 400 + x] = 0;
        Class<?> sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff"),
                hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var c = sc.getDeclaredConstructors()[0];
        c.setAccessible(true);
        Object upper = c.newInstance(63f, 135f, 18f), lower = c.newInstance(239f, 311f, 18f);
        var h = hc.getDeclaredConstructors()[0];
        h.setAccessible(true);
        Object head = h.newInstance(300, 169, 191, 180, 198, 180f, 189f);
        for (var m : OmrScoreInterpreter.class.getDeclaredMethods())
            if (m.getName().equals("printedLedgerOwner")) {
                m.setAccessible(true);
                assertSame(
                        upper, m.invoke(null, g, 400, 420, java.util.List.of(upper, lower), head));
                return;
            }
        fail("missing existing ownership method");
    }

    @Test
    public void doubledGeometryRetainsLowerOwnership() {
        var n = transformed(false, 2, 0, 0);
        assertNotNull(n);
        assertEquals(15, n.staffStep());
    }

    @Test
    public void translatedGeometryRetainsLowerOwnership() {
        var n = transformed(false, 1, 13, 23);
        assertNotNull(n);
        assertEquals(15, n.staffStep());
    }
}

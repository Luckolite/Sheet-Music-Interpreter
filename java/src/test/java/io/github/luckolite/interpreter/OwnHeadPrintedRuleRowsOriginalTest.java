// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural paper, ovals, rules and rails; no score material. */
public class OwnHeadPrintedRuleRowsOriginalTest {
    static final int W = 400, H = 260, L = 190, R = 210, T = 80, B = 96;

    static class Ink {
        byte[] g = new byte[W * H], l = new byte[W * H];
        int rule = 88;

        Ink(boolean ledger) {
            Arrays.fill(g, (byte) 255);
            for (int k = 0; k < 5; k++) line(30, 370, 104 + k * 16, 3, 4);
            if (ledger) line(183, 217, 88, 2, 5);
            for (int y = 88; y <= 118; y++) pixel(190, y, 0, 1);
            for (int x = 190; x <= 250; x++) for (int y = 114; y <= 118; y++) pixel(x, y, 0, 5);
            for (int y = T; y <= B; y++)
                for (int x = L; x <= R; x++)
                    if (Math.pow((x - 200) / 10., 2) + Math.pow((y - 88) / 8., 2) <= 1)
                        pixel(x, y, 0, 2);
        }

        void pixel(int x, int y, int tone, int label) {
            g[y * W + x] = (byte) tone;
            l[y * W + x] = (byte) label;
        }

        void line(int a, int b, int y, int thickness, int label) {
            for (int x = a; x <= b; x++)
                for (int d = 0; d < thickness; d++)
                    pixel(x, y + d, d == 0 ? 100 : d == 1 ? 20 : 150, label);
        }

        boolean[] rows() {
            return OwnHeadPrintedRuleRows.observe(
                    l, g, W, H, L, R, T, B, 104, 168, 16, new int[] {190, 118, 1});
        }

        boolean excludes() {
            boolean[] rows = rows();
            return rows != null && rows[rule - T];
        }

        int core() throws Exception {
            Class<?> c = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component"),
                    s = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Staff");
            Constructor<?> cc = c.getDeclaredConstructors()[0], sc = s.getDeclaredConstructors()[0];
            cc.setAccessible(true);
            sc.setAccessible(true);
            int n = 0;
            long xx = 0, yy = 0;
            for (int y = T; y <= B; y++)
                for (int x = L; x <= R; x++)
                    if (l[y * W + x] == 2) {
                        n++;
                        xx += x;
                        yy += y;
                    }
            Object head = cc.newInstance(n, L, R, T, B, xx / (float) n, yy / (float) n);
            Method m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "detectBeamCount",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            c,
                            s);
            m.setAccessible(true);
            return (Integer) m.invoke(null, l, g, W, H, head, sc.newInstance(104f, 168f, 16f));
        }
    }

    @Test
    public void bilateralPrintedLedgerInsideOwnBodyIsObserved() {
        assertTrue(new Ink(true).excludes());
    }

    @Test
    public void nativeCounterDoesNotCountTheLedgerAsAnotherBeam() throws Exception {
        assertEquals(1, new Ink(true).core());
    }

    @Test
    public void thinBlackTwoPixelRailRemains() {
        Ink p = new Ink(true);
        for (int x = 183; x <= 217; x++) for (int y = 88; y <= 89; y++) p.pixel(x, y, 0, 5);
        assertFalse(p.excludes());
    }

    @Test
    public void thinBlackThreePixelRailRemains() {
        Ink p = new Ink(true);
        for (int x = 183; x <= 217; x++) for (int y = 88; y <= 90; y++) p.pixel(x, y, 0, 5);
        assertFalse(p.excludes());
    }

    @Test
    public void partialHookPastOneEndpointRemains() {
        Ink p = new Ink(true);
        p.line(218, 229, 88, 2, 5);
        assertFalse(p.excludes());
    }

    @Test
    public void transverseRailPastBothEndpointsRemains() {
        Ink p = new Ink(true);
        p.line(150, 250, 88, 2, 5);
        assertFalse(p.excludes());
    }

    @Test
    public void separatedHookBeyondPaperRemains() {
        Ink p = new Ink(true);
        p.line(219, 220, 88, 2, 5);
        assertFalse(p.excludes());
    }

    @Test
    public void asymmetricShortRuleCannotSupplyBothSides() {
        Ink p = new Ink(true);
        for (int x = 183; x <= 186; x++) for (int y = 88; y <= 89; y++) p.pixel(x, y, 255, 0);
        assertFalse(p.excludes());
    }

    @Test
    public void missingAnyPrintedStaffLineAbstains() {
        for (int k = 0; k < 5; k++) {
            Ink p = new Ink(true);
            for (int x = 30; x <= 370; x++)
                for (int y = 104 + k * 16; y <= 106 + k * 16; y++) p.pixel(x, y, 255, 0);
            assertFalse("line " + k, p.excludes());
        }
    }

    @Test
    public void wrongPrintedRulePhaseAbstains() {
        Ink p = new Ink(false);
        p.line(183, 217, 91, 2, 5);
        assertFalse(p.excludes());
    }

    @Test
    public void hollowBodyAbstains() {
        Ink p = new Ink(true);
        for (int x = 194; x <= 206; x++) for (int y = 84; y <= 92; y++) p.pixel(x, y, 255, 0);
        assertFalse(p.excludes());
    }

    @Test
    public void shaftRequiredAndGeometryBounded() {
        Ink p = new Ink(true);
        assertNull(OwnHeadPrintedRuleRows.observe(p.l, p.g, W, H, L, R, T, B, 104, 168, 16, null));
        assertNull(
                OwnHeadPrintedRuleRows.observe(
                        p.l, p.g, W, H, L, R, T, B, 104, 168, Float.NaN, new int[] {190, 118, 1}));
        assertNull(
                OwnHeadPrintedRuleRows.observe(
                        p.l, p.g, W, H, L, R, T, B, 104, 168, 128, new int[] {190, 118, 1}));
    }

    @Test
    public void onlyObservedRowsInsideOwnBoundsAndInputsUnchanged() {
        Ink p = new Ink(true);
        byte[] g = p.g.clone(), l = p.l.clone();
        boolean[] rows = p.rows();
        assertEquals(B - T + 1, rows.length);
        for (int y = T; y <= B; y++) assertEquals(y == 88 || y == 89, rows[y - T]);
        assertArrayEquals(g, p.g);
        assertArrayEquals(l, p.l);
    }

    @Test
    public void literalStaffProfileInsideOwnBodyIsObserved() {
        Ink p = new Ink(false);
        p.line(30, 370, 88, 3, 4);
        boolean[] rows =
                OwnHeadPrintedRuleRows.observe(
                        p.l, p.g, W, H, L, R, T, B, 88, 152, 16, new int[] {190, 118, 1});
        assertNotNull(rows);
        assertTrue(rows[88 - T]);
        assertTrue(rows[89 - T]);
        assertTrue(rows[90 - T]);
    }

    @Test
    public void positiveRailOnStaffProfileIsRetained() {
        Ink p = new Ink(false);
        p.line(30, 370, 88, 3, 4);
        p.line(213, 240, 88, 2, 5);
        boolean[] rows =
                OwnHeadPrintedRuleRows.observe(
                        p.l, p.g, W, H, L, R, T, B, 88, 152, 16, new int[] {190, 118, 1});
        assertNotNull(rows);
        assertFalse(rows[88 - T]);
    }

    @Test
    public void unclassifiedExtraStaffThicknessIsRetained() {
        Ink p = new Ink(false);
        p.line(30, 370, 88, 3, 4);
        p.line(180, 220, 90, 2, 0);
        boolean[] rows =
                OwnHeadPrintedRuleRows.observe(
                        p.l, p.g, W, H, L, R, T, B, 88, 152, 16, new int[] {190, 118, 1});
        assertNotNull(rows);
        assertFalse(rows[88 - T]);
    }

    @Test
    public void reflectedAndDoubledPrintedRulesAreObserved() {
        for (int scale : new int[] {1, 2})
            for (boolean mirror : new boolean[] {false, true}) {
                Ink p = new Ink(true);
                int w = W * scale, h = H * scale;
                byte[] g = new byte[w * h], l = new byte[g.length];
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int xx = mirror ? w - 1 - x : x, yy = mirror ? h - 1 - y : y;
                        g[yy * w + xx] = p.g[y / scale * W + x / scale];
                        l[yy * w + xx] = p.l[y / scale * W + x / scale];
                    }
                int left = mirror ? w - 1 - ((R + 1) * scale - 1) : L * scale,
                        right = mirror ? w - 1 - L * scale : (R + 1) * scale - 1;
                int top = mirror ? h - 1 - ((B + 1) * scale - 1) : T * scale,
                        bottom = mirror ? h - 1 - T * scale : (B + 1) * scale - 1;
                float st = mirror ? h - 1 - 168 * scale : 104 * scale,
                        sb = mirror ? h - 1 - 104 * scale : 168 * scale;
                int[] shaft =
                        mirror
                                ? new int[] {w - 1 - 190 * scale, h - 1 - 118 * scale, -1}
                                : new int[] {190 * scale, 118 * scale, 1};
                boolean[] rows =
                        OwnHeadPrintedRuleRows.observe(
                                l, g, w, h, left, right, top, bottom, st, sb, 16 * scale, shaft);
                assertNotNull("scale " + scale + " mirror " + mirror, rows);
                int row = mirror ? h - 1 - 88 * scale : 88 * scale;
                assertTrue("scale " + scale + " mirror " + mirror, rows[row - top]);
            }
    }
}

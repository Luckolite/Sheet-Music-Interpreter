package io.github.luckolite.interpreter;

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric ink only: no commercial score imagery or coordinates. */
public final class RuleContactSingleBeamEvidenceTest {
    static final int W = 320, H = 260;

    static class Ink {
        byte[] g = new byte[W * H], l = new byte[W * H];
        List<Object> heads = new ArrayList<>();

        Ink(String kind) throws Exception {
            this(kind, 13);
        }

        Ink(String kind, int width) throws Exception {
            this(kind, width, 142);
        }

        Ink(String kind, int width, int beamY) throws Exception {
            Arrays.fill(g, (byte) 255);
            for (int line = 0; line < 5; line++)
                for (int y = 99 + line * 14; y <= 101 + line * 14; y++)
                    for (int x = 20; x < 290; x++) {
                        g[y * W + x] = (byte) (y == 99 + line * 14 ? 100 : 0);
                        l[y * W + x] = 4;
                    }
            if (!kind.equals("quarter"))
                for (int x = 86; x <= 220; x++) {
                    if (kind.equals("partial") && x > 112 && x < 137) continue;
                    int rise =
                            kind.equals("slur")
                                    ? Math.round(9 * (float) Math.sin(Math.PI * (x - 86) / 134))
                                    : 0;
                    int top = Math.round(beamY - .17f * (x - 86)) + rise;
                    for (int y = top; y < top + 4; y++) {
                        g[y * W + x] = 0;
                        l[y * W + x] = 5;
                    }
                    if (kind.equals("double"))
                        for (int y = top + 8; y < top + 12; y++) {
                            g[y * W + x] = 0;
                            l[y * W + x] = 5;
                        }
                    if (kind.equals("branch") && x > 100 && x < 208)
                        for (int y = top + 5; y < top + 9; y++) {
                            g[y * W + x] = 0;
                            l[y * W + x] = 5;
                        }
                }
            for (int i = 0; i < 3; i++) {
                int x = 86 + i * 62, cy = 183 - i * 14, end = Math.round(beamY - .17f * (x - 86));
                boolean opposite = kind.equals("opposed") && i > 0;
                if (opposite) end = cy + 40;
                for (int xx = x; xx <= x + 1; xx++)
                    for (int y = Math.min(end, cy); y <= Math.max(end, cy); y++) {
                        g[y * W + xx] = 0;
                        l[y * W + xx] = 1;
                    }
                int area = 0, minX = 999, maxX = 0, minY = 999, maxY = 0;
                long sx = 0, sy = 0;
                int cx = x - 6;
                for (int y = cy - 6; y <= cy + 6; y++)
                    for (int xx = cx - 7; xx <= cx + 7; xx++)
                        if ((xx - cx) * (xx - cx) / (width * width * .25f)
                                        + (y - cy) * (y - cy) / 30.25f
                                <= 1) {
                            g[y * W + xx] = 0;
                            l[y * W + xx] = 2;
                            area++;
                            sx += xx;
                            sy += y;
                            minX = Math.min(minX, xx);
                            maxX = Math.max(maxX, xx);
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y);
                        }
                Constructor<?> c =
                        Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component")
                                .getDeclaredConstructors()[0];
                c.setAccessible(true);
                heads.add(
                        c.newInstance(
                                area,
                                minX,
                                maxX,
                                minY,
                                maxY,
                                sx / (float) area,
                                sy / (float) area));
            }
        }

        boolean proof(int[] a, int[] b, float ah, float bh, float top) {
            return PrintedSingleBeamPath.connectsAfterRuleContact(
                    g, W, H, a, b, ah, bh, 14, top, null);
        }

        int count() throws Exception {
            return count(false);
        }

        int coreCount() throws Exception {
            return count(true);
        }

        int count(boolean core) throws Exception {
            Class<?> c = heads.get(0).getClass(),
                    s = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Staff");
            Constructor<?> sc = s.getDeclaredConstructors()[0];
            sc.setAccessible(true);
            Method m =
                    core
                            ? OmrScoreInterpreter.class.getDeclaredMethod(
                                    "detectBeamCount",
                                    byte[].class,
                                    byte[].class,
                                    int.class,
                                    int.class,
                                    c,
                                    s)
                            : OmrScoreInterpreter.class.getDeclaredMethod(
                                    "detectBeamCount",
                                    byte[].class,
                                    byte[].class,
                                    int.class,
                                    int.class,
                                    c,
                                    s,
                                    List.class);
            m.setAccessible(true);
            return (Integer)
                    (core
                            ? m.invoke(
                                    null, l, g, W, H, heads.get(0), sc.newInstance(100f, 156f, 14f))
                            : m.invoke(
                                    null,
                                    l,
                                    g,
                                    W,
                                    H,
                                    heads.get(0),
                                    sc.newInstance(100f, 156f, 14f),
                                    heads));
        }
    }

    static final int[] A = {86, 141, -1}, B = {148, 127, -1};

    @Test
    public void crossingSingleRailHasBoundedProof() throws Exception {
        assertTrue(new Ink("single").proof(A, B, 183, 169, 100));
    }

    @Test
    public void actualListIngressRecoversEighth() throws Exception {
        for (int width : new int[] {12, 13, 14, 15})
            assertEquals("own complete rail width " + width, 1, new Ink("single", width).count());
    }

    @Test
    public void ordinaryRailAboveRuleRemainsEighth() throws Exception {
        assertEquals(1, new Ink("single", 13, 140).count());
    }

    @Test
    public void ordinaryRailBelowRuleRemainsEighth() throws Exception {
        assertEquals(1, new Ink("single", 13, 144).count());
    }

    @Test
    public void ordinaryTwoRailsRemainSixteenth() throws Exception {
        assertEquals(2, new Ink("double", 13, 144).coreCount());
    }

    @Test
    public void quarterShaftsHaveNoRail() throws Exception {
        assertFalse(new Ink("quarter").proof(A, B, 183, 169, 100));
        assertEquals(0, new Ink("quarter").count());
    }

    @Test
    public void bowedSlurCannotSupplyStraightRail() throws Exception {
        assertFalse(new Ink("slur").proof(A, B, 183, 169, 100));
    }

    @Test
    public void partialRailCannotBridgePaper() throws Exception {
        assertFalse(new Ink("partial").proof(A, B, 183, 169, 100));
    }

    @Test
    public void oppositeShaftCannotOwnRail() throws Exception {
        assertFalse(new Ink("single").proof(A, new int[] {148, 209, 1}, 183, 169, 100));
    }

    @Test
    public void crossStaffHeadCannotOwnSameRail() throws Exception {
        assertFalse(new Ink("single").proof(A, B, 183, 140, 100));
    }

    @Test
    public void secondRailCannotSupplyExclusiveProof() throws Exception {
        assertFalse(new Ink("double").proof(A, B, 183, 169, 100));
    }

    @Test
    public void competingBranchCannotSupplyExclusiveProof() throws Exception {
        assertFalse(new Ink("branch").proof(A, B, 183, 169, 100));
    }

    @Test
    public void ruleFrameMustBeIndependentlyPresent() throws Exception {
        assertFalse(new Ink("single").proof(A, B, 183, 169, 80));
    }

    @Test
    public void downwardOwnedRailUsesSameEvidence() throws Exception {
        Ink i = new Ink("single");
        byte[] mirror = new byte[i.g.length];
        for (int y = 0; y < H; y++) System.arraycopy(i.g, y * W, mirror, (H - 1 - y) * W, W);
        assertTrue(
                PrintedSingleBeamPath.connectsAfterRuleContact(
                        mirror,
                        W,
                        H,
                        new int[] {86, 118, 1},
                        new int[] {148, 132, 1},
                        76,
                        90,
                        14,
                        103,
                        null));
    }

    @Test
    public void overlongShaftIsNotSameStaffEvidence() throws Exception {
        assertFalse(new Ink("single").proof(A, B, 230, 169, 100));
    }
}

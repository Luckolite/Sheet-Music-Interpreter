// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric head/shaft/rail fixtures. No score pixels or private coordinates. */
public final class ObservedStaffFringeOriginalTest {
    static final int W = 320, H = 260;
    static final Class<?> C, S;
    static final Constructor<?> CC, SC;
    static final Method M;

    static {
        try {
            C = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component");
            S = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Staff");
            CC = C.getDeclaredConstructors()[0];
            SC = S.getDeclaredConstructors()[0];
            CC.setAccessible(true);
            SC.setAccessible(true);
            M =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "detectBeamCount",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            C,
                            S,
                            List.class);
            M.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class Ink {
        byte[] g = new byte[W * H], l = new byte[W * H];
        List<Object> heads = new ArrayList<>();
        int root, rails;
        float staffTop = 100, staffBottom = 156;

        Ink(int beamY, int railCount) throws Exception {
            this(beamY, railCount, 1);
        }

        Ink(int beamY, int railCount, int staffShift) throws Exception {
            root = beamY;
            rails = railCount;
            Arrays.fill(g, (byte) 255);
            for (int line = 0; line < 5; line++)
                for (int y = 99 + line * 14; y <= 101 + line * 14; y++)
                    for (int x = 20; x < 290; x++)
                        g[y * W + x] = (byte) (y == 99 + line * 14 ? 100 : 0);
            for (int x = 86; x <= 160; x++)
                for (int rail = 0; rail < rails; rail++) {
                    int top = Math.round(root - .10f * (x - 86)) + rail * 8;
                    for (int y = top; y < top + 5; y++) {
                        g[y * W + x] = 0;
                        l[y * W + x] = 5;
                    }
                }
            for (int x = 86; x <= 87; x++)
                for (int y = root; y <= 150; y++) {
                    g[y * W + x] = 0;
                    l[y * W + x] = 1;
                }
            for (int x = 148; x <= 149; x++)
                for (int y = Math.round(root - .10f * 62); y <= 165; y++) {
                    g[y * W + x] = 0;
                    l[y * W + x] = 1;
                }
            for (int line = 0; line < 5; line++)
                for (int y = 99 + staffShift + line * 14; y <= 101 + staffShift + line * 14; y++)
                    for (int x = 20; x < 290; x++) l[y * W + x] = 4;
            head(80, 150);
            head(142, 165);
        }

        Ink(int root, int rails, String kind) throws Exception {
            this(root, rails);
            if (kind.equals("partial") || kind.equals("slur")) {
                for (int x = kind.equals("partial") ? 105 : 86;
                        x <= (kind.equals("partial") ? 122 : 160);
                        x++) {
                    int top = Math.round(root - .10f * (x - 86));
                    for (int y = top; y < top + 5; y++) {
                        g[y * W + x] = (byte) 255;
                        l[y * W + x] = 0;
                    }
                    if (kind.equals("slur")) {
                        int bow = (int) Math.round(12 * Math.sin(Math.PI * (x - 86) / 74));
                        for (int y = top + bow; y < top + bow + 5; y++) {
                            g[y * W + x] = 0;
                            l[y * W + x] = 5;
                        }
                    }
                }
            }
            if (kind.startsWith("secondary:")) {
                String[] parts = kind.split(":");
                int thick = Integer.parseInt(parts[1]);
                boolean hook = parts[2].equals("hook"), masked = parts[3].equals("mask");
                for (int x = 86; x <= (hook ? 103 : 160); x++) {
                    int top = Math.round(root - .1f * (x - 86)) + 8;
                    for (int y = top; y < top + thick; y++) {
                        g[y * W + x] = 0;
                        l[y * W + x] = (byte) (masked ? 4 : 5);
                    }
                }
            }
            if (kind.equals("thinSecond"))
                for (int x = 86; x <= 160; x++) {
                    int top = Math.round(root - .1f * (x - 86)) + 8;
                    for (int y = top; y < top + 3; y++) {
                        g[y * W + x] = 0;
                        l[y * W + x] = 5;
                    }
                }
            if (kind.equals("hook") || kind.equals("clippedHook"))
                for (int x = 104; x <= 125; x++) {
                    int top = Math.round(root - .1f * (x - 86)) + (kind.equals("hook") ? 8 : 15);
                    for (int y = top; y < top + 5; y++) {
                        g[y * W + x] = 0;
                        l[y * W + x] = 5;
                    }
                }
            if (kind.equals("otherHead")) head(115, 136);
            if (kind.equals("missingPeer") || kind.equals("opposed")) {
                for (int x = 148; x <= 149; x++)
                    for (int y = root; y < 159; y++) {
                        g[y * W + x] = (byte) 255;
                        l[y * W + x] = 0;
                    }
                if (kind.equals("opposed"))
                    for (int y = 165; y <= 210; y++) {
                        g[y * W + 136] = 0;
                        l[y * W + 136] = 1;
                    }
            }
        }

        void head(int cx, int cy) throws Exception {
            int area = 0, minX = 999, maxX = 0, minY = 999, maxY = 0;
            long sx = 0, sy = 0;
            for (int y = cy - 6; y <= cy + 6; y++)
                for (int x = cx - 7; x <= cx + 7; x++)
                    if ((x - cx) * (x - cx) / 42.25f + (y - cy) * (y - cy) / 30.25f <= 1) {
                        g[y * W + x] = 0;
                        l[y * W + x] = 2;
                        area++;
                        sx += x;
                        sy += y;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
            heads.add(
                    CC.newInstance(
                            area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area));
        }

        void rotate() throws Exception {
            byte[] ng = new byte[g.length], nl = new byte[l.length];
            for (int i = 0; i < g.length; i++) {
                ng[g.length - 1 - i] = g[i];
                nl[l.length - 1 - i] = l[i];
            }
            g = ng;
            l = nl;
            List<Object> rotated = new ArrayList<>();
            for (Object head : heads) {
                String[] names = {"area", "minX", "maxX", "minY", "maxY", "centerX", "centerY"};
                Number[] v = new Number[7];
                for (int i = 0; i < 7; i++) {
                    Method m = C.getDeclaredMethod(names[i]);
                    m.setAccessible(true);
                    v[i] = (Number) m.invoke(head);
                }
                rotated.add(
                        CC.newInstance(
                                v[0].intValue(),
                                W - 1 - v[2].intValue(),
                                W - 1 - v[1].intValue(),
                                H - 1 - v[4].intValue(),
                                H - 1 - v[3].intValue(),
                                W - 1 - v[5].floatValue(),
                                H - 1 - v[6].floatValue()));
            }
            heads = rotated;
            staffTop = 103;
            staffBottom = 159;
        }

        int core() throws Exception {
            Method m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "detectBeamCount",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            C,
                            S);
            m.setAccessible(true);
            return (Integer)
                    m.invoke(
                            null,
                            l,
                            g,
                            W,
                            H,
                            heads.get(0),
                            SC.newInstance(staffTop, staffBottom, 14f));
        }

        int count() throws Exception {
            return (Integer)
                    M.invoke(
                            null,
                            l,
                            g,
                            W,
                            H,
                            heads.get(0),
                            SC.newInstance(staffTop, staffBottom, 14f),
                            heads);
        }
    }

    @Test
    public void isolatedUpperRuleFringeDoesNotMakeSingleBeamDouble() throws Exception {
        assertEquals(1, new Ink(126, 1).count());
    }

    @Test
    public void twoIsolatedFringesDoNotMakeSingleBeamTriple() throws Exception {
        assertEquals(1, new Ink(123, 1, -1).count());
    }

    @Test
    public void saturatedFourBandOpticsRemainOutsideTheAdjudicationScope() throws Exception {
        Ink i = new Ink(128, 1);
        assertEquals(i.core(), i.count());
    }

    @Test
    public void mirroredRuleFringePreservesSingleBeam() throws Exception {
        Ink i = new Ink(126, 1);
        i.rotate();
        assertEquals(1, i.count());
    }

    @Test
    public void unchangedCoreStillRecordsItsOriginalAlias() throws Exception {
        assertEquals(2, new Ink(126, 1).core());
    }

    @Test
    public void realDoubleBeamRetainsBothVisibleRails() throws Exception {
        assertTrue(new Ink(126, 2).count() >= 2);
    }

    @Test
    public void realTripleBeamRetainsThreeVisibleRails() throws Exception {
        assertEquals(3, new Ink(126, 3).count());
    }

    @Test
    public void mirroredDoubleAndTripleRailsSurvive() throws Exception {
        for (int n : new int[] {2, 3}) {
            Ink i = new Ink(126, n);
            i.rotate();
            assertTrue(i.count() >= n);
        }
    }

    @Test
    public void twoAndThreePixelRailsAndOwnHooksSurviveSemanticOcclusion() throws Exception {
        for (int root : new int[] {126, 128, 130})
            for (int width : new int[] {2, 3})
                for (String topology : new String[] {"full", "hook"})
                    for (String label : new String[] {"symbol", "mask"}) {
                        Ink i =
                                new Ink(
                                        root,
                                        1,
                                        "secondary:" + width + ":" + topology + ":" + label);
                        assertTrue(
                                "root=" + root + " width=" + width + " " + topology + " " + label,
                                i.count() >= 2);
                    }
    }

    @Test
    public void bareQuarterRemainsQuarter() throws Exception {
        assertEquals(0, new Ink(110, 0).count());
    }

    @Test
    public void partialSecondaryAndClippedUnknownHooksRemainMultiple() throws Exception {
        for (String kind : new String[] {"hook", "clippedHook"})
            assertTrue(new Ink(126, 1, kind).count() >= 2);
    }

    @Test
    public void bowedSlurIsNotReclassifiedByRuleEvidence() throws Exception {
        assertEquals(new Ink(126, 1, "slur").core(), new Ink(126, 1, "slur").count());
    }

    @Test
    public void absentOrOpposedPeerProvidesNoAdjudication() throws Exception {
        for (String kind : new String[] {"missingPeer", "opposed"})
            assertEquals(new Ink(126, 1, kind).core(), new Ink(126, 1, kind).count());
    }

    @Test
    public void twoUnclassifiedRowsAreOutsideSingleRowScope() throws Exception {
        Ink i = new Ink(126, 1, 2);
        assertEquals(i.core(), i.count());
    }

    @Test
    public void aLongPositiveSymbolRunProtectsTheObservedBand() throws Exception {
        Ink i = new Ink(126, 1);
        for (int x = 90; x <= 105; x++) i.l[141 * W + x] = 5;
        assertEquals(i.core(), i.count());
    }

    @Test
    public void aTwoDimensionalSymbolPatchProtectsTheObservedBand() throws Exception {
        Ink i = new Ink(126, 1);
        for (int y = 141; y <= 142; y++) for (int x = 94; x <= 98; x++) i.l[y * W + x] = 5;
        assertEquals(i.core(), i.count());
    }

    @Test
    public void aShortIsolatedSymbolSeamDoesNotChangeRuleOwnership() throws Exception {
        Ink i = new Ink(126, 1);
        for (int x = 94; x <= 96; x++) i.l[141 * W + x] = 5;
        assertEquals(1, i.count());
    }

    @Test
    public void missingRawStaffRulePreventsAdjudication() throws Exception {
        Ink i = new Ink(126, 1);
        for (int y = 98; y <= 103; y++)
            for (int x = 20; x < 290; x++) {
                i.g[y * W + x] = (byte) 255;
                if (i.l[y * W + x] == 4) i.l[y * W + x] = 0;
            }
        assertEquals(i.core(), i.count());
    }

    @Test
    public void countingNeverMutatesCallerPlanes() throws Exception {
        Ink i = new Ink(126, 1);
        byte[] gray = i.g.clone(), labels = i.l.clone();
        i.count();
        assertArrayEquals(gray, i.g);
        assertArrayEquals(labels, i.l);
    }

    public static void main(String[] args) throws Exception {
        for (int root : new int[] {126, 128, 130})
            for (int width : new int[] {2, 3})
                for (String topology : new String[] {"full", "hook"})
                    for (String label : new String[] {"symbol", "mask"}) {
                        Ink i =
                                new Ink(
                                        root,
                                        1,
                                        "secondary:" + width + ":" + topology + ":" + label);
                        System.out.println(
                                root + " " + width + " " + topology + " " + label + " core="
                                        + i.core() + " list=" + i.count());
                    }
    }
}

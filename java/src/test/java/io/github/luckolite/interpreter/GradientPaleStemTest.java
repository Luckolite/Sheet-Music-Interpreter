// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;

/** Original painted shafts on sloped paper tone; no score-derived samples. */
public class GradientPaleStemTest {
    static final int W = 340, H = 270;
    final byte[] gray = new byte[W * H], labels = new byte[W * H];
    Object head, staff;
    boolean down;

    int y(int row) {
        return down ? H - 1 - row : row;
    }

    int paper(int x, int slope) {
        return Math.max(180, Math.min(255, 230 + (x - 121) * slope));
    }

    int paperAt(int row, int x, int slope) {
        return row >= 110 && row <= 145 ? paper(x, slope) : 250;
    }

    void rect(int l, int r, int t, int b, int ink) {
        for (int row = t; row <= b; row++)
            for (int x = l; x <= r; x++) gray[y(row) * W + x] = (byte) ink;
    }

    void scene(boolean down, int slope, boolean shaft, int beams, boolean rules) throws Exception {
        this.down = down;
        Arrays.fill(labels, (byte) 0);
        for (int row = 0; row < H; row++)
            for (int x = 0; x < W; x++) gray[y(row) * W + x] = (byte) paperAt(row, x, slope);
        if (rules)
            for (int row = 0; row < 5; row++) {
                int yy = 110 + row * 16;
                rect(15, 320, yy, yy, 100);
                for (int x = 15; x <= 320; x++) labels[y(yy) * W + x] = 4;
            }
        int axis = down ? 101 : 121;
        if (shaft)
            for (int row = 91; row <= 160; row++)
                for (int x = axis - 1; x <= axis + 1; x++)
                    gray[y(row) * W + x] = (byte) (paperAt(row, x, slope) - 12);
        for (int beam = 0; beam < beams; beam++)
            rect(axis, axis + 65, 90 + beam * 13, 96 + beam * 13, 35);
        for (int row = 153; row <= 167; row++)
            for (int x = 100; x <= 122; x++)
                if (Math.pow((x - 111) / 11., 2) + Math.pow((row - 160) / 7., 2) <= 1) {
                    gray[y(row) * W + x] = 65;
                    labels[y(row) * W + x] = 2;
                }
        Class<?> hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Constructor<?> cc = hc.getDeclaredConstructors()[0];
        cc.setAccessible(true);
        head = cc.newInstance(220, 100, 122, y(160) - 7, y(160) + 7, 111f, (float) y(160));
        Class<?> sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        cc = sc.getDeclaredConstructor(float.class, float.class, float.class);
        cc.setAccessible(true);
        staff = cc.newInstance((float) y(down ? 174 : 110), (float) y(down ? 110 : 174), 16f);
    }

    int count() throws Exception {
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        head.getClass(),
                        staff.getClass(),
                        List.class);
        m.setAccessible(true);
        return (int) m.invoke(null, labels, gray, W, H, head, staff, List.of(head));
    }

    @Test
    public void faintShaftOnSlopedPaperKeepsItsTwoBeams() throws Exception {
        for (boolean down : new boolean[] {false, true})
            for (boolean rules : new boolean[] {false, true})
                for (int slope : new int[] {-2, -1, 0, 1, 2}) {
                    scene(down, slope, true, 2, rules);
                    assertEquals(
                            "down=" + down + " rules=" + rules + " slope=" + slope, 2, count());
                }
    }

    @Test
    public void paperGradientAloneCannotSupplyTheShaft() throws Exception {
        for (boolean down : new boolean[] {false, true})
            for (int slope : new int[] {-2, -1, 0, 1, 2}) {
                scene(down, slope, false, 2, false);
                assertNotEquals(2, count());
            }
    }

    @Test
    public void singleBeamCannotProveVeryFaintStem() throws Exception {
        for (boolean down : new boolean[] {false, true}) {
            scene(down, 2, true, 1, false);
            assertNotEquals(2, count());
        }
    }

    @Test
    public void paperGapCannotConnectTheHeadToDetachedBeams() throws Exception {
        for (boolean down : new boolean[] {false, true}) {
            scene(down, 2, true, 2, false);
            for (int row = 125; row <= 137; row++)
                for (int x = 98; x <= 125; x++) gray[y(row) * W + x] = (byte) paperAt(row, x, 2);
            assertNotEquals(2, count());
        }
    }

    @Test
    public void broadPaperShadowCannotSupplyAShaft() throws Exception {
        for (boolean down : new boolean[] {false, true}) {
            scene(down, 0, true, 2, false);
            rect(down ? 90 : 110, down ? 112 : 132, 110, 145, 190);
            assertNotEquals(2, count());
        }
    }

    @Test
    public void monotonicShadowEdgeCannotSupplyAShaft() throws Exception {
        for (boolean down : new boolean[] {false, true})
            for (int shade : new int[] {195, 215, 225, 230, 235, 240}) {
                scene(down, 0, false, 2, false);
                rect(0, down ? 101 : 121, 91, 160, shade);
                assertNotEquals("shade=" + shade, 2, count());
            }
    }

    @Test
    public void analysisPreservesInputBytes() throws Exception {
        scene(false, 2, true, 2, true);
        byte[] g = gray.clone(), l = labels.clone();
        count();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    @Test
    public void faintQuarterHasNoBeam() throws Exception {
        for (boolean down : new boolean[] {false, true})
            for (int slope : new int[] {-2, 0, 2}) {
                scene(down, slope, true, 0, true);
                assertEquals(0, count());
            }
    }

    @Test
    public void thinPairedRulesCannotProveBeams() throws Exception {
        scene(false, 2, true, 0, false);
        rect(121, 186, 90, 91, 35);
        rect(121, 186, 103, 104, 35);
        assertNotEquals(2, count());
    }

    @Test
    public void fullRecognitionRetainsSixteenthDuration() throws Exception {
        for (boolean down : new boolean[] {false, true}) {
            scene(down, 2, true, 2, true);
            var notes =
                    OmrScoreInterpreter.analyze(
                                    labels,
                                    gray,
                                    W,
                                    H,
                                    List.of(new MeasureRegion(.03f, .97f, .12f, .98f)))
                            .notes();
            assertEquals(1, notes.size());
            assertEquals(2, notes.get(0).beamCount());
            assertEquals(.25, ScoreNoteTiming.writtenDurationBeats(notes.get(0)), 0);
        }
    }
}

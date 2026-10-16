// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original hollow chord drawings with a displaced lowest second. */
public final class SmallHollowSecondChordTest {
    static final int W = 300, H = 270, G = 18;
    final byte[] gray = new byte[W * H], labels = new byte[W * H];

    SmallHollowSecondChordTest(boolean filled) {
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {108, 126}) oval(150, y, filled);
        oval(128, 135, filled);
        for (int y = 90; y <= 180; y++) {
            gray[y * W + 139] = 0;
            if (labels[y * W + 139] == 0) labels[y * W + 139] = 1;
        }
        // Segmentation joins the three printed ovals, without inventing new ink.
        for (int y = 108; y <= 135; y++) for (int x = 139; x <= 142; x++) labels[y * W + x] = 2;
    }

    public SmallHollowSecondChordTest() {
        this(false);
    }

    void oval(int cx, int cy, boolean filled) {
        for (int y = cy - 9; y <= cy + 9; y++)
            for (int x = cx - 13; x <= cx + 13; x++) {
                double radius = Math.pow((x - cx) / 13d, 2) + Math.pow((y - cy) / 9d, 2);
                if (radius <= 1) {
                    labels[y * W + x] = 2;
                    if (filled || radius > .45) gray[y * W + x] = 0;
                }
            }
    }

    Object component(int left, int right, int top, int bottom) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(
                900, left, right, top, bottom, (left + right) / 2f, (top + bottom) / 2f);
    }

    Object staff() throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Staff")
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(90f, 162f, 18f);
    }

    Object actualChordComponent() throws Exception {
        int area = 0, minX = W, maxX = 0, minY = H, maxY = 0;
        long sumX = 0, sumY = 0;
        for (int y = 99; y <= 144; y++)
            for (int x = 115; x <= 163; x++)
                if (labels[y * W + x] == 2) {
                    area++;
                    sumX += x;
                    sumY += y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(
                area, minX, maxX, minY, maxY, sumX / (float) area, sumY / (float) area);
    }

    List<?> split() throws Exception {
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "splitStackedHeads",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        method.setAccessible(true);
        return (List<?>)
                method.invoke(
                        null,
                        labels,
                        gray,
                        W,
                        H,
                        List.of(actualChordComponent()),
                        List.of(staff()));
    }

    @Test
    public void threeHollowTonesSurviveFusedMask() throws Exception {
        assertEquals(3, split().size());
    }

    @Test
    public void threeFilledOvalsAlsoRemainThreeTones() throws Exception {
        assertEquals(3, new SmallHollowSecondChordTest(true).split().size());
    }

    @Test
    public void missingUpperOvalCannotBeInvented() throws Exception {
        for (int y = 99; y <= 117; y++)
            for (int x = 137; x <= 163; x++) gray[y * W + x] = (byte) 255;
        assertTrue(split().size() < 3);
    }

    @Test
    public void detachedNeighborCannotExtendHalfChordDotSearch() throws Exception {
        Arrays.fill(gray, (byte) 255);
        oval(150, 126, false);
        oval(128, 135, false);
        assertEquals(141, dotRight());
    }

    @Test
    public void sharedStemMovesDotSearchPastDisplacedPartner() throws Exception {
        assertEquals(163, dotRight());
    }

    int dotRight() throws Exception {
        Object a = component(115, 141, 126, 144), b = component(137, 163, 117, 135);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "halfChordDotAnchor",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        a.getClass(),
                        List.class,
                        float.class);
        method.setAccessible(true);
        Object result = method.invoke(null, labels, gray, W, H, a, List.of(a, b), 18f);
        var right = result.getClass().getDeclaredMethod("maxX");
        right.setAccessible(true);
        return (int) right.invoke(result);
    }

    @Test
    public void sourceRasterAndLabelsArePreserved() throws Exception {
        byte[] g = gray.clone(), l = labels.clone();
        split();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    @Test
    public void restFilteringKeepsDotBeyondDisplacedPartner() throws Exception {
        for (int y = 132; y <= 138; y++)
            for (int x = 174; x <= 180; x++)
                if ((x - 177) * (x - 177) + (y - 135) * (y - 135) <= 9) gray[y * W + x] = 0;
        Object head = component(115, 141, 126, 144), anchor = component(115, 163, 126, 144);
        var event = new ScoreNoteEvent(0, .4f, 0, 0, 1, .5f, false, 1, 0, 2, 2f);
        var type = Class.forName(OmrScoreInterpreter.class.getName() + "$DetectedNote");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object note = ctor.newInstance(event, head, 18f);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "dotsOutsideRests",
                        List.class,
                        type,
                        List.class,
                        List.class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        head.getClass(),
                        List.class);
        method.setAccessible(true);
        List<ScoreRestEvent> rests = List.of(new ScoreRestEvent(0, .8f, .5f, .1f, 0, 1));
        List<MeasureRegion> measures = List.of(new MeasureRegion(0, 1, 0, 1));
        assertEquals(
                1,
                method.invoke(
                        null, List.of(), note, rests, measures, gray, W, H, List.of(), anchor,
                        List.of()));
        assertEquals(
                0,
                method.invoke(
                        null, List.of(), note, rests, measures, gray, W, H, List.of(), head,
                        List.of()));
    }

    @Test
    public void eachMissingPrintedOvalPreventsThreeHeadRecovery() throws Exception {
        for (int[] center : new int[][] {{150, 108}, {150, 126}, {128, 135}}) {
            var page = new SmallHollowSecondChordTest();
            for (int y = center[1] - 9; y <= center[1] + 9; y++)
                for (int x = center[0] - 13; x <= center[0] + 13; x++)
                    page.gray[y * W + x] = (byte) 255;
            assertTrue(page.split().size() < 3);
        }
    }

    @Test
    public void whiteMaskCannotRecoverPrintedChord() throws Exception {
        Arrays.fill(gray, (byte) 255);
        assertTrue(split().size() < 3);
    }
}

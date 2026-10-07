// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;

/** Original painted double beams with a locally faded junction, no score pixels. */
public class FadedBeamJunctionTest {
    private static final int W = 300, H = 240;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];
    private Object head, staff;

    private void rect(int left, int right, int top, int bottom, int value) {
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) gray[y * W + x] = (byte) value;
    }

    private void scene(boolean down, int junction, int thickness, int paper, boolean rules)
            throws Exception {
        Arrays.fill(gray, (byte) paper);
        Arrays.fill(labels, (byte) 0);
        if (rules)
            for (int row = 0; row < 5; row++) {
                int y = Math.round(100 + row * 14.25f);
                rect(12, 280, y, y, 100);
                for (int x = 12; x <= 280; x++) labels[y * W + x] = 4;
            }
        int stemInk = paper == 250 ? 235 : 135;
        if (down) {
            rect(89, 93, 150, 210, stemInk);
            rect(91, 91, 211, 211, stemInk);
            rect(20, 90, 211 - thickness, 210, 35);
            rect(20, 90, 199 - thickness, 198, 35);
            rect(86, 90, 199 - thickness, 198, junction);
        } else {
            rect(109, 113, 90, 150, stemInk);
            rect(111, 111, 89, 89, stemInk);
            rect(112, 190, 90, 89 + thickness, 35);
            rect(112, 190, 102, 101 + thickness, 35);
            rect(112, 116, 102, 101 + thickness, junction);
        }
        for (int y = 144; y <= 156; y++)
            for (int x = 90; x <= 112; x++)
                if (Math.pow((x - 101) / 11., 2) + Math.pow((y - 150) / 6., 2) <= 1) {
                    gray[y * W + x] = 80;
                    labels[y * W + x] = 2;
                }
        Class<?> hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Constructor<?> constructor = hc.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        head = constructor.newInstance(180, 90, 112, 144, 156, 100f, 150f);
        Class<?> sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        constructor = sc.getDeclaredConstructor(float.class, float.class, float.class);
        constructor.setAccessible(true);
        staff = constructor.newInstance(100f, 157f, 14.25f);
    }

    private int count() throws Exception {
        Method method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        head.getClass(),
                        staff.getClass(),
                        List.class);
        method.setAccessible(true);
        return (int) method.invoke(null, labels, gray, W, H, head, staff, List.of(head));
    }

    @Test
    public void fadedJunctionDoesNotLoseTheSecondBeam() throws Exception {
        for (boolean down : new boolean[] {false, true})
            for (boolean rules : new boolean[] {false, true})
                for (int shade : new int[] {170, 185, 195, 202}) {
                    scene(down, shade, 7, 250, rules);
                    assertEquals(
                            "down=" + down + " rules=" + rules + " shade=" + shade, 2, count());
                }
    }

    @Test
    public void blankJunctionCannotBorrowTheDetachedBand() throws Exception {
        for (boolean down : new boolean[] {false, true}) {
            scene(down, 250, 7, 250, false);
            assertNotEquals(2, count());
        }
    }

    @Test
    public void thinParallelStrokesCannotProveTwoBeams() throws Exception {
        for (int thickness : new int[] {1, 2, 3}) {
            scene(false, 185, thickness, 250, false);
            assertNotEquals(2, count());
        }
    }

    @Test
    public void grayPaperDoesNotConnectAnUnattachedDarkBand() throws Exception {
        for (int paper : new int[] {145, 185, 198, 205, 225}) {
            scene(false, paper, 7, paper, false);
            assertNotEquals("paper=" + paper, 2, count());
        }
    }

    @Test
    public void recognitionPreservesTheSourceArrays() throws Exception {
        scene(false, 185, 7, 250, true);
        byte[] originalGray = gray.clone(), originalLabels = labels.clone();
        count();
        assertArrayEquals(originalGray, gray);
        assertArrayEquals(originalLabels, labels);
    }

    @Test
    public void fullRecognitionRetainsWrittenSixteenthDuration() throws Exception {
        for (boolean down : new boolean[] {false, true}) {
            scene(down, 185, 7, 250, true);
            var notes =
                    OmrScoreInterpreter.analyze(
                                    labels,
                                    gray,
                                    W,
                                    H,
                                    List.of(new MeasureRegion(.04f, .93f, .30f, .97f)))
                            .notes();
            assertEquals(1, notes.size());
            assertEquals(2, notes.get(0).beamCount());
            assertEquals(.25, ScoreNoteTiming.writtenDurationBeats(notes.get(0)), 0);
        }
    }
}

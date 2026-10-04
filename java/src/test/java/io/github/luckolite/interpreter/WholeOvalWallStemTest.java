// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural masks and ovals; no source score pixels or glyph outlines. */
public class WholeOvalWallStemTest {
    private static final int W = 280, H = 240;
    private final byte[] labels = new byte[W * H], gray = new byte[W * H];
    private final List<Object> heads = new ArrayList<>();
    private final Class<?> component;
    private final Constructor<?> constructor;

    public WholeOvalWallStemTest() throws Exception {
        Arrays.fill(gray, (byte) 255);
        component = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        constructor = component.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
    }

    private Object oval(int cx, int cy, int rx, int ry, int modelRight) throws Exception {
        int area = 0;
        long sx = 0, sy = 0;
        int minX = W, maxX = 0, minY = H, maxY = 0;
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++) {
                double d =
                        (x - cx) * (x - cx) / (double) (rx * rx)
                                + (y - cy) * (y - cy) / (double) (ry * ry);
                if (d > 1) continue;
                if (d >= .45) gray[y * W + x] = 20;
                if (x <= cx + modelRight) {
                    labels[y * W + x] = 2;
                    area++;
                    sx += x;
                    sy += y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        Object head =
                constructor.newInstance(
                        area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
        heads.add(head);
        return head;
    }

    private void shaft(int x, int top, int bottom, boolean physical) {
        for (int y = top; y <= bottom; y++) {
            labels[y * W + x] = 1;
            if (physical) gray[y * W + x] = 20;
        }
    }

    private float value(Object head) throws Exception {
        Method method;
        try {
            method =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "detectUnbeamedDuration",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            component,
                            float.class,
                            int.class,
                            List.class);
            method.setAccessible(true);
            return (float) method.invoke(null, labels, gray, W, H, head, 16f, 0, heads);
        } catch (NoSuchMethodException original) {
            method =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "detectUnbeamedDuration",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            component,
                            float.class,
                            int.class);
            method.setAccessible(true);
            return (float) method.invoke(null, labels, gray, W, H, head, 16f, 0);
        }
    }

    @Test
    public void slightlyTallWholeIgnoresWhiteModelShaft() throws Exception {
        Object n = oval(130, 100, 16, 9, 16);
        shaft(146, 45, 100, false);
        assertEquals(4, value(n), 0);
    }

    @Test
    public void slightlyTallWideHalfKeepsPhysicalShaft() throws Exception {
        Object n = oval(130, 100, 16, 9, 16);
        shaft(145, 45, 100, true);
        assertEquals(2, value(n), 0);
    }

    private void stacked(boolean exterior) throws Exception {
        oval(130, 90, 16, 8, 8);
        oval(130, 108, 16, 8, 8);
        oval(130, 126, 16, 8, 16);
        // Ovals aligned at their sides can leave a continuous chord-height run.
        for (int y = 82; y <= 134; y++) {
            gray[y * W + 114] = 20;
            gray[y * W + 143] = 20;
        }
        shaft(148, 80, 134, false);
        if (exterior) shaft(137, 40, 134, true);
    }

    @Test
    public void ovalWallRunIsNotAWholeChordShaft() throws Exception {
        stacked(false);
        for (Object n : heads) assertEquals(4, value(n), 0);
    }

    @Test
    public void exteriorChordShaftPreservesHalves() throws Exception {
        stacked(true);
        for (Object n : heads) assertEquals(2, value(n), 0);
    }

    @Test
    public void narrowHalfAboveWholeRetainsSemanticStemEvidence() throws Exception {
        Object half = oval(130, 100, 12, 9, 12);
        oval(130, 136, 16, 8, 16);
        shaft(119, 100, 127, false);
        assertEquals(2, value(half), 0);
    }

    @Test
    public void callerMasksAreUnchanged() throws Exception {
        stacked(false);
        byte[] a = labels.clone(), b = gray.clone();
        for (Object n : heads) value(n);
        assertArrayEquals(a, labels);
        assertArrayEquals(b, gray);
    }
}

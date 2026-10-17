// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original circles and rules; no source image pixels or score geometry. */
public class PrintedBarlineDotEndpointTest {
    static final int W = 240, H = 220;
    byte[] gray = new byte[W * H];
    boolean mirror;

    public PrintedBarlineDotEndpointTest() {
        Arrays.fill(gray, (byte) 250);
    }

    void ink(int x, int y, int value) {
        gray[(mirror ? H - 1 - y : y) * W + x] = (byte) value;
    }

    void fixture(int kind) {
        int bar = kind == 1 ? 128 : 108;
        for (int line = 0; line < 5; line++)
            if (!(kind == 2 && line == 2))
                for (int x = 45; x <= 170; x++)
                    for (int dy = -1; dy <= 1; dy++) ink(x, 60 + line * 16 + dy, 100);
        for (int y = 60; y <= 124; y++)
            for (int x = bar - 1; x <= bar + 1; x++)
                if (!(kind == 3 && y >= 83 && y <= 91)) ink(x, y, 100);
        if (kind == 4)
            for (int y = 42; y <= 142; y++) for (int x = 107; x <= 109; x++) ink(x, y, 100);
        if (kind == 5)
            for (int y = 60; y <= 124; y++) for (int x = 96; x <= 120; x++) ink(x, y, 100);
        for (int y = -3; y <= 3; y++)
            for (int x = -3; x <= 3; x++) if (x * x + y * y <= 9) ink(108 + x, 60 + y, 30);
    }

    Object head(int minX, int maxX, int minY, int maxY, float cx, float cy) throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ct = c.getDeclaredConstructors()[0];
        ct.setAccessible(true);
        return ct.newInstance(
                200,
                minX,
                maxX,
                mirror ? H - 1 - maxY : minY,
                mirror ? H - 1 - minY : maxY,
                cx,
                mirror ? H - 1 - cy : cy);
    }

    int count(boolean shaftHead) throws Exception {
        Object h = head(65, 85, 53, 67, 75, 60);
        List<Object> heads = new ArrayList<>();
        heads.add(h);
        if (shaftHead) heads.add(head(101, 115, 118, 131, 108, 124));
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "countAugmentationDots",
                        List.class,
                        h.getClass(),
                        float.class,
                        byte[].class,
                        int.class,
                        int.class,
                        boolean.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        return (int) m.invoke(null, List.of(), h, 16f, gray, W, H, false, List.of(), heads);
    }

    @Test
    public void roundedTopCrossingIsNotAugmentation() throws Exception {
        fixture(0);
        assertEquals(0, count(false));
    }

    @Test
    public void roundedBottomCrossingIsNotAugmentation() throws Exception {
        mirror = true;
        fixture(0);
        assertEquals(0, count(false));
    }

    @Test
    public void genuineDotBesideBarlineRemains() throws Exception {
        fixture(1);
        assertEquals(1, count(false));
    }

    @Test
    public void missingRuleCannotProveBarline() throws Exception {
        fixture(2);
        assertEquals(1, count(false));
    }

    @Test
    public void brokenVerticalCannotProveBarline() throws Exception {
        fixture(3);
        assertEquals(1, count(false));
    }

    @Test
    public void existingCreaseRejectionRemains() throws Exception {
        fixture(4);
        assertEquals(0, count(false));
    }

    @Test
    public void broadInkCannotProveBarline() throws Exception {
        fixture(5);
        assertEquals(1, count(false));
    }

    @Test
    public void actualNoteShaftCannotProveBarline() throws Exception {
        fixture(0);
        assertEquals(1, count(true));
    }

    @Test
    public void sourcePixelsRemainUnchanged() throws Exception {
        fixture(0);
        byte[] before = gray.clone();
        count(false);
        assertArrayEquals(before, gray);
    }
}

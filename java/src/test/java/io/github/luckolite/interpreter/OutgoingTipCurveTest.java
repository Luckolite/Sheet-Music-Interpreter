// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class OutgoingTipCurveTest {
    final int W = 300, H = 180;
    byte[] gray = new byte[W * H], labels = new byte[W * H];

    void setup(boolean up, boolean arc) {
        Arrays.fill(gray, (byte) 255);
        for (int d = 1; d <= 14; d++) ink(103, 80 + (up ? d : -d));
        for (int x = 106; x <= 109; x++) ink(x, 80);
        for (int d = 23; d <= 25; d++) ink(121, 80 + (up ? d : -d));
        if (arc)
            for (int dx = 6; dx <= 24; dx++)
                for (int t = 0; t < 2; t++) ink(100 + dx, 80 + (up ? -1 : 1) * (dx - 4 + t));
    }

    void ink(int x, int y) {
        gray[y * W + x] = 0;
    }

    boolean flag(boolean up) throws Exception {
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var head = ctor.newInstance(100, 90, 110, 135, 145, 100f, 140f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasCurvedFlag",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        float.class,
                        int.class,
                        int.class,
                        boolean.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, labels, gray, W, H, head, 14f, 100, 80, up);
    }

    @Test
    public void outgoingUpperArcCannotJoinShaftAndDistantInk() throws Exception {
        setup(true, true);
        assertFalse(flag(true));
    }

    @Test
    public void outgoingLowerArcCannotJoinShaftAndDistantInk() throws Exception {
        setup(false, true);
        assertFalse(flag(false));
    }

    @Test
    public void absentArcDoesNotTriggerTheNewExclusion() throws Exception {
        setup(true, false);
        assertTrue(flag(true));
    }

    @Test
    public void inputsStayExact() throws Exception {
        setup(true, true);
        var g = gray.clone();
        var l = labels.clone();
        flag(true);
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }
}

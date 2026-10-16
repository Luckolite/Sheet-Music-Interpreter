// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlagTipRuleRootTest {
    final int W = 300, H = 180;
    byte[] gray = new byte[W * H], labels = new byte[W * H];

    void setup(boolean up, boolean rule) {
        Arrays.fill(gray, (byte) 255);
        if (rule) for (int x = 20; x < 260; x++) ink(x, 80 + (up ? -1 : 1));
        for (int x = 103; x <= 122; x++) ink(x, 80);
        for (int d = 8; d <= 11; d++) for (int x = 110; x <= 114; x++) ink(x, 80 + (up ? d : -d));
        ink(103, 80 + (up ? 13 : -13));
        for (int d = 20; d <= 27; d++) ink(103, 80 + (up ? d : -d));
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
        return (boolean) m.invoke(null, labels, gray, W, H, head, 14.5f, 100, 80, up);
    }

    @Test
    public void upperTipRuleFringeCannotSupplyFlagRoot() throws Exception {
        setup(true, true);
        assertFalse(flag(true));
    }

    @Test
    public void lowerTipRuleFringeCannotSupplyFlagRoot() throws Exception {
        setup(false, true);
        assertFalse(flag(false));
    }

    @Test
    public void absentRuleCannotRemoveIndependentRoot() throws Exception {
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

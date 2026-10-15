// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BroadRuleFringeTest {
    @Test
    public void twoFringesAtBothProbesStillBelongToStaff() throws Exception {
        int w = 300, h = 200;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 175);
        for (int line = 0; line < 5; line++)
            for (int y = 79 + line * 14; y <= 81 + line * 14; y++)
                for (int x = 20; x < 280; x++) {
                    g[y * w + x] = (byte) 80;
                    l[y * w + x] = 4;
                }
        for (int y : new int[] {78, 82}) for (int x = 140; x <= 160; x++) g[y * w + x] = (byte) 120;
        var type = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var staff = ctor.newInstance(80f, 136f, 14f);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "thickNonHeadBands",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        type,
                        int.class);
        method.setAccessible(true);
        assertEquals(
                0, ((Number) method.invoke(null, g, l, w, h, 150, 70, 86, staff, 146)).intValue());
    }
}

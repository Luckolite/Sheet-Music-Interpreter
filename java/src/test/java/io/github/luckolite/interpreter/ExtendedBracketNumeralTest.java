// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import java.util.function.Predicate;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic five and bracket geometry, not source-score pixels. */
public class ExtendedBracketNumeralTest {
    private byte[] ink(boolean left, boolean right, boolean below) {
        byte[] g = new byte[500 * 400];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 12; x++) {
                boolean dark =
                        y < 3
                                || y < 9 && x < 3
                                || y >= 9 && y < 12
                                || y >= 12 && y < 19 && x >= 9
                                || y >= 15 && y < 19 && x < 3
                                || y >= 19 && x >= 2 && x <= 8;
                if (dark) g[(140 + y) * 500 + 284 + x] = 0;
            }
        if (left) for (int x = 194; x <= 280; x++) g[145 * 500 + x] = 0;
        if (right) for (int x = 299; x <= 386; x++) g[145 * 500 + x] = 0;
        if (below) {
            byte[] shifted = new byte[g.length];
            Arrays.fill(shifted, (byte) 255);
            for (int y = 0; y < 295; y++)
                System.arraycopy(g, y * 500, shifted, (y + 105) * 500, 500);
            g = shifted;
        }
        return g;
    }

    private Object find(byte[] g, boolean below, boolean owned) throws Exception {
        var m =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "findPrintedNumeral",
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        boolean.class,
                        float.class,
                        float.class,
                        int.class,
                        boolean.class,
                        Predicate.class);
        m.setAccessible(true);
        return m.invoke(
                null,
                g,
                500,
                400,
                200f,
                380f,
                below ? 100f : 290f,
                below ? 110f : 300f,
                20f,
                true,
                Float.NaN,
                Float.NaN,
                5,
                false,
                (Predicate<Object>) (x -> owned));
    }

    @Test
    public void extendedAboveRequiresBothArms() throws Exception {
        assertNotNull(find(ink(true, true, false), false, true));
    }

    @Test
    public void extendedBelowRequiresBothArms() throws Exception {
        assertNotNull(find(ink(true, true, true), true, true));
    }

    @Test
    public void missingLeftArmAbstains() throws Exception {
        assertNull(find(ink(false, true, false), false, true));
    }

    @Test
    public void missingRightArmAbstains() throws Exception {
        assertNull(find(ink(true, false, false), false, true));
    }

    @Test
    public void ownershipStillRequired() throws Exception {
        assertNull(find(ink(true, true, false), false, false));
    }
}

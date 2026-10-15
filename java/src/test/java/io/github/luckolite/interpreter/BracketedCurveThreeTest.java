// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.function.Predicate;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic numeral, bracket and crossing rule; no source-score pixels. */
public class BracketedCurveThreeTest {
    private byte[] ink(boolean bracket, boolean digit, boolean thick, boolean closed) {
        byte[] g = new byte[500 * 300];
        Arrays.fill(g, (byte) 255);
        if (digit)
            for (int y = 0; y < 24; y++)
                for (int x = 0; x < 14; x++) {
                    boolean dark =
                            y < 3 && x >= 3 && x <= 11
                                    || y >= 2 && y < 10 && x >= 11
                                    || y >= 10 && y < 13 && x >= 5 && x <= 12
                                    || y >= 13 && y < 22 && x >= 11
                                    || y >= 21 && x >= 2 && x <= 11
                                    || y >= 2 && y <= 4 && x <= 3
                                    || y >= 19 && y <= 21 && x <= 2
                                    || closed && x < 3;
                    if (dark) g[(130 + y) * 500 + 243 + x] = 0;
                }
        for (int x = 195; x <= 305; x++)
            for (int y = 153; y < (thick ? 164 : 155); y++) g[y * 500 + x] = 0;
        if (bracket) {
            for (int x = 194; x <= 239; x++) g[136 * 500 + x] = 0;
            for (int x = 261; x <= 306; x++) g[136 * 500 + x] = 0;
        }
        return g;
    }

    private Object find(byte[] g, boolean owned) throws Exception {
        return find(g, owned, Float.NaN);
    }

    private Object find(byte[] g, boolean owned, float headX) throws Exception {
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
                300,
                200f,
                300f,
                180f,
                180f,
                20f,
                true,
                headX,
                140f,
                3,
                false,
                (Predicate<Object>) (x -> owned));
    }

    @Test
    public void joinedThreeWithBracketIsRecovered() throws Exception {
        assertNotNull(find(ink(true, true, false, false), true));
    }

    @Test
    public void missingBracketAbstains() throws Exception {
        assertNull(find(ink(false, true, false, false), true));
    }

    @Test
    public void curveWithoutNumeralAbstains() throws Exception {
        assertNull(find(ink(true, false, false, false), true));
    }

    @Test
    public void thickCrossingStrokeAbstains() throws Exception {
        assertNull(find(ink(true, true, true, false), true));
    }

    @Test
    public void closedEightAbstains() throws Exception {
        assertNull(find(ink(true, true, false, true), true));
    }

    @Test
    public void unrelatedGroupAbstains() throws Exception {
        assertNull(find(ink(true, true, false, false), false));
    }

    @Test
    public void matchingPointConstraintIsPreserved() throws Exception {
        assertNotNull(find(ink(true, true, false, false), true, 250f));
    }

    @Test
    public void foreignPointConstraintAbstains() throws Exception {
        assertNull(find(ink(true, true, false, false), true, 350f));
    }
}

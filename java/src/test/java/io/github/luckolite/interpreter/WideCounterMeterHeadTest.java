// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original broad-counter numeral and non-meter controls. */
public class WideCounterMeterHeadTest {
    static final int W = 240, H = 160;
    byte[] gray = new byte[W * H];

    void box(int l, int t, int r, int b, int value) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = (byte) value;
    }

    void setup(boolean numerator, boolean barline) {
        Arrays.fill(gray, (byte) 250);
        for (int y = 40; y <= 104; y += 16) box(0, y, W - 1, y, 30);
        if (barline) box(73, 40, 74, 104, 30);
        box(95, 68, 117, 86, 30);
        box(99, 73, 111, 81, 250);
        box(95, 87, 117, 104, 30);
        box(99, 91, 111, 99, 250);
        if (numerator) {
            box(96, 43, 99, 65, 30);
            box(92, 43, 99, 47, 30);
            box(106, 43, 121, 47, 30);
            box(118, 47, 121, 54, 30);
            box(107, 52, 120, 56, 30);
            box(105, 55, 108, 64, 30);
            box(105, 63, 121, 67, 30);
        }
    }

    boolean fragment() throws Exception {
        Class<?> type = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor =
                type.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ctor.setAccessible(true);
        Object head = ctor.newInstance(80, 96, 106, 73, 81, 101f, 77f);
        Class<?> staffType = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var sc = staffType.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        Object staff = sc.newInstance(40f, 104f, 16f);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "inlineEightMeterFragment",
                        byte[].class,
                        int.class,
                        int.class,
                        type,
                        List.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, gray, W, H, head, List.of(staff));
    }

    @Test
    public void completeBroadCountersRemoveOffCenterFragment() throws Exception {
        setup(true, true);
        assertTrue(fragment());
    }

    @Test
    public void missingNumeratorDoesNotAuthorizeBroaderWindow() throws Exception {
        setup(false, true);
        assertFalse(fragment());
    }

    @Test
    public void missingBarlineProtectsHead() throws Exception {
        setup(true, false);
        assertFalse(fragment());
    }

    @Test
    public void singleCounterDoesNotProveEight() throws Exception {
        setup(true, true);
        box(95, 87, 117, 104, 250);
        assertFalse(fragment());
    }

    @Test
    public void externalStemProtectsHead() throws Exception {
        setup(true, true);
        box(106, 20, 107, 81, 30);
        assertFalse(fragment());
    }

    @Test
    public void openCounterDoesNotProveEight() throws Exception {
        setup(true, true);
        box(110, 75, 118, 79, 250);
        assertFalse(fragment());
    }

    @Test
    public void separateHollowHeadsDoNotProveEight() throws Exception {
        setup(true, true);
        box(95, 83, 117, 90, 250);
        box(95, 104, 117, 104, 30);
        assertFalse(fragment());
    }
}

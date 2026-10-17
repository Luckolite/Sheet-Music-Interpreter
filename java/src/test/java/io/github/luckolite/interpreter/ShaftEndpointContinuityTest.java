// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ShaftEndpointContinuityTest {
    static boolean owned(byte[] g, int left, int right, int row, int direction) throws Exception {
        var owner =
                Arrays.stream(ScoreArpeggioDetector.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("ColumnOwner"))
                        .findFirst()
                        .orElseThrow();
        var ctor = owner.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var o =
                ctor.newInstance(
                        91f,
                        56f,
                        260f,
                        new PlayingTechniqueDetector.Staff(52, 100, 12, 0, 2),
                        AnchoredObjectTest.page(direction).notes());
        var m =
                ScoreArpeggioDetector.class.getDeclaredMethod(
                        "ownedChordInk",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        owner);
        m.setAccessible(true);
        return (boolean) m.invoke(null, g, 260, 250, left, right, row, 12f, o);
    }

    static byte[] up(boolean extension) {
        var g = ExtentChallenge.paper();
        for (int y = 40; y <= (extension ? 113 : 100); y++) ExtentChallenge.pixel(g, 98, y, 25);
        for (int hy : new int[] {58, 82, 100})
            for (int x = 89; x <= 98; x++) ExtentChallenge.pixel(g, x, hy - 3, 25);
        for (int y = 112; y <= 113; y++)
            for (int x = 98; x <= 99; x++) ExtentChallenge.pixel(g, x, y, 25);
        return g;
    }

    @Test
    public void isolatedBlobBelowValidatedUpStemCannotInheritShaftOwnership() throws Exception {
        assertFalse(owned(up(false), 98, 99, 112, 1));
    }

    @Test
    public void genuineUpStemExtensionContinuouslyReachesCurrentComponent() throws Exception {
        assertTrue(owned(up(true), 98, 99, 112, 1));
    }

    @Test
    public void genuineDownStemExtensionContinuouslyReachesCurrentComponent() throws Exception {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int y = 45; y <= 58; y++) ExtentChallenge.pixel(g, 84, y, 25);
        for (int y = 45; y <= 46; y++) ExtentChallenge.pixel(g, 83, y, 25);
        assertTrue(owned(g, 83, 84, 45, -1));
    }
}

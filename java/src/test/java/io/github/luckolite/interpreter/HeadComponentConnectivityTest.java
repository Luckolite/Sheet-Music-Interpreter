// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class HeadComponentConnectivityTest {
    static boolean owned(byte[] g) throws Exception {
        Class<?> owner =
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
                        AnchoredObjectTest.page(-1).notes());
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
        return (boolean) m.invoke(null, g, 260, 250, 97, 98, 97, 12f, o);
    }

    @Test
    public void hollowHeadSideHasNonrailTwoDimensionalConnection() throws Exception {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int y = 94; y <= 99; y++)
            for (int x = 97; x <= 98; x++) ExtentChallenge.pixel(g, x, y, 25);
        for (int y : new int[] {94, 99})
            for (int x = 84; x <= 98; x++) ExtentChallenge.pixel(g, x, y, 25);
        assertTrue(owned(g));
    }

    @Test
    public void staffRailCannotSupplyOtherwiseMissingHeadConnection() throws Exception {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int y = 96; y <= 100; y++)
            for (int x = 97; x <= 98; x++) ExtentChallenge.pixel(g, x, y, 25);
        assertFalse(owned(g));
    }

    @Test
    public void fixedColumnBowlContactAcrossCompleteRailIsOwned() throws Exception {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int x = 84; x <= 98; x++) ExtentChallenge.pixel(g, x, 99, 25);
        for (int x = 97; x <= 98; x++) ExtentChallenge.pixel(g, x, 101, 25);
        assertTrue(ownedAt(g, 101));
    }

    @Test
    public void missingOppositeFaceCannotSupplyHeadMembership() throws Exception {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int x = 97; x <= 98; x++) ExtentChallenge.pixel(g, x, 101, 25);
        assertFalse(ownedAt(g, 101));
    }

    static boolean ownedAt(byte[] g, int row) throws Exception {
        Class<?> owner =
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
                        AnchoredObjectTest.page(-1).notes());
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
        return (boolean) m.invoke(null, g, 260, 250, 97, 98, row, 12f, o);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent helper-contract controls, not whole-product error claims. */
public final class ComponentRouteChallengeTest {
    static byte[] base() {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int y = 96; y <= 97; y++)
            for (int x = 97; x <= 98; x++) ExtentChallenge.pixel(g, x, y, 25);
        return g;
    }

    static void line(byte[] g, int x1, int y1, int x2, int y2) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int k = 0; k <= n; k++) {
            int x = (int) Math.round(x1 + (x2 - x1) * k / (double) Math.max(1, n)),
                    y = (int) Math.round(y1 + (y2 - y1) * k / (double) Math.max(1, n));
            ExtentChallenge.pixel(g, x, y, 25);
        }
    }

    @Test
    public void diagonalNonrailConnectionBelongsToHead() throws Exception {
        var g = base();
        line(g, 84, 94, 92, 94);
        line(g, 92, 94, 98, 97);
        assertTrue(HeadComponentConnectivityTest.owned(g));
    }

    @Test
    public void diagonalOnlyRailBridgeCannotSupplyOwnership() throws Exception {
        var g = AnchoredObjectTest.ink(56, true, false);
        line(g, 84, 99, 96, 99);
        line(g, 97, 101, 98, 101);
        assertFalse(HeadComponentConnectivityTest.ownedAt(g, 101));
    }

    @Test
    public void missingFixedColumnRawPixelCannotUseNeighborRailInk() throws Exception {
        var g = AnchoredObjectTest.ink(56, true, false);
        line(g, 84, 99, 98, 99);
        line(g, 97, 101, 98, 101);
        g[100 * 260 + 97] = (byte) 255;
        g[100 * 260 + 98] = (byte) 255;
        assertFalse(HeadComponentConnectivityTest.ownedAt(g, 101));
    }

    @Test
    public void connectionViaOtherHeadsScopeCannotOwnCurrentComponent() throws Exception {
        var g = base();
        line(g, 98, 90, 98, 97);
        line(g, 84, 90, 98, 90);
        assertFalse(HeadComponentConnectivityTest.owned(g));
    }

    @Test
    public void connectionOnlyBelowHeadRoiCannotSupplyOwnership() throws Exception {
        var g = base();
        line(g, 98, 97, 98, 109);
        line(g, 84, 109, 98, 109);
        assertFalse(HeadComponentConnectivityTest.owned(g));
    }

    @Test
    public void connectionOnlyOutsideSideRoiCannotSupplyOwnership() throws Exception {
        var g = base();
        line(g, 98, 97, 102, 97);
        line(g, 102, 97, 102, 105);
        line(g, 84, 105, 102, 105);
        assertFalse(HeadComponentConnectivityTest.owned(g));
    }

    @Test
    public void componentIncludingPaperMustNotBeOwned() throws Exception {
        var g = base();
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
        assertFalse((boolean) m.invoke(null, g, 260, 250, 96, 98, 96, 12f, o));
    }
}

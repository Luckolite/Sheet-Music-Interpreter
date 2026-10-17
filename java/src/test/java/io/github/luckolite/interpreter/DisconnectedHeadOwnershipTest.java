// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Helper-contract counterexample only; no whole-product regression asserted. */
public final class DisconnectedHeadOwnershipTest {
    @Test
    public void disconnectedNearHeadBlobMustNotInheritOtherShaftOwnership() throws Exception {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int y = 96; y <= 97; y++)
            for (int x = 97; x <= 98; x++) ExtentChallenge.pixel(g, x, y, 25);
        var q = new ArrayDeque<Integer>();
        var seen = new HashSet<Integer>();
        q.add(96 * 260 + 97);
        seen.add(96 * 260 + 97);
        while (!q.isEmpty()) {
            int at = q.remove(), x = at % 260, y = at / 260;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= 260 || yy < 0 || yy >= 250) continue;
                    int n = yy * 260 + xx;
                    if ((g[n] & 255) < 50 && seen.add(n)) q.add(n);
                }
        }
        assertEquals(
                "Exactly four isolated blob pixels; excludes staff gray100 and never reaches genuine shaft84/head bars ending94",
                4,
                seen.size());
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
        assertFalse(
                "A different genuine head-connected shaft must not own a disconnected nearby component",
                (boolean) m.invoke(null, g, 260, 250, 97, 98, 97, 12f, o));
    }
}

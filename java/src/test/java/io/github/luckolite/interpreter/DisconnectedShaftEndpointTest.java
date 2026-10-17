// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Helper-only endpoint contract test; no whole-product falsebinding asserted. */
public final class DisconnectedShaftEndpointTest {
    @Test
    public void isolatedBlobAboveValidatedDownStemCannotInheritShaftOwnership() throws Exception {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int y = 45; y <= 46; y++)
            for (int x = 83; x <= 84; x++) ExtentChallenge.pixel(g, x, y, 25);
        var q = new ArrayDeque<Integer>();
        var seen = new HashSet<Integer>();
        q.add(45 * 260 + 83);
        seen.add(45 * 260 + 83);
        while (!q.isEmpty()) {
            int n = q.remove(), x = n % 260, y = n / 260;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= 260 || yy < 0 || yy >= 250) continue;
                    int z = yy * 260 + xx;
                    if ((g[z] & 255) < 50 && seen.add(z)) q.add(z);
                }
        }
        assertEquals(
                "Blob has own four-pixel raw8component, separated from shaft beginning52; highest chordhead58",
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
                "Aligned but disconnected ink above the validated shaft segment must not be owned",
                (boolean) m.invoke(null, g, 260, 250, 83, 84, 45, 12f, o));
    }
}

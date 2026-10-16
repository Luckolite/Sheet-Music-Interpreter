// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original partially joined beams; short surviving channel must retain two stable rails. */
public class CompactGraceChannelTest extends BorrowedGraceShaftFixture {
    void closeUntil(int end) {
        for (int x = 132; x <= end; x++) {
            int top = 118 - Math.round((x - 132) * .25f);
            for (int y = top; y < top + 15; y++) gray[y * W + x] = 0;
        }
    }

    boolean channel() throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "compactInnerBeamChannel",
                        byte[].class,
                        int.class,
                        int.class,
                        int[].class,
                        int[].class,
                        float.class);
        m.setAccessible(true);
        return (boolean)
                m.invoke(null, gray, W, H, new int[] {132, 118, -1}, new int[] {156, 112, -1}, 20f);
    }

    @Test
    public void shortSurvivingChannelOwnsCap() throws Exception {
        closeUntil(149);
        assertTrue(channel());
        assertEquals(1, count(true));
    }

    @Test
    public void twoPixelsDoNotEstablishChannel() throws Exception {
        closeUntil(152);
        assertFalse(channel());
        assertEquals(0, count(true));
    }

    @Test
    public void fullyJoinedBlobIsNotTwoBeams() throws Exception {
        closeUntil(156);
        assertFalse(channel());
        assertEquals(0, count(true));
    }
}

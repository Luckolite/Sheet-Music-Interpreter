// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class OneSidedBarlinePocketTest {
    @Test
    public void curvedFlagBesideStraightBarDoesNotOwnBarline() {
        int w = 220, h = 180;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 238);
        for (int y = 60; y <= 140; y++) for (int x = 93; x <= 95; x++) g[y * w + x] = 45;
        for (int y = 119; y <= 135; y++) {
            int left = 76 + (Math.abs(y - 127) >= 5 ? 2 : 0);
            for (int x = left; x <= 93; x++)
                g[y * w + x] =
                        (byte) ((y == 119 || y == 135 || x <= left + 1 || x >= 92) ? 45 : 178);
        }
        assertFalse(ClosedHeadBarlineGuard.attached(g, w, h, 94, 60, 140, 20));
    }
}

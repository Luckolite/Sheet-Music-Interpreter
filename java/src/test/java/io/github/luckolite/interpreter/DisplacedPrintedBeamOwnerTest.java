// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class DisplacedPrintedBeamOwnerTest {
    private static final int W = 400, H = 400;

    private static byte[] image(boolean lastShaft) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 167; y <= 173; y++) for (int x = 108; x <= 228; x++) gray[y * W + x] = 0;
        for (int x : new int[] {108, lastShaft ? 228 : 108})
            for (int y = 100; y <= 170; y++)
                for (int dx = -1; dx <= 1; dx++) gray[y * W + x + dx] = 0;
        return gray;
    }

    @Test
    public void sharedAttackAtDisplacedHeadEdgeRetainsAttachedBeamOwner() {
        assertTrue(
                PrintedTupletBeamOwner.owns(
                        image(true), W, H, 108, 100, 228, 100, 20, -1, 162, 190, 174, 211));
    }

    @Test
    public void displacedAttackCannotReplaceMissingLastShaft() {
        assertFalse(
                PrintedTupletBeamOwner.owns(
                        image(false), W, H, 108, 100, 228, 100, 20, -1, 162, 190, 174, 211));
    }
}

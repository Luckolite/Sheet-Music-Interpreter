// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Procedural rotations isolate finite neighboring ink from continuing shaded texture. */
public class BoundedNeighborWitnessTest {
    static final int W = 100, H = 100;

    boolean proof(byte[] gray, int l, int t, int r, int b, int side) throws Exception {
        Method m =
                DiffuseLedgerSurface.class.getDeclaredMethod(
                        "boundedNeighbor",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, gray, W, l, t, r, b, 5, side, 100);
    }

    void rotation(int side) throws Exception {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 220);
        int far = side == 0 ? 25 : side == 1 ? 65 : side == 2 ? 25 : 65;
        int near = side == 0 ? 35 : side == 1 ? 55 : side == 2 ? 35 : 55;
        for (int a = 40; a <= 50; a++)
            for (int d = -1; d <= 1; d++)
                gray[side < 2 ? a * W + near + d : (near + d) * W + a] = (byte) 100;
        byte[] original = gray.clone();
        assertTrue(proof(gray, 40, 40, 50, 50, side));
        assertArrayEquals(original, gray);
        // Extending the neighboring dark glyph through the far witness removes bounded ownership.
        for (int a = 40; a <= 50; a++)
            for (int d = -1; d <= 1; d++)
                gray[side < 2 ? a * W + far + d : (far + d) * W + a] = (byte) 100;
        original = gray.clone();
        assertFalse(proof(gray, 40, 40, 50, 50, side));
        assertArrayEquals(original, gray);
    }

    @Test
    public void leftNeighbor() throws Exception {
        rotation(0);
    }

    @Test
    public void rightNeighbor() throws Exception {
        rotation(1);
    }

    @Test
    public void upperNeighbor() throws Exception {
        rotation(2);
    }

    @Test
    public void lowerNeighbor() throws Exception {
        rotation(3);
    }

    @Test
    public void clippedFarWitnessCannotProveAnySide() throws Exception {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 220);
        byte[] original = gray.clone();
        assertFalse(proof(gray, 10, 40, 20, 50, 0));
        assertFalse(proof(gray, 80, 40, 90, 50, 1));
        assertFalse(proof(gray, 40, 10, 50, 20, 2));
        assertFalse(proof(gray, 40, 80, 50, 90, 3));
        assertArrayEquals(original, gray);
    }
}

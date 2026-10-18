// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original semantic-helper geometry; no commercial pixels or classifier-acquisition claim. */
public class QuantizedFiniteBeamOriginalTest {
    private static int[][] straight() {
        int[] t = new int[37], b = new int[37];
        for (int i = 0; i < b.length; i++) {
            b[i] = 100 + i / 5 + (i % 2 == 0 ? 1 : 0);
            t[i] = b[i] - 7;
        }
        return new int[][] {t, b};
    }

    @Test
    public void straightAlternatingIntegerBinsHaveOneParallelBand() {
        int[][] p = straight();
        assertNotNull(FiniteParallelBeamBand.prove(p[0], p[1], 8));
    }

    @Test
    public void curvedMiddleDoesNotBecomeStraightByEndpointSelection() {
        int[][] p = straight();
        for (int i = 10; i <= 26; i++) {
            p[0][i] += 4;
            p[1][i] += 4;
        }
        assertNull(FiniteParallelBeamBand.prove(p[0], p[1], 8));
    }

    @Test
    public void finiteKinkCannotBorrowTwoSeparateStraightFits() {
        int[][] p = straight();
        for (int i = 18; i < 37; i++) {
            p[0][i] += 4;
            p[1][i] += 4;
        }
        assertNull(FiniteParallelBeamBand.prove(p[0], p[1], 8));
    }

    @Test
    public void visibleLowerBranchIsNotParallelBandThickness() {
        int[][] p = straight();
        p[1][18] += 4;
        assertNull(FiniteParallelBeamBand.prove(p[0], p[1], 8));
    }

    private static final int W = 40, H = 40;

    private static byte[] foot() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int x = 10; x <= 12; x++)
            for (int y = 8; y <= (x == 12 ? 17 : 24); y++) g[y * W + x] = 0;
        for (int y = 19; y <= 24; y++) g[y * W + 12] = 0;
        for (int y = 21; y <= 24; y++) g[y * W + 13] = 0;
        return g;
    }

    private static FiniteShaftFoot prove(byte[] g, int[] ceiling) {
        return FiniteShaftFoot.prove(
                g, W, H, 10, 12, 8, new int[] {24, 24, 17}, 13, 21, 24, ceiling);
    }

    @Test
    public void finiteFootNeedsActualFourNeighborShaftAndRailConnection() {
        byte[] g = foot(), before = g.clone();
        FiniteShaftFoot p = prove(g, new int[] {25, 25, 25});
        assertNotNull(p);
        assertTrue(p.contains(12, 19));
        assertFalse(p.contains(12, 18));
        assertArrayEquals(before, g);
    }

    @Test
    public void detachedTerminalIslandCannotBorrowRemoteShaftAndRail() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        int[] ends = {17, 17, 17, 24, 24};
        for (int x = 10; x <= 14; x++) for (int y = 8; y <= ends[x - 10]; y++) g[y * W + x] = 0;
        for (int y = 21; y <= 24; y++) g[y * W + 15] = 0;
        g[21 * W + 10] = 0; // An actual isolated island, not a rectangle of inferred support.
        assertNull(
                FiniteShaftFoot.prove(
                        g, W, H, 10, 14, 8, ends, 15, 21, 24, new int[] {25, 25, 25, 25, 25}));
    }

    @Test
    public void continuousShaftWithoutObservedRailContactCannotOwnFoot() {
        byte[] g = foot();
        for (int y = 21; y <= 24; y++) g[y * W + 13] = (byte) 255;
        assertNull(prove(g, new int[] {25, 25, 25}));
    }

    @Test
    public void connectedFootPastEveryFeasibleBandRemainsUnowned() {
        byte[] g = foot();
        assertNull(prove(g, new int[] {25, 25, 22}));
    }
}

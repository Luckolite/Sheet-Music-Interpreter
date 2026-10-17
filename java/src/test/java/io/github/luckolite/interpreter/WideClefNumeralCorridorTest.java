// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generic numeral-like strokes and adversarial context, without source pixels. */
public class WideClefNumeralCorridorTest {
    private static final int W = 240, H = 160, L = 140, T = 60;

    private byte[] page(int x, int top) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = T; y < T + 20; y++)
            for (int xx = L; xx < L + 12; xx++)
                if (xx == L || xx == L + 11 || y == T || y == T + 10 || y == T + 19)
                    g[y * W + xx] = 20;
        for (int xx = x; xx < x + 10; xx++) {
            g[top * W + xx] = 20;
            g[(top + 19) * W + xx] = 20;
        }
        for (int y = 1; y < 19; y++) g[(top + y) * W + x + 9 - Math.round((y - 1) * 9 / 17f)] = 20;
        return g;
    }

    private boolean separate(byte[] g, int a, int b) {
        byte[] old = g.clone();
        boolean ok = ClefOctaveNumerals.separateNeighbor(g, W, H, L, T, 12, 20, a, b);
        assertArrayEquals(old, g);
        return ok;
    }

    @Test
    public void completeAlignedBodyAcrossWideWhiteCorridorIsSeparate() {
        assertTrue(separate(page(100, T), 100, 109));
    }

    @Test
    public void tightHeaderRemainsNeighbor() {
        assertFalse(separate(page(128, T), 128, 137));
    }

    @Test
    public void differentBaselineDoesNotRelaxGuard() {
        assertFalse(separate(page(100, T - 10), 100, 109));
    }

    @Test
    public void interveningAttackInkDoesNotOfferWhiteCorridor() {
        byte[] g = page(100, T);
        g[70 * W + 130] = 20;
        assertFalse(separate(g, 100, 109));
    }

    @Test
    public void unrelatedOldRegionNoiseIsNotExplainedByCompleteBody() {
        byte[] g = page(100, T);
        g[68 * W + 101] = 20;
        assertFalse(separate(g, 100, 109));
    }

    @Test
    public void attachedStaffOrNoteTailMakesComponentIncomplete() {
        byte[] g = page(100, T);
        for (int y = T + 19; y <= 100; y++) g[y * W + 100] = 20;
        assertFalse(separate(g, 100, 109));
    }

    @Test
    public void solidBodyCannotMasqueradeAsCompleteCharacter() {
        byte[] g = page(100, T);
        for (int y = T; y < T + 20; y++) for (int x = 100; x < 110; x++) g[y * W + x] = 20;
        assertFalse(separate(g, 100, 109));
    }

    @Test
    public void allTriggeringNeighborComponentsRequireProof() {
        byte[] g = page(100, T);
        g[69 * W + 90] = 20;
        assertFalse(separate(g, 90, 109));
    }
}

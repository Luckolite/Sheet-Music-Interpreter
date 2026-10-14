// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pixels: printed staves absent from the semantic projection. */
public class RawStaffWitnessTest {
    static final int W = 800, H = 900;

    static byte[] paper(int witnesses) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int s = 0; s < witnesses; s++) rules(g, 350 + s * 250, 16, 30, 40, 760);
        return g;
    }

    static void rules(byte[] g, int top, int gap, int shade, int left, int right) {
        for (int n = 0; n < 5; n++)
            for (int x = left; x < right; x++) g[(top + n * gap) * W + x] = (byte) shade;
    }

    static boolean retained(byte[] g, int top, boolean validated) {
        int[] v = new int[H];
        for (int n = 0; n < 5; n++) v[top + n * 12] = 500;
        var out =
                validated
                        ? RawStaffLineDetector.detectValidatedFromStrength(v, 20, H, g, W)
                        : RawStaffLineDetector.detectFromStrength(v, 20, H, g, W);
        return out.stream().anyMatch(s -> Math.abs(s.top() - top) < 4);
    }

    @Test
    public void rawWitnessesRejectUnsupportedTiltedSemanticHeader() {
        assertFalse(retained(paper(2), 60, true));
    }

    @Test
    public void rawWitnessesRejectUnsupportedStraightSemanticHeader() {
        assertFalse(retained(paper(2), 60, false));
    }

    @Test
    public void oneRawWitnessCannotAuthorizeRejection() {
        assertTrue(retained(paper(1), 60, true));
    }

    @Test
    public void noPixelsPreserveSemanticStaff() {
        assertTrue(retained(null, 60, true));
    }

    @Test
    public void faintHeaderRulesArePreserved() {
        byte[] g = paper(2);
        rules(g, 60, 12, 249, 40, 760);
        assertTrue(retained(g, 60, true));
    }

    @Test
    public void shortHeaderRulesArePreserved() {
        byte[] g = paper(2);
        rules(g, 60, 12, 245, 120, 210);
        assertTrue(retained(g, 60, true));
    }

    @Test
    public void interiorWeakStaffIsPreserved() {
        assertTrue(retained(paper(2), 470, true));
    }

    @Test
    public void sourcePixelsStayUnchanged() {
        byte[] g = paper(2), before = g.clone();
        retained(g, 60, true);
        assertArrayEquals(before, g);
    }
}

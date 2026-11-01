// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original OFL shapes and exact affine-cell counterexamples; no private raster data. */
public class IntegerCellClefEightOriginalTest {
    static int[] bounds(byte[] a) {
        int l = 180, r = -1, t = 140, b = -1;
        for (int y = 0; y < 140; y++)
            for (int x = 0; x < 180; x++)
                if ((a[y * 180 + x] & 255) <= 175) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                }
        return new int[] {l, t, r - l + 1, b - t + 1};
    }

    static boolean prove(byte[] a) {
        int[] b = bounds(a);
        byte[] saved = a.clone();
        boolean r = ClefEightWaist.proves(a, 180, 140, b[0], b[1], b[2], b[3]);
        assertArrayEquals(saved, a);
        return r;
    }

    static byte[] shear(byte[] a, int sign, int phase) {
        int[] b = bounds(a);
        byte[] out = new byte[a.length];
        Arrays.fill(out, (byte) 255);
        int bottom = b[1] + b[3] - 1;
        for (int y = b[1]; y <= bottom; y++)
            for (int x = b[0]; x < b[0] + b[2]; x++) {
                int xx = x + sign * ((bottom - y + phase) / 4);
                assertTrue(xx >= 0 && xx < 180);
                out[y * 180 + xx] = a[y * 180 + x];
            }
        return out;
    }

    @Test
    public void originalEightAllItalicLatticePhases() throws Exception {
        var s = OriginalWideClefEightTest.scene("\uE088", false);
        for (int sign : new int[] {-1, 1})
            for (int phase = 0; phase < 4; phase++)
                assertTrue(
                        "8 sign=" + sign + " phase=" + phase, prove(shear(s.gray(), sign, phase)));
    }

    @Test
    public void originalLetterBAllItalicLatticePhases() throws Exception {
        var s = OriginalWideClefEightTest.ordinary("B");
        for (int sign : new int[] {-1, 1})
            for (int phase = 0; phase < 4; phase++)
                assertFalse(
                        "B sign=" + sign + " phase=" + phase, prove(shear(s.gray(), sign, phase)));
    }

    @Test
    public void staircaseSpineDoesNotSupplyACompletePaperCell() {
        // Anchorleft0/1 has midpoint.5; rasterleft1 passes fractional strictness
        // but cannot leave a whole paper cell. No tunable indentation margin.
        assertFalse(ClefEightWaist.retreats(1, 3, 1, 8, 2));
        assertTrue(ClefEightWaist.retreats(2, 2, 1, 8, 2));
        assertFalse(ClefEightWaist.retreats(2, 4, 1, 8, 2));
    }

    @Test
    public void detachedStackedLoopsAreNotOneWholeEight() {
        byte[] a = new byte[180 * 140];
        Arrays.fill(a, (byte) 255);
        for (int top : new int[] {30, 48})
            for (int y = top; y <= top + 12; y++)
                for (int x = 72; x <= 84; x++)
                    if (y == top || y == top + 12 || x == 72 || x == 84) a[y * 180 + x] = 0;
        assertFalse(prove(a));
    }

    @Test
    public void openCountersAndPlainSingleLoopRefuse() throws Exception {
        var s = OriginalWideClefEightTest.scene("\uE088", false);
        byte[] a = s.gray().clone();
        for (int y = s.top(); y <= s.bottom(); y++)
            for (int x = s.left(); x <= (s.left() + s.right()) / 2; x++)
                a[y * 180 + x] = (byte) 255;
        assertFalse(prove(a));
        assertFalse(prove(OriginalWideClefEightTest.scene("\uE080", false).gray()));
    }

    @Test
    public void finiteInputAndWorkBoundsRefuse() {
        assertFalse(ClefEightWaist.proves(null, 180, 140, 0, 0, 1, 1));
        assertFalse(ClefEightWaist.proves(new byte[10], 180, 140, 0, 0, 1, 1));
        assertFalse(ClefEightWaist.proves(new byte[180 * 140], 180, 140, -1, 0, 10, 10));
        assertFalse(ClefEightWaist.proves(new byte[300 * 300], 300, 300, 0, 0, 300, 300));
    }

    @Test
    public void knownLegacyBCompatibilityIsExplicit() throws Exception {
        var s = OriginalWideClefEightTest.ordinary("B");
        assertFalse(prove(s.gray()));
        assertTrue(
                "Knownoldroute limitation, not a passed negative",
                OriginalWideClefEightTest.above(s));
    }
}

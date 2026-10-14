// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric controls; no score-derived pixels. */
public class FragmentedRestBarHeadsTest {
    static final int W = 320, H = 220;

    static void rect(byte[] a, int l, int t, int r, int b, int value) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) a[y * W + x] = (byte) value;
    }

    static byte[][] page(boolean caps, boolean bilateral) {
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        rect(gray, 70, 100, 190, 111, 20);
        if (caps)
            for (int x : new int[] {70, 188}) rect(gray, x, 94, x + 2, bilateral ? 117 : 111, 20);
        rect(labels, 89, 103, 99, 109, 2);
        rect(labels, 145, 102, 167, 109, 2);
        return new byte[][] {labels, gray};
    }

    static int heads(byte[] a) {
        int n = 0;
        for (byte b : a) if (b == 2) n++;
        return n;
    }

    @Test
    public void separatedInteriorFragmentsBelongToCompleteCappedBar() {
        var p = page(true, true);
        assertEquals(0, heads(PrintedRestBarHeads.withoutBars(p[0], p[1], W, H)));
    }

    @Test
    public void oneSidedBeamFragmentsRemain() {
        var p = page(true, false);
        assertSame(p[0], PrintedRestBarHeads.withoutBars(p[0], p[1], W, H));
    }

    @Test
    public void uncappedRectangleFragmentsRemain() {
        var p = page(false, false);
        assertSame(p[0], PrintedRestBarHeads.withoutBars(p[0], p[1], W, H));
    }

    @Test
    public void nearbyWrittenHeadOutsideProvedBodyRemains() {
        var p = page(true, true);
        rect(p[0], 210, 120, 224, 128, 2);
        rect(p[1], 210, 120, 224, 128, 20);
        byte[] result = PrintedRestBarHeads.withoutBars(p[0], p[1], W, H);
        assertEquals(135, heads(result));
        assertEquals(2, result[124 * W + 217]);
    }

    @Test
    public void componentExtendingOutsideBodyRemains() {
        var p = page(true, true);
        Arrays.fill(p[0], (byte) 0);
        rect(p[0], 110, 104, 121, 124, 2);
        rect(p[1], 110, 112, 121, 124, 20);
        assertSame(p[0], PrintedRestBarHeads.withoutBars(p[0], p[1], W, H));
    }

    @Test
    public void inputBuffersArePreserved() {
        var p = page(true, true);
        byte[] l = p[0].clone(), g = p[1].clone();
        PrintedRestBarHeads.withoutBars(p[0], p[1], W, H);
        assertArrayEquals(l, p[0]);
        assertArrayEquals(g, p[1]);
    }

    @Test
    public void narrowHeadWithBilateralStemIsNotWideRest() {
        var p = page(false, false);
        Arrays.fill(p[0], (byte) 0);
        Arrays.fill(p[1], (byte) 250);
        rect(p[1], 100, 100, 114, 110, 20);
        rect(p[1], 100, 85, 101, 124, 20);
        rect(p[0], 103, 103, 111, 108, 2);
        assertSame(p[0], PrintedRestBarHeads.withoutBars(p[0], p[1], W, H));
    }
}

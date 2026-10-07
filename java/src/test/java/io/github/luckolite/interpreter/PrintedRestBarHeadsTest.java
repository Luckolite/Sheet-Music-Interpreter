// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated capped-rest and one-sided beam controls. */
public class PrintedRestBarHeadsTest {
    private static final int W = 300, H = 230;

    private byte[][] page(int paper, boolean caps, boolean oneSided) {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Arrays.fill(g, (byte) paper);
        for (int y = 106; y <= 170; y += 16)
            for (int x = 20; x < 280; x++) {
                l[y * W + x] = 4;
                g[y * W + x] = 60;
            }
        for (int y = 130; y <= 145; y++)
            for (int x = 100; x <= 200; x++) {
                l[y * W + x] = 2;
                g[y * W + x] = 20;
            }
        if (caps)
            for (int x : new int[] {100, 101, 102, 198, 199, 200})
                for (int y = 120; y <= (oneSided ? 145 : 155); y++) {
                    l[y * W + x] = 1;
                    g[y * W + x] = 20;
                }
        return new byte[][] {l, g};
    }

    private int heads(byte[] a) {
        int n = 0;
        for (byte b : a) if (b == 2) n++;
        return n;
    }

    @Test
    public void cappedRestIsNotANotehead() {
        var p = page(130, true, false);
        assertEquals(0, heads(PrintedRestBarHeads.withoutBars(p[0], p[1], W, H)));
    }

    @Test
    public void whiteCappedRestIsNotANotehead() {
        var p = page(250, true, false);
        assertEquals(0, heads(PrintedRestBarHeads.withoutBars(p[0], p[1], W, H)));
    }

    @Test
    public void oneSidedBeamStemsRemainWrittenInk() {
        var p = page(130, true, true);
        assertSame(p[0], PrintedRestBarHeads.withoutBars(p[0], p[1], W, H));
    }

    @Test
    public void uncappedRectangleRemainsWrittenInk() {
        var p = page(130, false, false);
        assertSame(p[0], PrintedRestBarHeads.withoutBars(p[0], p[1], W, H));
    }

    @Test
    public void verifiedRestAdmittedDespiteSemanticHeadBar() {
        var p = page(130, true, false);
        assertEquals(
                List.of(0),
                MultiMeasureRestDetector.candidateMeasureIndexes(
                        p[0],
                        p[1],
                        W,
                        H,
                        List.of(new MeasureRegion(.06f, .94f, 90f / H, 186f / H))));
    }

    @Test
    public void preservesCallerInkAndLabels() {
        var p = page(130, true, false);
        byte[] l = p[0].clone(), g = p[1].clone();
        PrintedRestBarHeads.withoutBars(p[0], p[1], W, H);
        assertArrayEquals(l, p[0]);
        assertArrayEquals(g, p[1]);
    }
}

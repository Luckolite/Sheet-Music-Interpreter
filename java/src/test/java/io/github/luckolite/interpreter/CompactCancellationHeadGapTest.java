// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original cancellation geometry with compact first-note spacing and local-accidental controls. */
public class CompactCancellationHeadGapTest {
    JoinedSignatureSharpTest page(int headCenter, boolean bar, int naturalY) {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 0, 0, false);
        for (int y = 70; y <= 180; y++)
            for (int x = 30; x <= 55; x++) {
                f.labels[y * f.W + x] = 0;
                f.gray[y * f.W + x] = (byte) 255;
            }
        if (bar) for (int x : new int[] {177, 178}) for (int y = 100; y <= 164; y++) f.ink(x, y, 1);
        for (int y = naturalY - 22; y <= naturalY + 9; y++)
            for (int x = 199; x <= 200; x++) f.ink(x, y, 3);
        for (int y = naturalY - 9; y <= naturalY + 22; y++)
            for (int x = 208; x <= 209; x++) f.ink(x, y, 3);
        for (int cy : new int[] {naturalY - 8, naturalY + 8})
            for (int y = cy - 1; y <= cy + 1; y++) for (int x = 200; x <= 208; x++) f.ink(x, y, 3);
        f.sharp(238, 100);
        var old = f.labels.clone();
        for (int y = 80; y <= 145; y++)
            for (int x = 340; x <= 360; x++) {
                f.labels[y * f.W + x] = (byte) (y >= 100 && (y - 100) % 16 == 0 ? 4 : 0);
                f.gray[y * f.W + x] = (byte) (f.labels[y * f.W + x] == 4 ? 0 : 255);
            }
        for (int y = 80; y <= 145; y++)
            for (int x = 340; x <= 360; x++)
                if (old[y * f.W + x] == 1 || old[y * f.W + x] == 2)
                    f.ink(x + headCenter - 350, y, old[y * f.W + x]);
        return f;
    }

    @Test
    public void compactInkSeparatedSuffixStartsNewKey() {
        for (int x : new int[] {266, 267, 268})
            assertEquals("head=" + x, List.of(1), page(x, true, 132).keys());
    }

    @Test
    public void ordinarySpacingRemainsAccepted() {
        assertEquals(List.of(1), page(350, true, 132).keys());
    }

    @Test
    public void headTouchingSharpRemainsLocal() {
        assertEquals(List.of(), page(252, true, 132).keys());
    }

    @Test
    public void missingPrintedBarCannotEstablishSignature() {
        assertEquals(List.of(), page(266, false, 132).keys());
    }

    @Test
    public void wrongCancellationPitchIsRejected() {
        assertEquals(List.of(), page(266, true, 116).keys());
    }

    @Test
    public void inputsRemainUnchanged() {
        var f = page(266, true, 132);
        var l = f.labels.clone();
        var g = f.gray.clone();
        f.keys();
        assertArrayEquals(l, f.labels);
        assertArrayEquals(g, f.gray);
    }
}

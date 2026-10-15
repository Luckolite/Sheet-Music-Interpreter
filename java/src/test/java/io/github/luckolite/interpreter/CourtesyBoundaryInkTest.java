// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original terminal signature/line geometry; recognition proof is supplied separately by callers. */
public class CourtesyBoundaryInkTest {
    static final int W = 220, H = 120;

    record Page(byte[] labels, byte[] gray) {}

    static void rect(Page p, int x, int y, int w, int h, int label) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                p.labels[yy * W + xx] = (byte) label;
                p.gray[yy * W + xx] = 0;
            }
    }

    static Page page() {
        var p = new Page(new byte[W * H], new byte[W * H]);
        Arrays.fill(p.gray, (byte) 255);
        for (int y = 30; y <= 82; y += 13) rect(p, 80, y, 90, 1, 4);
        rect(p, 99, 30, 2, 53, 1);
        rect(p, 105, 30, 2, 53, 1);
        rect(p, 112, 20, 4, 26, 3);
        rect(p, 140, 32, 8, 14, 5);
        rect(p, 140, 58, 8, 14, 5);
        return p;
    }

    static boolean read(Page p) {
        return CourtesyBoundaryInk.matches(p.labels, p.gray, W, H, 106, 160, 30, 13);
    }

    @Test
    public void doubleBarAndOwnedSignaturePass() {
        assertTrue(read(page()));
    }

    @Test
    public void playedHeadRetainsTail() {
        var p = page();
        rect(p, 175, 65, 4, 4, 2);
        assertFalse(read(p));
    }

    @Test
    public void unexplainedRestRetainsTail() {
        var p = page();
        rect(p, 175, 65, 7, 7, 1);
        assertFalse(read(p));
    }

    @Test
    public void missingKeyRejected() {
        var p = page();
        for (int i = 0; i < p.labels.length; i++) if (p.labels[i] == 3) p.labels[i] = 0;
        assertFalse(read(p));
    }

    @Test
    public void missingMeterOwnershipRejected() {
        var p = page();
        for (int i = 0; i < p.labels.length; i++) if (p.labels[i] == 5) p.labels[i] = 0;
        assertFalse(read(p));
    }

    @Test
    public void oneBoundaryBarInsufficient() {
        var p = page();
        for (int y = 30; y <= 82; y++)
            for (int x = 99; x <= 100; x++) {
                p.gray[y * W + x] = (byte) 255;
                p.labels[y * W + x] = 0;
            }
        assertFalse(read(p));
    }

    @Test
    public void sourceIsPreserved() {
        var p = page();
        byte[] g = p.gray.clone(), l = p.labels.clone();
        read(p);
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }
}

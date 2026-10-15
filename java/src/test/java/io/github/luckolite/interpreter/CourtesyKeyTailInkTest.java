// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

public final class CourtesyKeyTailInkTest {
    @org.junit.Test
    public void originalControls() {
        main(new String[0]);
    }

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
        return p;
    }

    static boolean read(Page p) {
        return CourtesyKeyTailInk.matches(p.labels, p.gray, W, H, 106, 160, 30, 13);
    }

    public static void main(String[] args) {
        int checks = 0;
        var p = page();
        if (!read(p)) throw new AssertionError("key tail");
        checks++;
        for (int label : new int[] {0, 1, 2, 5}) {
            p = page();
            rect(p, 145, 50, 8, 5, label);
            if (read(p)) throw new AssertionError("missed rest/head label=" + label);
            checks++;
        }
        p = page();
        for (int i = 0; i < p.labels.length; i++) if (p.labels[i] == 3) p.labels[i] = 0;
        if (read(p)) throw new AssertionError("missing key");
        checks++;
        p = page();
        for (int y = 30; y <= 82; y++)
            for (int x = 99; x <= 100; x++) {
                p.gray[y * W + x] = (byte) 255;
                p.labels[y * W + x] = 0;
            }
        if (read(p)) throw new AssertionError("single bar");
        checks++;
        p = page();
        byte[] old = p.gray.clone(), labels = p.labels.clone();
        read(p);
        if (!Arrays.equals(old, p.gray) || !Arrays.equals(labels, p.labels))
            throw new AssertionError("mutated input");
        checks++;
        System.out.println(
                "PASS "
                        + checks
                        + " courtesy ink controls, including missed rests under four label classes");
    }
}

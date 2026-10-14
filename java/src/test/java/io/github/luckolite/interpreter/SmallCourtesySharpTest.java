// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original procedural geometry, with no source-score pixels or coordinates. */
public class SmallCourtesySharpTest {
    static final int W = 400, H = 240, L = 300, R = 350;
    static final float GAP = 16;

    record Page(byte[] labels, byte[] gray) {}

    static void box(Page p, int x, int y, int w, int h, int label, int shade) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                p.labels[yy * W + xx] = (byte) label;
                p.gray[yy * W + xx] = (byte) shade;
            }
    }

    static Page page(float slope) {
        Page p = new Page(new byte[W * H], new byte[W * H]);
        Arrays.fill(p.gray, (byte) 255);
        for (int x = 100; x <= R; x++)
            for (int line = 0; line < 5; line++)
                box(p, x, Math.round(150 + slope * (x - L) - line * GAP), 1, 1, 4, 0);
        box(p, 298, 86, 2, 65, 1, 0);
        box(p, 306, 86, 2, 65, 1, 0);
        box(p, 321, 65, 2, 43, 3, 0);
        box(p, 329, 65, 2, 43, 3, 0);
        box(p, 318, 78, 16, 5, 3, 0);
        box(p, 318, 94, 16, 5, 3, 0);
        return p;
    }

    static boolean detect(Page p) throws Exception {
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "localSmallCourtesySharp",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p.labels, p.gray, W, H, L, R, 166, GAP, 40, 205);
    }

    static int passed, failed;

    static void check(String name, Page p, boolean expected) throws Exception {
        byte[] labels = p.labels.clone(), gray = p.gray.clone();
        boolean actual = detect(p);
        if (!Arrays.equals(labels, p.labels) || !Arrays.equals(gray, p.gray))
            throw new AssertionError(name + " changed input");
        if (actual != expected) {
            System.out.println("FAIL " + name + ": expected " + expected + " got " + actual);
            failed++;
            return;
        }
        System.out.println("PASS " + name);
        passed++;
    }

    @org.junit.Test
    public void originalSyntheticControls() throws Exception {
        passed = 0;
        failed = 0;
        main(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        check("small sharp with displaced staff hint", page(0), true);
        check("upward sloped rails", page(-.015f), true);
        check("downward sloped rails", page(.015f), true);
        var p = page(0);
        box(p, 339, 122, 6, 4, 2, 0);
        check("note retains measure", p, false);
        p = page(0);
        box(p, 339, 119, 6, 9, 1, 0);
        check("rest retains measure", p, false);
        p = page(0);
        box(p, 339, 119, 6, 9, 0, 0);
        check("unlabelled musical ink retains measure", p, false);
        p = page(0);
        box(p, 298, 85, 2, 67, 0, 255);
        check("single bar insufficient", p, false);
        p = page(0);
        box(p, 306, 110, 2, 25, 0, 255);
        check("broken second bar insufficient", p, false);
        p = page(0);
        box(p, 313, 86, 2, 65, 1, 0);
        check("third bar ambiguous", p, false);
        p = page(0);
        box(p, 100, 84, 180, 5, 0, 255);
        check("missing interior rail", p, false);
        p = page(0);
        box(p, 100, 84, 180, 69, 0, 255);
        check("missing interior staff", p, false);
        p = page(0);
        for (int i = 0; i < p.labels.length; i++) if (p.labels[i] == 3) p.labels[i] = 0;
        check("raw sharp needs semantic evidence", p, false);
        p = page(0);
        for (int i = 0; i < p.labels.length; i++) if (p.labels[i] == 3) p.gray[i] = (byte) 255;
        check("semantic sharp needs raw evidence", p, false);
        p = page(0);
        box(p, 340, 67, 3, 3, 3, 0);
        check("extra key component ambiguous", p, false);
        p = page(0);
        box(p, 329, 65, 2, 13, 0, 255);
        check("missing upper spine", p, false);
        p = page(.10f);
        check("excessive rail slope", p, false);
        p = page(0);
        box(p, 339, 117, 8, 3, 1, 0);
        check("thin rest on staff rail retains measure", p, false);
        p = page(0);
        box(p, 339, 120, 3, 6, 1, 0);
        check("small detached rest retains measure", p, false);
        p = page(-.015f);
        box(p, 329, 65, 2, 13, 0, 255);
        check("rail cleanup cannot complete missing sharp spine", p, false);
        p = page(.015f);
        box(p, 339, 122, 6, 4, 2, 0);
        check("sloped staff note retains measure", p, false);
        System.out.println("Passed " + passed + " original controls; failed " + failed);
        if (failed > 0) throw new AssertionError("Failed " + failed);
    }
}

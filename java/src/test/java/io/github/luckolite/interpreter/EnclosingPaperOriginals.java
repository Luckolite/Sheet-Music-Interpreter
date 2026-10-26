// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original local cross sections and bounded callback/refusal cases. */
public final class EnclosingPaperOriginals {
    static final int W = 192, H = 192, X = 96, Y = 96;
    static int checks;

    static void check(boolean v, String m) {
        checks++;
        if (!v) throw new AssertionError(m);
    }

    static byte[] scene(int gap, int shade, int width, int side, boolean interior) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 245);
        int r = (int) Math.ceil(gap * .75);
        for (int y = Y - gap; y <= Y + gap; y++)
            for (int x = X; x < X + width; x++) g[y * W + x] = (byte) shade;
        for (int sign : new int[] {-1, 1})
            if (side == 0 || side == sign) {
                int at = X + sign * (interior ? r - 2 : r);
                for (int y = Y - 2; y <= Y + 2; y++) g[y * W + at] = 80;
            }
        return g;
    }

    public static void main(String[] args) {
        for (int gap : new int[] {8, 12, 16, 24, 32})
            for (int shade : new int[] {170, 185, 205})
                for (int side : new int[] {-1, 1, 0})
                    for (boolean interior : new boolean[] {false, true}) {
                        var g = scene(gap, shade, 1, side, interior);
                        byte[] before = g.clone();
                        long[] q = {0};
                        check(
                                !PrintedBeamRawContrast.faint(g, W, H, X, Y, gap, true, n -> {}),
                                "old fixed normal loses local component");
                        check(
                                PrintedBeamRawContrast.shaftWithEnclosingPaper(
                                        g, W, H, X, Y, gap, n -> q[0] += n),
                                "closed local paper g="
                                        + gap
                                        + " shade="
                                        + shade
                                        + " side="
                                        + side);
                        check(q[0] > 0 && Arrays.equals(before, g), "paid immutable scene");
                    }
        byte[] flat = new byte[W * H];
        Arrays.fill(flat, (byte) 180);
        check(
                !PrintedBeamRawContrast.shaftWithEnclosingPaper(flat, W, H, X, Y, 16, n -> {}),
                "uniform dark paper is not a shaft");
        for (int width : new int[] {7, 12, 24}) {
            var g = scene(16, 185, width, 1, false);
            check(
                    !PrintedBeamRawContrast.shaftWithEnclosingPaper(g, W, H, X, Y, 16, n -> {}),
                    "broad/open band");
        }
        var pale = scene(16, 225, 1, 1, false);
        check(
                !PrintedBeamRawContrast.shaftWithEnclosingPaper(pale, W, H, X, Y, 16, n -> {}),
                "no30-unit local paper contrast");
        var g = scene(16, 185, 1, 1, false);
        long[] cost = {0};
        check(
                PrintedBeamRawContrast.shaftWithEnclosingPaper(
                        g, W, H, X, Y, 16, n -> cost[0] += n),
                "measure positive");
        long[] exact = {cost[0]};
        check(
                PrintedBeamRawContrast.shaftWithEnclosingPaper(
                        g,
                        W,
                        H,
                        X,
                        Y,
                        16,
                        n -> {
                            if (n < 0 || n > exact[0]) throw new IllegalStateException("refused");
                            exact[0] -= n;
                        }),
                "exact allowance");
        check(exact[0] == 0, "exact cost used");
        long[] under = {cost[0] - 1};
        try {
            PrintedBeamRawContrast.shaftWithEnclosingPaper(
                    g,
                    W,
                    H,
                    X,
                    Y,
                    16,
                    n -> {
                        if (n < 0 || n > under[0]) throw new IllegalStateException("refused");
                        under[0] -= n;
                    });
            throw new AssertionError("one under");
        } catch (IllegalStateException expected) {
            checks++;
        }
        long[] pool = {cost[0] * 2};
        for (int i = 0; i < 2; i++)
            check(
                    PrintedBeamRawContrast.shaftWithEnclosingPaper(
                            g,
                            W,
                            H,
                            X,
                            Y,
                            16,
                            n -> {
                                if (n < 0 || n > pool[0])
                                    throw new IllegalStateException("refused");
                                pool[0] -= n;
                            }),
                    "cumulative pool");
        try {
            PrintedBeamRawContrast.shaftWithEnclosingPaper(
                    g,
                    W,
                    H,
                    X,
                    Y,
                    16,
                    n -> {
                        if (n < 0 || n > pool[0]) throw new IllegalStateException("refused");
                        pool[0] -= n;
                    });
            throw new AssertionError("new allowance");
        } catch (IllegalStateException expected) {
            checks++;
        }
        for (boolean error : new boolean[] {false, true}) {
            try {
                PrintedBeamRawContrast.shaftWithEnclosingPaper(
                        g,
                        W,
                        H,
                        X,
                        Y,
                        16,
                        n -> {
                            if (error) throw new AssertionError("parent");
                            throw new IllegalStateException("parent");
                        });
                throw new RuntimeException("parent swallowed");
            } catch (IllegalStateException | AssertionError expected) {
                check(expected.getMessage().equals("parent"), "parent refusal propagated");
            }
        }
        check(
                !PrintedBeamRawContrast.shaftWithEnclosingPaper(g, W, H, 0, Y, 16, n -> {}),
                "clipped normal");
        System.out.println("PASS " + checks + " original enclosing-paper assertions");
    }
}

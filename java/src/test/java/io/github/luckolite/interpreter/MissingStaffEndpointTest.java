// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class MissingStaffEndpointTest {
    @org.junit.Test
    public void originalGeneratedControls() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        int checks = 0;
        for (int texture = 0; texture < 2; texture++)
            for (int gap : new int[] {12, 16, 20})
                for (int mode = 0; mode < 6; mode++) {
                    int w = 620, h = 240, cut = 400, end = cut + 6 * gap, top = 50;
                    byte[] g = new byte[w * h];
                    Arrays.fill(g, (byte) 160);
                    for (int y = 0; y < h; y++)
                        for (int x = cut + 1; x < w; x++)
                            g[y * w + x] =
                                    (byte)
                                            (mode == 3
                                                    ? 255
                                                    : mode == 4
                                                            ? 120
                                                            : 95
                                                                    + ((x / 7) % 8) * 4
                                                                    + (texture == 1
                                                                            ? ((y / 19) % 3) * 6
                                                                            : 0));
                    for (int line = 0; line < (mode == 5 ? 3 : 5); line++)
                        for (int x = 20; x <= end; x++)
                            if (x <= cut || mode == 1) g[(top + line * gap) * w + x] = 25;
                    if (mode != 2) for (int y = top; y <= top + 4 * gap; y++) g[y * w + cut] = 20;
                    byte[] copy = g.clone();
                    Integer found =
                            PrintedStaffEnd.closingBeforeTexture(
                                    g,
                                    w,
                                    h,
                                    gap,
                                    List.of(20, 200, end),
                                    (line, x) -> top + line * gap);
                    if (mode == 0) {
                        if (found == null || Math.abs(found - cut) > 1)
                            throw new AssertionError("missed cut gap=" + gap + " found=" + found);
                    } else if (found != null)
                        throw new AssertionError(
                                "false cut mode=" + mode + " gap=" + gap + " found=" + found);
                    if (!Arrays.equals(copy, g)) throw new AssertionError("mutated source");
                    checks++;
                }
        System.out.println("PASS " + checks + " original missing-endpoint controls");
    }
}

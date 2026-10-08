// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original tiny predictions on blurred beam strips and independent oval controls. */
public final class SmallCornerOwnershipTest {
    static final int W = 180, H = 180;
    static int checks;

    static byte[] strip() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 158);
        for (int x = 40; x <= 72; x++) for (int y = 80; y <= 94; y++) p[y * W + x] = 50;
        return p;
    }

    static boolean tiny(byte[] p) {
        return StemOwnedBeamTip.mergedCorner(
                p, W, H, new int[] {40, 96, 1}, new int[] {72, 96, 1}, 72, 91, 5, 5, 16, 12);
    }

    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        require(tiny(strip()), "tiny mask inside uniform merged double-beam strip is owned");
        byte[] p = strip();
        for (int x = 50; x <= 60; x++) for (int y = 75; y <= 100; y++) p[y * W + x] = (byte) 158;
        require(!tiny(p), "disconnected strips cannot own the tiny component");
        p = strip();
        for (int x = 65; x <= 78; x++)
            for (int y = 82; y <= 101; y++)
                if ((x - 72) * (x - 72) / 36. + (y - 91) * (y - 91) / 81. <= 1) p[y * W + x] = 30;
        require(!tiny(p), "independent small oval bulge survives tiny-mask admission");
        require(
                !StemOwnedBeamTip.mergedCorner(
                        strip(),
                        W,
                        H,
                        new int[] {40, 96, 1},
                        new int[] {72, 96, -1},
                        72,
                        91,
                        5,
                        5,
                        16,
                        12),
                "opposing actual shaft preserves a separate head");
        byte[] before = p.clone();
        tiny(p);
        require(Arrays.equals(before, p), "source pixels are preserved");
        System.out.println(checks + " original tiny-corner controls passed");
    }

    @org.junit.Test
    public void originalProceduralControls() throws Exception {
        main(new String[0]);
    }
}

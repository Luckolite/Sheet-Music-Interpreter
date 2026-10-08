// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original shaded double beams and independent small-head controls. */
public final class ShadedBeamOwnershipTest {
    static final int W = 260, H = 180;
    static int checks;

    static byte[] fixture(int paper, boolean second) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int x = 40; x <= 210; x++) {
            for (int y = 70; y <= 75; y++) p[y * W + x] = 55;
            if (second) for (int y = 82; y <= 87; y++) p[y * W + x] = 55;
        }
        return p;
    }

    static boolean paired(byte[] p, int bw, int bh, int area) {
        return StemOwnedBeamTip.pairedCorner(
                p, W, H, new int[] {40, 70, -1}, new int[] {210, 70, -1}, 44, 78, bw, bh, area, 16);
    }

    static void require(boolean yes, String label) {
        checks++;
        if (!yes) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        require(
                paired(fixture(158, true), 13, 9, 85),
                "wider thin semantic island agrees with caller and owned shaded pair");
        require(
                paired(fixture(255, true), 8, 15, 80),
                "legacy tall narrow corner on white paper retained");
        require(
                !paired(fixture(158, false), 13, 9, 85),
                "a single band does not own wider thin corner");
        byte[] p = fixture(158, true);
        for (int y = 64; y <= 96; y++) for (int x = 90; x <= 120; x++) p[y * W + x] = (byte) 158;
        require(!paired(p, 13, 9, 85), "broken pair cannot establish ownership");
        require(
                !paired(fixture(158, true), 22, 15, 220),
                "normal full head is outside semantic corner bounds");
        p = fixture(158, true);
        for (int y = 73; y <= 83; y++)
            for (int x = 37; x <= 51; x++)
                if ((x - 44) * (x - 44) / 49. + (y - 78) * (y - 78) / 25. <= 1) p[y * W + x] = 45;
        require(!paired(p, 13, 9, 85), "real small grace oval that bridges the bands survives");
        p = fixture(158, true);
        require(
                !StemOwnedBeamTip.pairedCorner(
                        p,
                        W,
                        H,
                        new int[] {40, 70, -1},
                        new int[] {210, 70, 1},
                        44,
                        78,
                        13,
                        9,
                        85,
                        16),
                "independent opposing stem note survives");
        byte[] before = p.clone();
        paired(p, 13, 9, 85);
        require(Arrays.equals(before, p), "pixels preserved");
        System.out.println(checks + " original shaded-beam ownership controls passed");
    }

    @org.junit.Test
    public void originalProceduralControls() throws Exception {
        main(new String[0]);
    }
}

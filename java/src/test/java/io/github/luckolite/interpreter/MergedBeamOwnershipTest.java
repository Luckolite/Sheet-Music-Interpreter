// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original broad merged strips; real oval and disconnected-owner controls. */
public final class MergedBeamOwnershipTest {
    static final int W = 180, H = 180;
    static int checks;

    static byte[] fixture() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 158);
        for (int x = 40; x <= 100; x++) {
            int cy = 91 - Math.round((x - 40) * .1f);
            for (int y = cy - 6; y <= cy + 6; y++) p[y * W + x] = 50;
        }
        return p;
    }

    static boolean match(byte[] p) {
        return StemOwnedBeamTip.mergedCorner(
                p, W, H, new int[] {40, 99, 1}, new int[] {100, 93, 1}, 97, 85, 22, 16, 200, 12);
    }

    static void require(boolean yes, String label) {
        checks++;
        if (!yes) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        require(match(fixture()), "broad blurred beam strip owned by two complete shafts");
        byte[] p = fixture();
        for (int y = 65; y <= 108; y++) for (int x = 60; x <= 75; x++) p[y * W + x] = (byte) 158;
        require(!match(p), "disconnected broad bands do not establish ownership");
        p = fixture();
        for (int y = 76; y <= 94; y++)
            for (int x = 87; x <= 107; x++)
                if ((x - 97) * (x - 97) / 100. + (y - 85) * (y - 85) / 81. <= 1) p[y * W + x] = 35;
        require(!match(p), "independent normal oval bulge survives broad-strip gate");
        p = fixture();
        for (int x = 40; x <= 100; x++) for (int y = 70; y <= 110; y++) p[y * W + x] = (byte) 158;
        for (int x = 40; x <= 100; x++) for (int y = 82; y <= 87; y++) p[y * W + x] = 50;
        require(!match(p), "single ordinary beam is too thin for broad merged strip");
        require(
                !StemOwnedBeamTip.mergedCorner(
                        fixture(),
                        W,
                        H,
                        new int[] {40, 99, 1},
                        new int[] {100, 93, -1},
                        97,
                        85,
                        22,
                        16,
                        200,
                        12),
                "opposing independent shafts preserve true head");
        byte[] before = p.clone();
        match(p);
        require(Arrays.equals(before, p), "pixels preserved");
        System.out.println(checks + " original merged-beam controls passed");
    }

    @org.junit.Test
    public void originalProceduralControls() throws Exception {
        main(new String[0]);
    }
}

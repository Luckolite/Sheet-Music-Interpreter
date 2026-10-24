// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Pixel-area rendered originals, independent of private score pixels. */
public final class RasterLedgerOriginals {
    static final int W = 240, H = 240;
    static final float GAP = 13.75f, NX = 120, NY = 71.875f;
    static int passed;

    record Scene(byte[] g, int[] members) {}

    static void line(byte[] g, double center, int left, int right, double stroke) {
        for (int y = (int) Math.floor(center - stroke / 2 - 1);
                y <= (int) Math.ceil(center + stroke / 2 + 1);
                y++) {
            double overlap =
                    Math.max(
                            0,
                            Math.min(y + .5, center + stroke / 2)
                                    - Math.max(y - .5, center - stroke / 2));
            int shade = (int) Math.round(255 * (1 - overlap));
            for (int x = left; x <= right; x++)
                g[y * W + x] = (byte) Math.min(g[y * W + x] & 255, shade);
        }
    }

    static Scene scene(int shift, int kind) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        double top = 120;
        for (int k = 0; k < 5; k++)
            if (!(kind == 1 && k == 4))
                line(g, top + k * GAP + (kind == 2 && k == 4 ? 2 : 0), 30, 210, 3.2);
        for (int j = 1; j <= 3; j++)
            if (j != 2 && !(kind == 3 && j == 3))
                line(g, top - j * GAP, 110 + shift, 130 + shift, 3.2);
        double cy = top - 2 * GAP + (kind == 4 ? 3 : 0);
        int l = 110 + shift, r = 130 + shift;
        if (kind == 5) {
            l += 5;
            r -= 5;
        }
        line(g, cy, l, r, kind == 6 ? 5.2 : 3.2);
        if (kind == 7)
            for (int y = 90; y <= 95; y++)
                for (int x = l; x <= r; x++)
                    if ((g[y * W + x] & 255) < 255) g[y * W + x] = (byte) 254;
        if (kind == 8) g[92 * W + 120 + shift] = (byte) 255;
        // An ordinary physical head overdraws only the closest own ledger's normal
        // profile; independently observed finite flanks must still prove that line.
        for (int y = 68; y <= 76; y++)
            for (int x = 115 + shift; x <= 125 + shift; x++)
                if (Math.pow((x - 120 - shift) / 4.5, 2) + Math.pow((y - NY) / 3.5, 2) <= 1)
                    g[y * W + x] = 20;
        var members = new ArrayList<Integer>();
        for (int y = 89; y <= 99; y++)
            for (int x = l; x <= r; x++)
                if ((g[y * W + x] & 255) < 155 || (kind == 7 && (g[y * W + x] & 255) == 254))
                    members.add(y * W + x);
        return new Scene(g, members.stream().mapToInt(Integer::intValue).toArray());
    }

    static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }

    public static void main(String[] args) {
        for (int shift : new int[] {-17, 0, 19}) {
            var s = scene(shift, 0);
            var p = RasterPhaseLedgerOwnership.prove(s.g, W, H, s.members, NX + shift, NY, GAP);
            check(p != null, "complete raster-ledger original shift" + shift);
            check(
                    p.requiredLedgers == 3 && p.referenceMaximumRows == 4,
                    "full own ledgers and calibrated sampled width");
            check(p.matches(s.g, W, H, s.members, NX + shift, NY, GAP), "same source");
            check(
                    !p.matches(s.g.clone(), W, H, s.members, NX + shift, NY, GAP),
                    "foreign equal source refused");
            s.g[120 * W + 120 + shift] ^= 1;
            check(
                    !p.matches(s.g, W, H, s.members, NX + shift, NY, GAP),
                    "changed used rail refuses proof");
        }
        for (int kind = 1; kind <= 8; kind++) {
            var s = scene(0, kind);
            check(
                    RasterPhaseLedgerOwnership.prove(s.g, W, H, s.members, NX, NY, GAP) == null,
                    "proper/partial/faint/missing/rival original kind" + kind);
        }
        var six = scene(0, 0);
        line(six.g, 120 + 5 * GAP, 30, 210, 3.2);
        check(
                RasterPhaseLedgerOwnership.prove(six.g, W, H, six.members, NX, NY, GAP) == null,
                "sixth coextensive rail keeps");
        var s = scene(0, 0);
        check(
                RasterPhaseLedgerOwnership.prove(
                                s.g,
                                W,
                                H,
                                Arrays.copyOf(s.members, s.members.length - 1),
                                NX,
                                NY,
                                GAP)
                        == null,
                "partial raw vector keeps");
        check(
                RasterPhaseLedgerOwnership.prove(s.g, W, H, s.members, NX, 147.5f, GAP) == null,
                "proper tenuto owner on staff keeps");
        check(
                RasterPhaseLedgerOwnership.prove(s.g, W, H, s.members, NX, NY + GAP * .25f, GAP)
                        == null,
                "rival head-phase keeps");
        check(
                RasterPhaseLedgerOwnership.prove(s.g, W, H, s.members, NX, NY, Float.NaN) == null,
                "unknown native frame keeps");
        System.out.println("PASS rasterPhaseLedgerOriginals=" + passed);
    }
}

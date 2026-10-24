// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.nio.file.*;

public final class FaintHeadBodyOriginals {
    record Scene(byte[] gray, int[] members) {}

    static Scene scene(
            boolean hollow, boolean partial, boolean curve, boolean staff, int a, int b) {
        int w = 80, h = 70, cx2 = 80, cy2 = 70, norm = a * a + b * b;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        var members = new HashSet<Integer>();
        for (int y = 24; y < 47; y++)
            for (int x = 29; x < 52; x++) {
                long dx = 2L * x - cx2,
                        dy = 2L * y - cy2,
                        u = a * dx + b * dy,
                        v = -b * dx + a * dy;
                if (u * u * 49 + v * v * 81 > norm * 81L * 49) continue;
                int p = y * w + x;
                g[p] =
                        (byte)
                                (hollow && u * u * 49 + v * v * 81 < norm * 81L * 49 * .25
                                        ? 255
                                        : 254);
                if (!partial || Math.floorMod(x + 2 * y, 4) != 0) members.add(p);
            }
        if (curve)
            for (int x = 22; x <= 36; x++) {
                int y = 35 + (int) Math.round(Math.sin((x - 22) * Math.PI / 28));
                g[y * w + x] = 0;
                members.add(y * w + x);
            }
        if (staff) for (int y = 34; y <= 35; y++) for (int x = 0; x < w; x++) g[y * w + x] = 0;
        return new Scene(g, members.stream().mapToInt(Integer::intValue).toArray());
    }

    public static void main(String[] args) throws Exception {
        int passed = 0;
        for (boolean hollow : new boolean[] {false, true})
            for (boolean partial : new boolean[] {false, true})
                for (boolean curve : new boolean[] {false, true}) {
                    var s = scene(hollow, partial, curve, false, 1, 0);
                    var b = new SlurEvidenceBudget(180000000, 30000000);
                    if (!FaintHeadBodyPreserver.supported(
                            s.gray, 80, 70, s.members, 17.25f, p -> false, b))
                        throw new AssertionError(
                                "original9x7 hollow="
                                        + hollow
                                        + " partial="
                                        + partial
                                        + " curve="
                                        + curve);
                    passed++;
                }
        for (int[] basis : new int[][] {{1, 1}, {2, 1}, {3, 1}}) {
            var s = scene(false, false, true, false, basis[0], basis[1]);
            if (!FaintHeadBodyPreserver.supported(
                    s.gray,
                    80,
                    70,
                    s.members,
                    17.25f,
                    p -> false,
                    new SlurEvidenceBudget(180000000, 30000000)))
                throw new AssertionError("rotated faint quarter");
            passed++;
        }
        var staff = scene(false, false, true, true, 1, 0);
        if (!FaintHeadBodyPreserver.supported(
                staff.gray,
                80,
                70,
                staff.members,
                17.25f,
                p -> p / 80 == 34 || p / 80 == 35,
                new SlurEvidenceBudget(180000000, 30000000)))
            throw new AssertionError("faint head plus carrier and incoming curve");
        passed++;
        boolean zero = false;
        try {
            var s = scene(false, false, false, false, 1, 0);
            FaintHeadBodyPreserver.supported(
                    s.gray, 80, 70, s.members, 17.25f, p -> false, new SlurEvidenceBudget(0, 0));
        } catch (SlurEvidenceBudget.Unknown e) {
            zero = true;
        }
        if (!zero) throw new AssertionError("zero budget");
        passed++;
        var eroded = scene(false, true, true, false, 1, 0);
        for (int p : eroded.members)
            if ((eroded.gray[p] & 255) == 254 && Math.floorMod(p, 17) == 0)
                eroded.gray[p] = (byte) 255;
        if (!FaintHeadBodyPreserver.supported(
                eroded.gray,
                80,
                70,
                eroded.members,
                17.25f,
                p -> false,
                new SlurEvidenceBudget(180000000, 30000000)))
            throw new AssertionError("physically partial faint head with incoming curve");
        passed++;
        boolean wideUnknown = false;
        try {
            var s = scene(false, false, false, false, 1, 0);
            FaintHeadBodyPreserver.supported(
                    s.gray,
                    80,
                    70,
                    s.members,
                    64,
                    p -> false,
                    new SlurEvidenceBudget(180000000, 30000000));
        } catch (SlurEvidenceBudget.Unknown e) {
            wideUnknown = true;
        }
        if (!wideUnknown) throw new AssertionError("counter encoding must refuse overflow");
        passed++;
        System.out.println(
                "PASS original9x7Faint254255AndPartialIncomingRotatedCarrierControls="
                        + passed
                        + " allShaftFree=true no25ToneThreshold=true");
    }
}

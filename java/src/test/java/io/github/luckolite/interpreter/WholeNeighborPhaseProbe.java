// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class WholeNeighborPhaseProbe {
    static UpperLedgerAdversarialProbe.Page scene(float cx, float cy, boolean hollow, int mode) {
        var p = new UpperLedgerAdversarialProbe.Page("neighbor");
        p.erase(136, 164, 363, 387);
        p.line(20, 620, 375, 1, 4);
        int sx = (int) Math.floor(cx - 11);
        p.line(sx, sx + 1, (int) Math.floor(cy), 479 - (int) Math.floor(cy) + 1, 1);
        for (int y = (int) Math.floor(cy - 7); y <= Math.ceil(cy + 7); y++)
            for (int x = (int) Math.floor(cx - 11); x <= Math.ceil(cx + 11); x++) {
                double q = (x - cx) * (x - cx) / 121. + (y - cy) * (y - cy) / 49.;
                if (q <= 1) {
                    p.l[y * 640 + x] = 2;
                    if (!hollow || q > .45)
                        p.g[y * 640 + x] = (byte) Math.min(p.g[y * 640 + x] & 255, 0);
                }
            }
        if (mode != 0) {
            int a = mode == 3 ? 20 : 136, b = mode == 3 ? 620 : 164;
            for (int x = a; x <= b; x++) p.l[375 * 640 + x] = (byte) (mode == 1 ? 4 : 0);
        }
        return p;
    }

    public static void main(String[] a) {
        for (float dx : new float[] {-.4f, 0, .4f})
            for (float dy : new float[] {-1.5f, 0, 1.5f})
                for (boolean hollow : new boolean[] {false, true})
                    for (int mode = 0; mode < 4; mode++) {
                        var p = scene(150 + dx, 375 + dy, hollow, mode);
                        byte[] l = p.l.clone(), g = p.g.clone();
                        var ns =
                                OmrScoreInterpreter.extract(
                                        p.l,
                                        p.g,
                                        640,
                                        600,
                                        List.of(
                                                new MeasureRegion(.03f, .97f, .15f, .38f),
                                                new MeasureRegion(.03f, .97f, .46f, .90f)));
                        if (!Arrays.equals(l, p.l) || !Arrays.equals(g, p.g))
                            throw new AssertionError("input mutation");
                        var target =
                                ns.stream()
                                        .filter(
                                                n ->
                                                        n.pageY() * 600 >= 360
                                                                && n.pageY() * 600 <= 390)
                                        .toList();
                        System.out.println(
                                dx
                                        + ","
                                        + dy
                                        + ","
                                        + hollow
                                        + ","
                                        + mode
                                        + "|"
                                        + target.size()
                                        + "|"
                                        + ns);
                    }
    }
}

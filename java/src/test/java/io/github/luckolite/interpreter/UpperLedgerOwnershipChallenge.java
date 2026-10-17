// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class UpperLedgerOwnershipChallenge {
    static void oval(
            UpperLedgerAdversarialProbe.Page p, int cx, int cy, int rx, int ry, boolean hollow) {
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++) {
                double q =
                        (x - cx) * (x - cx) / (double) (rx * rx)
                                + (y - cy) * (y - cy) / (double) (ry * ry);
                if (q <= 1) {
                    p.l[y * 640 + x] = 2;
                    if (!hollow || q > .45)
                        p.g[y * 640 + x] = (byte) Math.min(p.g[y * 640 + x] & 255, 0);
                }
            }
    }

    static UpperLedgerAdversarialProbe.Page scene(String kind) {
        var p = new UpperLedgerAdversarialProbe.Page("neighbor");
        p.erase(137, 163, 365, 385);
        for (int x = 20; x <= 620; x++) {
            p.g[375 * 640 + x] = 0;
            p.l[375 * 640 + x] = 4;
        }
        p.line(139, 140, 375, 11, 1);
        if (kind.equals("pair")) {
            oval(p, 150, 371, 11, 3, false);
            oval(p, 150, 379, 11, 3, false);
            p.line(160, 161, 340, 32, 1);
            p.line(139, 140, 379, 35, 1);
        } else oval(p, 150, 375, 11, 7, kind.equals("hollow"));
        for (int x = 20; x <= 620; x++) {
            p.g[375 * 640 + x] = 0;
            p.l[375 * 640 + x] = 4;
        }
        return p;
    }

    public static void main(String[] a) {
        for (String k : new String[] {"filled", "hollow", "pair"}) {
            var p = scene(k);
            var n =
                    OmrScoreInterpreter.extract(
                            p.l,
                            p.g,
                            640,
                            600,
                            List.of(
                                    new MeasureRegion(.03f, .97f, .15f, .38f),
                                    new MeasureRegion(.03f, .97f, .46f, .90f)));
            System.out.println(
                    k
                            + " "
                            + n.stream()
                                    .filter(x -> x.pageY() * 600 >= 365 && x.pageY() * 600 <= 385)
                                    .toList());
        }
    }
}

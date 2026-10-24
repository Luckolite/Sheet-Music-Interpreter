// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Universal-family containment controls across pair gaps, phases and mirrors. */
public final class PairFamilyContainmentOriginals {
    static int assertions, admitted;

    static void check(boolean v, String message) {
        assertions++;
        if (!v) throw new AssertionError(message);
    }

    static void box(byte[] raw, int w, int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) raw[y * w + x] = 0;
    }

    public static void main(String[] args) {
        int w = 640, h = 520;
        for (double[] gaps :
                new double[][] {
                    {3, 64}, {4, 24}, {9, 16}, {16, 17}, {20, 24}, {24, 4}, {64, 3}, {17, 16}
                })
            for (int distance : new int[] {5, 6, 8, 40, 60, 100})
                for (double slope : new double[] {-.5, 0, .5})
                    for (int side : new int[] {-1, 1})
                        for (int direction : new int[] {-1, 1}) {
                            int origin = 300,
                                    cap = 200,
                                    otherCap = (int) (cap + slope * distance),
                                    k =
                                            Math.max(
                                                    2,
                                                    (int)
                                                            Math.ceil(
                                                                    (gaps[0] + gaps[1])
                                                                            * .5
                                                                            * .18));
                            double mean = (gaps[0] + gaps[1]) * .5, offset = .2 * mean + .125;
                            byte[] raw = new byte[w * h];
                            Arrays.fill(raw, (byte) 255);
                            for (int dx = 0; dx <= distance; dx++) {
                                int top = (int) Math.floor(cap + slope * dx + offset + .5);
                                for (int y = top; y < top + k; y++)
                                    raw[(direction < 0 ? y : h - 1 - y) * w + origin + side * dx] =
                                            0;
                            }
                            int ac = direction < 0 ? cap : h - 1 - cap,
                                    bc = direction < 0 ? otherCap : h - 1 - otherCap,
                                    start = direction < 0 ? 470 : h - 1 - 470;
                            var a =
                                    new PrintedBeamGeometry.Column(
                                            0,
                                            0,
                                            0,
                                            1,
                                            direction,
                                            origin,
                                            origin,
                                            ac,
                                            gaps[0],
                                            1,
                                            List.of(new PrintedBeamGeometry.End(origin, ac)),
                                            start);
                            var b =
                                    new PrintedBeamGeometry.Column(
                                            1,
                                            0,
                                            0,
                                            1,
                                            direction,
                                            origin + side * distance,
                                            origin + side * distance,
                                            bc,
                                            gaps[1],
                                            1,
                                            List.of(
                                                    new PrintedBeamGeometry.End(
                                                            origin + side * distance, bc)),
                                            start);
                            for (var n : List.of(a, b))
                                box(
                                        raw,
                                        w,
                                        n.left(),
                                        Math.min(n.cap(), start),
                                        n.right(),
                                        Math.max(n.cap(), start));
                            var nodes = side > 0 ? List.of(a, b) : List.of(b, a);
                            var geometry =
                                    PrintedBeamGeometry.detect(
                                            raw,
                                            w,
                                            h,
                                            nodes,
                                            (aa, bb, x, y) -> false,
                                            new PrintedBeamGeometry.Budget(64000000));
                            if (!geometry.groups().isEmpty()) {
                                admitted++;
                                var carrier =
                                        new FiniteRawBeamStrip.Carrier() {
                                            public boolean at(int x, int y) {
                                                return false;
                                            }

                                            public boolean unknown(int x, int y) {
                                                return true;
                                            }
                                        };
                                var negative =
                                        new RawPrimaryBeamAbsence(
                                                raw,
                                                w,
                                                h,
                                                carrier,
                                                new PrintedIsolatedFlags.Budget(64000000));
                                check(
                                        negative.possible(a, side, distance, b)
                                                != RawPrimaryBeamAbsence.State.IMPOSSIBLE,
                                        "Actual geometry family excluded gaps="
                                                + Arrays.toString(gaps)
                                                + " distance="
                                                + distance
                                                + " side="
                                                + side
                                                + " direction="
                                                + direction);
                                check(
                                        negative.prove(a, nodes).isEmpty(),
                                        "Raw admitted primary certified absent");
                            }
                        }
        check(
                admitted > 100,
                "Containment controls must authenticate broad actual geometry families");
        for (int dx : new int[] {1, 2, 3, 4})
            for (boolean opposite : new boolean[] {false, true})
                for (boolean bridge : new boolean[] {false, true}) {
                    byte[] raw = new byte[w * h];
                    Arrays.fill(raw, (byte) 255);
                    int ax = 250, bx = ax + dx;
                    var a =
                            new PrintedBeamGeometry.Column(
                                    0,
                                    0,
                                    0,
                                    2,
                                    -1,
                                    ax,
                                    ax,
                                    100,
                                    14,
                                    1,
                                    List.of(new PrintedBeamGeometry.End(ax, 100)),
                                    160);
                    int bd = opposite ? 1 : -1, bs = opposite ? 240 : 360;
                    var b =
                            new PrintedBeamGeometry.Column(
                                    1,
                                    1,
                                    1,
                                    2,
                                    bd,
                                    bx,
                                    bx,
                                    300,
                                    13,
                                    1,
                                    List.of(new PrintedBeamGeometry.End(bx, 300)),
                                    bs);
                    box(raw, w, ax, 100, ax, 160);
                    box(raw, w, bx, Math.min(300, bs), bx, Math.max(300, bs));
                    if (bridge) {
                        box(raw, w, ax, 160, bx, 300);
                    }
                    var plane =
                            new FiniteRawBeamStrip.Carrier() {
                                public boolean at(int x, int y) {
                                    return bridge;
                                }

                                public boolean unknown(int x, int y) {
                                    return !bridge;
                                }
                            };
                    var negative =
                            new RawPrimaryBeamAbsence(
                                    raw, w, h, plane, new PrintedIsolatedFlags.Budget(64000000));
                    check(
                            negative.prove(a, List.of(a, b)).isPresent() != bridge,
                            "Cross-staff short raw contact lost dx="
                                    + dx
                                    + " opposite="
                                    + opposite
                                    + " bridge="
                                    + bridge);
                }
        System.out.println(
                "{\"status\":\"PAIR_FAMILY_SUPERSET_PASS\",\"assertions\":"
                        + assertions
                        + ",\"actualGeometryFamilies\":"
                        + admitted
                        + "}");
    }
}

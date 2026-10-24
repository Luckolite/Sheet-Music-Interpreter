// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class IndependentV3Probe {
    static int assertions, admitted;

    static void check(boolean v, String s) {
        assertions++;
        if (!v) throw new AssertionError(s);
    }

    static final int W = 800, H = 520;

    static void box(byte[] r, int l, int t, int rr, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= rr; x++) r[y * W + x] = 0;
    }

    static PrintedBeamGeometry.Column col(
            int id, int x, int width, int cap, double gap, int direction) {
        var ends = new ArrayList<PrintedBeamGeometry.End>();
        for (int i = 0; i < width; i++)
            ends.add(new PrintedBeamGeometry.End(x + i, cap + (i % 3 - 1)));
        return new PrintedBeamGeometry.Column(
                id,
                0,
                0,
                1,
                direction,
                x,
                x + width - 1,
                cap,
                gap,
                1,
                ends,
                direction < 0 ? 470 : 40);
    }

    static void containment() {
        var random = new Random(3197003);
        for (int sample = 0; sample < 180; sample++) {
            double ga = 3 + random.nextInt(62), gb = 3 + random.nextInt(62), mean = (ga + gb) * .5;
            int wa = 1 + random.nextInt(9),
                    wb = 1 + random.nextInt(9),
                    distance = 5 + random.nextInt(350),
                    direction = (sample % 2 == 0 ? -1 : 1),
                    aX = 80,
                    bX = aX + wa - 1 + distance;
            int cap = 240,
                    bc = cap + (int) Math.round((random.nextDouble() * 1.5 - .75) * distance);
            if (bc < 50 || bc > 450) {
                sample--;
                continue;
            }
            var a = col(0, aX, wa, cap, ga, direction);
            var b = col(1, bX, wb, bc, gb, direction);
            int thickness =
                    sample % 3 == 0
                            ? (int) Math.floor(mean)
                            : Math.max(2, (int) Math.ceil(mean * .18));
            double offset = (sample % 3 - 1) * mean * .285;
            byte[] raw = new byte[W * H];
            Arrays.fill(raw, (byte) 255);
            for (int x = a.right(); x <= b.left(); x++) {
                double center = cap + (bc - cap) * (x - a.right()) / (double) distance;
                int top =
                        (int)
                                Math.floor(
                                        center + offset - (direction > 0 ? thickness - 1 : 0) + .5);
                if (top < 1 || top + thickness >= H)
                    throw new AssertionError("Bad abstract canvas");
                box(raw, x, top, x, top + thickness - 1);
            }
            for (var n : List.of(a, b))
                for (var e : n.shaftColumns())
                    box(
                            raw,
                            e.x(),
                            Math.min(e.endY(), n.shaftStart()),
                            e.x(),
                            Math.max(e.endY(), n.shaftStart()));
            var actual =
                    FiniteRawBeamStrip.prove(
                            raw,
                            (x, y) -> false,
                            W,
                            H,
                            a.right() + 2,
                            b.left() - 2,
                            cap + (bc - cap) * 2.0 / distance,
                            bc - (bc - cap) * 2.0 / distance,
                            mean,
                            direction,
                            0,
                            0);
            if (actual == null) continue;
            admitted++;
            var uncertain =
                    new FiniteRawBeamStrip.Carrier() {
                        public boolean at(int x, int y) {
                            return true;
                        }

                        public boolean unknown(int x, int y) {
                            return true;
                        }
                    };
            for (int side : new int[] {-1, 1}) {
                var own = side > 0 ? a : b;
                var rival = side > 0 ? b : a;
                var n =
                        new RawPrimaryBeamAbsence(
                                raw, W, H, uncertain, new PrintedIsolatedFlags.Budget(64000000));
                check(
                        n.possible(own, side, distance, rival)
                                != RawPrimaryBeamAbsence.State.IMPOSSIBLE,
                        "Lost witnessed broad-width family sample=" + sample + " side=" + side);
                check(
                        n.prove(own, List.of(a, b)).isEmpty(),
                        "False absence for actual broad-width family");
            }
        }
        check(admitted > 80, "Independent families must be substantive");
    }

    static void overlap() {
        for (int width : new int[] {1, 3})
            for (boolean joined : new boolean[] {false, true}) {
                int ax = 300, bx = ax + (width == 1 ? 0 : 1);
                byte[] raw = new byte[W * H];
                Arrays.fill(raw, (byte) 255);
                var ae = new ArrayList<PrintedBeamGeometry.End>();
                for (int x = ax; x < ax + width; x++) ae.add(new PrintedBeamGeometry.End(x, 100));
                var a =
                        new PrintedBeamGeometry.Column(
                                0, 0, 0, 2, -1, ax, ax + width - 1, 100, 14, 1, ae, 160);
                var b =
                        new PrintedBeamGeometry.Column(
                                1,
                                1,
                                1,
                                2,
                                -1,
                                bx,
                                bx,
                                300,
                                13,
                                1,
                                List.of(new PrintedBeamGeometry.End(bx, 300)),
                                360);
                box(raw, ax, 100, ax + width - 1, 160);
                box(raw, bx, 300, bx, 360);
                if (joined) box(raw, ax, 160, bx, 300);
                var n =
                        new RawPrimaryBeamAbsence(
                                raw,
                                W,
                                H,
                                (x, y) -> false,
                                new PrintedIsolatedFlags.Budget(64000000));
                var proof = n.prove(a, List.of(a, b));
                var direct = n.possible(a, 1, Math.max(0, b.left() - a.right()), b);
                System.out.println(
                        "{\"case\":\"overlap-short-domain\",\"width\":"
                                + width
                                + ",\"joined\":"
                                + joined
                                + ",\"absenceCertified\":"
                                + proof.isPresent()
                                + ",\"directContact\":\""
                                + direct
                                + "\"}");
            }
    }

    static void overlapFlag() {
        for (boolean joined : new boolean[] {false, true}) {
            byte[] raw = new byte[W * H];
            Arrays.fill(raw, (byte) 255);
            int origin = 300, cap = 230;
            var a =
                    new PrintedBeamGeometry.Column(
                            0,
                            0,
                            0,
                            2,
                            -1,
                            origin,
                            origin,
                            cap,
                            24,
                            1,
                            List.of(new PrintedBeamGeometry.End(origin, cap)),
                            430);
            var b =
                    new PrintedBeamGeometry.Column(
                            1,
                            1,
                            1,
                            2,
                            -1,
                            origin,
                            origin,
                            100,
                            14,
                            1,
                            List.of(new PrintedBeamGeometry.End(origin, 100)),
                            160);
            box(raw, origin, cap, origin, 430);
            box(raw, origin, 100, origin, 160);
            if (joined) box(raw, origin, 160, origin, cap);
            int[] t = {0, 7, 18, 28, 40, 48},
                    left = {0, 0, 10, 20, 20, 16},
                    right = {6, 16, 28, 30, 24, 18};
            for (int i = 0; i < t.length - 1; i++)
                for (int y = t[i]; y <= t[i + 1]; y++) {
                    double u = (y - t[i]) / (double) (t[i + 1] - t[i]);
                    box(
                            raw,
                            origin + (int) Math.round(left[i] + u * (left[i + 1] - left[i])),
                            cap + y,
                            origin + (int) Math.round(right[i] + u * (right[i + 1] - right[i])),
                            cap + y);
                }
            for (int y = 419; y <= 441; y++)
                for (int x = 272; x <= 306; x++)
                    if (Math.pow((x - 289) / 17.0, 2) + Math.pow((y - 430) / 11.0, 2) <= 1)
                        raw[y * W + x] = 0;
            for (int y = 154; y <= 166; y++)
                for (int x = 283; x <= 303; x++)
                    if (Math.pow((x - 293) / 10.0, 2) + Math.pow((y - 160) / 6.0, 2) <= 1)
                        raw[y * W + x] = 0;
            var carrier =
                    new FiniteRawBeamStrip.Carrier() {
                        public boolean at(int x, int y) {
                            return false;
                        }
                    };
            var result =
                    PrintedIsolatedFlags.prove(
                            raw,
                            W,
                            H,
                            a,
                            List.of(0),
                            List.of(
                                    new PrintedIsolatedFlags.Head(0, 289, 430, 24),
                                    new PrintedIsolatedFlags.Head(1, 293, 160, 14)),
                            List.of(a, b),
                            carrier,
                            new PrintedIsolatedFlags.Budget(64000000));
            var direct =
                    new RawPrimaryBeamAbsence(
                                    raw, W, H, carrier, new PrintedIsolatedFlags.Budget(64000000))
                            .possible(a, 1, 0, b);
            System.out.println(
                    "{\"case\":\"overlap-full-flag\",\"joined\":"
                            + joined
                            + ",\"flagAdmitted\":"
                            + result.isPresent()
                            + ",\"directContact\":\""
                            + direct
                            + "\"}");
        }
    }

    public static void main(String[] args) {
        containment();
        overlap();
        overlapFlag();
        System.out.println(
                "{\"status\":\"INDEPENDENT_V3_WITNESSED_FAMILIES_RETAINED\",\"assertions\":"
                        + assertions
                        + ",\"witnessedFamilies\":"
                        + admitted
                        + "}");
    }
}

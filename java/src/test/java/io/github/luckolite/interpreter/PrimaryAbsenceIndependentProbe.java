// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independent rectangles/shafts; no score, font, model or source truth inputs. */
public final class PrimaryAbsenceIndependentProbe {
    static final int W = 640, H = 500;

    static PrintedBeamGeometry.Column column(int id, int x, int cap, int direction, double gap) {
        return new PrintedBeamGeometry.Column(
                id,
                0,
                0,
                1,
                direction,
                x,
                x,
                cap,
                gap,
                1,
                List.of(new PrintedBeamGeometry.End(x, cap)),
                direction < 0 ? 430 : 60);
    }

    static void box(byte[] raw, int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) raw[y * W + x] = 0;
    }

    static void run(
            String name,
            double ownGap,
            double otherGap,
            int distance,
            int offset,
            int side,
            int direction,
            boolean unknown) {
        int origin = 300,
                cap = 230,
                k = Math.max(2, (int) Math.ceil((ownGap + otherGap) * .5 * .18));
        var shaft = column(0, origin, cap, direction, ownGap);
        var rival = column(1, origin + side * distance, cap, direction, otherGap);
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        int top = direction < 0 ? cap + offset : cap - offset - k + 1;
        box(
                raw,
                Math.min(shaft.left(), rival.left()),
                top,
                Math.max(shaft.right(), rival.right()),
                top + k - 1);
        for (var c : List.of(shaft, rival))
            box(
                    raw,
                    c.left(),
                    Math.min(c.cap(), c.shaftStart()),
                    c.right(),
                    Math.max(c.cap(), c.shaftStart()));
        var carrier =
                new FiniteRawBeamStrip.Carrier() {
                    public boolean at(int x, int y) {
                        return false;
                    }

                    public boolean unknown(int x, int y) {
                        return unknown;
                    }
                };
        var nodes = side > 0 ? List.of(shaft, rival) : List.of(rival, shaft);
        var geom =
                PrintedBeamGeometry.detect(
                        raw,
                        W,
                        H,
                        nodes,
                        new PrintedBeamGeometry.CarrierPlane() {
                            public boolean at(
                                    PrintedBeamGeometry.Column a,
                                    PrintedBeamGeometry.Column b,
                                    int x,
                                    int y) {
                                return false;
                            }
                        },
                        new PrintedBeamGeometry.Budget(64000000));
        var budget = new PrintedIsolatedFlags.Budget(64000000);
        var absence = new RawPrimaryBeamAbsence(raw, W, H, carrier, budget).prove(shaft, nodes);
        var direct =
                new RawPrimaryBeamAbsence(
                                raw, W, H, carrier, new PrintedIsolatedFlags.Budget(64000000))
                        .possible(shaft, side, distance, rival);
        System.out.println(
                "{\"name\":\""
                        + name
                        + "\",\"ownGap\":"
                        + ownGap
                        + ",\"rivalGap\":"
                        + otherGap
                        + ",\"distance\":"
                        + distance
                        + ",\"offset\":"
                        + offset
                        + ",\"side\":"
                        + side
                        + ",\"direction\":"
                        + direction
                        + ",\"unknown\":"
                        + unknown
                        + ",\"thickness\":"
                        + k
                        + ",\"geometryAdmitted\":"
                        + !geom.groups().isEmpty()
                        + ",\"geometryBudgetExceeded\":"
                        + geom.budgetExceeded()
                        + ",\"absenceCertified\":"
                        + absence.isPresent()
                        + ",\"actualRivalPossibility\":\""
                        + direct
                        + "\",\"work\":"
                        + budget.used()
                        + "}");
    }

    static void flag(String name, boolean beam, boolean mixed, int distance, int offset) {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        int cap = 230, origin = 300, rivalX = origin - distance;
        double gap = 24, other = mixed ? 4 : 24;
        int thickness = Math.max(2, (int) Math.ceil((gap + other) * .5 * .18));
        var shaft = column(0, origin, cap, -1, gap);
        var rival = column(1, rivalX, cap, -1, other);
        for (var c : List.of(shaft, rival)) box(raw, c.left(), c.cap(), c.right(), c.shaftStart());
        // Original piecewise filled returning arm. No submitted fixture or font is read.
        int[] t = {0, 7, 18, 28, 40, 48},
                left = {0, 0, 10, 20, 20, 16},
                right = {6, 16, 28, 30, 24, 18};
        for (int i = 0; i < t.length - 1; i++)
            for (int y = t[i]; y <= t[i + 1]; y++) {
                double u = (y - t[i]) / (double) (t[i + 1] - t[i]);
                int l = (int) Math.round(left[i] + u * (left[i + 1] - left[i])),
                        r = (int) Math.round(right[i] + u * (right[i + 1] - right[i]));
                box(raw, origin + l, cap + y, origin + r, cap + y);
            }
        if (beam) box(raw, rivalX, cap + offset, origin, cap + offset + thickness - 1);
        for (int y = 419; y <= 441; y++)
            for (int x = 272; x <= 306; x++)
                if (Math.pow((x - 289) / 17.0, 2) + Math.pow((y - 430) / 11.0, 2) <= 1)
                    raw[y * W + x] = 0;
        for (int y = 428; y <= 432; y++)
            for (int x = rivalX - 5; x <= rivalX + 1; x++)
                if (Math.pow((x - (rivalX - 2)) / 3.0, 2) + Math.pow((y - 430) / 2.0, 2) <= 1)
                    raw[y * W + x] = 0;
        var plane =
                new FiniteRawBeamStrip.Carrier() {
                    public boolean at(int x, int y) {
                        return false;
                    }

                    public boolean unknown(int x, int y) {
                        return beam
                                && x < origin - 1
                                && x > rivalX + 1
                                && y >= cap + offset
                                && y < cap + offset + thickness;
                    }
                };
        var nodes = List.of(rival, shaft);
        var budget = new PrintedIsolatedFlags.Budget(64000000);
        var result =
                PrintedIsolatedFlags.prove(
                        raw,
                        W,
                        H,
                        shaft,
                        List.of(0),
                        List.of(
                                new PrintedIsolatedFlags.Head(0, 289, 430, 24),
                                new PrintedIsolatedFlags.Head(1, rivalX - 2, 430, other)),
                        nodes,
                        plane,
                        budget);
        var possible =
                new RawPrimaryBeamAbsence(
                                raw, W, H, plane, new PrintedIsolatedFlags.Budget(64000000))
                        .possible(shaft, -1, distance, rival);
        var geo =
                PrintedBeamGeometry.detect(
                        raw,
                        W,
                        H,
                        nodes,
                        new PrintedBeamGeometry.CarrierPlane() {
                            public boolean at(
                                    PrintedBeamGeometry.Column a,
                                    PrintedBeamGeometry.Column b,
                                    int x,
                                    int y) {
                                return false;
                            }

                            public boolean unknown(
                                    PrintedBeamGeometry.Column a,
                                    PrintedBeamGeometry.Column b,
                                    int x,
                                    int y) {
                                return plane.unknown(x, y);
                            }
                        },
                        new PrintedBeamGeometry.Budget(64000000));
        var rawGeo =
                PrintedBeamGeometry.detect(
                        raw,
                        W,
                        H,
                        nodes,
                        (a, b, x, y) -> false,
                        new PrintedBeamGeometry.Budget(64000000));
        System.out.println(
                "{\"name\":\""
                        + name
                        + "\",\"beam\":"
                        + beam
                        + ",\"mixedGap\":"
                        + mixed
                        + ",\"distance\":"
                        + distance
                        + ",\"offset\":"
                        + offset
                        + ",\"flagAdmitted\":"
                        + result.isPresent()
                        + ",\"actualRivalPossibility\":\""
                        + possible
                        + "\",\"strictGeometryAdmitted\":"
                        + !geo.groups().isEmpty()
                        + ",\"rawGeometryAdmitted\":"
                        + !rawGeo.groups().isEmpty()
                        + ",\"budgetExhausted\":"
                        + budget.exhausted()
                        + "}");
    }

    public static void main(String[] args) {
        for (int side : new int[] {-1, 1})
            for (int direction : new int[] {-1, 1}) {
                run("uniform-near", 24, 24, 40, 0, side, direction, false);
                run("mixed-gap-far-thin", 24, 4, 100, 0, side, direction, false);
                run("mixed-gap-near-offset", 24, 4, 40, 3, side, direction, false);
                run("adjacent-scale-far-thin", 17, 16, 60, 0, side, direction, false);
                run("near-pixel-edge-offset", 24, 20, 40, 7, side, direction, false);
                run("unknown-mixed-gap-far-thin", 24, 4, 100, 0, side, direction, true);
            }
        flag("original-flag-positive", false, true, 100, 0);
        flag("flag-with-unknown-far-mixed-beam", true, true, 100, 0);
        flag("flag-with-unknown-near-offset-mixed-beam", true, true, 40, 3);
        flag("flag-with-unknown-far-uniform-beam", true, false, 100, 0);
    }
}

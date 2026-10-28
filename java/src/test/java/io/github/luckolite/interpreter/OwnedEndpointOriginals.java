// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;

/** Original, analytic raster fixtures; conditional authenticated columns, no score pixels. */
public final class OwnedEndpointOriginals {
    static final int W = 90, H = 100;
    static boolean fixed;
    static int checks, cases;

    static void ck(boolean v, String why) {
        checks++;
        if (!v) throw new AssertionError(why);
    }

    static byte[] blank() {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        return r;
    }

    static void dot(byte[] r, int x, int y, int v) {
        r[y * W + x] = (byte) v;
    }

    static class Plane implements PrintedBeamGeometry.CarrierPlane {
        final Set<String> carrier = new HashSet<>(), unknown = new HashSet<>();
        int calls;
        boolean throwGoal;
        int goal;

        String k(int x, int y) {
            return x + ":" + y;
        }

        public boolean at(
                PrintedBeamGeometry.Column a, PrintedBeamGeometry.Column b, int x, int y) {
            calls++;
            if (throwGoal && x == goal) throw new IllegalStateException("Goal classified");
            return carrier.contains(k(x, y));
        }

        public boolean unknown(
                PrintedBeamGeometry.Column a, PrintedBeamGeometry.Column b, int x, int y) {
            calls++;
            return unknown.contains(k(x, y));
        }
    }

    static PrintedBeamGeometry.Column shaft(
            int id, int x, int direction, List<PrintedBeamGeometry.End> ends) {
        return new PrintedBeamGeometry.Column(
                id,
                0,
                0,
                1,
                direction,
                x,
                x + ends.size() - 1,
                30,
                10,
                2,
                ends,
                direction < 0 ? 90 : 0);
    }

    static boolean contact(
            byte[] raw,
            PrintedBeamGeometry.Column shaft,
            int boundary,
            int lowTop,
            int highTop,
            Plane plane,
            long limit)
            throws Throwable {
        var other = shaft(1, 70, shaft.direction(), List.of(new PrintedBeamGeometry.End(70, 30)));
        var cols = List.of(shaft, other);
        Class<?> d = Class.forName(PrintedBeamGeometry.class.getName() + "$Detector"),
                edge = Class.forName(PrintedBeamGeometry.class.getName() + "$Edge");
        // These controls exercise legacy geometry without optional Source evidence.
        var dc =
                d.getDeclaredConstructor(
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        PrintedBeamGeometry.CarrierPlane.class,
                        PrintedBeamGeometry.Budget.class,
                        PrintedBeamGeometry.PrimaryObserver.class);
        dc.setAccessible(true);
        Object detector =
                dc.newInstance(raw, W, H, cols, plane, new PrintedBeamGeometry.Budget(limit), null);
        var ec = edge.getDeclaredConstructor(int.class, int.class);
        ec.setAccessible(true);
        Object e = ec.newInstance(0, 1);
        var m =
                d.getDeclaredMethod(
                        "contact",
                        edge,
                        PrintedBeamGeometry.Column.class,
                        int.class,
                        FiniteRawBeamStrip.Proof.class,
                        int.class,
                        boolean.class);
        m.setAccessible(true);
        var interval = new FiniteRawBeamStrip.Interval(lowTop * 1024, highTop * 1024);
        var proof = new FiniteRawBeamStrip.Proof(1, List.of(interval), List.of(interval), null);
        try {
            return (boolean) m.invoke(detector, e, shaft, boundary, proof, 0, false);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static void outcome(String name, boolean value, boolean expected) {
        cases++;
        ck(value == expected, name + " expected=" + expected + " actual=" + value);
    }

    static void matrix() throws Throwable {
        for (int dir : new int[] {-1, 1})
            for (int side : new int[] {-1, 1})
                for (String kind :
                        List.of(
                                "known",
                                "unknownGoal",
                                "carrierGoal",
                                "whiteGoal",
                                "thresholdGoal",
                                "unknownConnector",
                                "carrierConnector",
                                "whiteConnector",
                                "foreignShaft",
                                "shortSpur")) {
                    byte[] raw = blank();
                    int gx = 40, cx = gx + side, bx = gx + 2 * side;
                    for (int x : List.of(gx, cx, bx)) dot(raw, x, 30, 0);
                    var ends =
                            List.of(
                                    new PrintedBeamGeometry.End(
                                            gx,
                                            kind.equals("shortSpur") ? (dir < 0 ? 31 : 29) : 30));
                    var own = shaft(0, gx, dir, ends);
                    Plane p = new Plane();
                    if (kind.equals("unknownGoal")) p.unknown.add(p.k(gx, 30));
                    if (kind.equals("carrierGoal")) p.carrier.add(p.k(gx, 30));
                    if (kind.equals("whiteGoal")) dot(raw, gx, 30, 255);
                    if (kind.equals("thresholdGoal")) dot(raw, gx, 30, 165);
                    if (kind.equals("unknownConnector")) p.unknown.add(p.k(cx, 30));
                    if (kind.equals("carrierConnector")) p.carrier.add(p.k(cx, 30));
                    if (kind.equals("whiteConnector")) dot(raw, cx, 30, 255);
                    if (kind.equals("foreignShaft")) {
                        dot(raw, gx, 30, 255);
                        for (int y = 29; y <= 31; y++) dot(raw, cx, y, 0);
                    }
                    boolean expect =
                            kind.equals("known")
                                    || (fixed
                                            && (kind.equals("unknownGoal")
                                                    || kind.equals("carrierGoal")));
                    outcome(
                            dir + "/" + side + "/" + kind,
                            contact(raw, own, bx, 30, 30, p, 100000),
                            expect);
                }
    }

    static void allTops() throws Throwable {
        for (int dir : new int[] {-1, 1})
            for (int side : new int[] {-1, 1}) {
                int gx = 40, bx = gx + 2 * side, cx = gx + side;
                Plane p = new Plane();
                byte[] r = blank();
                for (int y = 30; y <= 40; y++) for (int x : List.of(gx, cx, bx)) dot(r, x, y, 0);
                var own =
                        shaft(
                                0,
                                gx,
                                dir,
                                List.of(new PrintedBeamGeometry.End(gx, dir < 0 ? 30 : 40)));
                for (int y = 30; y <= 40; y++) p.unknown.add(p.k(gx, y));
                outcome(
                        "all11OwnedUnknownTops/" + dir + "/" + side,
                        contact(r, own, bx, 30, 40, p, 100000),
                        fixed);
                for (int x = 0; x < W; x++)
                    dot(
                            r, x, 36,
                            255); // all earlier tops may pass; one later required top must refuse.
                outcome(
                        "lateRequiredWhiteTop/" + dir + "/" + side,
                        contact(r, own, bx, 30, 40, p, 100000),
                        false);
            }
    }

    static void seedAndEnds() throws Throwable {
        for (int dir : new int[] {-1, 1})
            for (String role : List.of("unknown", "carrier", "white")) {
                byte[] r = blank();
                dot(r, 40, 30, role.equals("white") ? 255 : 0);
                Plane p = new Plane();
                if (role.equals("unknown")) p.unknown.add(p.k(40, 30));
                else if (role.equals("carrier")) p.carrier.add(p.k(40, 30));
                outcome(
                        "ownedSeed/" + dir + "/" + role,
                        contact(
                                r,
                                shaft(0, 40, dir, List.of(new PrintedBeamGeometry.End(40, 30))),
                                40,
                                30,
                                30,
                                p,
                                100000),
                        fixed && !role.equals("white"));
            }
        for (int dir : new int[] {-1, 1}) {
            byte[] r = blank();
            for (int x = 40; x <= 43; x++) dot(r, x, 30, 0);
            Plane p = new Plane();
            p.unknown.add(p.k(41, 30));
            var own =
                    shaft(
                            0,
                            40,
                            dir,
                            List.of(
                                    new PrintedBeamGeometry.End(40, 30),
                                    new PrintedBeamGeometry.End(41, dir < 0 ? 31 : 29)));
            outcome(
                    "shortSpurUnknownNotOwned/" + dir,
                    contact(r, own, 43, 30, 30, p, 100000),
                    false);
            p.unknown.clear();
            p.unknown.add(p.k(40, 30));
            outcome("actualPerColumnEnd/" + dir, contact(r, own, 43, 30, 30, p, 100000), fixed);
        }
    }

    static void budgetsAndQueries() throws Throwable {
        byte[] r = blank();
        for (int x = 40; x <= 42; x++) dot(r, x, 30, 0);
        var own = shaft(0, 40, -1, List.of(new PrintedBeamGeometry.End(40, 30)));
        Plane p = new Plane();
        try {
            contact(r, own, 42, 30, 30, p, 1);
            throw new AssertionError("budget must fail");
        } catch (Throwable t) {
            ck(t.getClass().getSimpleName().equals("Exhausted"), "same sticky budget exception");
            cases++;
        }
        p = new Plane();
        p.throwGoal = true;
        p.goal = 40;
        boolean threw = false;
        try {
            boolean got = contact(r, own, 42, 30, 30, p, 100000);
            ck(fixed && got, "fixed endpoint bypasses only goal classification");
        } catch (IllegalStateException ex) {
            threw = true;
        }
        ck(threw != fixed, "baseline asks goal plane; fix legitimately does not");
        cases++;
        Plane fault =
                new Plane() {
                    public boolean at(
                            PrintedBeamGeometry.Column a,
                            PrintedBeamGeometry.Column b,
                            int x,
                            int y) {
                        throw new IllegalStateException("Intermediate failure");
                    }
                };
        try {
            contact(r, own, 42, 30, 30, fault, 100000);
            throw new AssertionError("intermediate fault swallowed");
        } catch (IllegalStateException expected) {
            checks++;
            cases++;
        }
        var budget = new PrintedBeamGeometry.Budget(1);
        try {
            budget.spend(2);
            throw new AssertionError();
        } catch (RuntimeException expected) {
        }
        try {
            budget.spend(0);
            throw new AssertionError();
        } catch (RuntimeException expected) {
            checks++;
            cases++;
        }
    }

    static void actualDetect() {
        for (int dir : new int[] {-1, 1})
            for (String role : List.of("known", "unknown", "carrier", "white")) {
                byte[] r = blank();
                for (int x = 20; x <= 60; x++) for (int y = 30; y <= 33; y++) dot(r, x, y, 0);
                var a =
                        new PrintedBeamGeometry.Column(
                                0,
                                0,
                                0,
                                1,
                                dir,
                                20,
                                20,
                                dir < 0 ? 30 : 33,
                                10,
                                1,
                                List.of(new PrintedBeamGeometry.End(20, dir < 0 ? 30 : 33)),
                                dir < 0 ? 90 : 0);
                var b =
                        new PrintedBeamGeometry.Column(
                                1,
                                0,
                                0,
                                1,
                                dir,
                                60,
                                60,
                                dir < 0 ? 30 : 33,
                                10,
                                1,
                                List.of(new PrintedBeamGeometry.End(60, dir < 0 ? 30 : 33)),
                                dir < 0 ? 90 : 0);
                Plane p = new Plane();
                for (int y = 30; y <= 33; y++)
                    for (int x : new int[] {20, 60}) {
                        if (role.equals("unknown")) p.unknown.add(p.k(x, y));
                        if (role.equals("carrier")) p.carrier.add(p.k(x, y));
                        if (role.equals("white")) dot(r, x, y, 255);
                    }
                var got =
                        PrintedBeamGeometry.detect(
                                r, W, H, List.of(a, b), p, new PrintedBeamGeometry.Budget(1000000));
                outcome(
                        "actualDetect/" + dir + "/" + role,
                        got.groups().size() == 1,
                        role.equals("known") || fixed && !role.equals("white"));
                ck(!got.budgetExceeded(), "ordinary unchanged budget bound");
            }
        byte[] r = blank();
        var a = shaft(0, 20, -1, List.of(new PrintedBeamGeometry.End(20, 30)));
        var b = shaft(1, 60, -1, List.of(new PrintedBeamGeometry.End(60, 30)));
        var got =
                PrintedBeamGeometry.detect(
                        r, W, H, List.of(a, b), new Plane(), new PrintedBeamGeometry.Budget(1));
        ck(
                got.budgetExceeded() && got.groups().isEmpty(),
                "public detector refusal is page atomic");
        cases++;
    }

    public static void main(String[] args) throws Throwable {
        fixed = Boolean.parseBoolean(args[0]);
        matrix();
        allTops();
        seedAndEnds();
        budgetsAndQueries();
        actualDetect();
        System.out.println("PASS cases=" + cases + " checks=" + checks + " fixed=" + fixed);
    }
}

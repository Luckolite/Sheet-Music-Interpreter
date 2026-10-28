// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;

/** Original conditional head/shaft fixtures; no historical images or recognition run. */
public final class ShaftCertificateOriginals {
    static final int W = 80, H = 160;
    static int checks, cases;

    static void ck(boolean b, String s) {
        checks++;
        if (!b) throw new AssertionError(s);
    }

    static class Charge implements PrintedBeamShaftPixels.Charge {
        long remaining;
        boolean failed;

        Charge(long n) {
            remaining = n;
        }

        public void take(long n) {
            if (failed || n < 0 || n > remaining) {
                failed = true;
                throw new IllegalStateException("metadata exhausted");
            }
            remaining -= n;
        }
    }

    static Object construct(Class<?> c, Object... args) throws Exception {
        var ct = c.getDeclaredConstructors()[0];
        ct.setAccessible(true);
        return ct.newInstance(args);
    }

    static Object binding() throws Exception {
        var c = PrintedBeamSource.class;
        var lane =
                construct(
                        Class.forName(c.getName() + "$Lane"),
                        0,
                        new PlayingTechniqueDetector.Staff(20, 60, 10, 0, 1),
                        null,
                        null,
                        new HashMap<>(),
                        new HashMap<>());
        return construct(Class.forName(c.getName() + "$Binding"), lane, 60f, 10f);
    }

    static void put(byte[] r, int x, int y, boolean up) {
        if (up) {
            x = 90 - x;
            y = 159 - y;
        }
        r[y * W + x] = 0;
    }

    static byte[] image(boolean connected, boolean up) {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        for (int y = 22; y <= 80; y++) {
            if (connected || y != 59 && y != 61) put(r, 39, y, up);
            put(r, 41, y, up);
        }
        for (int y = 22; y <= 50; y++) put(r, 40, y, up);
        for (int x = 41; x <= 45; x++) put(r, x, 20, up);
        put(r, 41, 21, up);
        put(r, 37, 60, up);
        put(r, 38, 60, up);
        return r;
    }

    record Fixture(
            byte[] raw,
            boolean up,
            PrintedBeamShaftPixels.Frame frame,
            PrintedBeamShafts.Shaft shaft,
            Object binding,
            PrintedBeamShaftPixels.Certificate cert,
            int[] root,
            Charge charge) {}

    static Fixture fixture(boolean connected, boolean up) throws Exception {
        return fixture(connected, up, false);
    }

    static Fixture fixture(boolean connected, boolean up, boolean allTops) throws Exception {
        byte[] r = image(connected, up);
        if (allTops)
            for (int y = 55; y <= 65; y++) {
                put(r, 37, y, up);
                put(r, 38, y, up);
            }
        Charge charge = new Charge(128000000);
        var frame = new PrintedBeamShaftPixels.Frame(new Object(), r, W, H, 0, charge);
        var budget = new PrintedBeamShafts.Budget(8000000);
        var all = PrintedBeamShafts.at(r, W, H, 45, up ? 139 : 20, 10, budget, frame);
        var s = all.stream().filter(x -> x.direction() == (up ? -1 : 1)).findFirst().orElseThrow();
        var ordinary =
                PrintedBeamShafts.at(
                                r,
                                W,
                                H,
                                45,
                                up ? 139 : 20,
                                10,
                                new PrintedBeamShafts.Budget(8000000))
                        .stream()
                        .filter(x -> x.direction() == s.direction())
                        .findFirst()
                        .orElseThrow();
        ck(
                s.left() == ordinary.left()
                        && s.right() == ordinary.right()
                        && s.startY() == ordinary.startY()
                        && s.endY() == ordinary.endY()
                        && s.columns().equals(ordinary.columns()),
                "certificate scanner shape parity");
        Object binding = binding();
        Class<?> bc = binding.getClass();
        var meth =
                PrintedBeamSource.class.getDeclaredMethod(
                        "headContact",
                        byte[].class,
                        int.class,
                        int.class,
                        PrintedSlurOwners.Location.class,
                        PrintedBeamShafts.Shaft.class,
                        float.class,
                        PrintedBeamSource.Work.class,
                        bc,
                        int[].class);
        meth.setAccessible(true);
        var old =
                PrintedBeamSource.class.getDeclaredMethod(
                        "headContact",
                        byte[].class,
                        int.class,
                        int.class,
                        PrintedSlurOwners.Location.class,
                        PrintedBeamShafts.Shaft.class,
                        float.class,
                        PrintedBeamSource.Work.class,
                        bc);
        old.setAccessible(true);
        var head = new PrintedSlurOwners.GeometryHead(0, 45, up ? 139 : 20, 0, 1);
        int[] root = {-1, -1};
        var w = new PrintedBeamSource.Work(128000000);
        var w0 = new PrintedBeamSource.Work(128000000);
        boolean result = (boolean) meth.invoke(null, r, W, H, head, s, 10f, w, binding, root);
        boolean prior = (boolean) old.invoke(null, r, W, H, head, s, 10f, w0, binding);
        ck(
                result && prior && w.remaining == w0.remaining,
                "actual headContact point capture preserves boolean/work");
        ck((r[root[1] * W + root[0]] & 255) < 165, "actual root literal");
        var cert =
                PrintedBeamShaftPixels.certify(
                        frame, new Object(), binding, 0, s, root[0], root[1]);
        return new Fixture(r, up, frame, s, binding, cert, root, charge);
    }

    static class Plane implements PrintedBeamGeometry.CarrierPlane {
        int gx, gy, cx;
        String role;
        List<String> calls = new ArrayList<>();

        Plane(int x, int y, int c, String r) {
            gx = x;
            gy = y;
            cx = c;
            role = r;
        }

        public boolean at(
                PrintedBeamGeometry.Column a, PrintedBeamGeometry.Column b, int x, int y) {
            boolean v =
                    role.equals("goalCarrier") && x == gx
                            || role.equals("connectorCarrier") && x == cx;
            calls.add("AT:" + x + ":" + y + ":" + v);
            return v;
        }

        public boolean unknown(
                PrintedBeamGeometry.Column a, PrintedBeamGeometry.Column b, int x, int y) {
            boolean v =
                    role.equals("goalUnknown") && x == gx
                            || role.equals("connectorUnknown") && x == cx;
            calls.add("U:" + x + ":" + y + ":" + v);
            return v;
        }
    }

    static PrintedBeamGeometry.Column column(Fixture f, boolean cert) {
        var s = f.shaft;
        return new PrintedBeamGeometry.Column(
                0,
                0,
                0,
                1,
                s.direction(),
                s.left(),
                s.right(),
                s.endY(),
                10,
                2,
                s.columns().stream()
                        .map(c -> new PrintedBeamGeometry.End(c.x(), c.endY()))
                        .toList(),
                s.startY(),
                cert ? new PrintedBeamShaftPixels.Targets(f.frame, 0, List.of(f.cert)) : null);
    }

    static boolean contact(
            Fixture f,
            PrintedBeamGeometry.Column s,
            int boundary,
            int low,
            int high,
            Plane plane,
            long limit)
            throws Throwable {
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
        var ec = edge.getDeclaredConstructor(int.class, int.class);
        ec.setAccessible(true);
        Object
                detector =
                        dc.newInstance(
                                f.raw,
                                W,
                                H,
                                List.of(s, s),
                                plane,
                                new PrintedBeamGeometry.Budget(limit),
                                null),
                e = ec.newInstance(0, 1);
        var interval = new FiniteRawBeamStrip.Interval(low * 1024, high * 1024);
        var proof = new FiniteRawBeamStrip.Proof(1, List.of(interval), List.of(interval), null);
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
        try {
            return (boolean) m.invoke(detector, e, s, boundary, proof, 0, false);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static void matrix() throws Throwable {
        for (boolean up : new boolean[] {false, true})
            for (boolean connected : new boolean[] {false, true}) {
                var f = fixture(connected, up);
                int gx = up ? 51 : 39, gy = up ? 99 : 60, cx = up ? 52 : 38, bx = up ? 53 : 37;
                ck(
                        f.cert.has(gx, gy) == connected,
                        "disconnected island excluded from certificate");
                cases++;
                for (String role :
                        List.of(
                                "known",
                                "goalUnknown",
                                "goalCarrier",
                                "connectorUnknown",
                                "connectorCarrier")) {
                    var legacy = new Plane(gx, gy, cx, role);
                    var plane = new Plane(gx, gy, cx, role);
                    boolean old = contact(f, column(f, false), bx, gy, gy, legacy, 100000),
                            now = contact(f, column(f, true), bx, gy, gy, plane, 100000);
                    boolean expect =
                            role.equals("known")
                                    || connected
                                            && (role.equals("goalUnknown")
                                                    || role.equals("goalCarrier"));
                    ck(now == expect, "contact/" + up + "/" + connected + "/" + role);
                    if (role.equals("known") || role.startsWith("connector"))
                        ck(
                                old == now && legacy.calls.equals(plane.calls),
                                "known/intermediate exact old query sequence");
                    else if (!connected)
                        ck(
                                !old && !now && legacy.calls.equals(plane.calls),
                                "uncertified island old query path");
                    cases++;
                }
                byte[] saved = f.raw.clone();
                for (int y = gy - 5; y <= gy + 5; y++) f.raw[y * W + gx] = (byte) 255;
                ck(
                        !contact(
                                f,
                                column(f, true),
                                bx,
                                gy,
                                gy,
                                new Plane(gx, gy, cx, "goalUnknown"),
                                100000),
                        "white target cannot use stored certificate");
                System.arraycopy(saved, 0, f.raw, 0, saved.length);
                cases++;
                var s = column(f, true);
                try {
                    contact(f, s, bx, gy, gy, new Plane(gx, gy, cx, "goalUnknown"), 1);
                    throw new AssertionError("budget");
                } catch (Throwable e) {
                    ck(
                            e.getClass().getSimpleName().equals("Exhausted"),
                            "geometry budget still fatal");
                }
                cases++;
            }
    }

    static void identityAndBounds() throws Throwable {
        var f = fixture(true, false);
        var targets = new PrintedBeamShaftPixels.Targets(f.frame, 0, List.of(f.cert));
        try {
            targets.has(f.raw.clone(), W, H, 0, 39, 60);
            throw new AssertionError();
        } catch (PrintedBeamShaftPixels.IdentityFault ok) {
            checks++;
        }
        cases++;
        try {
            targets.has(f.raw, W, H, 1, 39, 60);
            throw new AssertionError();
        } catch (PrintedBeamShaftPixels.IdentityFault ok) {
            checks++;
        }
        cases++;
        try {
            PrintedBeamShaftPixels.certify(f.frame, new Object(), f.binding, 0, f.shaft, 40, 60);
            throw new AssertionError();
        } catch (PrintedBeamShaftPixels.IdentityFault ok) {
            checks++;
        }
        cases++;
        var other =
                new PrintedBeamShaftPixels.Frame(
                        new Object(), f.raw, W, H, 0, new Charge(128000000));
        try {
            new PrintedBeamShaftPixels.Targets(other, 0, List.of(f.cert));
            throw new AssertionError();
        } catch (PrintedBeamShaftPixels.IdentityFault ok) {
            checks++;
        }
        cases++;
        long prior = f.charge.remaining;
        f.charge.remaining = 0;
        try {
            PrintedBeamShaftPixels.certify(
                    f.frame, new Object(), f.binding, 0, f.shaft, f.root[0], f.root[1]);
            throw new AssertionError();
        } catch (IllegalStateException ok) {
            ck(f.charge.failed, "metadata refuses before flood allocation");
        }
        try {
            targets.has(f.raw, W, H, 0, 39, 60);
            throw new AssertionError();
        } catch (IllegalStateException ok) {
            checks++;
        }
        cases++;
        // A failed scanner allocation is fatal even before a candidate can be returned.
        Charge zero = new Charge(64);
        var frame = new PrintedBeamShaftPixels.Frame(new Object(), f.raw, W, H, 0, zero);
        try {
            PrintedBeamShafts.at(
                    f.raw, W, H, 45, 20, 10, new PrintedBeamShafts.Budget(8000000), frame);
            throw new AssertionError();
        } catch (IllegalStateException ok) {
            ck(zero.failed, "scanner metadata reserve before rows");
        }
        cases++;
    }

    static void mergeAndAllTops() throws Throwable {
        for (boolean up : new boolean[] {false, true}) {
            Fixture f = fixture(true, up, true);
            int gx = up ? 51 : 39, gy = up ? 99 : 60, cx = up ? 52 : 38, bx = up ? 53 : 37;
            ck(
                    contact(
                            f,
                            column(f, true),
                            bx,
                            gy - 5,
                            gy + 5,
                            new Plane(gx, gy, cx, "goalUnknown"),
                            1000000),
                    "all11 actual certified top paths required");
            cases++;
            byte prior = f.raw[gy * W + bx];
            f.raw[gy * W + bx] = (byte) 255;
            ck(
                    !contact(
                            f,
                            column(f, true),
                            bx,
                            gy - 5,
                            gy + 5,
                            new Plane(gx, gy, cx, "goalUnknown"),
                            1000000),
                    "later required top white seed refuses");
            f.raw[gy * W + bx] = prior;
            cases++;
        }
        byte[] r = image(false, false);
        for (int y = 7; y < 22; y++) for (int x = 39; x <= 41; x++) put(r, x, y, false);
        for (int x = 41; x <= 45; x++) put(r, x, 5, false);
        put(r, 41, 6, false);
        Charge charge = new Charge(128000000);
        var frame = new PrintedBeamShaftPixels.Frame(new Object(), r, W, H, 0, charge);
        var a =
                PrintedBeamShafts.at(
                                r, W, H, 45, 20, 10, new PrintedBeamShafts.Budget(8000000), frame)
                        .stream()
                        .filter(x -> x.direction() == 1)
                        .findFirst()
                        .orElseThrow();
        var b =
                PrintedBeamShafts.at(
                                r, W, H, 45, 5, 10, new PrintedBeamShafts.Budget(8000000), frame)
                        .stream()
                        .filter(x -> x.direction() == 1)
                        .findFirst()
                        .orElseThrow();
        Object binding = binding();
        var meth =
                PrintedBeamSource.class.getDeclaredMethod(
                        "headContact",
                        byte[].class,
                        int.class,
                        int.class,
                        PrintedSlurOwners.Location.class,
                        PrintedBeamShafts.Shaft.class,
                        float.class,
                        PrintedBeamSource.Work.class,
                        binding.getClass(),
                        int[].class);
        meth.setAccessible(true);
        List<PrintedBeamShaftPixels.Certificate> certs = new ArrayList<>();
        int owner = 0;
        for (var sh : List.of(a, b)) {
            int[] point = {-1, -1};
            var head = new PrintedSlurOwners.GeometryHead(owner, 45, owner == 0 ? 20 : 5, 0, 1);
            ck(
                    (boolean)
                            meth.invoke(
                                    null,
                                    r,
                                    W,
                                    H,
                                    head,
                                    sh,
                                    10f,
                                    new PrintedBeamSource.Work(128000000),
                                    binding,
                                    point),
                    "actual accepted headContact per merged original");
            certs.add(
                    PrintedBeamShaftPixels.certify(
                            frame, new Object(), binding, owner++, sh, point[0], point[1]));
        }
        var targets = new PrintedBeamShaftPixels.Targets(frame, 0, certs);
        ck(
                !targets.has(r, W, H, 0, 39, 60),
                "union original certificates cannot certify merged interval island");
        ck(targets.has(r, W, H, 0, 41, 60), "true shared backbone retained");
        cases++;
        certs.clear();
        ck(targets.has(r, W, H, 0, 41, 60), "target list immutable copy");
        cases++;
    }

    public static void main(String[] args) throws Throwable {
        matrix();
        identityAndBounds();
        mergeAndAllTops();
        OwnedEndpointOriginals.main(new String[] {"false"});
        System.out.println(
                "PASS certificateCases=" + cases + " checks=" + checks + " plusLegacy71");
    }
}

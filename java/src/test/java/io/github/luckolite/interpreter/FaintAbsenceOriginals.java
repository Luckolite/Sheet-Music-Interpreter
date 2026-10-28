// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;

/** Original possibility-envelope controls. Unknown pixels never admit a beam or flag. */
public class FaintAbsenceOriginals {
    static int checks;

    static void check(boolean b, String m) {
        checks++;
        if (!b) throw new AssertionError(m);
    }

    static final int W = 180, H = 180;

    static byte[] page(int shade) {
        byte[] a = new byte[W * H];
        Arrays.fill(a, (byte) shade);
        return a;
    }

    static void rect(byte[] a, int l, int r, int t, int b, int shade) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) a[y * W + x] = (byte) shade;
    }

    static PrintedBeamGeometry.Column column(int id, int x) {
        return new PrintedBeamGeometry.Column(
                id, 0, 0, 1, -1, x, x, 45, 16, 2, List.of(new PrintedBeamGeometry.End(x, 45)), 130);
    }

    static FiniteRawBeamStrip.Carrier carrier(boolean enabled) {
        return new FiniteRawBeamStrip.Carrier() {
            public boolean at(int x, int y) {
                return false;
            }

            public boolean contrastEnabled() {
                return enabled;
            }
        };
    }

    public static void main(String[] args) throws Exception {
        byte[] a = page(255);
        rect(a, 50, 130, 45, 50, 200);
        rect(a, 50, 50, 45, 130, 100);
        rect(a, 130, 130, 45, 130, 100);
        var left = column(0, 50);
        var right = column(1, 130);
        var old =
                new RawPrimaryBeamAbsence(
                        a, W, H, carrier(false), new PrintedIsolatedFlags.Budget(64_000_000));
        var wider =
                new RawPrimaryBeamAbsence(
                        a, W, H, carrier(true), new PrintedIsolatedFlags.Budget(64_000_000));
        check(
                old.possible(left, 1, 80, right) == RawPrimaryBeamAbsence.State.IMPOSSIBLE,
                "legacy threshold domain unchanged");
        check(
                wider.possible(left, 1, 80, right) == RawPrimaryBeamAbsence.State.POSSIBLE,
                "faint primary remains possible, never declared absent");
        check(
                wider.prove(left, List.of(left, right)).isEmpty(),
                "isolated flag has no false primary-absence certificate");
        Class<?> type = Class.forName(PrintedBeamGeometry.class.getName() + "$Detector");
        // These controls exercise legacy geometry without optional Source evidence.
        var ctor =
                type.getDeclaredConstructor(
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        PrintedBeamGeometry.CarrierPlane.class,
                        PrintedBeamGeometry.Budget.class,
                        PrintedBeamGeometry.PrimaryObserver.class);
        ctor.setAccessible(true);
        var method =
                type.getDeclaredMethod(
                        "faintPossibility",
                        int.class,
                        double.class,
                        int.class,
                        double.class,
                        int.class);
        method.setAccessible(true);
        var plane =
                new PrintedBeamGeometry.CarrierPlane() {
                    public boolean at(
                            PrintedBeamGeometry.Column x,
                            PrintedBeamGeometry.Column y,
                            int xx,
                            int yy) {
                        return false;
                    }

                    public boolean contrastEnabled() {
                        return true;
                    }
                };
        byte[] unknown = page(245);
        rect(unknown, 70, 90, 43, 49, 225);
        check(
                !PrintedBeamRawContrast.faint(unknown, W, H, 80, 45, 16, false, n -> {}),
                "weak tail is not certain foreground");
        var budget = new PrintedBeamGeometry.Budget(1_000_000);
        var detector = ctor.newInstance(unknown, W, H, List.of(), plane, budget, null);
        check(
                (boolean) method.invoke(detector, 80, 45d, 3, 16d, -1),
                "uncertain faint tail vetoes a false hook");
        var white =
                ctor.newInstance(
                        page(255),
                        W,
                        H,
                        List.of(),
                        plane,
                        new PrintedBeamGeometry.Budget(1_000_000),
                        null);
        check(
                !(boolean) method.invoke(white, 80, 45d, 3, 16d, -1),
                "clear paper does not invent possible ink");
        var open =
                ctor.newInstance(
                        page(225),
                        W,
                        H,
                        List.of(),
                        plane,
                        new PrintedBeamGeometry.Budget(1_000_000),
                        null);
        check(
                (boolean) method.invoke(open, 80, 45d, 3, 16d, -1),
                "open uncertain run also blocks claimed absence");
        long total = budget.used();
        var exact = new PrintedBeamGeometry.Budget(total);
        var d = ctor.newInstance(unknown, W, H, List.of(), plane, exact, null);
        check(
                (boolean) method.invoke(d, 80, 45d, 3, 16d, -1) && exact.used() == total,
                "exact veto prepayment");
        var under = new PrintedBeamGeometry.Budget(total - 1);
        d = ctor.newInstance(unknown, W, H, List.of(), plane, under, null);
        try {
            method.invoke(d, 80, 45d, 3, 16d, -1);
            throw new AssertionError();
        } catch (InvocationTargetException expected) {
            check(under.used() == 32, "one-under veto charge is atomic before scanning");
        }
        System.out.println(
                "PASS " + checks + " original non-admitting faint possibility/absence controls");
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Original support-containment and whole-profile selection controls; not score fixtures. */
public class StrongCoreOriginals {
    static int checks;
    static final int W = 180, H = 180;

    static void check(boolean b, String m) {
        checks++;
        if (!b) throw new AssertionError(m);
    }

    static byte[] page() {
        byte[] a = new byte[W * H];
        Arrays.fill(a, (byte) 255);
        return a;
    }

    static void rect(byte[] a, int l, int r, int t, int b, int shade) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) a[y * W + x] = (byte) shade;
    }

    static PrintedBeamGeometry.Column column(int id) {
        return new PrintedBeamGeometry.Column(
                id,
                0,
                0,
                1,
                -1,
                30 + id * 20,
                30 + id * 20,
                45,
                16,
                2,
                List.of(new PrintedBeamGeometry.End(30 + id * 20, 45)),
                130);
    }

    public static void main(String[] args) throws Exception {
        byte[] a = page();
        rect(a, 100, 101, 60, 175, 100);
        rect(a, 100, 101, 45, 59, 200);
        rect(a, 116, 116, 45, 128, 100);
        Object source = new Object();
        var frame = new PrintedBeamShaftPixels.Frame(source, a, W, H, 0, n -> {}, true);
        var core =
                PrintedBeamShafts.atStrict(
                                a,
                                W,
                                H,
                                90,
                                130,
                                16,
                                new PrintedBeamShafts.Budget(8_000_000),
                                frame)
                        .get(0);
        var envelope =
                PrintedBeamShafts.atAll(
                                a,
                                W,
                                H,
                                90,
                                130,
                                16,
                                new PrintedBeamShafts.Budget(8_000_000),
                                frame)
                        .get(0);
        check(
                !core.pixels().contrast && envelope.pixels().contrast,
                "mode belongs to actual Scan under ONE frame");
        check(
                core.endY() == 60 && envelope.endY() == 45,
                "strict core and faint envelope retain distinct observed caps");
        check(
                PrintedBeamShaftPixels.covers(envelope, core),
                "actual same-frame saved-bit containment");
        check(
                !PrintedBeamShaftPixels.covers(core, envelope),
                "containment is not symmetric box equality");
        var otherFrame = new PrintedBeamShaftPixels.Frame(source, a, W, H, 0, n -> {}, true);
        var other =
                PrintedBeamShafts.atAll(
                                a,
                                W,
                                H,
                                90,
                                130,
                                16,
                                new PrintedBeamShafts.Budget(8_000_000),
                                otherFrame)
                        .get(0);
        check(
                !PrintedBeamShaftPixels.covers(other, core),
                "equal source/raw values cannot alias distinct frame epochs");
        var far =
                PrintedBeamShafts.atAll(
                                a,
                                W,
                                H,
                                106,
                                130,
                                16,
                                new PrintedBeamShafts.Budget(8_000_000),
                                frame)
                        .stream()
                        .filter(s -> s.left() == 116)
                        .findFirst()
                        .orElseThrow();
        check(
                !PrintedBeamShaftPixels.covers(far, core),
                "nearby disjoint physical support is not an envelope");
        var down =
                PrintedBeamShafts.atAll(
                                a,
                                W,
                                H,
                                110,
                                130,
                                16,
                                new PrintedBeamShafts.Budget(8_000_000),
                                frame)
                        .stream()
                        .filter(s -> s.direction() > 0)
                        .findFirst()
                        .orElseThrow();
        check(!PrintedBeamShaftPixels.covers(down, core), "opposite shaft directions never merge");
        byte[] rivals = page();
        rect(rivals, 99, 99, 45, 128, 200);
        rect(rivals, 103, 103, 45, 128, 200);
        var rf = new PrintedBeamShaftPixels.Frame(new Object(), rivals, W, H, 0, n -> {}, true);
        check(
                PrintedBeamShafts.at(
                                rivals,
                                W,
                                H,
                                90,
                                130,
                                16,
                                new PrintedBeamShafts.Budget(8_000_000),
                                rf)
                        .isEmpty(),
                "legacy disjoint-run API unchanged");
        check(
                PrintedBeamShafts.atAll(
                                        rivals,
                                        W,
                                        H,
                                        90,
                                        130,
                                        16,
                                        new PrintedBeamShafts.Budget(8_000_000),
                                        rf)
                                .size()
                        == 2,
                "Source sees both actual qualifying rivals before head ownership");

        var method =
                PrintedBeamGeometry.class.getDeclaredMethod(
                        "preserveStrictProfiles",
                        PrintedBeamGeometry.Result.class,
                        PrintedBeamGeometry.Result.class,
                        List.class,
                        PrintedBeamGeometry.CarrierPlane.class,
                        PrintedBeamGeometry.Budget.class);
        method.setAccessible(true);
        var old = new PrintedBeamGeometry.Group(List.of(0, 1, 2), List.of(3, 3), List.of());
        var lower = new PrintedBeamGeometry.Group(List.of(0, 1, 2), List.of(1, 3), List.of());
        var extended =
                new PrintedBeamGeometry.Group(List.of(0, 1, 2, 3), List.of(3, 3, 1), List.of());
        var columns = List.of(column(0), column(1), column(2), column(3));
        long[] paid = {0};
        var plane =
                new PrintedBeamGeometry.CarrierPlane() {
                    public boolean at(
                            PrintedBeamGeometry.Column x,
                            PrintedBeamGeometry.Column y,
                            int xx,
                            int yy) {
                        return false;
                    }

                    public void chargeContrast(long n) {
                        paid[0] += n;
                    }
                };
        var budget = new PrintedBeamGeometry.Budget(1_000_000);
        var strict = new PrintedBeamGeometry.Result(List.of(old), List.of(3), false, 0);
        var result =
                (PrintedBeamGeometry.Result)
                        method.invoke(
                                null,
                                strict,
                                new PrintedBeamGeometry.Result(
                                        List.of(lower), List.of(3), false, 0),
                                columns,
                                plane,
                                budget);
        check(
                result.groups().size() == 1 && result.groups().get(0) == old,
                "lower secondary cannot replace entire strict proof");
        check(result.groups().get(0).bridges().equals(List.of(3, 3)), "no mask splicing");
        check(paid[0] == budget.used(), "selection payment conserved across BOTH existing pools");
        long exact = budget.used();
        budget = new PrintedBeamGeometry.Budget(1_000_000);
        result =
                (PrintedBeamGeometry.Result)
                        method.invoke(
                                null,
                                strict,
                                new PrintedBeamGeometry.Result(
                                        List.of(extended), List.of(), false, 0),
                                columns,
                                plane,
                                budget);
        check(
                result.groups().size() == 1 && result.groups().get(0) == extended,
                "whole compatible extension retains all strict facts");
        check(
                result.unlinkedColumns().isEmpty(),
                "actual selected column membership recomputes unlinked context");
        var eq = new PrintedBeamGeometry.Budget(exact);
        result =
                (PrintedBeamGeometry.Result)
                        method.invoke(
                                null,
                                strict,
                                new PrintedBeamGeometry.Result(
                                        List.of(lower), List.of(3), false, 0),
                                columns,
                                plane,
                                eq);
        check(eq.used() == exact && result.groups().get(0) == old, "exact selector quota");
        var under = new PrintedBeamGeometry.Budget(exact - 1);
        try {
            method.invoke(
                    null,
                    strict,
                    new PrintedBeamGeometry.Result(List.of(lower), List.of(3), false, 0),
                    columns,
                    plane,
                    under);
            throw new AssertionError();
        } catch (InvocationTargetException expected) {
            check(under.used() < exact, "one-under returns no favorable partial profile");
        }
        System.out.println(
                "PASS "
                        + checks
                        + " original strong-core/epoch/rival/whole-profile/quota controls");
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original procedural pixels only; no commercial score fragments. */
public final class RawContrastOriginals {
    static int checks;
    static final int W = 180, H = 180;

    static void check(boolean b, String message) {
        checks++;
        if (!b) throw new AssertionError(message);
    }

    static byte[] page(int paper) {
        byte[] a = new byte[W * H];
        Arrays.fill(a, (byte) paper);
        return a;
    }

    static void rect(byte[] a, int l, int r, int t, int b, int ink) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) a[y * W + x] = (byte) ink;
    }

    static boolean faint(byte[] a, int x, int y, boolean shaft) {
        return PrintedBeamRawContrast.faint(a, W, H, x, y, 16, shaft, n -> {});
    }

    static PrintedBeamShaftPixels.Frame frame(byte[] a, boolean enabled) {
        return new PrintedBeamShaftPixels.Frame(new Object(), a, W, H, 0, n -> {}, enabled);
    }

    public static void main(String[] args) {
        for (int ink : new int[] {172, 190, 212, 225}) {
            byte[] a = page(255);
            rect(a, 99, 100, 45, 128, ink);
            check(faint(a, 99, 70, true), "bounded faint shaft");
            check(
                    PrintedBeamShafts.at(
                                    a, W, H, 90, 130, 16, new PrintedBeamShafts.Budget(8_000_000))
                            .isEmpty(),
                    "legacy threshold exact");
            var shafts =
                    PrintedBeamShafts.at(
                            a,
                            W,
                            H,
                            90,
                            130,
                            16,
                            new PrintedBeamShafts.Budget(8_000_000),
                            frame(a, true));
            check(shafts.size() == 1, "full connected faint shaft");
            check(shafts.get(0).pixels().has(99, 70), "actual literal scanner bit retained");
            byte[] before = a.clone();
            faint(a, 99, 70, true);
            check(Arrays.equals(a, before), "raw unchanged");
            rect(a, 99, 100, 80, 81, 255);
            check(
                    PrintedBeamShafts.at(
                                    a,
                                    W,
                                    H,
                                    90,
                                    130,
                                    16,
                                    new PrintedBeamShafts.Budget(8_000_000),
                                    frame(a, true))
                            .stream()
                            .allMatch(sh -> sh.endY() >= 82),
                    "two white rows are not filled");
        }
        byte[] a = page(245);
        rect(a, 99, 100, 45, 126, 225);
        check(!faint(a, 99, 70, true), "insufficient local depth refuses");
        a = page(255);
        rect(a, 87, 112, 45, 126, 200);
        check(!faint(a, 99, 70, true), "broad ink is not a shaft");
        a = page(255);
        rect(a, 99, 99, 45, 128, 200);
        rect(a, 103, 103, 45, 128, 200);
        check(faint(a, 99, 70, true), "sampler does not pretend to decide ownership");
        check(
                PrintedBeamShafts.at(
                                a,
                                W,
                                H,
                                90,
                                130,
                                16,
                                new PrintedBeamShafts.Budget(8_000_000),
                                frame(a, true))
                        .isEmpty(),
                "actual separated competing shaft columns refuse");
        a = page(255);
        rect(a, 0, W - 1, 70, 71, 200);
        check(!faint(a, 99, 70, true), "staff cannot become a faint shaft");
        check(faint(a, 99, 70, false), "horizontal physical ink can be sampled but is not owned");
        a = page(255);
        rect(a, 0, 1, 45, 126, 200);
        check(!faint(a, 0, 70, true), "source boundary requires both real paper shoulders");
        a = page(255);
        rect(a, 99, 100, 45, 128, 200);
        var low = new PrintedBeamShafts.Budget(0);
        check(
                PrintedBeamShafts.at(a, W, H, 90, 130, 16, low, frame(a, true)).isEmpty()
                        && low.refused(),
                "quota refusal never returns partial rival set");
        final byte[] q = a;
        try {
            PrintedBeamRawContrast.faint(
                    q,
                    W,
                    H,
                    99,
                    70,
                    16,
                    true,
                    n -> {
                        throw new IllegalStateException("quota");
                    });
            throw new AssertionError();
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("quota"), "prepayment propagates");
        }
        try {
            frame(a, true).check(a.clone(), W, H);
            throw new AssertionError();
        } catch (PrintedBeamShaftPixels.IdentityFault expected) {
            check(true, "different equal raster has no certificate authority");
        }
        byte[] beam = page(255);
        rect(beam, 40, 140, 45, 49, 200);
        var carrier =
                new FiniteRawBeamStrip.Carrier() {
                    public boolean at(int x, int y) {
                        return false;
                    }

                    public boolean contrastEnabled() {
                        return true;
                    }
                };
        check(
                FiniteRawBeamStrip.prove(beam, carrier, W, H, 50, 130, 45, 45, 16, -1, 0, 0)
                        != null,
                "literal faint finite strip");
        var unknown =
                new FiniteRawBeamStrip.Carrier() {
                    public boolean at(int x, int y) {
                        return false;
                    }

                    public boolean unknown(int x, int y) {
                        return true;
                    }

                    public boolean contrastEnabled() {
                        return true;
                    }
                };
        check(
                FiniteRawBeamStrip.prove(beam, unknown, W, H, 50, 130, 45, 45, 16, -1, 0, 0)
                        == null,
                "unknown staff never supplies non-carrier beam core");
        rect(beam, 90, 90, 45, 49, 255);
        check(
                FiniteRawBeamStrip.prove(beam, carrier, W, H, 50, 130, 45, 45, 16, -1, 0, 0)
                        == null,
                "disconnected finite beam refuses");
        check(
                FiniteRawBeamStrip.prove(page(255), carrier, W, H, 50, 130, 45, 45, 16, -1, 0, 0)
                        == null,
                "enabling contrast cannot declare white foreground");
        byte[] cross = page(255);
        rect(cross, 99, 100, 45, 128, 200);
        rect(cross, 0, W - 1, 80, 80, 200);
        check(!faint(cross, 99, 80, true), "crossing has no independent normal shoulders");
        check(
                PrintedBeamRawContrast.crossing(cross, W, H, 99, 80, 16, n -> {}),
                "both literal approaches prove short ink crossing");
        var crossed =
                PrintedBeamShafts.at(
                        cross,
                        W,
                        H,
                        90,
                        130,
                        16,
                        new PrintedBeamShafts.Budget(8_000_000),
                        frame(cross, true));
        check(
                crossed.size() == 1 && crossed.get(0).pixels().has(99, 80),
                "crossing is saved literal ink, not interpolated ownership");
        cross[80 * W + 99] = (byte) 255;
        check(
                !PrintedBeamRawContrast.crossing(cross, W, H, 99, 80, 16, n -> {}),
                "white center never filled");
        cross[80 * W + 99] = (byte) 200;
        cross[79 * W + 99] = (byte) 255;
        check(
                !PrintedBeamRawContrast.crossing(cross, W, H, 99, 80, 16, n -> {}),
                "one-sided approach refuses");
        cross = page(255);
        rect(cross, 99, 100, 45, 128, 200);
        rect(cross, 0, W - 1, 77, 82, 200);
        check(
                !PrintedBeamRawContrast.crossing(cross, W, H, 99, 80, 16, n -> {}),
                "broad opaque crossing refuses");
        cross = page(255);
        rect(cross, 0, W - 1, 80, 80, 200);
        check(
                !PrintedBeamRawContrast.crossing(cross, W, H, 99, 80, 16, n -> {}),
                "staff-only cannot manufacture approaches");
        cross = page(225);
        rect(cross, 99, 100, 45, 128, 100);
        rect(cross, 99, 100, 80, 80, 225);
        check(
                !PrintedBeamRawContrast.crossing(cross, W, H, 99, 80, 16, n -> {}),
                "dim paper between dark approaches is not ink");
        cross = page(225);
        rect(cross, 99, 100, 45, 128, 100);
        rect(cross, 0, W - 1, 80, 80, 190);
        rect(cross, 99, 100, 80, 80, 225);
        check(
                !PrintedBeamRawContrast.crossing(cross, W, H, 99, 80, 16, n -> {}),
                "proved flanks do not fill a lighter center gap");
        for (int gap : new int[] {3, 4, 8, 16, 32, 64}) {
            cross = page(255);
            rect(cross, 80, 80, 20, 160, 200);
            rect(cross, 0, W - 1, 80, 80, 200);
            check(
                    PrintedBeamRawContrast.crossing(cross, W, H, 80, 80, gap, n -> {}),
                    "bounded crossing across supported gap domain " + gap);
        }
        byte[] neck = page(255);
        rect(neck, 88, 108, 110, 115, 190);
        long[] cost = {0};
        check(
                PrintedBeamRawContrast.neck(neck, W, H, 99, 112, 16, n -> cost[0] += n),
                "bounded literal broad neck");
        long exact = cost[0];
        long[] remaining = {exact};
        check(
                PrintedBeamRawContrast.neck(
                                neck,
                                W,
                                H,
                                99,
                                112,
                                16,
                                n -> {
                                    if (n > remaining[0]) throw new IllegalStateException();
                                    remaining[0] -= n;
                                })
                        && remaining[0] == 0,
                "exact neck reserve");
        remaining[0] = exact - 1;
        try {
            PrintedBeamRawContrast.neck(
                    neck,
                    W,
                    H,
                    99,
                    112,
                    16,
                    n -> {
                        if (n > remaining[0]) throw new IllegalStateException();
                        remaining[0] -= n;
                    });
            throw new AssertionError();
        } catch (IllegalStateException expected) {
            check(remaining[0] == exact - 1, "one-under is atomic before neck reads");
        }
        System.out.println(
                "PASS " + checks + " original faint/legacy/physical/rival/boundary/quota controls");
    }
}

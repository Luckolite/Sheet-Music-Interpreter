// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Full flag boundary: mirrored arms plus distant/mixed/cross-staff unknown primaries. */
public final class FlagPrimaryJunctionOriginals {
    static final int W = 640, H = 500;
    static int assertions;

    static void box(byte[] r, int l, int t, int rr, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= rr; x++) r[y * W + x] = 0;
    }

    public static void main(String[] args) {
        for (int direction : new int[] {-1, 1})
            for (int side : new int[] {-1, 1})
                for (boolean crossLane : new boolean[] {false, true})
                    for (int kind = 0; kind < 4; kind++) {
                        byte[] raw = new byte[W * H];
                        Arrays.fill(raw, (byte) 255);
                        int origin = 300,
                                cap = 230,
                                distance = kind == 2 ? 40 : 100,
                                rx = origin + side * distance,
                                offset = kind == 2 ? 3 : 0;
                        double gap = 24, other = kind == 3 ? 24 : 4;
                        int thickness = Math.max(2, (int) Math.ceil((gap + other) * .5 * .18));
                        box(raw, origin, cap, origin, 430);
                        box(raw, rx, cap, rx, 430);
                        int[] times = {0, 7, 18, 28, 40, 48},
                                left = {0, 0, 10, 20, 20, 16},
                                right = {6, 16, 28, 30, 24, 18};
                        for (int i = 0; i < times.length - 1; i++)
                            for (int y = times[i]; y <= times[i + 1]; y++) {
                                double u = (y - times[i]) / (double) (times[i + 1] - times[i]);
                                box(
                                        raw,
                                        origin
                                                + (int)
                                                        Math.round(
                                                                left[i]
                                                                        + u
                                                                                * (left[i + 1]
                                                                                        - left[i])),
                                        cap + y,
                                        origin
                                                + (int)
                                                        Math.round(
                                                                right[i]
                                                                        + u
                                                                                * (right[i + 1]
                                                                                        - right[
                                                                                                i])),
                                        cap + y);
                            }
                        if (kind != 0)
                            box(
                                    raw,
                                    Math.min(rx, origin),
                                    cap + offset,
                                    Math.max(rx, origin),
                                    cap + offset + thickness - 1);
                        for (int y = 419; y <= 441; y++)
                            for (int x = 272; x <= 306; x++)
                                if (Math.pow((x - 289) / 17.0, 2) + Math.pow((y - 430) / 11.0, 2)
                                        <= 1) raw[y * W + x] = 0;
                        for (int y = 428; y <= 432; y++)
                            for (int x = rx - 5; x <= rx + 1; x++)
                                if (Math.pow((x - (rx - 2)) / 3.0, 2) + Math.pow((y - 430) / 2.0, 2)
                                        <= 1) raw[y * W + x] = 0;
                        if (direction > 0) {
                            byte[] mirror = raw.clone();
                            for (int y = 0; y < H; y++)
                                System.arraycopy(raw, y * W, mirror, (H - 1 - y) * W, W);
                            raw = mirror;
                        }
                        int ac = direction < 0 ? cap : H - 1 - cap,
                                start = direction < 0 ? 430 : H - 1 - 430;
                        var a =
                                new PrintedBeamGeometry.Column(
                                        0,
                                        0,
                                        0,
                                        2,
                                        direction,
                                        origin,
                                        origin,
                                        ac,
                                        gap,
                                        1,
                                        List.of(new PrintedBeamGeometry.End(origin, ac)),
                                        start);
                        var b =
                                new PrintedBeamGeometry.Column(
                                        1,
                                        crossLane ? 1 : 0,
                                        crossLane ? 1 : 0,
                                        2,
                                        direction,
                                        rx,
                                        rx,
                                        ac,
                                        other,
                                        1,
                                        List.of(new PrintedBeamGeometry.End(rx, ac)),
                                        start);
                        boolean beam = kind != 0;
                        var plane =
                                new FiniteRawBeamStrip.Carrier() {
                                    public boolean at(int x, int y) {
                                        return false;
                                    }

                                    public boolean unknown(int x, int y) {
                                        int yy = direction < 0 ? y : H - 1 - y;
                                        return beam
                                                && x > Math.min(rx, origin) + 1
                                                && x < Math.max(rx, origin) - 1
                                                && yy >= cap + offset
                                                && yy < cap + offset + thickness;
                                    }
                                };
                        var budget = new PrintedIsolatedFlags.Budget(64000000);
                        var result =
                                PrintedIsolatedFlags.prove(
                                        raw,
                                        W,
                                        H,
                                        a,
                                        List.of(0),
                                        List.of(
                                                new PrintedIsolatedFlags.Head(0, 289, start, gap),
                                                new PrintedIsolatedFlags.Head(
                                                        1, rx - 2, start, other)),
                                        List.of(a, b),
                                        plane,
                                        budget);
                        assertions++;
                        if (budget.exhausted() || result.isPresent() == beam)
                            throw new AssertionError(
                                    "Unsafe full flag kind="
                                            + kind
                                            + " side="
                                            + side
                                            + " direction="
                                            + direction
                                            + " cross="
                                            + crossLane
                                            + " result="
                                            + result);
                    }
        System.out.println(
                "{\"status\":\"FULL_FLAG_UNKNOWN_PRIMARY_GUARDS_PASS\",\"assertions\":"
                        + assertions
                        + "}");
    }
}

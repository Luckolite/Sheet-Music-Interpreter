// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Finite interval and boundary controls drawn independently of submitted fixtures. */
public final class PrimaryAbsenceBoundaryProbe {
    static int assertions;

    static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        int w = 240, h = 200;
        byte[] raw = new byte[w * h];
        Arrays.fill(raw, (byte) 255);
        var plain =
                new FiniteRawBeamStrip.Carrier() {
                    public boolean at(int x, int y) {
                        return false;
                    }
                };
        var shaft =
                new PrintedBeamGeometry.Column(
                        0,
                        0,
                        0,
                        1,
                        -1,
                        120,
                        120,
                        80,
                        12,
                        1,
                        List.of(new PrintedBeamGeometry.End(120, 80)),
                        150);
        for (int y = 80; y <= 150; y++) raw[y * w + 120] = 0;
        var absent =
                new RawPrimaryBeamAbsence(
                        raw, w, h, plain, new PrintedIsolatedFlags.Budget(64000000));
        check(
                absent.prove(shaft, List.of(shaft)).isPresent(),
                "Observed white prefixes must permit absence");
        var boundary =
                new PrintedBeamGeometry.Column(
                        1,
                        0,
                        0,
                        1,
                        -1,
                        10,
                        10,
                        80,
                        12,
                        1,
                        List.of(new PrintedBeamGeometry.End(10, 80)),
                        150);
        check(
                new RawPrimaryBeamAbsence(
                                raw, w, h, plain, new PrintedIsolatedFlags.Budget(64000000))
                        .prove(boundary, List.of(boundary))
                        .isEmpty(),
                "Unseen page prefix is not absence");
        check(
                absent.possible(shaft, 1, 1, null) == RawPrimaryBeamAbsence.State.UNKNOWN,
                "One-column contact stays unknown");
        // A continuous quantized affine stripe remains possible through every raster phase.
        int affine = 0;
        for (int phase = 0; phase < 8; phase++)
            for (int numerator : new int[] {-3, -1, 0, 1, 3}) {
                byte[] stripe = new byte[w * h];
                Arrays.fill(stripe, (byte) 255);
                for (int dx = 0; dx <= 28; dx++) {
                    int top = (int) Math.floor(80 + (phase + .5) / 8 + dx * numerator / 8.0 + .5);
                    for (int y = top; y < top + 3; y++) stripe[y * w + 120 + dx] = 0;
                }
                var uncertain =
                        new FiniteRawBeamStrip.Carrier() {
                            public boolean at(int x, int y) {
                                return false;
                            }

                            public boolean unknown(int x, int y) {
                                return true;
                            }
                        };
                var state =
                        new RawPrimaryBeamAbsence(
                                        stripe,
                                        w,
                                        h,
                                        uncertain,
                                        new PrintedIsolatedFlags.Budget(64000000))
                                .possible(shaft, 1, 28, null);
                check(
                        state == RawPrimaryBeamAbsence.State.POSSIBLE,
                        "Quantized affine unknown stripe lost phase="
                                + phase
                                + " numerator="
                                + numerator
                                + " state="
                                + state);
                affine++;
            }
        // Independently enumerate small affine witnesses and require the interval family to retain
        // them.
        Method family =
                RawPrimaryBeamAbsence.class.getDeclaredMethod("family", List.class, int[].class);
        family.setAccessible(true);
        var rng = new Random(719201);
        int witnessed = 0;
        for (int test = 0; test < 120; test++) {
            int n = 3 + test % 8;
            long intercept = 80000 + rng.nextInt(1000), slope = -700 + rng.nextInt(1401);
            var choices = new ArrayList<List<RawPrimaryBeamAbsence.Interval>>();
            for (int x = 0; x < n; x++) {
                long at = intercept + slope * x;
                var row = new ArrayList<RawPrimaryBeamAbsence.Interval>();
                row.add(
                        new RawPrimaryBeamAbsence.Interval(
                                at - rng.nextInt(513), at + rng.nextInt(513)));
                if (x % 3 == 0) row.add(new RawPrimaryBeamAbsence.Interval(at + 5000, at + 5200));
                choices.add(row);
            }
            var state =
                    (RawPrimaryBeamAbsence.State) family.invoke(absent, choices, new int[] {256});
            check(
                    state != RawPrimaryBeamAbsence.State.IMPOSSIBLE,
                    "Explicit affine witness was excluded");
            witnessed++;
        }
        var many = new ArrayList<List<RawPrimaryBeamAbsence.Interval>>();
        for (int i = 0; i < 10; i++)
            many.add(
                    List.of(
                            new RawPrimaryBeamAbsence.Interval(79000, 80000),
                            new RawPrimaryBeamAbsence.Interval(82000, 83000)));
        check(
                family.invoke(absent, many, new int[] {0}) == RawPrimaryBeamAbsence.State.UNKNOWN,
                "Branch refusal must remain unknown");
        var exhausted = new PrintedIsolatedFlags.Budget(1);
        var heads = List.of(new PrintedIsolatedFlags.Head(0, 120, 150, 12));
        check(
                PrintedIsolatedFlags.prove(
                                raw,
                                w,
                                h,
                                shaft,
                                List.of(0),
                                heads,
                                List.of(shaft),
                                plain,
                                exhausted)
                        .isEmpty(),
                "Budget cannot admit");
        check(exhausted.exhausted(), "Budget must mark exhaustion");
        long work = exhausted.used();
        check(
                PrintedIsolatedFlags.prove(
                                raw,
                                w,
                                h,
                                shaft,
                                List.of(0),
                                heads,
                                List.of(shaft),
                                plain,
                                exhausted)
                        .isEmpty(),
                "Sticky exhaustion cannot admit");
        check(work == exhausted.used(), "Sticky budget performs no new work");
        System.out.println(
                "{\"status\":\"INDEPENDENT_BOUNDARY_CONTROLS_PASS\",\"assertions\":"
                        + assertions
                        + ",\"quantizedAffineStripes\":"
                        + affine
                        + ",\"explicitAffineWitnesses\":"
                        + witnessed
                        + "}");
    }
}

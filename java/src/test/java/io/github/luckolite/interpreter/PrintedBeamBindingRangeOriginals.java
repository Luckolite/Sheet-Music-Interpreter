// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independent actual float acceptance law is the oracle; no lane/frame is constructed. */
public final class PrintedBeamBindingRangeOriginals {
    static int checked, accepted, rejectedFar;

    static void pair(float b, float g, int step, float actualB, float actualG, float y) {
        checked++;
        if (!Float.isFinite(actualB)
                || !Float.isFinite(actualG)
                || actualG <= 0
                || !Float.isFinite(y)) return;
        float phase = g * .4f, scale = g * .12f;
        if (Math.abs(actualB - b) > phase || Math.abs(actualG - g) > scale) return;
        if (Math.abs(y - (actualB - step * actualG * .5f)) > actualG * .45f) return;
        accepted++;
        if (!PrintedBeamBindingRange.possible(y, step, b, g, phase, scale, .45f))
            throw new AssertionError(
                    "false veto "
                            + b
                            + "/"
                            + g
                            + "/"
                            + step
                            + " ->"
                            + actualB
                            + "/"
                            + actualG
                            + "/"
                            + y);
    }

    public static void main(String[] args) {
        float[] bs = {-0f, 0, 1, 50, 10000, 1048576, 10000000, Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] gs = {3, 8, 14.25f, 32, 64};
        int[] steps = {
            Integer.MIN_VALUE,
            -16777217,
            -1000,
            -32,
            -1,
            0,
            1,
            32,
            1000,
            16777217,
            Integer.MAX_VALUE
        };
        for (float b : bs)
            for (float g : gs)
                for (int step : steps)
                    for (float bf : new float[] {-1, -.5f, 0, .5f, 1})
                        for (float gf : new float[] {-1, -.5f, 0, .5f, 1}) {
                            float ab = b + g * .4f * bf,
                                    ag = g + g * .12f * gf,
                                    pred = ab - step * ag * .5f;
                            for (float y :
                                    new float[] {
                                        pred,
                                        pred - ag * .45f,
                                        pred + ag * .45f,
                                        Math.nextDown(pred),
                                        Math.nextUp(pred)
                                    }) pair(b, g, step, ab, ag, y);
                        }
        Random random = new Random(0x193659dbL);
        for (int i = 0; i < 100000; i++) {
            float b = (random.nextFloat() - .5f) * 20000000, g = 3 + random.nextFloat() * 61;
            int step = i % 3 == 0 ? random.nextInt() : random.nextInt(201) - 100;
            float ab = b + (random.nextFloat() * 2 - 1) * g * .4f,
                    ag = g + (random.nextFloat() * 2 - 1) * g * .12f;
            float y = ab - step * ag * .5f + (random.nextFloat() * 2 - 1) * ag * .45f;
            pair(b, g, step, ab, ag, y);
        }
        for (int i = 0; i < 100; i++)
            if (!PrintedBeamBindingRange.possible(
                    1000 + i, 0, 100, 14.25f, 14.25f * .4f, 14.25f * .12f, .45f)) rejectedFar++;
        if (rejectedFar != 100) throw new AssertionError("far lanes not pruned");
        System.out.println(
                "PASS checked="
                        + checked
                        + " actualAcceptedNeverVetoed="
                        + accepted
                        + " farLaneRefusals="
                        + rejectedFar);
    }
}

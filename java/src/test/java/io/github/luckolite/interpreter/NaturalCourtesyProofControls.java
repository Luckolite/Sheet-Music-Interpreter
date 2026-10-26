// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Original procedural ink; no source score pixels. Exercises actual courtesy admission. */
public final class NaturalCourtesyProofControls {
    static final int W = 600, H = 250;
    byte[] gray = new byte[W * H], labels = new byte[W * H];

    NaturalCourtesyProofControls() {
        Arrays.fill(gray, (byte) 255);
    }

    void rect(int x, int y, int w, int h, byte label) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                gray[yy * W + xx] = 0;
                if (label != 0) labels[yy * W + xx] = label;
            }
    }

    Object make(String n, Object... a) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + n)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(a);
    }

    float run(
            boolean semanticWhole,
            boolean bar,
            boolean lowerRight,
            boolean note,
            boolean stem,
            boolean unknown,
            boolean nullGray,
            boolean falseCounterLabel,
            boolean fringe,
            boolean foreign,
            int phaseMode)
            throws Exception {
        for (int y : new int[] {100, 116, 132, 148, 164}) rect(30, y, 540, 1, (byte) 0);
        if (bar) rect(497, 100, 2, 65, (byte) 0);
        rect(513, 111, 2, 30, (byte) 0);
        rect(522, 124, 2, lowerRight ? 30 : 17, (byte) 0);
        rect(513, 127, 11, 3, (byte) 0);
        rect(513, 139, 11, 3, (byte) 0);
        if (semanticWhole) {
            for (int i = 0; i < gray.length; i++)
                if ((gray[i] & 255) == 0 && i % W >= 513 && i % W <= 523) labels[i] = 3;
        } else {
            for (int y = 127; y <= 129; y++) for (int x = 513; x <= 523; x++) labels[y * W + x] = 3;
        }
        if (note) rect(544, 137, 9, 6, (byte) 2);
        if (stem) rect(546, 110, 2, 30, (byte) 1);
        if (unknown) rect(545, 112, 10, 20, (byte) 0);
        if (!semanticWhole) labels[140 * W + 518] = 2;
        if (falseCounterLabel) labels[134 * W + 518] = 2;
        if (fringe) labels[141 * W + 518] = 2;
        if (foreign) for (int x = 518; x <= 530; x++) labels[140 * W + x] = 2;
        Object c =
                semanticWhole
                        ? make("Component", 182, 513, 523, 111, 153, 518f, 132f)
                        : make("Component", 33, 513, 523, 127, 129, 518f, 128f);
        Object a = make("AccidentalCandidate", c, (byte) 3),
                s = make("Staff", phaseMode > 0 ? 107f : 100f, phaseMode > 0 ? 171f : 164f, 16f);
        if (phaseMode > 0) {
            for (String field : new String[] {"pitchBottom", "pitchGap", "printedPhase"}) {
                var f = s.getClass().getDeclaredField(field);
                f.setAccessible(true);
                if (field.equals("printedPhase")) f.setBoolean(s, phaseMode != 2);
                else
                    f.setFloat(
                            s,
                            field.equals("pitchBottom") ? 164f : phaseMode == 3 ? Float.NaN : 16f);
            }
        }
        Class<?> budget = Class.forName(OmrScoreInterpreter.class.getName() + "$RawCourtesyBudget");
        var bc = budget.getDeclaredConstructors()[0];
        bc.setAccessible(true);
        Object operation = bc.newInstance(64_000_000L, 16L * 1024 * 1024);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "provedNaturalCourtesyBoundary",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        s.getClass(),
                        List.class,
                        budget);
        m.setAccessible(true);
        return (float)
                m.invoke(
                        null, labels, nullGray ? null : gray, W, H, 500f, s, List.of(a), operation);
    }

    public static void main(String[] a) throws Exception {
        int passed = 0, failed = 0;
        for (int mode = 0; mode < 14; mode++) {
            var f = new NaturalCourtesyProofControls();
            float x =
                    f.run(
                            mode == 1,
                            mode != 2,
                            mode != 3,
                            mode == 4,
                            mode == 5,
                            mode == 6,
                            mode == 7,
                            mode == 8,
                            mode == 9,
                            mode == 10,
                            mode >= 11 ? mode - 10 : 0);
            boolean expected = mode < 2 || mode == 9 || mode == 11;
            boolean ok = Float.isFinite(x) == expected;
            if (!ok) failed++;
            else passed++;
            System.out.println((ok ? "PASS" : "FAIL") + " mode=" + mode + " boundary=" + x);
        }
        System.out.println("controls passed=" + passed + " failed=" + failed);
        if (failed != 0) throw new AssertionError("failures=" + failed);
    }
}

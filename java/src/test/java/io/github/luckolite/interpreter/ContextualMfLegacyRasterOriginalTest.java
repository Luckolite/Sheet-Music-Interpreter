// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class ContextualMfLegacyRasterOriginalTest {
    static void check(String id, boolean expected, ContextualMfStrokeFixture f, String literal) {
        byte[] old = f.p.clone();
        var z = f.word(literal);
        var e =
                ContextualMfBodyEvidence.inspect(
                        literal,
                        f.p,
                        f.W,
                        f.H,
                        (int) Math.floor(z.left() * f.W),
                        (int) Math.floor(z.top() * f.H),
                        (int) Math.ceil(z.right() * f.W),
                        (int) Math.ceil(z.bottom() * f.H),
                        f.G,
                        ContextualMfBodyEvidence.withoutNotation(f.p, f.W, f.H));
        if (!Arrays.equals(old, f.p)) throw new AssertionError("Mutation");
        if (expected != e.supported())
            throw new AssertionError(
                    id
                            + " expected="
                            + expected
                            + " actual="
                            + e.supported()
                            + " reason="
                            + e.reason());
        System.out.println(
                id
                        + " expected="
                        + expected
                        + " actual="
                        + e.supported()
                        + " reason="
                        + e.reason()
                        + " unknown="
                        + e.unknownSamples());
    }

    @org.junit.Test
    public void originalRasterVectors() {
        var f = new ContextualMfStrokeFixture();
        f.draw(3, false);
        check("disconnectedLettersAreNotThisJoinedBodyProof", false, f, "mf");
        f = new ContextualMfStrokeFixture();
        f.draw(3, true);
        for (int y = 218; y <= 238; y++) for (int x = 362; x <= 425; x++) f.p[y * f.W + x] = 20;
        check("solidBodyCannotSupplyOpenLegs", false, f, "mf");
        for (String literal : List.of("f", "ff", "mp", "m", "for", "Taylor")) {
            f = new ContextualMfStrokeFixture();
            f.draw(3, true);
            check("otherReadWords-" + literal, false, f, literal);
        }
        f = new ContextualMfStrokeFixture();
        f.draw(3, true);
        f.line(380, 219, 380, 247);
        check("completeExtraDownwardBranchIsUnknown", false, f, "mf");
        for (int shade : new int[] {200, 224, 225, 230, 240, 250, 254}) {
            f = new ContextualMfStrokeFixture();
            f.draw(3, true);
            for (int y = 202; y <= 218; y++) f.p[y * f.W + 375] = (byte) shade;
            check("unrelatedFaintAttachedBranchCannotBeIgnored-" + shade, false, f, "mf");
        }
        f = new ContextualMfStrokeFixture();
        f.draw(3, true);
        for (int y = 221; y <= 237; y++) f.p[y * f.W + 360] = (byte) 254;
        f.p[220 * f.W + 360] = 20;
        f.p[219 * f.W + 360] = 20;
        check("extraFaintFourthLegCannotSupplyMezzoBody", false, f, "mf");
    }
}

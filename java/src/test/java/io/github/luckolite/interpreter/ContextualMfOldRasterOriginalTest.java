// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class ContextualMfOldRasterOriginalTest {
    static PlayingTechniqueDetector.Word tight(ContextualMfStrokeFixture f) {
        int l = f.W, t = f.H, r = -1, b = -1;
        for (int y = 0; y < f.H; y++)
            for (int x = 0; x < f.W; x++)
                if ((f.p[y * f.W + x] & 255) < 255) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                }
        return new PlayingTechniqueDetector.Word(
                "mf",
                l / (float) f.W,
                t / (float) f.H,
                (r + 1) / (float) f.W,
                (b + 1) / (float) f.H);
    }

    static void check(
            String id,
            boolean expected,
            ContextualMfStrokeFixture f,
            PlayingTechniqueDetector.Word word) {
        byte[] old = f.p.clone();
        var e =
                ContextualMfBodyEvidence.inspect(
                        "mf",
                        f.p,
                        f.W,
                        f.H,
                        (int) Math.floor(word.left() * f.W),
                        (int) Math.floor(word.top() * f.H),
                        (int) Math.ceil(word.right() * f.W),
                        (int) Math.ceil(word.bottom() * f.H),
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
        f.draw(3, true);
        check("exactReadBoundsDoNotClipTheCompleteRawWord", true, f, tight(f));
        f = new ContextualMfStrokeFixture();
        f.draw(3, true);
        check("extraPaperDoesNotChangeTheSameGlyphRole", true, f, f.word("mf"));
        f = new ContextualMfStrokeFixture();
        f.draw(0, false);
        check("missingMezzoBodyIsNotReconstructedFromReadText", false, f, tight(f));
        for (int v : new int[] {225, 230, 240, 250, 254}) {
            f = new ContextualMfStrokeFixture();
            f.draw(3, true);
            f.p[205 * f.W + 375] = (byte) v;
            check("lightUnknownWithinReadBoxKeepsAbstention-" + v, false, f, f.word("mf"));
        }
        for (int v : new int[] {20, 225, 230, 240, 250, 254}) {
            f = new ContextualMfStrokeFixture();
            f.draw(3, true);
            var w = tight(f);
            int x = (int) Math.round(w.left() * f.W) - 2, y = 225;
            f.p[y * f.W + x] = (byte) v;
            check("independentUnownedHaloDotCannotBeSilentlyDiscarded-" + v, false, f, w);
        }
        f = new ContextualMfStrokeFixture();
        f.draw(3, true);
        var w = tight(f);
        f.line(420, 206, 420, 185);
        check("continuingRawBranchCannotBeDeclaredPaperPadding", false, f, w);
        f = new ContextualMfStrokeFixture();
        f.draw(3, true);
        w = tight(f);
        f.line(356, 219, 349, 236);
        f.line(356, 219, 370, 219);
        check("distinctFifthLegRemainsUnknown", false, f, w);
        f = new ContextualMfStrokeFixture();
        f.draw(3, true);
        w = tight(f);
        for (int i = 0; i < f.p.length; i++) if ((f.p[i] & 255) == 20) f.p[i] = (byte) 240;
        check("paleUnprovedBodyDoesNotInheritDarkBodyCertificate", false, f, w);
    }
}

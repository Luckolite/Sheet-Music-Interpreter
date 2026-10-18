// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raw-role scenes. These prove stripe membership, not quarter acquisition. */
public final class TransverseStripeOwnershipTest {
    static final int W = 320, H = 180;
    static final float G = 16;

    static final class Scene {
        final byte[] gray = new byte[W * H];

        Scene() {
            Arrays.fill(gray, (byte) 240);
            for (int k = 0; k < 5; k++) rect(30, 30 + k * 16 - 1, 285, 30 + k * 16 + 1);
        }

        void rect(int l, int t, int r, int b) {
            for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = 0;
        }

        void oval(int cx, int cy) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 11; x <= cx + 11; x++)
                    if ((x - cx) * (x - cx) / 121. + (y - cy) * (y - cy) / 64. <= 1)
                        gray[y * W + x] = 0;
        }
    }

    static Class<?> nested(String n) {
        return Arrays.stream(FiniteBeamQuarterOwnership.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals(n))
                .findFirst()
                .orElseThrow();
    }

    static Object stem(float x, float y) throws Exception {
        Constructor<?> c = nested("Stem").getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(
                Math.round(x) - 11,
                Math.round(x) - 8,
                115,
                1,
                x,
                y,
                Math.round(y + 4),
                new int[] {115, 115, 115, 115});
    }

    static Object rules(Scene s) throws Exception {
        Method m =
                FiniteBeamQuarterOwnership.class.getDeclaredMethod(
                        "rules",
                        byte[].class,
                        int.class,
                        int.class,
                        nested("Stem"),
                        nested("Stem"),
                        float.class,
                        float.class);
        m.setAccessible(true);
        return m.invoke(null, s.gray, W, H, stem(110, 38), stem(160, 54), 30f, G);
    }

    static boolean stripe(Scene s, int x, int y) throws Exception {
        Object r = rules(s);
        if (r == null) return false;
        Method m =
                FiniteBeamQuarterOwnership.class.getDeclaredMethod(
                        "transversePixel", int.class, int.class, nested("Rules"));
        m.setAccessible(true);
        return (boolean) m.invoke(null, x, y, r);
    }

    @Test
    public void independentlyExposedRuleOwnsOnlyItsStripeAcrossAThickHead() throws Exception {
        var s = new Scene();
        s.oval(110, 38);
        assertTrue(stripe(s, 110, 30));
        assertFalse(stripe(s, 110, 38));
    }

    @Test
    public void touchingQuarterBranchOutsideStripeRemainsUnowned() throws Exception {
        var s = new Scene();
        s.oval(110, 38);
        s.rect(110, 40, 112, 57);
        assertFalse(stripe(s, 110, 51));
    }

    @Test
    public void slurPortOutsideThePaperBoundedStripeIsNotAbsorbed() throws Exception {
        var s = new Scene();
        s.oval(110, 38);
        s.rect(113, 24, 115, 36);
        assertFalse(stripe(s, 114, 25));
    }

    @Test
    public void brokenRegisteredCoreCannotBeReplacedByForeignParallelInk() throws Exception {
        var s = new Scene();
        s.oval(110, 38);
        for (int x = 50; x <= 55; x++)
            for (int y = 27; y <= 33; y++) s.gray[y * W + x] = (byte) 240;
        s.rect(50, 24, 55, 26);
        assertNull(rules(s));
    }

    @Test
    public void noIndependentPaperProfilesDoesNotMakeABroadBandARule() throws Exception {
        var s = new Scene();
        s.rect(45, 22, 205, 38);
        assertFalse(stripe(s, 110, 30));
    }

    @Test
    public void ruleProofDoesNotMutateTheOriginalGray() throws Exception {
        var s = new Scene();
        s.oval(110, 38);
        byte[] b = s.gray.clone();
        assertTrue(stripe(s, 110, 30));
        assertArrayEquals(b, s.gray);
    }
}

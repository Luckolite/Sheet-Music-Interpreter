// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original isolated second-ledger oval, unrelated opposite rails, and owned raw shaft. */
public class SecondLowerLedgerOwnerOriginalTest {
    static final int W = 420, H = 320;

    static Object call(String name, Object... args) throws Exception {
        for (var m : OmrScoreInterpreter.class.getDeclaredMethods())
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    m.setAccessible(true);
                    return m.invoke(null, args);
                } catch (IllegalArgumentException ignored) {
                }
            }
        throw new AssertionError(name);
    }

    static class Ink {
        byte[] g = new byte[W * H];
        Object upper, lower, head;

        Ink(String mode) throws Exception {
            Arrays.fill(g, (byte) 255);
            var sc =
                    Class.forName(OmrScoreInterpreter.class.getName() + "$Staff")
                            .getDeclaredConstructors()[0];
            sc.setAccessible(true);
            upper = sc.newInstance(40f, 96f, 14f);
            lower = sc.newInstance(222f, 278f, 14f);
            var hc =
                    Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                            .getDeclaredConstructors()[0];
            hc.setAccessible(true);
            head = hc.newInstance(245, 190, 208, 116, 132, 199f, 124f);
            for (int y : new int[] {40, 54, 68, 82, 96, 222, 236, 250, 264, 278})
                rect(30, y, 350, 1);
            for (int y : new int[] {166, 180, 194}) rect(180, y, 39, 1);
            if (!mode.equals("missingInner")) rect(180, 110, 39, 1);
            if (!mode.equals("missingOwn")) rect(180, 124, 39, 1);
            if (!mode.equals("missingShaft"))
                rect(
                        mode.equals("detached") ? 227 : 207,
                        mode.equals("opposed") ? 124 : 60,
                        2,
                        mode.equals("opposed") ? 90 : 65);
            if (!mode.equals("missingBody"))
                for (int y = 116; y <= 132; y++)
                    for (int x = 190; x <= 208; x++)
                        if (Math.pow((x - 199) / 9., 2) + Math.pow((y - 124) / 8., 2) <= 1)
                            g[y * W + x] = 0;
            if (mode.equals("broken"))
                for (int y = 100; y <= 106; y++)
                    for (int x = 207; x <= 208; x++) g[y * W + x] = (byte) 255;
        }

        void rect(int x, int y, int w, int h) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = 0;
        }

        Object owner() throws Exception {
            return call("printedLedgerOwner", g, W, H, List.of(upper, lower), head);
        }
    }

    @Test
    public void ownSecondLedgerAndUpShaftBeatUnrelatedOppositeRails() throws Exception {
        var i = new Ink("valid");
        assertSame(i.upper, i.owner());
    }

    @Test
    public void missingInnerRuleCannotEstablishNewOwner() throws Exception {
        var i = new Ink("missingInner");
        assertNotSame(i.upper, i.owner());
    }

    @Test
    public void missingRuleThroughOvalCannotEstablishNewOwner() throws Exception {
        var i = new Ink("missingOwn");
        assertNotSame(i.upper, i.owner());
    }

    @Test
    public void longParallelBeamsCannotSubstituteForShortLedgerRules() throws Exception {
        var i = new Ink("valid");
        i.rect(30, 110, 350, 1);
        i.rect(30, 124, 350, 1);
        assertNotSame(i.upper, i.owner());
    }

    @Test
    public void missingPrintedOvalCannotBorrowShaft() throws Exception {
        var i = new Ink("missingBody");
        assertNotSame(i.upper, i.owner());
    }

    @Test
    public void absentShaftCannotEstablishNewOwner() throws Exception {
        var i = new Ink("missingShaft");
        assertNotSame(i.upper, i.owner());
    }

    @Test
    public void oppositeShaftCannotEstablishUpperOwner() throws Exception {
        var i = new Ink("opposed");
        assertNotSame(i.upper, i.owner());
    }

    @Test
    public void detachedShaftCannotEstablishUpperOwner() throws Exception {
        var i = new Ink("detached");
        assertNotSame(i.upper, i.owner());
    }

    @Test
    public void brokenShaftCannotReachPrintedStaff() throws Exception {
        var i = new Ink("broken");
        assertNotSame(i.upper, i.owner());
    }

    @Test
    public void ownershipDoesNotMutateRaster() throws Exception {
        var i = new Ink("valid");
        var copy = i.g.clone();
        i.owner();
        assertArrayEquals(copy, i.g);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CrossStaffBeamCapBudgetTest {
    static List<?> direct(CrossStaffBeamCapOriginalTest.Page p, long budget) throws Exception {
        var c = OmrScoreInterpreter.class;
        var comp =
                c.getDeclaredMethod(
                        "findComponents", byte[].class, int.class, int.class, byte.class);
        comp.setAccessible(true);
        var heads = (List<?>) comp.invoke(null, p.l, 360, 340, (byte) 2);
        var staff = Class.forName(c.getName() + "$Staff");
        var ctor = staff.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object upper = ctor.newInstance(60f, 116f, 14f), lower = ctor.newInstance(200f, 256f, 14f);
        var count = staff.getDeclaredField("count");
        var index = staff.getDeclaredField("index");
        count.setAccessible(true);
        index.setAccessible(true);
        count.setInt(upper, 2);
        count.setInt(lower, 2);
        index.setInt(upper, 0);
        index.setInt(lower, 1);
        var m =
                c.getDeclaredMethod(
                        "crossStaffSingleBeamCaps",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class,
                        long.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(null, p.g, 360, 340, heads, List.of(upper, lower), budget);
    }

    @Test
    public void zeroBudgetCannotPublishPartialRejection() throws Exception {
        assertTrue(direct(CrossStaffBeamCapOriginalTest.scene(false), 0).isEmpty());
    }

    @Test
    public void candidateScanExhaustionCannotPublishPartialRejection() throws Exception {
        assertTrue(direct(CrossStaffBeamCapOriginalTest.scene(false), 100).isEmpty());
    }

    @Test
    public void completeBudgetCanProveOneCap() throws Exception {
        assertEquals(1, direct(CrossStaffBeamCapOriginalTest.scene(false), 64000000).size());
    }

    @Test
    public void ownedShaftCacheDoesNotSurviveInvocation() throws Exception {
        var p = CrossStaffBeamCapOriginalTest.scene(false);
        assertEquals(1, direct(p, 64000000).size());
        for (int y = 130; y < 145; y++) p.g[y * 360 + 177] = (byte) 255;
        assertTrue(direct(p, 64000000).isEmpty());
    }
}

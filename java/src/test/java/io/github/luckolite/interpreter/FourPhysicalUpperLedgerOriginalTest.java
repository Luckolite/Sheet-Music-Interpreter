// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original four-rule certificate stage; no claim about unrelated legacy acquisition routes. */
public class FourPhysicalUpperLedgerOriginalTest {
    static boolean chain(int missing, boolean broad, boolean shift, float distance)
            throws Exception {
        int w = 180, h = 180;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        float gap = 13.75f, cy = 60, top = cy + distance * gap;
        for (int rule = 1; rule <= 4; rule++) {
            if (rule == missing) continue;
            int y = Math.round(top - rule * gap) + (shift && rule == 2 ? 7 : 0);
            for (int yy = y; yy <= y + 1; yy++)
                for (int x = broad ? 20 : 46; x <= (broad ? 150 : 74); x++) g[yy * w + x] = 0;
        }
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var hc = c.getDeclaredConstructors()[0];
        hc.setAccessible(true);
        Object head = hc.newInstance(276, 50, 70, 52, 68, 60f, 60f);
        var sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var ctor = sc.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object staff = ctor.newInstance(top, top + 4 * gap, gap);
        java.lang.reflect.Method m;
        try {
            m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "completeFourUpperLedgerChain",
                            byte[].class,
                            int.class,
                            int.class,
                            c,
                            sc);
        } catch (NoSuchMethodException old) {
            m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "completeUpperLedgerChain", byte[].class, int.class, int.class, c, sc);
        }
        m.setAccessible(true);
        return (boolean) m.invoke(null, g, w, h, head, staff);
    }

    @Test
    public void fourPhysicalRulesAtNearBoundaryAreCertified() throws Exception {
        assertTrue(chain(0, false, false, 4.585f));
    }

    @Test
    public void eachMissingPhysicalRuleRefuses() throws Exception {
        for (int i = 1; i <= 4; i++)
            assertFalse("Missing physical rule " + i, chain(i, false, false, 4.585f));
    }

    @Test
    public void broadUnderlinesCannotStandForLedgers() throws Exception {
        assertFalse(chain(0, true, false, 4.585f));
    }

    @Test
    public void shiftedRuleCannotStandForStaffPhase() throws Exception {
        assertFalse(chain(0, false, true, 4.585f));
    }

    @Test
    public void lowerScopeBoundaryStillAbstains() throws Exception {
        assertFalse(chain(0, false, false, 4.5f));
    }

    @Test
    public void upperInsideScopeRetainsAllFourPhysicalObligations() throws Exception {
        assertTrue(chain(0, false, false, 4.649f));
    }
}

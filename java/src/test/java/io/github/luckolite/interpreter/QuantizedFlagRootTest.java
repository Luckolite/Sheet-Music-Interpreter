// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuantizedFlagRootTest extends PaleFlagRootTest {
    boolean rooted() throws Exception {
        var hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var c = hc.getDeclaredConstructors()[0];
        c.setAccessible(true);
        var head = c.newInstance(180, 90, 110, 144, 156, 100f, 150f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "rootedPaleFlag",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        hc,
                        float.class,
                        int.class,
                        int.class,
                        boolean.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, labels, g, W, H, head, 16f, 110, 80, true);
    }

    void edgeRule(int y) {
        rect(10, 250, y, y, 35);
        rect(46, 53, y, y, 255);
        rect(46, 53, y + 1, y + 1, 35);
    }

    @Test
    public void staffEdgesCannotRootDetachedHook() throws Exception {
        setup(false, true, true);
        edgeRule(82);
        edgeRule(84);
        edgeRule(94);
        assertFalse(rooted());
    }

    @Test
    public void genuineRootSurvivesNearbyStaffEdges() throws Exception {
        setup(true, true, true);
        edgeRule(82);
        edgeRule(84);
        edgeRule(94);
        assertTrue(rooted());
    }

    @Test
    public void oneSidedContinuationIsNotAStaffRule() {
        rect(111, 180, 82, 82, 35);
        assertFalse(FlagRootStaffRule.matches(g, W, H, 82, 110, 16, 205));
    }

    @Test
    public void sourceRemainsUnchanged() {
        edgeRule(82);
        byte[] before = g.clone();
        assertTrue(FlagRootStaffRule.matches(g, W, H, 82, 110, 16, 205));
        assertArrayEquals(before, g);
    }
}

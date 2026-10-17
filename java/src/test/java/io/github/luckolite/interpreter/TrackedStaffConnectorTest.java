// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original curved five-rule staves and independently printed system connector. */
public class TrackedStaffConnectorTest {
    static final int W = 340, H = 250;

    private int systems(boolean connector, boolean nearRules, boolean complete) throws Exception {
        return systems(connector, nearRules, complete, false);
    }

    private int systems(boolean connector, boolean nearRules, boolean complete, boolean affineInk)
            throws Exception {
        byte[] g = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        var upper =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {20, 85, 10}, new float[] {300, 90, 10}));
        var lower =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {20, 210, 10}, new float[] {300, 210, 10}));
        for (int x = nearRules ? 20 : 90; x <= 300; x++)
            for (int line = 0; line < (complete ? 5 : 1); line++) {
                g[Math.round((affineInk ? 90 : upper.at(x)[0]) - (4 - line) * 10) * W + x] = 0;
                g[Math.round(lower.at(x)[0] - (4 - line) * 10) * W + x] = 0;
            }
        if (connector) for (int y = 45; y <= 210; y++) g[y * W + 20] = 0;
        var c = Class.forName(OmrMeasurePostProcessor.class.getName() + "$StaffRun");
        var ctor =
                Arrays.stream(c.getDeclaredConstructors())
                        .filter(k -> k.getParameterCount() == 8)
                        .findFirst()
                        .orElseThrow();
        ctor.setAccessible(true);
        Object a = ctor.newInstance(50, 90, 10f, 20, 300, List.of(20, 160, 300), 0f, upper);
        Object b = ctor.newInstance(170, 210, 10f, 20, 300, List.of(20, 160, 300), 0f, lower);
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "mergeAlignedStaffs",
                        List.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return ((List<?>) m.invoke(null, List.of(a, b), labels, g, W, H)).size();
    }

    @Test
    public void connectedCurvedStavesUseTheirLocalRulePositions() throws Exception {
        assertEquals(1, systems(true, true, true));
    }

    @Test
    public void alignedButDisconnectedStavesStaySeparate() throws Exception {
        assertEquals(2, systems(false, true, true));
    }

    @Test
    public void aCreaseAwayFromStaffEndsDoesNotJoinSystems() throws Exception {
        assertEquals(2, systems(true, false, true));
    }

    @Test
    public void provenAffineConnectionSurvivesAnUnhelpfulTrack() throws Exception {
        assertEquals(1, systems(true, true, true, true));
    }

    @Test
    public void trackCannotSupplyAbsentPrintedRules() throws Exception {
        assertEquals(2, systems(true, true, false));
    }
}

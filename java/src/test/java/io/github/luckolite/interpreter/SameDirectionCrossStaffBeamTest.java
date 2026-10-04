// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original ink fixtures for two shafts joined outside both noteheads. */
public class SameDirectionCrossStaffBeamTest {
    private byte[] image(boolean up, int thickness, boolean firstStem, boolean secondStem) {
        byte[] gray = new byte[240 * 360];
        Arrays.fill(gray, (byte) 255);
        int a = up ? 126 : 114, b = up ? 166 : 154, beam = up ? 140 : 310;
        if (firstStem)
            for (int y = Math.min(260, beam); y <= Math.max(260, beam); y++) gray[y * 240 + a] = 0;
        if (secondStem)
            for (int y = Math.min(200, beam); y <= Math.max(200, beam); y++) gray[y * 240 + b] = 0;
        for (int x = a; x <= b; x++)
            for (int t = -thickness; t <= thickness; t++) gray[(beam + t) * 240 + x] = 0;
        return gray;
    }

    @Test
    public void sameUpStemsConnectAboveBothHeads() {
        assertTrue(
                CrossStaffBeamDetector.connected(
                        image(true, 2, true, true), 240, 360, 120, 260, 160, 200, 10));
    }

    @Test
    public void sameDownStemsConnectBelowBothHeads() {
        assertTrue(
                CrossStaffBeamDetector.connected(
                        image(false, 2, true, true), 240, 360, 120, 260, 160, 200, 10));
    }

    @Test
    public void thinStaffRuleCannotBecomeABeam() {
        assertFalse(
                CrossStaffBeamDetector.connected(
                        image(true, 0, true, true), 240, 360, 120, 260, 160, 200, 10));
    }

    @Test
    public void bothShaftsMustAttach() {
        assertFalse(
                CrossStaffBeamDetector.connected(
                        image(true, 2, false, true), 240, 360, 120, 260, 160, 200, 10));
        assertFalse(
                CrossStaffBeamDetector.connected(
                        image(true, 2, true, false), 240, 360, 120, 260, 160, 200, 10));
    }

    @Test
    public void ordinaryShortVerticalJumpIsExcluded() {
        assertFalse(
                CrossStaffBeamDetector.connected(
                        image(true, 2, true, true), 240, 360, 120, 230, 160, 200, 10));
    }
}

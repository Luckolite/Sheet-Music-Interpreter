// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NearOpposingCrossStaffBeamTest {
    private byte[] image(boolean bridge, boolean firstStem, boolean secondStem) {
        byte[] gray = new byte[240 * 360];
        Arrays.fill(gray, (byte) 255);
        if (firstStem) for (int y = 230; y <= 260; y++) gray[y * 240 + 126] = 0;
        if (secondStem) for (int y = 202; y <= 230; y++) gray[y * 240 + 154] = 0;
        if (bridge)
            for (int x = 126; x <= 154; x++)
                for (int dy = -2; dy <= 2; dy++) gray[(230 + dy) * 240 + x] = 0;
        return gray;
    }

    @Test
    public void opposingStemsCanBridgeHeadsLessThanSixGapsApart() {
        assertTrue(
                CrossStaffBeamDetector.connected(
                        image(true, true, true), 240, 360, 120, 260, 160, 202, 10));
    }

    @Test
    public void nearHeadsStillNeedBothShaftsAndTheBeam() {
        assertFalse(
                CrossStaffBeamDetector.connected(
                        image(false, true, true), 240, 360, 120, 260, 160, 202, 10));
        assertFalse(
                CrossStaffBeamDetector.connected(
                        image(true, false, true), 240, 360, 120, 260, 160, 202, 10));
        assertFalse(
                CrossStaffBeamDetector.connected(
                        image(true, true, false), 240, 360, 120, 260, 160, 202, 10));
    }
}

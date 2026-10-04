// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** A broken continuation into another beam is distinct from an attached printed shaft. */
public class CrossStaffShaftGapTest {
    private byte[] drawing(int blanks) {
        byte[] g = new byte[240 * 360];
        Arrays.fill(g, (byte) 255);
        for (int y = 140; y <= 260; y++) if (y < 180 || y >= 180 + blanks) g[y * 240 + 126] = 0;
        for (int y = 140; y <= 200; y++) g[y * 240 + 166] = 0;
        for (int x = 126; x <= 166; x++) for (int d = -2; d <= 2; d++) g[(140 + d) * 240 + x] = 0;
        return g;
    }

    @Test
    public void longWhiteBreakCannotBorrowTheOtherBeam() {
        assertFalse(CrossStaffBeamDetector.connected(drawing(6), 240, 360, 120, 260, 160, 200, 10));
    }

    @Test
    public void continuousPrintedShaftRemainsConnected() {
        assertTrue(CrossStaffBeamDetector.connected(drawing(0), 240, 360, 120, 260, 160, 200, 10));
    }

    @Test
    public void oneRasterPixelSeamRemainsConnected() {
        assertTrue(CrossStaffBeamDetector.connected(drawing(1), 240, 360, 120, 260, 160, 200, 10));
    }
}

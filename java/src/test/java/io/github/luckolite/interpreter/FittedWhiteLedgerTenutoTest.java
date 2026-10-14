// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original faded ledger stacks and independent ruled-staff/tenuto controls. */
public class FittedWhiteLedgerTenutoTest {
    private int read(
            int gap,
            boolean below,
            int rails,
            int missingLedger,
            boolean broad,
            boolean irregular,
            boolean isolated) {
        int w = 960, h = 1280, nx = 300, ny = below ? 500 : 260, direction = below ? -1 : 1;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) 250);
        int edge = ny + direction * 4 * gap, mark = ny + direction * 3 * gap;
        if (!isolated) {
            for (int line = 0; line < rails; line++) {
                int cy = edge + direction * line * gap + (irregular && line == 3 ? gap / 2 : 0);
                for (int x = 80; x < 540; x++)
                    for (int dy = -(broad ? 5 : 1); dy <= (broad ? 5 : 1); dy++)
                        gray[(cy + dy) * w + x] = (byte) 195;
            }
            for (int line = 1; line <= 3; line++) {
                if (line == missingLedger) continue;
                int cy = edge - direction * line * gap;
                for (int x = nx - gap; x <= nx + gap; x++)
                    for (int dy = -1; dy <= 1; dy++) gray[(cy + dy) * w + x] = (byte) 195;
            }
        }
        for (int x = nx + Math.round(gap * .12f); x <= nx + Math.round(gap * .95f); x++)
            for (int dy = -1; dy <= 1; dy++) gray[(mark + dy) * w + x] = 90;
        byte[] before = gray.clone();
        int result =
                NoteArticulationDetector.detect(
                        labels,
                        gray,
                        w,
                        h,
                        List.of(new NoteArticulationDetector.Anchor(nx, ny, gap * 1.04f, 0)))[0];
        assertArrayEquals(before, gray);
        return result;
    }

    @Test
    public void fullWhiteStaffOwnsItsOffCenterLedgerCore() {
        for (int gap : new int[] {12, 16, 20})
            for (boolean below : new boolean[] {false, true})
                assertEquals(0, read(gap, below, 5, 0, false, false, false));
    }

    @Test
    public void isolatedTenutoRetainsItsMeaning() {
        assertEquals(NoteArticulation.TENUTO, read(16, false, 0, 0, false, false, true));
    }

    @Test
    public void fourStaffRulesDoNotProveACompleteStaff() {
        assertEquals(NoteArticulation.TENUTO, read(16, false, 4, 0, false, false, false));
    }

    @Test
    public void missingInterveningLedgerKeepsTheMark() {
        assertEquals(NoteArticulation.TENUTO, read(16, false, 5, 2, false, false, false));
    }

    @Test
    public void broadBandsDoNotProveStaffRules() {
        assertEquals(NoteArticulation.TENUTO, read(16, false, 5, 0, true, false, false));
    }

    @Test
    public void irregularRulesDoNotSupplyAFittedFrame() {
        assertEquals(NoteArticulation.TENUTO, read(16, false, 5, 0, false, true, false));
    }
}

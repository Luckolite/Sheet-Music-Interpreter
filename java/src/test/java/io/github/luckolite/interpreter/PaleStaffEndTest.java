// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pale texture and faint-rule fixtures; no score-derived pixels. */
public class PaleStaffEndTest {
    static ShadedPrintedStaffEndTest.Drawing drawing(int shade, int rules) {
        var d = new ShadedPrintedStaffEndTest.Drawing(false, 5, 1);
        for (int y = 0; y < 220; y++)
            for (int x = 261; x < 480; x++) d.g[y * 480 + x] = (byte) (shade + ((x / 13) % 2) * 16);
        for (int line = 0; line < rules; line++)
            for (int x = 277; x < 420; x++) {
                int at = (70 + line * 16) * 480 + x;
                d.g[at] = (byte) ((d.g[at] & 255) - 6);
            }
        return d;
    }

    static Integer cut(ShadedPrintedStaffEndTest.Drawing d) {
        return d.cut(List.of(20, 260, 420));
    }

    @Test
    public void paleTextureBeyondPrintedBarIsExcluded() {
        assertEquals(Integer.valueOf(260), cut(drawing(220, 0)));
    }

    @Test
    public void lighterTextureDoesNotNeedDarkPixelQuota() {
        assertEquals(Integer.valueOf(260), cut(drawing(238, 0)));
    }

    @Test
    public void fiveVeryFaintContinuingRulesSurvive() {
        assertNull(cut(drawing(238, 5)));
    }

    @Test
    public void threeFaintSurvivingRulesPreventPruning() {
        assertNull(cut(drawing(238, 3)));
    }

    @Test
    public void missingPrintedClosingBarAbstains() {
        var d = drawing(220, 0);
        for (int y = 70; y <= 134; y++) d.g[y * 480 + 260] = (byte) 130;
        assertNull(cut(d));
    }

    @Test
    public void whitePaperStillAbstains() {
        assertNull(cut(new ShadedPrintedStaffEndTest.Drawing(false, 5, 1)));
    }

    @Test
    public void sparseSpecksDoNotProveVariedBackground() {
        var d = new ShadedPrintedStaffEndTest.Drawing(false, 5, 1);
        d.g[78 * 480 + 300] = 20;
        assertNull(cut(d));
    }

    @Test
    public void inputPixelsArePreserved() {
        var d = drawing(220, 0);
        byte[] before = d.g.clone();
        cut(d);
        assertArrayEquals(before, d.g);
    }

    @Test
    public void twoIsolatedTextureColumnsAreNotContinuingRules() {
        var d = drawing(220, 0);
        for (int x : new int[] {310, 370})
            for (int line = 0; line < 5; line++) d.g[(70 + 16 * line) * 480 + x] = 20;
        assertEquals(Integer.valueOf(260), cut(d));
    }

    @Test
    public void manyIsolatedRuleFragmentsKeepAmbiguousEnding() {
        var d = drawing(220, 0);
        for (int x = 280; x < 410; x += 8)
            for (int line = 0; line < 5; line++) d.g[(70 + 16 * line) * 480 + x] = 20;
        assertNull(cut(d));
    }
}

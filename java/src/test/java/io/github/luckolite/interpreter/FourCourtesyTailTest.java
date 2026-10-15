// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic ink, independent of private scores. */
public final class FourCourtesyTailTest {
    static MeterOnlyCourtesyTailTest.Page page() {
        var p = MeterOnlyCourtesyTailTest.page();
        rect(p, 210, 89, 35, 35, 0, 255);
        rect(p, 218, 94, 16, 15, 5, 0);
        rect(p, 222, 98, 7, 7, 0, 255);
        rect(p, 229, 94, 5, 28, 5, 0);
        // Independent key ink to the left of the stacked numerals.
        rect(p, 190, 65, 3, 22, 3, 0);
        rect(p, 193, 78, 5, 5, 3, 0);
        return p;
    }

    static void rect(MeterOnlyCourtesyTailTest.Page p, int x, int y, int w, int h, int l, int g) {
        MeterOnlyCourtesyTailTest.rect(p, x, y, w, h, l, g);
    }

    static boolean detect(MeterOnlyCourtesyTailTest.Page p) {
        return MeterOnlyCourtesyTailTest.detect(p);
    }

    @Test
    public void keyAndFourDenominatorTailHasNoTime() {
        assertTrue(detect(page()));
    }

    @Test
    public void missingShaftRetainsRegion() {
        var p = page();
        rect(p, 229, 109, 5, 13, 0, 255);
        assertFalse(detect(p));
    }

    @Test
    public void missingCrossbarRetainsRegion() {
        var p = page();
        rect(p, 218, 105, 16, 4, 0, 255);
        assertFalse(detect(p));
    }

    @Test
    public void missingNumeratorRetainsRegion() {
        var p = page();
        rect(p, 202, 60, 40, 29, 0, 255);
        assertFalse(detect(p));
    }

    @Test
    public void playedHeadRetainsRegion() {
        var p = page();
        rect(p, 250, 100, 7, 5, 2, 0);
        assertFalse(detect(p));
    }

    @Test
    public void unownedRestRetainsRegion() {
        var p = page();
        rect(p, 250, 100, 7, 8, 1, 0);
        assertFalse(detect(p));
    }

    @Test
    public void noBarlineRetainsRegion() {
        var p = page();
        rect(p, 180, 60, 2, 65, 0, 255);
        assertFalse(detect(p));
    }
}

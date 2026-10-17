// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Independent variations of the authored anchor fixture; no source-score data. */
public final class AnchorChallengeTest {
    @Test
    public void shaftContactingOnlyOneHeadIsNotChordOwned() {
        var g = AnchoredObjectTest.ink(56, false, false);
        for (int x = 84; x <= 94; x++) ExtentChallenge.pixel(g, x, 55, 25);
        assertEquals(0, AnchoredObjectTest.apply(g, -1));
    }

    @Test
    public void contactsOutsideAllHeadScopesDoNotImpersonateChord() {
        var g = AnchoredObjectTest.ink(56, false, false);
        for (int hy : new int[] {58, 82, 100})
            for (int x = 84; x <= 94; x++) ExtentChallenge.pixel(g, x, hy + 7, 25);
        assertEquals(0, AnchoredObjectTest.apply(g, -1));
    }

    @Test
    public void interruptedStraightShaftCannotUseHeadContacts() {
        var g = AnchoredObjectTest.ink(56, true, false);
        g[110 * 260 + 84] = (byte) 255;
        assertEquals(0, AnchoredObjectTest.apply(g, -1));
    }

    @Test
    public void shaftOutsideBoundaryToleranceIsUnknown() {
        assertEquals(0, AnchoredObjectTest.apply(AnchoredObjectTest.ink(66, true, false), -1));
    }

    @Test
    public void nearbyUnconnectedObjectCannotBecomeMeasureOwned() {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int y = 70; y <= 125; y++) ExtentChallenge.pixel(g, 73, y, 25);
        assertEquals(0, AnchoredObjectTest.apply(g, -1));
    }

    @Test
    public void strongerActualHeadContactDoesNotRelaxVisibleWaveLink() {
        var g = AnchoredObjectTest.ink(56, true, false);
        for (int x = 64; x <= 84; x++) ExtentChallenge.pixel(g, x, 87, 25);
        assertEquals(0, AnchoredObjectTest.apply(g, -1));
    }
}

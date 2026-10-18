// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent source-free obstruction/counterexamples; no actual book coordinates. */
public final class NearFlankMeterSeedsTest {
    private static MeterComponentSeedsTest.Page page(boolean left) {
        var p = new MeterComponentSeedsTest.Page();
        // Unknown nearby glyph masks the BROAD witness but is outside both local strips/crop.
        int lo = left ? 176 : 270, hi = left ? 220 : 314;
        for (int y = 100; y <= 178; y++) for (int x = lo; x <= hi; x++) p.raw(x, y);
        return p;
    }

    private static void proof(MeterComponentSeedsTest.Page p, boolean left) {
        var a =
                PrintedDirectionStaff.printedRules(
                        p.gray, MeterComponentSeedsTest.W, MeterComponentSeedsTest.H, 221, 162, 14);
        var b =
                PrintedDirectionStaff.printedRules(
                        p.gray, MeterComponentSeedsTest.W, MeterComponentSeedsTest.H, 269, 162, 14);
        assertNull("obstructed broad witness", left ? a : b);
        assertNotNull("independent remaining broad five", left ? b : a);
    }

    @Test
    public void completeNearFiveOnBothSidesCanReplaceObstructedBroadLeft() {
        var p = page(true);
        proof(p, true);
        assertEquals(1, p.seeds().size());
    }

    @Test
    public void completeNearFiveOnBothSidesCanReplaceObstructedBroadRight() {
        var p = page(false);
        proof(p, false);
        assertEquals(1, p.seeds().size());
    }

    @Test
    public void aSingleBroadWitnessNeverCompensatesMissingNearRule() {
        var p = page(true);
        for (int x = 223; x <= 229; x++)
            p.gray[(MeterComponentSeedsTest.FIRST + 28) * MeterComponentSeedsTest.W + x] =
                    (byte) 255;
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void foreignNearFiveAtDifferentPhaseCannotSupplyTheMissingSide() {
        var p = page(true);
        for (int x = 221; x <= 231; x++)
            for (int k = 0; k < 5; k++) {
                p.gray[(MeterComponentSeedsTest.FIRST + k * 14) * MeterComponentSeedsTest.W + x] =
                        (byte) 255;
                p.gray[
                                (MeterComponentSeedsTest.FIRST + k * 14 + 4)
                                                * MeterComponentSeedsTest.W
                                        + x] =
                        0;
            }
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void parallelUnownedRowInsideSearchBandAbstains() {
        var p = page(true);
        for (int x = 223; x <= 229; x++)
            p.gray[(MeterComponentSeedsTest.FIRST - 3) * MeterComponentSeedsTest.W + x] = 0;
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void mergedVerticalInkIsNotFiveIndependentRawPaths() {
        var p = page(true);
        for (int y = 103; y <= 174; y++) p.gray[y * MeterComponentSeedsTest.W + 225] = 0;
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void twoMissingBroadWitnessesCannotUseLocalBandsAlone() {
        var p = page(true);
        for (int y = 100; y <= 178; y++) for (int x = 270; x <= 314; x++) p.raw(x, y);
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void completeNearCopyProofIsReadOnly() {
        var p = page(true);
        byte[] gray = p.gray.clone(), labels = p.labels.clone();
        assertEquals(1, p.seeds().size());
        assertArrayEquals(gray, p.gray);
        assertArrayEquals(labels, p.labels);
    }
}

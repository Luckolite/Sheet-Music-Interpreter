// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original antialiased opposite-body family with explicit semantic head masks. */
public class HeadMaskedCounterTieTest {
    @Test
    public void anOppositeReturnJoiningBothHeadsCannotSupplyATie() {
        for (int step : new int[] {4, 5})
            for (int bx : new int[] {80, 90})
                for (double offset : new double[] {.25, .4, .55})
                    for (double rise : new double[] {.3, .45, .6, .75})
                        assertFalse(
                                step + "," + bx + "," + offset + "," + rise,
                                ClosedCounterStaffOwnershipTest.proof(
                                        step, bx, offset, rise, true));
    }

    @Test
    public void theCorrespondingPreviouslyProvedOpenContoursRemainOpen() {
        for (double[] v :
                new double[][] {
                    {4, 80, 0.25, 0.3},
                    {4, 80, 0.25, 0.45},
                    {4, 80, 0.25, 0.6},
                    {4, 80, 0.4, 0.3},
                    {4, 80, 0.4, 0.45},
                    {4, 80, 0.55, 0.3},
                    {4, 90, 0.25, 0.3},
                    {4, 90, 0.25, 0.45},
                    {4, 90, 0.25, 0.6},
                    {4, 90, 0.4, 0.3},
                    {4, 90, 0.4, 0.45},
                    {4, 90, 0.55, 0.3},
                    {5, 80, 0.25, 0.75},
                    {5, 80, 0.4, 0.45},
                    {5, 80, 0.4, 0.6},
                    {5, 80, 0.4, 0.75},
                    {5, 80, 0.55, 0.3},
                    {5, 80, 0.55, 0.45},
                    {5, 80, 0.55, 0.6},
                    {5, 80, 0.55, 0.75},
                    {5, 90, 0.4, 0.6},
                    {5, 90, 0.4, 0.75},
                    {5, 90, 0.55, 0.3},
                    {5, 90, 0.55, 0.45},
                    {5, 90, 0.55, 0.6},
                    {5, 90, 0.55, 0.75}
                })
            assertTrue(
                    java.util.Arrays.toString(v),
                    ClosedCounterStaffOwnershipTest.proof(
                            (int) v[0], (int) v[1], v[2], v[3], false));
    }
}

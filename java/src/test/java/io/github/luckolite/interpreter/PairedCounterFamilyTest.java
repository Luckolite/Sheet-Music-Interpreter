// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** The ordinary head/rule phases, independently redrawn at complete printed row ends. */
public class PairedCounterFamilyTest {
    @Test
    public void aClosedOppositeReturnCannotSupplyEitherCompleteSystemBow() throws Exception {
        for (int step : new int[] {4, 5})
            for (int span : new int[] {35, 45})
                for (double offset : new double[] {.25, .4, .55})
                    for (double rise : new double[] {.3, .45, .6, .75})
                        assertFalse(
                                step + "," + span + "," + offset + "," + rise,
                                PairedSystemTieOwnershipTest.detect(
                                        PairedSystemTieOwnershipTest.draw(
                                                "ring", "ring", true, false, 7, .375, 75, 10, step,
                                                offset, rise, span),
                                        false,
                                        false));
    }

    @Test
    public void allPreviouslyProvedOpenFamilyShapesRetainTheirTwoCompleteBows() throws Exception {
        for (double[] v :
                new double[][] {
                    {4, 35, 0.25, 0.3},
                    {4, 35, 0.4, 0.3},
                    {4, 35, 0.4, 0.45},
                    {4, 35, 0.55, 0.3},
                    {4, 45, 0.25, 0.3},
                    {4, 45, 0.4, 0.3},
                    {4, 45, 0.4, 0.45},
                    {4, 45, 0.55, 0.3},
                    {5, 35, 0.4, 0.6},
                    {5, 35, 0.4, 0.75},
                    {5, 35, 0.55, 0.3},
                    {5, 35, 0.55, 0.45},
                    {5, 35, 0.55, 0.6},
                    {5, 35, 0.55, 0.75},
                    {5, 45, 0.4, 0.6},
                    {5, 45, 0.4, 0.75},
                    {5, 45, 0.55, 0.45},
                    {5, 45, 0.55, 0.6},
                    {5, 45, 0.55, 0.75}
                })
            assertTrue(
                    java.util.Arrays.toString(v),
                    PairedSystemTieOwnershipTest.detect(
                            PairedSystemTieOwnershipTest.draw(
                                    "bow", "bow", true, false, 7, .375, 75, 10, (int) v[0], v[2],
                                    v[3], (int) v[1]),
                            false,
                            false));
    }

    public static void main(String[] args) throws Exception {
        for (int step : new int[] {4, 5})
            for (int span : new int[] {35, 45})
                for (double offset : new double[] {.25, .4, .55})
                    for (double rise : new double[] {.3, .45, .6, .75}) {
                        boolean open =
                                PairedSystemTieOwnershipTest.detect(
                                        PairedSystemTieOwnershipTest.draw(
                                                "bow", "bow", true, false, 7, .375, 75, 10, step,
                                                offset, rise, span),
                                        false,
                                        false);
                        boolean closed =
                                PairedSystemTieOwnershipTest.detect(
                                        PairedSystemTieOwnershipTest.draw(
                                                "ring", "ring", true, false, 7, .375, 75, 10, step,
                                                offset, rise, span),
                                        false,
                                        false);
                        System.out.println(
                                step
                                        + ","
                                        + span
                                        + ","
                                        + offset
                                        + ","
                                        + rise
                                        + " open="
                                        + open
                                        + " closed="
                                        + closed);
                    }
    }
}

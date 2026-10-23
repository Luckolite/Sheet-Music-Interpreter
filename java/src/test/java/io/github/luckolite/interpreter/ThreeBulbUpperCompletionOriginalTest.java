// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import org.junit.Test;

/** An unrecognized complete three-bulb rest cannot be cropped into a two-bulb rest. */
public class ThreeBulbUpperCompletionOriginalTest {
    @Test
    public void upperThirdBulbMustNotBeDiscardedByPairProposal() {
        var s = new RawBeamedTwoBulbRestTest.Scene();
        for (int y = 103; y <= 116; y++) {
            int x = (int) Math.round(213 - (y - 120) * .23);
            s.box(x, y, x + 2, y);
        }
        s.oval(208, 106, 5, 4);
        var found = s.rests();
        assertTrue(
                "Three connected bulbs denote a thirty-second rest, never a new .25 rest: " + found,
                found.isEmpty());
    }
}

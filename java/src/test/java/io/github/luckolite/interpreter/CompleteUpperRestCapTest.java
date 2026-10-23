// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Upper component closure is independent of density and unrelated nearby ink. */
public class CompleteUpperRestCapTest {
    @Test
    public void connectedThirdBulbAtDoubledDensityIsNotASixteenth() {
        var s = new RawBeamedTwoBulbRestTest.Scene(2, 0, false);
        for (int y = 103; y <= 116; y++) {
            int x = (int) Math.round(213 - (y - 120) * .23);
            s.box(x, y, x + 2, y);
        }
        s.oval(208, 106, 5, 4);
        s.negative();
    }

    @Test
    public void anExtraConnectedUpperShaftPreventsCroppingEvenWithoutAThirdBulb() {
        var s = new RawBeamedTwoBulbRestTest.Scene();
        for (int y = 104; y <= 116; y++) {
            int x = (int) Math.round(213 - (y - 120) * .23);
            s.box(x, y, x + 2, y);
        }
        s.negative();
    }

    @Test
    public void aDisconnectedUpperBulbDoesNotEraseTheCompleteTwoBulbRest() {
        var s = new RawBeamedTwoBulbRestTest.Scene();
        s.oval(205, 102, 5, 4);
        s.positive();
    }

    @Test
    public void independentUpperTextDoesNotPreventAClosedRestCap() {
        var s = new RawBeamedTwoBulbRestTest.Scene();
        s.box(200, 98, 220, 100);
        s.positive();
    }
}

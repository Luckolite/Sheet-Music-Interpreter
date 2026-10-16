// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;

/** Original paired system curves with a compact bow beginning above its outgoing head. */
public class ShortOutgoingHeadTieTest {
    private SystemBreakTieTest page(
            boolean above, boolean incoming, boolean straight, boolean wrongPitch) {
        var p = new SystemBreakTieTest();
        Arrays.fill(p.gray, (byte) 255);
        for (int top : new int[] {90, 300})
            for (int line = 0; line < 5; line++)
                for (int x = 40; x < 780; x++) p.ink(x, top + line * 16, 4);
        p.head(650, 186);
        int y = wrongPitch ? 388 : 396, side = above ? -1 : 1;
        p.head(180, y);
        p.arc(650, 674, 186, side, straight);
        if (incoming) p.arc(136, 168, y, side, straight);
        return p;
    }

    @Test
    public void compactAboveBowJoinsWithMatchingIncoming() {
        page(true, true, false, false).expected(true);
    }

    @Test
    public void compactBelowBowJoinsWithMatchingIncoming() {
        page(false, true, false, false).expected(true);
    }

    @Test
    public void outgoingAloneDoesNotJoin() {
        page(true, false, false, false).expected(false);
    }

    @Test
    public void straightStrokeDoesNotJoin() {
        page(true, true, true, false).expected(false);
    }

    @Test
    public void differentPitchDoesNotJoin() {
        page(true, true, false, true).expected(false);
    }

    @Test
    public void pixelsRemainUnchanged() {
        var p = page(true, true, false, false);
        var g = p.gray.clone();
        var l = p.labels.clone();
        p.notes();
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original MIN-composited oval and actual neighboring printed staff; no source pixels. */
public class UpperLedgerHollowAliasTest {
    static void one(String kind, double duration) {
        var p = UpperLedgerOwnershipChallenge.scene(kind);
        byte[] g = p.g.clone(), l = p.l.clone();
        var notes =
                OmrScoreInterpreter.extract(
                        p.l,
                        p.g,
                        640,
                        600,
                        List.of(
                                new MeasureRegion(.03f, .97f, .15f, .38f),
                                new MeasureRegion(.03f, .97f, .46f, .90f)));
        var physical =
                notes.stream()
                        .filter(n -> n.pageY() * 600 >= 367 && n.pageY() * 600 <= 383)
                        .toList();
        assertEquals("one actual printed oval must retain one pitch owner", 1, physical.size());
        assertEquals(duration, physical.get(0).unbeamedDurationBeats(), 0);
        assertArrayEquals(g, p.g);
        assertArrayEquals(l, p.l);
    }

    @Test
    public void hollowSplitOvalKeepsOneHalfNote() {
        one("hollow", 2);
    }

    @Test
    public void filledSplitOvalKeepsOneQuarterNote() {
        one("filled", 1);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source-free ellipse on a real neighboring staff; semantic rule may split its prediction. */
public class UpperLedgerNeighborOwnershipTest {
    static long physicalCount(byte[] l, byte[] g) {
        return OmrScoreInterpreter.extract(
                        l,
                        g,
                        640,
                        600,
                        List.of(
                                new MeasureRegion(.03f, .97f, .15f, .38f),
                                new MeasureRegion(.03f, .97f, .46f, .90f)))
                .stream()
                .filter(n -> n.pageY() * 600 >= 367 && n.pageY() * 600 <= 383)
                .count();
    }

    @Test
    public void splitHeadOnNeighboringStaffMustNotBecomeTwoPitches() {
        var p = new UpperLedgerAdversarialProbe.Page("neighbor");
        assertEquals(1, physicalCount(p.l, p.g));
    }

    @Test
    public void intactHeadOnNeighboringStaffKeepsSingleIdentity() {
        var p = new UpperLedgerAdversarialProbe.Page("neighbor");
        p.head(150, 375);
        assertEquals(1, physicalCount(p.l, p.g));
    }
}

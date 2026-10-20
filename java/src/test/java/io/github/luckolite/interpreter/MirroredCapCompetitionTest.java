// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MirroredCapCompetitionTest {
    @Test
    public void longerRightCapCannotShadowIndependentDownStem() {
        var p = new HeavyRestBarFragmentTest.Page();
        p.rect(170, 107, 310, 118, 1);
        p.rect(306, 64, 310, 160, 1);
        p.rect(170, 96, 172, 128, 1);
        p.head(299, 112, 5, 4);
        p.rect(294, 112, 294, 144, 1);
        p.head(100, 136, 10, 7);
        p.rect(90, 88, 90, 136, 1);
        assertEquals(2, p.normalize()[112 * 400 + 299]);
        assertEquals(2, p.analyze().notes().size());
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Uses admitted written clock values; does not infer timing or claim optical recognition. */
public final class ArpeggioMixedReleaseTest {
    @Test
    public void commonAttackKeepsIndependentQuarterAndHalfNoteReleases() {
        assertTrue(ScoreArpeggioNotation.compatibleMember(0, 2, 4, Double.NaN));
        assertTrue(ScoreArpeggioNotation.compatibleMember(0, 1, 4, 0));
        assertTrue(ScoreArpeggioNotation.compatibleMember(0, 2, 4, 0));
    }

    @Test
    public void separateOrInvalidAttacksDoNotBecomeAnArpeggio() {
        assertFalse(ScoreArpeggioNotation.compatibleMember(1, 2, 4, 0));
        assertFalse(ScoreArpeggioNotation.compatibleMember(0, 0, 4, 0));
        assertFalse(ScoreArpeggioNotation.compatibleMember(0, 5, 4, 0));
        assertFalse(ScoreArpeggioNotation.compatibleMember(Double.NaN, 1, 4, 0));
    }
}

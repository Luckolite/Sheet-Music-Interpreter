// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original elliptic engraved bow; terminal curvature meets the rail above its owner. */
public class EllipticalRailPhraseOriginalTest {
    static RailTerminatedPhraseOriginalTest.Scene scene(float phase) {
        var s = new RailTerminatedPhraseOriginalTest.Scene(phase, false);
        double top = s.bottom - 4 * s.g;
        for (int y = 85; y < 145; y++)
            for (int x = 120; x < 198; x++) {
                int covered = 0;
                for (int sy = 0; sy < 4; sy++)
                    for (int sx = 0; sx < 4; sx++) {
                        double xx = x + (sx + .5) / 4 - .5, yy = y + (sy + .5) / 4 - .5;
                        if (xx < 124 || xx > 192) continue;
                        double t = (xx - 124) / 68,
                                u = 2 * t - 1,
                                cy = top - 8 + 8 * t - 14 * Math.sqrt(Math.max(0, 1 - u * u)),
                                half = .6 + Math.sin(Math.PI * t);
                        if (Math.abs(yy - cy) <= half) covered++;
                    }
                s.gray[y * s.w + x] =
                        (byte)
                                Math.min(
                                        s.gray[y * s.w + x] & 255,
                                        Math.round(255 * (1 - covered / 16.)));
            }
        return s;
    }

    @Test
    public void completeEllipseMeetsItsRailAndTwoRecognizedHeads() {
        assertTrue(scene(.5f).captured());
    }

    @Test
    public void wholePixelRailPhasePreservesTheCompleteEllipse() {
        assertTrue(scene(0).captured());
    }

    @Test
    public void quarterPixelRailPhasePreservesTheCompleteEllipse() {
        assertTrue(scene(.25f).captured());
    }

    @Test
    public void absentReceivingOwnerCannotBeSuppliedByAnEllipse() {
        var s = scene(.5f);
        s.heads.remove(1);
        assertFalse(s.captured());
    }

    @Test
    public void absentStartingOwnerCannotBeSuppliedByAnEllipse() {
        var s = scene(.5f);
        s.heads.remove(0);
        assertFalse(s.captured());
    }
}

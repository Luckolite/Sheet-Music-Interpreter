// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original filled, tilted notehead families beside the complete rail-ended ribbon. */
public class RailCapHeadRimOriginalTest {
    static void paintHeadRims(RailTerminatedPhraseOriginalTest.Scene s, double rx, double ry) {
        for (var head : s.heads)
            for (int y = (int) head.y() - 12; y <= (int) head.y() + 12; y++)
                for (int x = (int) head.x() - 15; x <= (int) head.x() + 15; x++) {
                    int covered = 0;
                    for (int sy = 0; sy < 4; sy++)
                        for (int sx = 0; sx < 4; sx++) {
                            double dx = x + (sx + .5) / 4 - .5 - head.x(),
                                    dy = y + (sy + .5) / 4 - .5 - head.y();
                            if ((dx + .35 * dy) * (dx + .35 * dy) / (rx * rx) + dy * dy / (ry * ry)
                                    <= 1) covered++;
                        }
                    int at = y * s.w + x;
                    s.gray[at] =
                            (byte)
                                    Math.min(
                                            s.gray[at] & 255,
                                            Math.round(255 * (1 - covered / 16.)));
                    if (covered >= 4) s.labels[at] = OmrMeasurePostProcessor.NOTEHEAD;
                }
    }

    @Test
    public void independentFilledHeadRimCannotEraseACompleteRailEndedPhrase() {
        for (float phase : new float[] {0, .25f, .5f, .75f})
            for (double[] size : new double[][] {{10.5, 7}, {12, 8}}) {
                var s = new RailTerminatedPhraseOriginalTest.Scene(phase, true);
                paintHeadRims(s, size[0], size[1]);
                assertTrue("phase=" + phase + " rx=" + size[0] + " ry=" + size[1], s.captured());
            }
    }

    @Test
    public void aFilledHeadRimAndRailsCannotManufactureAMissingRibbon() {
        var s = new RailTerminatedPhraseOriginalTest.Scene(.5f, false);
        paintHeadRims(s, 12, 8);
        assertFalse(s.captured());
    }
}

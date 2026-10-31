// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original compact pale-head and hook geometry, independent of source scores. */
public class CompactFadedFlagDurationTest {
    private ScoreNoteEvent read(boolean flag, boolean broken, boolean detached, boolean wide) {
        int w = 300, h = 200;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int y : new int[] {80, 94, 108, 122, 136})
            for (int x = 20; x < 280; x++) {
                g[y * w + x] = 60;
                l[y * w + x] = OmrMeasurePostProcessor.STAFF;
            }
        int rx = wide ? 10 : 6, ry = wide ? 6 : 4;
        for (int y = 122 - ry; y <= 122 + ry; y++)
            for (int x = 120 - rx; x <= 120 + rx; x++)
                if ((x - 120) * (x - 120) / (double) (rx * rx)
                                + (y - 122) * (y - 122) / (double) (ry * ry)
                        <= 1) {
                    l[y * w + x] = OmrMeasurePostProcessor.NOTEHEAD;
                    g[y * w + x] =
                            (byte) ((x >= 118 && x <= 122 && y >= 120 && y <= 124) ? 240 : 50);
                }
        int shaft = 120 + rx;
        for (int y = 66; y <= 122; y++)
            for (int x = shaft - 1; x <= shaft; x++) {
                if (broken && y >= 100 && y <= 112) continue;
                g[y * w + x] = 35;
                l[y * w + x] = OmrMeasurePostProcessor.STEM_OR_REST;
            }
        if (flag)
            for (int d = 0; d <= 29; d++) {
                int x =
                        shaft
                                + (detached ? 12 : 0)
                                + Math.round(12 * (float) Math.sin(Math.PI * d / 29));
                for (int dx = 0; dx < 2; dx++) {
                    g[(66 + d) * w + x + dx] = 35;
                    l[(66 + d) * w + x + dx] = 3;
                }
            }
        byte[] oldG = g.clone(), oldL = l.clone();
        var notes =
                OmrScoreInterpreter.analyze(
                                l, g, w, h, List.of(new MeasureRegion(.1f, .9f, .30f, .76f)))
                        .notes();
        assertArrayEquals(oldG, g);
        assertArrayEquals(oldL, l);
        return notes.stream()
                .filter(n -> Math.abs(n.pageY() - .61f) < .03f)
                .findFirst()
                .orElseThrow();
    }

    @Test
    public void ownRootedFlagOverridesFadedCompactCounter() {
        var n = read(true, false, false, false);
        assertEquals(1, n.beamCount());
        assertEquals(0f, n.unbeamedDurationBeats(), 0f);
    }

    @Test
    public void noFlagPreservesHalf() {
        var n = read(false, false, false, false);
        assertEquals(0, n.beamCount());
        assertEquals(2f, n.unbeamedDurationBeats(), 0f);
    }

    @Test
    public void detachedHookCannotShortenSustain() {
        var n = read(true, false, true, false);
        assertEquals(0, n.beamCount());
        assertTrue(n.unbeamedDurationBeats() >= 2);
    }

    @Test
    public void brokenShaftCannotBorrowFlag() {
        var n = read(true, true, false, false);
        assertEquals(0, n.beamCount());
        assertTrue(n.unbeamedDurationBeats() >= 2);
    }

    @Test
    public void fullSizeSingleOwnedFlagHasEighthValueReviewedOracle() {
        var n = read(true, false, false, true);
        assertEquals(1, n.beamCount());
        assertEquals(0f, n.unbeamedDurationBeats(), 0f);
    }
    @Test
    public void unflaggedFullSizeHollowRetainsHalfValue() {
        var n = read(false, false, false, true);
        assertEquals(0, n.beamCount());
        assertEquals(2f, n.unbeamedDurationBeats(), 0f);
    }
}

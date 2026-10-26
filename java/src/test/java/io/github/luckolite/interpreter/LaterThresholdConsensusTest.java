// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Original SIL-OFL optical glyph with pale edge variation, procedural heads/stems/beam. */
public class LaterThresholdConsensusTest {
    static byte[] scene(int digit, boolean singleLevel) throws Exception {
        byte[] g = InStaffPrimaryBeamContextTest.ink(true, false, 3);
        var r = TupletConsensusFont.glyph(digit, 22, .5f, .15f, .8f);
        int w = InStaffPrimaryBeamContextTest.W, h = InStaffPrimaryBeamContextTest.H;
        for (int y = 0; y < 100; y++)
            for (int x = 0; x < 100; x++) {
                int xx = 229 + x - r.left(), yy = 187 + y - r.top();
                if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                int v = r.gray()[y * 100 + x] & 255;
                // Full source glyph stays intact; the low threshold sees its darker inner outline.
                v =
                        singleLevel
                                ? (v < 165 ? 200 : 255)
                                : Math.min(
                                        255,
                                        70
                                                + v * 185 / 255
                                                + Math.max(0, r.left() + r.width() - 1 - x) * 10
                                                + Math.abs(y - r.top() - r.height() / 2));
                g[yy * w + xx] = (byte) Math.min(g[yy * w + xx] & 255, v);
            }
        return g;
    }

    static List<ScoreNoteEvent> apply(byte[] g) {
        return TripletRhythmDetector.apply(
                InStaffPrimaryBeamContextTest.notes(),
                InStaffPrimaryBeamContextTest.BARS,
                g,
                InStaffPrimaryBeamContextTest.W,
                InStaffPrimaryBeamContextTest.H);
    }

    @Test
    public void laterIndependentLevelsAgreeAfterFirstTruncatedOutline() throws Exception {
        var g = scene(3, false);
        var before = g.clone();
        var result = apply(g);
        assertArrayEquals(before, g);
        for (var n : result) {
            assertEquals(3, n.tupletDivisor());
            assertEquals(2, n.tupletNormalNotes());
        }
    }

    @Test
    public void repeatedMasksAtOnlyOneEligibleLevelDoNotSupplyTwoVotes() throws Exception {
        var g = scene(3, true);
        var before = g.clone();
        assertEquals(InStaffPrimaryBeamContextTest.notes(), apply(g));
        assertArrayEquals(before, g);
    }

    @Test
    public void otherOpticalDigitsAreNotRescuedAsThree() throws Exception {
        for (int digit : new int[] {0, 1, 2, 4, 5, 6, 7, 8, 9})
            assertEquals(
                    "digit" + digit,
                    InStaffPrimaryBeamContextTest.notes(),
                    apply(scene(digit, false)));
    }
}

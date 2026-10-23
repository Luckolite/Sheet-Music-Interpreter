// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original density/occlusion controls retain literal raw samples and source identities. */
public class CompleteRailGapDensityOriginalTest {
    static ScoreSlurSource capture(
            int scale, boolean paperGap, boolean semanticOcclusion, boolean missingOwner) {
        var s = BroadRailCrossingPhraseOriginalTest.scene(.5f);
        int w = s.w * scale, h = s.h * scale;
        byte[] g = new byte[w * h], l = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                g[y * w + x] = s.gray[(y / scale) * s.w + x / scale];
                l[y * w + x] = s.labels[(y / scale) * s.w + x / scale];
            }
        for (int y = 158 * scale; y <= 182 * scale; y++)
            for (int x = 178 * scale; x <= 190 * scale; x++) {
                if (paperGap) g[y * w + x] = (byte) 255;
                if (semanticOcclusion) l[y * w + x] = OmrMeasurePostProcessor.CLEF_OR_KEY;
            }
        var heads = new ArrayList<PrintedSlurOwners.Head>();
        for (var n : s.heads)
            if (!missingOwner || n.owner() == 0)
                heads.add(
                        new PrintedSlurOwners.Head(
                                n.owner(),
                                n.x() * scale,
                                n.y() * scale,
                                n.staffIndex(),
                                n.staffCount(),
                                n.voice(),
                                n.attack(),
                                n.soundingPitch()));
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, s.bottom * scale, s.gap * scale},
                                new float[] {w - 1, s.bottom * scale, s.gap * scale}));
        var frame =
                new PrintedSlurPageCapture.Frame<PrintedSlurOwners.Head>(
                        0, 60 * scale, w - 1, h - 1, track, heads);
        var g0 = g.clone();
        var l0 = l.clone();
        var r = PrintedSlurPageCapture.capture(0, 2, g, l, w, h, List.of(frame), Set.of());
        assertFalse(r.limitReached());
        assertArrayEquals(g0, g);
        assertArrayEquals(l0, l);
        return r.source();
    }

    static void positive(int scale) {
        var s = capture(scale, false, false, false);
        assertEquals(s.toString(), 1, s.spans().size());
        assertEquals(0, s.spans().get(0).start().noteIndex());
        assertEquals(1, s.spans().get(0).end().noteIndex());
    }

    @Test
    public void doubledDensityPreservesTheEntirePrintedBow() {
        positive(2);
    }

    @Test
    public void tripledDensityPreservesTheEntirePrintedBow() {
        positive(3);
    }

    @Test
    public void doubledPaperGapCannotBecomeAStaffSupportedBow() {
        assertTrue(capture(2, true, false, false).spans().isEmpty());
    }

    @Test
    public void semanticGlyphOcclusionCannotBeUsedAsCrossingInk() {
        assertTrue(capture(2, false, true, false).spans().isEmpty());
    }

    @Test
    public void tripledDensityCannotInventTheMissingReceivingOwner() {
        assertTrue(capture(3, false, false, true).spans().isEmpty());
    }
}

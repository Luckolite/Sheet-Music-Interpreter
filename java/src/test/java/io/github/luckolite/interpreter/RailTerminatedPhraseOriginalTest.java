// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic bowed ribbon ending on a printed rail above its receiving head. */
public class RailTerminatedPhraseOriginalTest {
    static class Scene {
        final int w = 360, h = 240;
        final byte[] gray = new byte[w * h], labels = new byte[w * h];
        final float bottom, g = 14;
        final List<PrintedSlurOwners.Head> heads = new ArrayList<>();
        final StaffPitchTrack track;

        Scene(float phase, boolean ribbon) {
            bottom = 180 + phase;
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(new float[] {0, bottom, g}, new float[] {w - 1, bottom, g}));
            float top = bottom - 4 * g;
            heads.add(new PrintedSlurOwners.Head(0, 120, top + g * .5f, 0, 1, 0, 0, 72));
            heads.add(new PrintedSlurOwners.Head(1, 192, top + g, 0, 1, 0, 10080, 71));
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int ink = 0, body = 0;
                    for (int sy = 0; sy < 4; sy++)
                        for (int sx = 0; sx < 4; sx++) {
                            double xx = x + (sx + .5) / 4 - .5, yy = y + (sy + .5) / 4 - .5;
                            boolean dark = false, isHead = false;
                            for (int line = 0; line < 5; line++)
                                if (Math.abs(yy - (bottom - line * g)) <= .55) dark = true;
                            for (var head : heads) {
                                double dx = xx - head.x(), dy = yy - head.y();
                                if ((dx + .35 * dy) * (dx + .35 * dy) / 81 + dy * dy / 36 <= 1) {
                                    dark = true;
                                    isHead = true;
                                }
                                if (Math.abs(xx - (head.x() - 8)) <= .6
                                        && yy >= head.y()
                                        && yy <= head.y() + 46) dark = true;
                            }
                            if (ribbon && xx >= 124 && xx <= 192) {
                                double t = (xx - 124) / 68,
                                        cy = top - 8 + 8 * t - 46 * t * (1 - t),
                                        half = .6 + Math.sin(Math.PI * t);
                                if (Math.abs(yy - cy) <= half) dark = true;
                            }
                            if (dark) ink++;
                            if (isHead) body++;
                        }
                    gray[y * w + x] = (byte) Math.round(255 * (1 - ink / 16.));
                    if (body >= 4) labels[y * w + x] = OmrMeasurePostProcessor.NOTEHEAD;
                }
        }

        boolean captured() {
            var g0 = gray.clone();
            var l0 = labels.clone();
            var frame =
                    new PrintedSlurPageCapture.Frame<PrintedSlurOwners.Head>(
                            0, 50, w - 1, h - 1, track, heads);
            var result =
                    PrintedSlurPageCapture.capture(
                            0, 2, gray, labels, w, h, List.of(frame), Set.of());
            assertFalse(result.limitReached());
            assertArrayEquals(g0, gray);
            assertArrayEquals(l0, labels);
            return result.source().spans().stream()
                    .anyMatch(s -> s.start().noteIndex() == 0 && s.end().noteIndex() == 1);
        }
    }

    @Test
    public void completeBowedRailTerminationPreservesItsTwoHeads() {
        assertTrue(new Scene(.5f, true).captured());
    }

    @Test
    public void integerRailPhasePreservesTheSamePrintedPhrase() {
        assertTrue(new Scene(0, true).captured());
    }

    @Test
    public void railAndHeadsAloneCannotSupplyAPhrase() {
        assertFalse(new Scene(.5f, false).captured());
    }

    @Test
    public void absentReceivingHeadCannotBeInferredFromTheRail() {
        var s = new Scene(.5f, true);
        s.heads.remove(1);
        assertFalse(s.captured());
    }
}

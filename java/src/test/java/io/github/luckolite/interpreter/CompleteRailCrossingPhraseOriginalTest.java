// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original entire bowed ribbon crosses one actual rule before its real terminal cap. */
public class CompleteRailCrossingPhraseOriginalTest {
    static class Scene {
        final int w = 320, h = 240;
        final float gap = 14, bottom;
        final byte[] gray = new byte[w * h], labels = new byte[w * h];
        final List<PrintedSlurOwners.Head> heads = new ArrayList<>();
        final StaffPitchTrack track;

        Scene(float phase) {
            bottom = 170 + phase;
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, bottom, gap},
                                    new float[] {w - 1, bottom, gap}));
            heads.add(new PrintedSlurOwners.Head(0, 45, bottom, 0, 1, 0, 0, 64));
            heads.add(new PrintedSlurOwners.Head(1, 215, bottom - 35, 0, 1, 0, 10080, 69));
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int ink = 0, body = 0;
                    for (int sy = 0; sy < 4; sy++)
                        for (int sx = 0; sx < 4; sx++) {
                            double xx = x + (sx + .5) / 4 - .5, yy = y + (sy + .5) / 4 - .5;
                            boolean dark = false, head = false;
                            for (int r = 0; r < 5; r++)
                                if (Math.abs(yy - (bottom - r * gap)) <= .55) dark = true;
                            for (var n : heads) {
                                double dx = xx - n.x(), dy = yy - n.y();
                                if ((dx + .35 * dy) * (dx + .35 * dy) / 100 + dy * dy / 36 <= 1) {
                                    dark = true;
                                    head = true;
                                }
                                if (Math.abs(xx - (n.x() + 8)) <= .6
                                        && yy <= n.y()
                                        && yy >= n.y() - 48) dark = true;
                            }
                            if (xx >= 50 && xx <= 210) {
                                double t = (xx - 50) / 160,
                                        cy = bottom + 14 - 34 * t + 100 * t * (1 - t);
                                if (Math.abs(yy - cy) <= .65 + Math.sin(Math.PI * t)) dark = true;
                            }
                            if (dark) ink++;
                            if (head) body++;
                        }
                    gray[y * w + x] = (byte) Math.round(255 * (1 - ink / 16.));
                    if (body >= 4) labels[y * w + x] = OmrMeasurePostProcessor.NOTEHEAD;
                }
        }

        ScoreSlurSource capture() {
            var g0 = gray.clone();
            var l0 = labels.clone();
            var frame =
                    new PrintedSlurPageCapture.Frame<PrintedSlurOwners.Head>(
                            0, 60, w - 1, h - 1, track, heads);
            var r =
                    PrintedSlurPageCapture.capture(
                            0, 2, gray, labels, w, h, List.of(frame), Set.of());
            assertFalse(r.limitReached());
            assertArrayEquals(g0, gray);
            assertArrayEquals(l0, labels);
            return r.source();
        }

        void positive() {
            var s = capture();
            assertEquals(s.toString(), 1, s.spans().size());
            assertEquals(0, s.spans().get(0).start().noteIndex());
            assertEquals(1, s.spans().get(0).end().noteIndex());
            assertEquals(ScoreSlurSpan.Placement.BELOW, s.spans().get(0).placement());
        }
    }

    @Test
    public void entireRailCrossingBowKeepsItsActualTwoCaps() {
        new Scene(0).positive();
    }

    @Test
    public void subpixelRailPhaseDoesNotSplitTheSingleBow() {
        new Scene(.5f).positive();
    }

    @Test
    public void absentReceivingOwnerCannotBeInventedAcrossARule() {
        var s = new Scene(0);
        s.heads.remove(1);
        assertTrue(s.capture().spans().isEmpty());
    }
}

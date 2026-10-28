// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public final class BowCapMetamorphicTest {
    private static byte[] base() {
        byte[] r = BowArmCompletenessTest.scene(9);
        int w = BowArmCompletenessTest.W;
        for (int x = 247; x <= 250; x++) r[82 * w + x] = 0;
        for (int x = 246; x <= 251; x++) r[83 * w + x] = 0;
        for (int x = 244; x <= 251; x++) r[84 * w + x] = 0;
        return r;
    }

    private static List<ScoreTechniqueChange> detect(byte[] raw, int w, int h, int scale) {
        return BowDirectionDetector.detect(
                new byte[raw.length],
                raw,
                w,
                h,
                List.of(
                        new PlayingTechniqueDetector.Staff(
                                160 * scale, 240 * scale, 20 * scale, 0, 1)),
                List.of(new MeasureRegion(0, 1, 0, 1)),
                List.of(BowArmCompletenessTest.note()));
    }

    private static byte[] doubled(byte[] in) {
        int w = BowArmCompletenessTest.W, h = BowArmCompletenessTest.H, bw = 2 * w;
        byte[] big = new byte[in.length * 4];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                for (int dy = 0; dy < 2; dy++)
                    for (int dx = 0; dx < 2; dx++)
                        big[(2 * y + dy) * bw + 2 * x + dx] = in[y * w + x];
        return big;
    }

    @Test
    public void horizontalMirrorCapRemainsUpBow() {
        int w = BowArmCompletenessTest.W, h = BowArmCompletenessTest.H;
        byte[] in = base(), m = new byte[in.length];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) m[y * w + (w - 1 - x)] = in[y * w + x];
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .5f, 0, 1, ScoreTechniqueChange.UP_BOW)),
                detect(m, w, h, 1));
    }

    @Test
    public void doubledRasterCapRemainsUpBow() {
        int w = BowArmCompletenessTest.W, h = BowArmCompletenessTest.H;
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .5f, 0, 1, ScoreTechniqueChange.UP_BOW)),
                detect(doubled(base()), w * 2, h * 2, 2));
    }

    @Test
    public void doubledRasterUnmodifiedThickVRemainsUpBow() {
        int w = BowArmCompletenessTest.W, h = BowArmCompletenessTest.H;
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .5f, 0, 1, ScoreTechniqueChange.UP_BOW)),
                detect(doubled(BowArmCompletenessTest.scene(9)), w * 2, h * 2, 2));
    }

    private static List<NoteArticulationDetector.BowMark> skewCap(
            int scale, boolean mirror, int peers) {
        int w = scale * BowArmCompletenessTest.W, h = scale * BowArmCompletenessTest.H;
        byte[] input = scale == 1 ? base() : doubled(base()), raw = new byte[input.length];
        Arrays.fill(raw, (byte) 255);
        // Translate complete literal rows to make the apex and bounding-box center differ.
        // No pixel is deleted and all original ink, cap members, and white gaps remain.
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int nx = x + (int) Math.round((y - 74 * scale) * -.1);
                if (nx >= 0 && nx < w) raw[y * w + (mirror ? w - 1 - nx : nx)] = input[y * w + x];
            }
        int left = w, right = -1, top = h, bottom = -1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((raw[y * w + x] & 255) < 155) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
        double tip = 0;
        int tipCount = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((raw[y * w + x] & 255) < 155 && y >= bottom - (bottom - top) * .15) {
                    tip += x;
                    tipCount++;
                }
        tip /= tipCount;
        float gap = 20 * scale,
                center = (left + right) * .5f,
                tipPeer = (float) (center + Math.signum(tip - center) * (.7 * gap + .5 * scale));
        assertTrue(Math.abs(center - tipPeer) > .7 * gap);
        assertTrue(Math.abs(tip - tipPeer) <= .7 * gap);
        float[] xs = peers == 0 ? new float[] {tipPeer} : new float[] {center, tipPeer};
        var anchors = new ArrayList<NoteArticulationDetector.Anchor>();
        var events = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < xs.length; i++) {
            anchors.add(new NoteArticulationDetector.Anchor(xs[i], 180 * scale, gap, 0));
            events.add(BowTipOwnershipTest.event(peers == 1 && i == 1 ? .6f : .4f));
        }
        byte[] before = raw.clone(), labels = new byte[raw.length];
        var result =
                NoteArticulationDetector.bowMarks(
                        labels,
                        raw,
                        w,
                        h,
                        anchors,
                        events,
                        List.of(
                                new PlayingTechniqueDetector.Staff(
                                        160 * scale, 240 * scale, gap, 0, 1)));
        assertArrayEquals(before, raw);
        assertArrayEquals(new byte[raw.length], labels);
        return result;
    }

    @Test
    public void capCertifiedShapeUsesExistingTipOwner() {
        for (int scale : new int[] {1, 2})
            for (boolean mirror : new boolean[] {false, true})
                assertEquals(
                        "scale=" + scale + " mirror=" + mirror,
                        List.of(
                                new NoteArticulationDetector.BowMark(
                                        0, ScoreTechniqueChange.UP_BOW)),
                        skewCap(scale, mirror, 0));
    }

    @Test
    public void capCertifiedTipRivalCompetesWithCentroidOwner() {
        for (int scale : new int[] {1, 2})
            for (boolean mirror : new boolean[] {false, true})
                assertTrue(
                        "scale=" + scale + " mirror=" + mirror,
                        skewCap(scale, mirror, 1).isEmpty());
    }

    @Test
    public void capCertifiedSameAttackPeersRemainOneBow() {
        for (int scale : new int[] {1, 2})
            for (boolean mirror : new boolean[] {false, true})
                assertEquals(
                        "scale=" + scale + " mirror=" + mirror,
                        List.of(
                                new NoteArticulationDetector.BowMark(
                                        0, ScoreTechniqueChange.UP_BOW)),
                        skewCap(scale, mirror, 2));
    }
}

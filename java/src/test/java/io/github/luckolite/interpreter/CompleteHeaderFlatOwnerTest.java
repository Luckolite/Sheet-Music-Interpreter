// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural staff/clef/flat drawings; no score-derived pixels or outlines. */
public class CompleteHeaderFlatOwnerTest {
    static final int W = 640, H = 300, G = 16;
    static final List<MeasureRegion> M = List.of(new MeasureRegion(.105f, .95f, .1f, .9f));

    static final class Page {
        byte[] labels = new byte[W * H], gray = new byte[W * H];

        Page(boolean clef, int pitchOffset, int flatOffset, boolean body) {
            Arrays.fill(gray, (byte) 255);
            for (int x = 10; x < W - 10; x++) for (int y = 100; y <= 164; y += G) pixel(x, y, 4, 0);
            if (clef)
                for (int y = 72; y <= 194; y++)
                    for (int x = 25; x <= 51; x++)
                        if (x < 28 || x > 48 || y < 75 || y > 191) pixel(x, y, 3, 0);
            if (body) flat(76 + flatOffset, 132 + pitchOffset);
            note(220, 132, false, false);
            note(320, 124, true, false);
            shear(.06f);
        }

        void shear(float slope) {
            byte[] l = new byte[W * H], g = new byte[W * H];
            Arrays.fill(g, (byte) 255);
            for (int x = 0; x < W; x++)
                for (int y = 0; y < H; y++) {
                    int yy = y + Math.round(slope * (x - W * .5f));
                    if (yy >= 0 && yy < H) {
                        l[yy * W + x] = labels[y * W + x];
                        g[yy * W + x] = gray[y * W + x];
                    }
                }
            labels = l;
            gray = g;
        }

        void pixel(int x, int y, int label, int tone) {
            labels[y * W + x] = (byte) label;
            gray[y * W + x] = (byte) tone;
        }

        void flat(int x, int pitch) {
            for (int y = pitch - 29; y <= pitch + 10; y++)
                for (int xx = x; xx <= x + 2; xx++) pixel(xx, y, 5, 0);
            for (int xx = x + 1; xx <= x + 14; xx++) {
                double t = (xx - x - 1) / 13.;
                int upper = (int) Math.round(pitch - 9 + 7 * t * t),
                        lower = (int) Math.round(pitch + 10 - 12 * t);
                for (int y : new int[] {upper - 1, upper, upper + 1, lower - 1, lower, lower + 1})
                    pixel(xx, y, 5, 0);
            }
            for (int yy = pitch - 4; yy <= pitch + 4; yy++)
                for (int xx = x + 3; xx <= x + 13; xx++)
                    if (Math.pow((xx - x - 8) / 5., 2) + Math.pow((yy - pitch) / 4., 2) <= 1)
                        labels[yy * W + xx] = 2;
        }

        void note(int cx, int cy, boolean down, boolean hollow) {
            for (int y = cy - 6; y <= cy + 6; y++)
                for (int x = cx - 9; x <= cx + 9; x++) {
                    double v = Math.pow((x - cx) / 9., 2) + Math.pow((y - cy) / 6., 2);
                    if (v <= 1) {
                        pixel(x, y, 2, 0);
                        if (hollow && v < .45) gray[y * W + x] = (byte) 255;
                    }
                }
            for (int y = down ? cy : cy - 48; y <= (down ? cy + 48 : cy); y++)
                pixel(cx + (down ? -9 : 9), y, 3, 0);
        }

        OmrScoreInterpreter.Analysis run() {
            return OmrScoreInterpreter.analyze(labels, gray, W, H, M);
        }

        boolean near(OmrScoreInterpreter.Analysis a, int x) {
            return a.notes().stream()
                    .anyMatch(
                            n ->
                                    Math.abs(
                                                    (M.get(0).left()
                                                                            + (M.get(0).right()
                                                                                            - M.get(
                                                                                                            0)
                                                                                                    .left())
                                                                                    * n
                                                                                            .positionInMeasure())
                                                                    * W
                                                            - x)
                                            < 12);
        }
    }

    @Test
    public void completeRawFlatOwnsClassifiedBowl() {
        var p = new Page(true, 0, 0, true);
        var a = p.run();
        assertFalse(p.near(a, 85));
        assertTrue(p.near(a, 220));
        assertTrue(p.near(a, 320));
        assertEquals(List.of(new ScoreKeyChange(0, -1)), a.keyChanges());
    }

    @Test
    public void noClefCannotOwnBowl() {
        var p = new Page(false, 0, 0, true);
        assertTrue(p.near(p.run(), 85));
    }

    @Test
    public void wrongSignaturePitchCannotOwnBowl() {
        var p = new Page(true, 24, 0, true);
        assertTrue(p.near(p.run(), 85));
    }

    @Test
    public void distantLocalAccidentalCannotOwnBowl() {
        var p = new Page(true, 0, 80, true);
        assertTrue(p.near(p.run(), 165));
    }

    @Test
    public void trueUpStemQuarterSurvives() {
        var p = new Page(true, 0, 0, false);
        p.note(85, 132, false, false);
        assertTrue(p.near(p.run(), 85));
    }

    @Test
    public void trueDownStemQuarterSurvives() {
        var p = new Page(true, 0, 0, false);
        p.note(85, 132, true, false);
        assertTrue(p.near(p.run(), 85));
    }

    @Test
    public void trueHollowHalfSurvives() {
        var p = new Page(true, 0, 0, false);
        p.note(85, 132, false, true);
        assertTrue(p.near(p.run(), 85));
    }

    @Test
    public void sourceArraysRemainUnchanged() {
        var p = new Page(true, 0, 0, true);
        var l = p.labels.clone();
        var g = p.gray.clone();
        p.run();
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }

    @Test
    public void oppositeStaffSlopeKeepsIndependentFlatProof() {
        var p = new Page(true, 0, 0, true);
        p.shear(-.12f);
        var a = p.run();
        assertFalse(p.near(a, 85));
        assertTrue(p.near(a, 220));
        assertEquals(List.of(new ScoreKeyChange(0, -1)), a.keyChanges());
    }

    @Test
    public void nearbyGenuineAttackVetoesHeaderOwnership() {
        var p = new Page(true, 0, 0, true);
        p.note(112, 120, false, false);
        var a = p.run();
        assertTrue(p.near(a, 85));
        assertTrue(p.near(a, 112));
    }

    @Test
    public void missingRawSpineCannotOwnBowl() {
        var p = new Page(true, 0, 0, true);
        for (int y = 70; y < 111; y++)
            for (int x = 74; x <= 80; x++) p.gray[y * W + x] = (byte) 255;
        assertTrue(p.near(p.run(), 85));
    }

    @Test
    public void genuineNotesAtCanonicalFlatPitchSurvive() {
        for (boolean hollow : new boolean[] {false, true})
            for (boolean down : new boolean[] {false, true}) {
                var p = new Page(true, 0, 0, false);
                // Page has already sheared its staff; put the genuine head on its local B4 line.
                int y = 132 + Math.round(.06f * (85 - W * .5f));
                p.note(85, y, down, hollow);
                var notes =
                        p.run().notes().stream()
                                .filter(
                                        n ->
                                                Math.abs(
                                                                (M.get(0).left()
                                                                                        + (M.get(0)
                                                                                                                .right()
                                                                                                        - M
                                                                                                                .get(
                                                                                                                        0)
                                                                                                                .left())
                                                                                                * n
                                                                                                        .positionInMeasure())
                                                                                * W
                                                                        - 85)
                                                        < 12)
                                .toList();
                assertEquals(1, notes.size());
                assertEquals(4, notes.get(0).staffStep());
            }
    }
}

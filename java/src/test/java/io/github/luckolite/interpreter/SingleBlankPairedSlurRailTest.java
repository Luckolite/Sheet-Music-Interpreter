// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic two-rail group and a separately bowed stroke, with no score pixels. */
public class SingleBlankPairedSlurRailTest {
    int w = 320, h = 280, x = 164;
    float scale = 1;
    byte[] gray, labels;
    boolean rotated;

    void pixel(int xx, int yy, int ink, int kind) {
        int startX = Math.round(xx * scale), startY = Math.round(yy * scale);
        int endX = Math.round((xx + 1) * scale), endY = Math.round((yy + 1) * scale);
        for (int py = startY; py < Math.max(startY + 1, endY); py++)
            for (int px = startX; px < Math.max(startX + 1, endX); px++) {
                int a = rotated ? w - 1 - px : px, b = rotated ? h - 1 - py : py;
                if (a >= 0 && a < w && b >= 0 && b < h) {
                    gray[b * w + a] = (byte) ink;
                    labels[b * w + a] = (byte) kind;
                }
            }
    }

    void draw(boolean rotate, boolean bowed, boolean connected, int rails) {
        rotated = rotate;
        w = Math.round(320 * scale);
        h = Math.round(280 * scale);
        gray = new byte[w * h];
        labels = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int rule = 0; rule < 5; rule++)
            for (int xx = 20; xx < 300; xx++)
                for (int yy = 160 + rule * 16; yy <= 161 + rule * 16; yy++) pixel(xx, yy, 120, 4);
        for (int yy = 193; yy <= 207; yy++)
            for (int xx = 144; xx <= 164; xx++)
                if (Math.pow((xx - 154) / 10., 2) + Math.pow((yy - 200) / 7., 2) <= 1)
                    pixel(xx, yy, 20, 2);
        for (int yy = 143; yy < 200; yy++) pixel(x, yy, 20, 1);
        for (int rail = 0; rail < rails; rail++)
            for (int xx = 110; xx <= x; xx++) {
                int center = 146 + rail * 12 + Math.round((x - xx) * .2f);
                for (int yy = center - 3; yy <= center + 3; yy++) pixel(xx, yy, 20, 5);
            }
        for (int xx = x - 52; xx <= x + 52; xx++) {
            int dx = xx - x, center = bowed ? 139 + Math.round(dx * dx * .002f - dx * .10f) : 139;
            for (int yy = center - 2; yy <= center + 2; yy++) pixel(xx, yy, 20, 5);
        }
        if (connected) for (int yy = 137; yy <= 144; yy++) pixel(x, yy, 20, 1);
    }

    ScoreNoteEvent event() {
        var a =
                OmrScoreInterpreter.analyze(
                        labels, gray, w, h, List.of(new MeasureRegion(.04f, .96f, .04f, .96f)));
        assertEquals(1, a.notes().size());
        return a.notes().get(0);
    }

    @Test
    public void upperTwoRailsRejectSingleBlankBowedThirdBand() {
        draw(false, true, false, 2);
        assertEquals(2, event().beamCount());
    }

    @Test
    public void lowerTwoRailsRejectSingleBlankBowedThirdBand() {
        draw(true, true, false, 2);
        assertEquals(2, event().beamCount());
    }

    @Test
    public void upperJoinedCurveRemainsThree() {
        draw(false, true, true, 2);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void lowerJoinedCurveRemainsThree() {
        draw(true, true, true, 2);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void upperStraightThirdRailRemainsThree() {
        draw(false, false, false, 2);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void lowerStraightThirdRailRemainsThree() {
        draw(true, false, false, 2);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void brokenUpperCurveCannotProveRemoval() {
        draw(false, true, false, 2);
        for (int yy = 125; yy <= 150; yy++) pixel(x + 30, yy, 255, 0);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void brokenLowerCurveCannotProveRemoval() {
        draw(true, true, false, 2);
        for (int yy = 125; yy <= 150; yy++) pixel(x + 30, yy, 255, 0);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void oldUpperOneRailProofRemainsOne() {
        draw(false, true, false, 1);
        assertEquals(1, event().beamCount());
    }

    @Test
    public void oldLowerOneRailProofRemainsOne() {
        draw(true, true, false, 1);
        assertEquals(1, event().beamCount());
    }

    @Test
    public void singleBlankProofDoesNotMutateEitherRaster() {
        draw(false, true, false, 2);
        byte[] g = gray.clone(), l = labels.clone();
        event();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    @Test
    public void inkAtUpperCutEdgePreventsProof() {
        draw(false, true, false, 2);
        pixel(x + 1, 142, 20, 1);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void inkAtLowerCutEdgePreventsProof() {
        draw(true, true, false, 2);
        pixel(x + 1, 142, 20, 1);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void bothUpperPairProbesMustRetainTwoRails() {
        draw(false, true, false, 2);
        for (int yy = 153; yy <= 165; yy++) pixel(x - 10, yy, 255, 0);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void bothLowerPairProbesMustRetainTwoRails() {
        draw(true, true, false, 2);
        for (int yy = 153; yy <= 165; yy++) pixel(x - 10, yy, 255, 0);
        assertEquals(3, event().beamCount());
    }

    @Test
    public void largerUpperPairKeepsPrintedTwoRails() {
        scale = 1.25f;
        draw(false, true, false, 2);
        assertEquals(2, event().beamCount());
    }

    @Test
    public void largerLowerPairKeepsPrintedTwoRails() {
        scale = 1.25f;
        draw(true, true, false, 2);
        assertEquals(2, event().beamCount());
    }
}

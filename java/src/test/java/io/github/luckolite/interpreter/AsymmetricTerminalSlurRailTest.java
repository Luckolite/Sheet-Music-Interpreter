// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original asymmetric slur/terminal rail, straight secondary, joined shaft and rooted flag. */
public class AsymmetricTerminalSlurRailTest {
    static final int W = 320, H = 280, X = 164;
    byte[] gray, labels;
    boolean rotated;
    int curveCenter = 139;

    void pixel(int x, int y, int ink, int label) {
        if (rotated) {
            x = W - 1 - x;
            y = H - 1 - y;
        }
        gray[y * W + x] = (byte) ink;
        labels[y * W + x] = (byte) label;
    }

    void draw(boolean rotate, boolean bowed, boolean connected) {
        rotated = rotate;
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 20; x < 300; x++)
                for (int y = 160 + line * 16; y <= 161 + line * 16; y++) pixel(x, y, 120, 4);
        for (int y = 193; y <= 207; y++)
            for (int x = 144; x <= 164; x++)
                if (Math.pow((x - 154) / 10., 2) + Math.pow((y - 200) / 7., 2) <= 1)
                    pixel(x, y, 20, 2);
        for (int y = 143; y < 200; y++) pixel(X, y, 20, 1);
        for (int x = 110; x <= X; x++) {
            int center = 146 + Math.round((X - x) * .2f);
            for (int y = center - 3; y <= center + 3; y++) pixel(x, y, 20, 5);
        }
        for (int x = X - 48; x <= X + 48; x++) {
            int dx = x - X;
            int center = bowed ? curveCenter + Math.round(dx * dx * .002f - dx * .10f) : 139;
            for (int y = center - 2; y <= center + 2; y++) pixel(x, y, 20, 5);
        }
        if (connected) for (int y = 137; y <= 144; y++) pixel(X, y, 20, 1);
    }

    ScoreNoteEvent event() {
        var score =
                OmrScoreInterpreter.analyze(
                        labels,
                        gray,
                        W,
                        H,
                        java.util.List.of(new MeasureRegion(.04f, .96f, .04f, .96f)));
        assertEquals(1, score.notes().size());
        return score.notes().get(0);
    }

    @Test
    public void upperSeparatedCurveCannotExtendInitialShaft() {
        draw(false, true, false);
        assertEquals(1, event().beamCount());
    }

    @Test
    public void lowerSeparatedCurveCannotExtendInitialShaft() {
        draw(true, true, false);
        assertEquals(1, event().beamCount());
    }

    @Test
    public void joinedUpperShaftRetainsTwoBeams() {
        draw(false, true, true);
        assertEquals(2, event().beamCount());
    }

    @Test
    public void joinedLowerShaftRetainsTwoBeams() {
        draw(true, true, true);
        assertEquals(2, event().beamCount());
    }

    @Test
    public void straightUpperSecondaryRailRetainsTwoBeams() {
        draw(false, false, false);
        assertEquals(2, event().beamCount());
    }

    @Test
    public void straightLowerSecondaryRailRetainsTwoBeams() {
        draw(true, false, false);
        assertEquals(2, event().beamCount());
    }

    @Test
    public void brokenUpperCurveCannotSupplyProof() {
        draw(false, true, false);
        for (int y = 125; y <= 150; y++) pixel(X + 30, y, 255, 0);
        assertEquals(2, event().beamCount());
    }

    @Test
    public void brokenLowerCurveCannotSupplyProof() {
        draw(true, true, false);
        for (int y = 125; y <= 150; y++) pixel(X + 30, y, 255, 0);
        assertEquals(2, event().beamCount());
    }

    @Test
    public void bothRastersRemainExact() {
        draw(false, true, false);
        byte[] g = gray.clone(), l = labels.clone();
        assertEquals(1, event().beamCount());
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    void drawFlag(boolean rotate) {
        draw(rotate, false, false);
        for (int y = 125; y < 160; y++) for (int x = 100; x <= 215; x++) pixel(x, y, 255, 0);
        for (int y = 140; y < 200; y++) pixel(X, y, 20, 1);
        for (int d = 0; d <= 34; d++) {
            int offset = Math.round(20 * (float) Math.sin(Math.PI * d / 34));
            int x = rotate ? X - offset - 2 : X + offset;
            for (int dx = 0; dx < 3; dx++) pixel(x + dx, 140 + d, 20, 5);
        }
    }

    @Test
    public void rootedUpperEighthFlagRemainsOneBeam() {
        drawFlag(false);
        assertEquals(1, event().beamCount());
    }

    @Test
    public void rootedLowerEighthFlagRemainsOneBeam() {
        drawFlag(true);
        assertEquals(1, event().beamCount());
    }
}

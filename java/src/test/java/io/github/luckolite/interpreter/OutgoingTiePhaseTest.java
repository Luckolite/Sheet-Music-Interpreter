// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-system note geometry with separate returning tie curves. */
public class OutgoingTiePhaseTest {
    static final int W = 800, H = 480;
    byte[] labels = new byte[W * H], gray = new byte[W * H];

    OutgoingTiePhaseTest setup(int mode, boolean above) {
        Arrays.fill(gray, (byte) 255);
        for (int top : new int[] {90, 300})
            for (int line = 0; line < 5; line++)
                for (int x = 40; x < 780; x++) ink(x, top + line * 16, 4);
        int currentY = mode == 3 ? 388 : 396;
        head(650, 186);
        head(180, currentY);
        int side = above ? -1 : 1;
        if (mode != 1) arc(OUTLEFT, OUTLEFT + OUTSPAN, 186, side, mode == 5);
        if (mode != 2) arc(136, 168, currentY, mode == 4 ? -side : side, mode == 5);
        return this;
    }

    void ink(int x, int y, int label) {
        labels[y * W + x] = (byte) label;
        gray[y * W + x] = 0;
    }

    void head(int cx, int cy) {
        for (int y : new int[] {cy - 16, cy})
            for (int x = cx - 12; x <= cx + 12; x++) gray[y * W + x] = 0;
        for (int y = cy - 5; y <= cy + 5; y++)
            for (int x = cx - 7; x <= cx + 7; x++)
                if (Math.pow((x - cx) / 7d, 2) + Math.pow((y - cy) / 5d, 2) <= 1) ink(x, y, 2);
        for (int y = cy - 45; y < cy; y++) ink(cx + 7, y, 1);
    }

    void arc(int left, int right, int cy, int side, boolean straight) {
        for (int x = left; x <= right; x++) {
            float t = (x - left) / (float) (right - left);
            int y =
                    Math.round(
                            cy
                                    + side
                                            * (8
                                                    + (straight
                                                            ? 0
                                                            : (left > 600 ? BOW : 6)
                                                                    * 4
                                                                    * t
                                                                    * (1 - t))));
            ink(x, y, 5);
            ink(x, y + 1, 5);
        }
    }

    List<ScoreNoteEvent> notes() {
        return OmrScoreInterpreter.extract(
                labels,
                gray,
                W,
                H,
                List.of(
                        new MeasureRegion(.05f, .975f, 70f / H, 210f / H),
                        new MeasureRegion(.05f, .975f, 280f / H, 420f / H)));
    }

    void expected(boolean tied) {
        var notes = notes();
        assertEquals(2, notes.size());
        assertEquals(tied, notes.get(1).tiedFromPrevious());
    }

    int OUTLEFT, OUTSPAN, BOW;
    static final int[][] CASES = {{659, 26, 4}, {659, 29, 4}, {659, 35, 3}};

    OutgoingTiePhaseTest phase(int[] c) {
        OUTLEFT = c[0];
        OUTSPAN = c[1];
        BOW = c[2];
        return this;
    }

    @Test
    public void outgoingPhaseAbove() {
        for (int[] c : CASES) new OutgoingTiePhaseTest().phase(c).setup(0, true).expected(true);
    }

    @Test
    public void outgoingPhaseBelow() {
        for (int[] c : new int[][] {{659, 26, 4}, {659, 29, 4}})
            new OutgoingTiePhaseTest().phase(c).setup(0, false).expected(true);
    }

    @Test
    public void incomingAlone() {
        for (int[] c : CASES) new OutgoingTiePhaseTest().phase(c).setup(1, true).expected(false);
    }

    @Test
    public void outgoingAlone() {
        for (int[] c : CASES) new OutgoingTiePhaseTest().phase(c).setup(2, true).expected(false);
    }

    @Test
    public void differentPitch() {
        for (int[] c : CASES) new OutgoingTiePhaseTest().phase(c).setup(3, true).expected(false);
    }

    @Test
    public void oppositeSides() {
        for (int[] c : CASES) new OutgoingTiePhaseTest().phase(c).setup(4, true).expected(false);
    }

    @Test
    public void flatStrokes() {
        for (int[] c : CASES) new OutgoingTiePhaseTest().phase(c).setup(5, true).expected(false);
    }

    @Test
    public void sourceImmutable() {
        for (int[] c : CASES) {
            var scene = new OutgoingTiePhaseTest().phase(c).setup(0, true);
            var l = scene.labels.clone();
            var g = scene.gray.clone();
            scene.notes();
            assertArrayEquals(l, scene.labels);
            assertArrayEquals(g, scene.gray);
        }
    }
}

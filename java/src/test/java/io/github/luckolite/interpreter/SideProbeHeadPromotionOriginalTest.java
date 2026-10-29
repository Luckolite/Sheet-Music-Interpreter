// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Three authored ovals on one sloped rail; the middle oval is not another beam. */
public class SideProbeHeadPromotionOriginalTest {
    static final int W = 400, H = 280;

    static class Page {
        byte[] labels = new byte[W * H], gray = new byte[W * H];

        Page(int rails, boolean overlap) {
            Arrays.fill(gray, (byte) 255);
            for (int y = 100; y <= 156; y += 14) for (int x = 20; x < 380; x++) pixel(x, y, 4);
            for (int i = -1; i <= 1; i++)
                for (int y = 154 - i * 4; y <= 186 - i * 4; y++) pixel(201 + i * 28, y, 1);
            for (int rail = 0; rail < rails; rail++)
                for (int x = 173; x <= 229; x++) {
                    int bottom =
                            186
                                    + Math.round((201 - x) * 4 / 28f)
                                    - (overlap && rail == 1 ? 25 : rail * 10);
                    for (int y = bottom - 4; y <= bottom; y++) pixel(x, y, 5);
                }
            for (int i = -1; i <= 1; i++) oval(210 + i * 28, 154 - i * 4);
        }

        void pixel(int x, int y, int kind) {
            labels[y * W + x] = (byte) kind;
            gray[y * W + x] = 0;
        }

        void oval(int cx, int cy) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10., 2) + Math.pow((y - cy) / 8., 2) <= 1)
                        pixel(x, y, 2);
        }

        int[] counts(int scale, boolean mirror) {
            int width = W * scale, height = H * scale;
            byte[] l = new byte[width * height], g = new byte[l.length];
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) {
                    int xx = mirror ? width - 1 - x : x, yy = mirror ? height - 1 - y : y;
                    l[yy * width + xx] = labels[y / scale * W + x / scale];
                    g[yy * width + xx] = gray[y / scale * W + x / scale];
                }
            byte[] lb = l.clone(), gb = g.clone();
            var notes =
                    OmrScoreInterpreter.analyze(
                                    l,
                                    g,
                                    width,
                                    height,
                                    List.of(new MeasureRegion(.05f, .95f, .05f, .95f)))
                            .notes();
            assertArrayEquals(lb, l);
            assertArrayEquals(gb, g);
            assertEquals(3, notes.size());
            return notes.stream().mapToInt(ScoreNoteEvent::beamCount).toArray();
        }
    }

    @Test
    public void middleBodyCannotPromoteOneRailToTwo() {
        assertArrayEquals(new int[] {1, 1, 1}, new Page(1, false).counts(1, false));
    }

    @Test
    public void reflectedSingleRailKeepsItsValue() {
        assertArrayEquals(new int[] {1, 1, 1}, new Page(1, false).counts(1, true));
    }

    @Test
    public void doubledSingleRailKeepsItsValue() {
        assertArrayEquals(new int[] {1, 1, 1}, new Page(1, false).counts(2, false));
    }

    @Test
    public void reflectedDoubledSingleRailKeepsItsValue() {
        assertArrayEquals(new int[] {1, 1, 1}, new Page(1, false).counts(2, true));
    }

    @Test
    public void realSecondRailRemains() {
        assertArrayEquals(new int[] {2, 2, 2}, new Page(2, false).counts(1, false));
    }

    @Test
    public void reflectedSecondRailRemains() {
        assertArrayEquals(new int[] {2, 2, 2}, new Page(2, false).counts(1, true));
    }

    @Test
    public void doubledSecondRailRemains() {
        assertArrayEquals(new int[] {2, 2, 2}, new Page(2, false).counts(2, false));
    }

    @Test
    public void realRailMayCrossItsHeadBody() {
        assertArrayEquals(new int[] {2, 2, 2}, new Page(2, true).counts(1, false));
    }
}

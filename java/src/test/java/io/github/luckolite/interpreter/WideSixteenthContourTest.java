// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural paired bulbs and diagonal, at several horizontal proportions. */
public class WideSixteenthContourTest {
    static final int W = 400, H = 240;

    static byte[] page(double stretch) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 80; y <= 144; y += 16) for (int x = 20; x < 380; x++) g[y * W + x] = 0;
        for (int cy : new int[] {102, 118})
            for (int y = cy - 5; y <= cy + 5; y++)
                for (int x = 171; x <= 183; x++)
                    if (Math.pow((x - 177) / 6., 2) + Math.pow((y - cy) / 5., 2) <= 1) {
                        int xx = 170 + (int) Math.round((x - 170) * stretch);
                        for (int dx = 0; dx < Math.ceil(stretch); dx++) g[y * W + xx + dx] = 0;
                    }
        for (int y = 99; y <= 144; y++) {
            int x = 185 - (y - 99) * 12 / 45;
            int xx = 170 + (int) Math.round((x - 170) * stretch);
            for (int dx = 0; dx < Math.ceil(stretch * 2); dx++) g[y * W + xx + dx] = 0;
        }
        return g;
    }

    static List<ScoreRestEvent> detect(byte[] g) {
        return SixteenthRestDetector.detect(
                g,
                W,
                H,
                List.of(new MeasureRegion(0, 1, .1f, .8f)),
                List.of(new SixteenthRestDetector.Staff(80, 144, 16, 0, 1)),
                List.of());
    }

    void correct(double stretch) {
        var r = detect(page(stretch));
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.25, r.get(0).durationBeats(), 0);
    }

    @Test
    public void moderatelyWidePairedBulbsRemainSixteenth() {
        correct(1.6);
    }

    @Test
    public void widerPairedBulbsRemainSixteenth() {
        correct(1.8);
    }

    @Test
    public void nearLimitPairedBulbsRemainSixteenth() {
        correct(1.9);
    }

    @Test
    public void ordinaryWidthIsPreserved() {
        correct(1);
    }

    @Test
    public void missingTailCannotProveSixteenth() {
        byte[] g = page(1.8);
        for (int y = 124; y < 144; y++)
            if (y != 128) for (int x = 170; x < 210; x++) g[y * W + x] = (byte) 255;
        assertTrue(detect(g).stream().noneMatch(r -> r.durationBeats() == .25));
    }

    @Test
    public void missingSecondBulbCannotProveSixteenth() {
        byte[] g = page(1.8);
        for (int y = 113; y <= 123; y++) for (int x = 170; x < 210; x++) g[y * W + x] = (byte) 255;
        assertTrue(detect(g).stream().noneMatch(r -> r.durationBeats() == .25));
    }

    @Test
    public void wideRectangleCannotProveSixteenth() {
        byte[] g = page(1.8);
        for (int y = 97; y <= 143; y++) for (int x = 172; x <= 200; x++) g[y * W + x] = 0;
        assertTrue(detect(g).stream().noneMatch(r -> r.durationBeats() == .25));
    }

    @Test
    public void printedRulesAloneDoNotAddRest() {
        byte[] g = page(1.8);
        for (int y = 90; y < 144; y++)
            if ((y - 80) % 16 != 0) for (int x = 160; x < 215; x++) g[y * W + x] = (byte) 255;
        assertTrue(detect(g).isEmpty());
    }

    @Test
    public void sourceRasterRemainsUnchanged() {
        byte[] g = page(1.8), before = g.clone();
        detect(g);
        assertArrayEquals(before, g);
    }
}

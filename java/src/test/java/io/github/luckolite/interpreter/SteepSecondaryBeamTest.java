// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Procedural sloping beams and elliptical notes; no private score pixels. */
public final class SteepSecondaryBeamTest {
    static final int W = 400, H = 260;
    final byte[] labels = new byte[W * H], gray = new byte[W * H];

    public SteepSecondaryBeamTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 100; y <= 164; y += 16) for (int x = 10; x < 390; x++) pixel(x, y, 4);
        note(149, 105, 9, 7);
        note(213, 51, 9, 7);
        for (int y = 105; y <= 180; y++) pixel(140, y, 1);
        for (int y = 51; y <= 146; y++) pixel(204, y, 1);
        for (int x = 140; x <= 204; x++) {
            int edge = Math.round(180 - (x - 140) * 34 / 64f);
            for (int y = edge - 9; y < edge; y++) pixel(x, y, 5);
            if (x >= 184) for (int y = edge - 24; y <= edge - 15; y++) pixel(x, y, 5);
        }
        for (int x = 187; x <= 193; x++)
            for (int y = 131; y <= 137; y++) if (labels[y * W + x] == 5) labels[y * W + x] = 2;
    }

    void pixel(int x, int y, int l) {
        labels[y * W + x] = (byte) l;
        gray[y * W + x] = 0;
    }

    void note(int cx, int cy, int rx, int ry) {
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++)
                if (Math.pow((x - cx) / (double) rx, 2) + Math.pow((y - cy) / (double) ry, 2) <= 1)
                    pixel(x, y, 2);
    }

    boolean fragment() {
        return OmrScoreInterpreter.analyze(
                        labels, gray, W, H, List.of(new MeasureRegion(0, 1, .05f, .9f)))
                .notes()
                .stream()
                .anyMatch(
                        n ->
                                Math.abs(n.positionInMeasure() * W - 190) < 10
                                        && Math.abs(n.pageY() * H - 134) < 12);
    }

    @Test
    public void steepSecondaryIslandIsNotPitched() {
        assertFalse(fragment());
    }

    @Test
    public void independentStemRetainsSmallNote() {
        for (int y = 134; y <= 174; y++) pixel(186, y, 1);
        assertTrue(fragment());
    }

    @Test
    public void roundedBulgeRetainsNote() {
        var copy = labels.clone();
        note(190, 135, 9, 8);
        System.arraycopy(copy, 0, labels, 0, labels.length);
        assertTrue(fragment());
    }

    @Test
    public void brokenPrimaryDoesNotProveBeam() {
        for (int x = 155; x <= 177; x++)
            for (int y = 140; y <= 180; y++)
                if (labels[y * W + x] == 5) {
                    labels[y * W + x] = 0;
                    gray[y * W + x] = (byte) 255;
                }
        assertTrue(fragment());
    }

    @Test
    public void inputsRemainUnchanged() {
        var l = labels.clone();
        var g = gray.clone();
        fragment();
        assertArrayEquals(l, labels);
        assertArrayEquals(g, gray);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedFlatGlyphTest {
    private final int w = 160, h = 150;
    private final byte[] gray = new byte[w * h];

    public PrintedFlatGlyphTest() {
        Arrays.fill(gray, (byte) 255);
    }

    private void rect(int x, int y, int ww, int hh) {
        for (int yy = y; yy < y + hh; yy++)
            for (int xx = x; xx < x + ww; xx++) gray[yy * w + xx] = 0;
    }

    private void flat() {
        rect(60, 50, 3, 41);
        for (int y = 69; y <= 87; y++)
            for (int x = 61; x <= 74; x++) {
                double r = Math.pow((x - 63) / 11d, 2) + Math.pow((y - 78) / 9d, 2);
                if (r >= .4 && r <= 1) gray[y * w + x] = 0;
            }
    }

    private boolean read(int top) {
        return PrintedFlatGlyph.matches(gray, w, h, 60, top, 74, 90, 16);
    }

    @Test
    public void sourceFlatSurvivesStaffCrossings() {
        flat();
        for (int y : new int[] {54, 70, 86}) rect(20, y, 120, 2);
        assertTrue(read(50));
    }

    @Test
    public void truncatedSemanticBoxCanRecoverItsPrintedSpine() {
        flat();
        assertTrue(read(69));
    }

    @Test
    public void naturalKeepsItsLowerRightStem() {
        rect(60, 50, 3, 32);
        rect(71, 60, 3, 31);
        rect(60, 60, 14, 3);
        rect(60, 79, 14, 3);
        assertFalse(read(50));
    }

    @Test
    public void sharpKeepsBothUpperSpines() {
        rect(60, 50, 3, 41);
        rect(71, 50, 3, 41);
        rect(57, 62, 21, 3);
        rect(57, 77, 21, 3);
        assertFalse(read(50));
    }

    @Test
    public void simpleStemHasNoBowl() {
        rect(60, 50, 3, 41);
        assertFalse(read(50));
    }

    @Test
    public void adjacentLocalFlatsAreNotAKeyChange() {
        byte[] labels = new byte[400 * 240];
        for (int y = 80; y <= 120; y += 10) for (int x = 20; x < 380; x++) labels[y * 400 + x] = 4;
        RejectedKeyBoundaryTest.flat(labels, 90, 75);
        RejectedKeyBoundaryTest.flat(labels, 105, 75);
        RejectedKeyBoundaryTest.head(labels, 118, 110);
        var result =
                OmrScoreInterpreter.analyze(
                        labels,
                        RejectedKeyBoundaryTest.raw(labels),
                        400,
                        240,
                        RejectedKeyBoundaryTest.M);
        assertTrue(result.keyChanges().isEmpty());
    }

    @Test
    public void completeFlatOutcomeRereadsCallerAndKeepsItsPixels() {
        flat();
        byte[] before = gray.clone();
        assertTrue(read(50));
        assertTrue(read(50));
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 255);
        byte[] white = gray.clone();
        assertFalse(read(50));
        assertFalse(read(50));
        assertArrayEquals(white, gray);
    }

    @Test
    public void edgeFlatSkipsUnavailableRuleProbeAndKeepsGuardErrors() {
        flat();
        byte[] edge = new byte[w * h];
        Arrays.fill(edge, (byte) 255);
        for (int y = 0; y < h; y++)
            for (int x = 60; x <= 74; x++) edge[y * w + x - 60] = gray[y * w + x];
        byte[] before = edge.clone();
        assertTrue(PrintedFlatGlyph.matches(edge, w, h, 0, 50, 14, 90, 16));
        assertArrayEquals(before, edge);
        assertFalse(PrintedFlatGlyph.matches(null, w, h, 0, 50, 14, 90, 16));
        byte[] shortRaster = {(byte) 231};
        try {
            PrintedFlatGlyph.matches(shortRaster, w, h, 0, 50, 14, 90, 16);
            fail("the original first source-ink read must reject a short raster");
        } catch (ArrayIndexOutOfBoundsException expected) {
            assertEquals((byte) 231, shortRaster[0]);
        }
        Arrays.fill(edge, (byte) 255);
        before = edge.clone();
        for (float gap :
                new float[] {-0f, 0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            assertFalse(PrintedFlatGlyph.matches(edge, w, h, 0, 50, 14, 90, gap));
        assertFalse(PrintedFlatGlyph.matches(shortRaster, w, h, 0, 50, -1, 90, 16));
        assertArrayEquals(before, edge);
    }
}

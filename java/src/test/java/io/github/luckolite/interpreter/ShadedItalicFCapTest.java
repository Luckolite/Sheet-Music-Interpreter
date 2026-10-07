// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster strokes, independently drawn; no score or font fixture. */
public class ShadedItalicFCapTest {
    static final int W = 1000, H = 700, G = 16;
    byte[] gray, labels;

    void line(int x, int y, int endX, int endY, int value) {
        int steps = Math.max(Math.abs(endX - x), Math.abs(endY - y));
        for (int i = 0; i <= steps; i++) {
            int xx = Math.round(x + (endX - x) * i / (float) Math.max(1, steps));
            int yy = Math.round(y + (endY - y) * i / (float) Math.max(1, steps));
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    gray[(yy + dy) * W + xx + dx] = (byte) value;
                    labels[(yy + dy) * W + xx + dx] = 5;
                }
        }
    }

    void draw(boolean topCap, boolean full, boolean crossbar, boolean detached) {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 140);
        if (full) {
            line(420, 206, 405, 242, 95);
            line(420, 206, 421, 202, 95);
            line(421, 202, 424, 200, 95);
            line(424, 200, 427, 201, 95);
            line(427, 201, 429, 204, 95);
            line(405, 242, 403, 246, 95);
            line(403, 246, 399, 247, 95);
            line(399, 247, 397, 245, 95);
            if (crossbar) line(412, 217, 425, 217, 95);
        }
        if (detached)
            for (int y = 202; y <= 205; y++)
                for (int x = 417; x <= 431; x++) {
                    gray[y * W + x] = (byte) 140;
                    labels[y * W + x] = 0;
                }
        int x = topCap ? 424 : 398, y = topCap ? 200 : 247;
        for (int yy = y - 2; yy <= y + 2; yy++)
            for (int xx = x - 2; xx <= x + 2; xx++) {
                gray[yy * W + xx] = 65;
                labels[yy * W + xx] = 5;
            }
    }

    boolean proof(boolean topCap) {
        int x = topCap ? 424 : 398, y = topCap ? 200 : 247;
        byte[] gc = gray.clone(), lc = labels.clone();
        boolean result = ShadedItalicFCap.proved(gray, labels, W, H, G, x - 2, y - 2, x + 2, y + 2);
        assertArrayEquals(gc, gray);
        assertArrayEquals(lc, labels);
        return result;
    }

    int marks(boolean topCap) {
        int x = topCap ? 424 : 398, y = topCap ? 264 : 183;
        byte[] gc = gray.clone(), lc = labels.clone();
        int result =
                NoteArticulationDetector.detect(
                        labels,
                        gray,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(x, y, G, 0)))[0];
        assertArrayEquals(gc, gray);
        assertArrayEquals(lc, labels);
        return result;
    }

    @Test
    public void connectedTopCapBelongsToCompleteItalicF() {
        draw(true, true, true, false);
        assertTrue(proof(true));
    }

    @Test
    public void connectedLowerCapBelongsToCompleteItalicF() {
        draw(false, true, true, false);
        assertTrue(proof(false));
    }

    @Test
    public void topCapDoesNotBecomeNoteStaccato() {
        draw(true, true, true, false);
        assertEquals(0, marks(true));
    }

    @Test
    public void lowerCapDoesNotBecomeNoteStaccato() {
        draw(false, true, true, false);
        assertEquals(0, marks(false));
    }

    @Test
    public void isolatedTopDotKeepsStaccato() {
        draw(true, false, false, false);
        assertFalse(proof(true));
        assertEquals(NoteArticulation.STACCATO, marks(true));
    }

    @Test
    public void isolatedLowerDotKeepsStaccato() {
        draw(false, false, false, false);
        assertFalse(proof(false));
        assertEquals(NoteArticulation.STACCATO, marks(false));
    }

    @Test
    public void whiteGapKeepsDetachedTopMark() {
        draw(true, true, true, true);
        assertFalse(proof(true));
        assertEquals(NoteArticulation.STACCATO, marks(true));
    }

    @Test
    public void slashWithoutCrossbarCannotProveF() {
        draw(false, true, false, false);
        assertFalse(proof(false));
        assertEquals(NoteArticulation.STACCATO, marks(false));
    }

    @Test
    public void reflectedSlopeCannotProveItalicF() {
        draw(true, true, true, false);
        byte[] g = gray.clone(), l = labels.clone();
        for (int y = 0; y < H; y++)
            for (int x = 390; x <= 440; x++) {
                gray[y * W + x] = g[y * W + 830 - x];
                labels[y * W + x] = l[y * W + 830 - x];
            }
        assertFalse(ShadedItalicFCap.proved(gray, labels, W, H, G, 404, 198, 408, 202));
    }

    @Test
    public void broadRectangleCannotProveLetter() {
        draw(true, false, false, false);
        for (int y = 199; y <= 249; y++)
            for (int x = 396; x <= 430; x++) {
                gray[y * W + x] = 65;
                labels[y * W + x] = 5;
            }
        assertFalse(proof(true));
    }

    @Test
    public void middleOfLetterCannotEraseIndependentDot() {
        draw(true, true, true, false);
        assertFalse(ShadedItalicFCap.proved(gray, labels, W, H, G, 412, 224, 416, 228));
    }

    @Test
    public void notationLabelsCannotProveLetter() {
        draw(true, true, true, false);
        for (int i = 0; i < labels.length; i++) if (labels[i] == 5) labels[i] = 2;
        assertFalse(proof(true));
    }

    @Test
    public void brightPaperPreservesPreviousPath() {
        draw(true, true, true, false);
        for (int i = 0; i < gray.length; i++) if ((gray[i] & 255) == 140) gray[i] = (byte) 250;
        assertFalse(proof(true));
    }

    @Test
    public void clippedFrameCannotProveF() {
        draw(true, true, true, false);
        assertFalse(ShadedItalicFCap.proved(gray, labels, W, H, G, 1, 199, 3, 201));
    }

    @Test
    public void nonfiniteGapCannotProveF() {
        draw(true, true, true, false);
        assertFalse(ShadedItalicFCap.proved(gray, labels, W, H, Float.NaN, 423, 199, 425, 201));
    }

    @Test
    public void invalidImageCannotProveF() {
        assertFalse(ShadedItalicFCap.proved(new byte[4], new byte[4], W, H, G, 423, 199, 425, 201));
    }
}

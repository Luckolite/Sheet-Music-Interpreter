// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedParenthesisShapeTest {
    private static final int W = 240, H = 180;

    private int[] glyph(boolean left, double phase) {
        var p = new ArrayList<Integer>();
        for (int y = 60; y < 84; y++) {
            double t = 2 * (y - 60 + .5) / 24 - 1,
                    center = 100 + (left ? 1 : -1) * 4 * (1 - Math.sqrt(1 - t * t)) + phase;
            for (int x = (int) Math.floor(center - 1.3); x <= Math.ceil(center + 1.3); x++)
                p.add(y * W + x);
        }
        return p.stream().mapToInt(i -> i).toArray();
    }

    private Optional<PrintedParenthesisShape.Glyph> classify(int[] p) {
        return PrintedParenthesisShape.classify(p, W, H, 14, false);
    }

    @Test
    public void originalLeftGlyphHasOneCompleteReturningSide() {
        assertEquals(
                PrintedParenthesisShape.Side.LEFT, classify(glyph(true, 0)).orElseThrow().side());
    }

    @Test
    public void originalRightGlyphMirrorsThePrintedSide() {
        assertEquals(
                PrintedParenthesisShape.Side.RIGHT, classify(glyph(false, 0)).orElseThrow().side());
    }

    @Test
    public void rasterPhaseDoesNotReverseSide() {
        for (double p : new double[] {-.4, -.2, .2, .4})
            assertEquals(
                    PrintedParenthesisShape.Side.LEFT,
                    classify(glyph(true, p)).orElseThrow().side());
    }

    @Test
    public void pixelOrderingDoesNotSelectAnotherGlyph() {
        int[] p = glyph(true, 0), q = p.clone();
        for (int i = 0; i < q.length / 2; i++) {
            int v = q[i];
            q[i] = q[q.length - i - 1];
            q[q.length - i - 1] = v;
        }
        assertEquals(classify(p), classify(q));
    }

    @Test
    public void missingRowCannotBeTreatedAsPaper() {
        assertTrue(
                classify(Arrays.stream(glyph(true, 0)).filter(p -> p / W != 72).toArray())
                        .isEmpty());
    }

    @Test
    public void duplicateCellsRefuse() {
        int[] p = glyph(true, 0), q = Arrays.copyOf(p, p.length + 1);
        q[q.length - 1] = p[0];
        assertTrue(classify(q).isEmpty());
    }

    @Test
    public void fullComponentBranchCannotBeDropped() {
        var p = new HashSet<Integer>();
        for (int v : glyph(true, 0)) p.add(v);
        for (int x = 100; x <= 110; x++) p.add(72 * W + x);
        assertTrue(classify(p.stream().mapToInt(i -> i).toArray()).isEmpty());
    }

    @Test
    public void croppedCapCannotQualify() {
        assertTrue(PrintedParenthesisShape.classify(glyph(true, 0), W, H, 14, true).isEmpty());
    }

    @Test
    public void straightBarlineIsNotAParenthesis() {
        var p = new ArrayList<Integer>();
        for (int y = 60; y < 84; y++) for (int x = 100; x < 104; x++) p.add(y * W + x);
        assertTrue(classify(p.stream().mapToInt(i -> i).toArray()).isEmpty());
    }

    @Test
    public void rectangularBracketIsNotAParenthesis() {
        var p = new ArrayList<Integer>();
        for (int y = 60; y < 84; y++)
            for (int x = 100; x < ((y < 62 || y > 81) ? 108 : 103); x++) p.add(y * W + x);
        assertTrue(classify(p.stream().mapToInt(i -> i).toArray()).isEmpty());
    }

    @Test
    public void closedOvalKeepsBothBranchesAndRefuses() {
        var p = new ArrayList<Integer>();
        for (int y = 60; y < 84; y++)
            for (int x = 96; x <= 108; x++)
                if (y < 62 || y > 81 || x < 99 || x > 105) p.add(y * W + x);
        assertTrue(classify(p.stream().mapToInt(i -> i).toArray()).isEmpty());
    }

    @Test
    public void smallFilledHeadIsNotAParenthesis() {
        var p = new ArrayList<Integer>();
        for (int y = 65; y < 75; y++)
            for (int x = 96; x < 108; x++)
                if (Math.pow((x - 102) / 6., 2) + Math.pow((y - 70) / 5., 2) <= 1) p.add(y * W + x);
        assertTrue(classify(p.stream().mapToInt(i -> i).toArray()).isEmpty());
    }

    @Test
    public void diagonalSlashIsNotReturning() {
        var p = new ArrayList<Integer>();
        for (int y = 60; y < 84; y++)
            for (int x = 100 + (y - 60) / 4; x < 103 + (y - 60) / 4; x++) p.add(y * W + x);
        assertTrue(classify(p.stream().mapToInt(i -> i).toArray()).isEmpty());
    }

    @Test
    public void invalidScaleDoesNotInventAFrame() {
        assertTrue(
                PrintedParenthesisShape.classify(glyph(true, 0), W, H, Float.NaN, false).isEmpty());
    }
}

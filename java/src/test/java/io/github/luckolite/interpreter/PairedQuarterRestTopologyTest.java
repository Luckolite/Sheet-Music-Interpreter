// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PairedQuarterRestTopologyTest {
    static final int W = 260, H = 210;
    byte[] gray = new byte[W * H], labels = new byte[W * H];
    int gap;
    double shoulder;
    static final double[] T = {0, .04, .18, .24, .42, .48, .57, .62, .72, .78, .86, .96, 1};
    static final double[] L = {.4, .4, .46, .46, .2, .2, .45, .45, 0, 0, .08, .3, .3};

    void ink(int x, int y, int value, int label) {
        if (x < 0 || x >= W || y < 0 || y >= H) return;
        gray[y * W + x] = (byte) Math.min(gray[y * W + x] & 255, value);
        labels[y * W + x] = (byte) label;
    }

    void draw(int g, double sh) {
        gap = g;
        shoulder = sh;
        Arrays.fill(gray, (byte) 255);
        Arrays.fill(labels, (byte) 0);
        for (int rule = 0; rule < 5; rule++)
            for (int x = 20; x < W - 20; x++) ink(x, 60 + rule * g, 20, 4);
        double[] R = {.48, .48, .96, .96, .68, .68, sh, sh, .95, .95, .32, .42, .42};
        int rows = Math.round(g * 2.9f);
        for (int y = 0; y < rows; y++) {
            double t = y / (double) (rows - 1);
            int a = 0;
            while (a + 1 < T.length - 1 && T[a + 1] < t) a++;
            double f = (t - T[a]) / (T[a + 1] - T[a]);
            int lo = 130 + (int) Math.round(g * (L[a] + (L[a + 1] - L[a]) * f));
            int hi = 130 + (int) Math.round(g * (R[a] + (R[a + 1] - R[a]) * f));
            for (int x = lo; x <= hi; x++)
                ink(x, 60 + Math.round(g * .6f) + y, y < 2 ? 185 : 20, 5);
        }
    }

    List<ScoreRestEvent> detect() {
        return SixteenthRestDetector.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(.05f, .95f, .1f, .95f)),
                List.of(new SixteenthRestDetector.Staff(60, 60 + 4 * gap, gap, 0, 1)),
                List.of());
    }

    void oneQuarter() {
        var result = detect();
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).durationBeats(), 0);
    }

    @Test
    public void fullNarrowOutlineAtSixteenPixels() {
        draw(16, .6);
        oneQuarter();
    }

    @Test
    public void fullNarrowOutlineAtEighteenPixels() {
        draw(18, .6);
        oneQuarter();
    }

    @Test
    public void fullNarrowOutlineAtTwentyPixels() {
        draw(20, .6);
        oneQuarter();
    }

    @Test
    public void ordinaryEarlierGlyphRetainsItsBounds() {
        draw(12, .55);
        oneQuarter();
        var r = detect().get(0);
        assertEquals(.4, r.pageY(), 1e-6);
        assertEquals(35 / 210., r.pageHeight(), 1e-6);
    }

    @Test
    public void completeRestIsNotAPitchedHead() {
        draw(16, .6);
        var a =
                OmrScoreInterpreter.analyze(
                        labels, gray, W, H, List.of(new MeasureRegion(.05f, .95f, .1f, .95f)));
        assertTrue(a.notes().isEmpty());
        assertEquals(1, a.rests().size());
    }

    @Test
    public void inputsRemainExact() {
        draw(16, .6);
        byte[] g = gray.clone(), l = labels.clone();
        oneQuarter();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    @Test
    public void aBrokenMiddleCannotSupplyACompleteBody() {
        draw(16, .6);
        for (int x = 127; x < 150; x++) {
            gray[85 * W + x] = (byte) 255;
            labels[85 * W + x] = 0;
        }
        assertTrue(detect().isEmpty());
    }

    void blank() {
        gap = 16;
        Arrays.fill(gray, (byte) 255);
        Arrays.fill(labels, (byte) 0);
        for (int rule = 0; rule < 5; rule++)
            for (int x = 20; x < W - 20; x++) ink(x, 60 + rule * gap, 20, 4);
    }

    @Test
    public void aSingleStemCannotBeARest() {
        blank();
        for (int y = 69; y <= 119; y++) for (int x = 137; x <= 139; x++) ink(x, y, 20, 1);
        assertTrue(detect().isEmpty());
    }

    @Test
    public void genuineNoteStemKeepsItsHead() {
        blank();
        for (int y = 70; y <= 117; y++) for (int x = 144; x <= 145; x++) ink(x, y, 20, 1);
        for (int y = 108; y <= 120; y++)
            for (int x = 125; x <= 145; x++)
                if (Math.pow((x - 135) / 10., 2) + Math.pow((y - 114) / 6., 2) <= 1)
                    ink(x, y, 20, 2);
        var a =
                OmrScoreInterpreter.analyze(
                        labels, gray, W, H, List.of(new MeasureRegion(.05f, .95f, .1f, .95f)));
        assertEquals(1, a.notes().size());
        assertTrue(a.rests().isEmpty());
    }

    @Test
    public void parallelAccidentalSpinesCannotBeARest() {
        blank();
        for (int y = 70; y <= 118; y++)
            for (int x : new int[] {132, 133, 140, 141}) ink(x, y, 20, 5);
        for (int y : new int[] {83, 103})
            for (int x = 128; x <= 145; x++)
                for (int dy = 0; dy < 3; dy++) ink(x, y + dy + (145 - x) / 4, 20, 5);
        assertTrue(detect().isEmpty());
    }

    @Test
    public void textAndDigitsCannotBeRestContours() {
        for (String text : new String[] {"f", "p", "m", "3", "8", "b", "#"}) {
            blank();
            var image =
                    new java.awt.image.BufferedImage(
                            W, H, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
            var graphics = image.createGraphics();
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, W, H);
            graphics.setColor(java.awt.Color.BLACK);
            graphics.setFont(new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.PLAIN, 48));
            graphics.drawString(text, 126, 116);
            graphics.dispose();
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    if (image.getRaster().getSample(x, y, 0) < 170) ink(x, y, 20, 5);
            assertTrue(text, detect().isEmpty());
        }
    }

    @Test
    public void lowContrastPaperCannotSupplyACompleteGlyph() {
        draw(16, .6);
        for (int i = 0; i < gray.length; i++) gray[i] = (byte) ((gray[i] & 255) < 220 ? 149 : 158);
        assertTrue(detect().isEmpty());
    }

    @Test
    public void continuationAboveTipCannotBeAnIsolatedRest() {
        draw(16, .6);
        for (int y = 45; y < 72; y++) for (int x = 126; x <= 145; x++) ink(x, y, 20, 5);
        assertTrue(detect().isEmpty());
    }
}

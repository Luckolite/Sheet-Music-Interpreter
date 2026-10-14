// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public class BlurredQuarterBoundaryTest {
    static final int W = 600, H = 280;
    static final float GAP = 13;

    static byte[][] page(float fontSize, double sigma, int paper) throws Exception {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(new Color(paper, paper, paper));
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        Font font =
                Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"))
                        .deriveFont(fontSize);
        g.setFont(font);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String text = "\ue4e5";
        var bounds =
                font.createGlyphVector(g.getFontRenderContext(), text).getPixelBounds(null, 0, 0);
        g.drawString(text, 250 - bounds.width / 2 - bounds.x, 126 - bounds.height / 2 - bounds.y);
        g.dispose();
        byte[] labels = new byte[W * H];
        double[] raw = new double[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int v = im.getRGB(x, y) & 255;
                raw[y * W + x] = v;
                if (v < paper - 20) labels[y * W + x] = 5;
            }
        for (int n = 0; n < 5; n++)
            for (int x = 30; x < 570; x++) {
                int y = 100 + 13 * n;
                raw[y * W + x] = 0;
                labels[y * W + x] = 4;
            }
        for (int x : new int[] {40, 560})
            for (int y = 100; y <= 152; y++) {
                raw[y * W + x] = 0;
                labels[y * W + x] = 1;
            }
        int radius = (int) Math.ceil(sigma * 3);
        double[] kernel = new double[radius * 2 + 1];
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = Math.exp(-i * i / (2 * sigma * sigma));
            sum += kernel[i + radius];
        }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= sum;
        double[] tmp = new double[raw.length];
        byte[] gray = new byte[raw.length];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                for (int dx = -radius; dx <= radius; dx++)
                    tmp[y * W + x] +=
                            raw[y * W + Math.max(0, Math.min(W - 1, x + dx))] * kernel[dx + radius];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                double v = 0;
                for (int dy = -radius; dy <= radius; dy++)
                    v += tmp[Math.max(0, Math.min(H - 1, y + dy)) * W + x] * kernel[dy + radius];
                gray[y * W + x] = (byte) Math.round(v);
            }
        return new byte[][] {labels, gray};
    }

    static java.util.List<Integer> boundaries(byte[][] p) throws Exception {
        var method =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "findBoundaries",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        int.class,
                        int.class,
                        float.class);
        method.setAccessible(true);
        return (java.util.List<Integer>)
                method.invoke(
                        null,
                        p[0],
                        p[1],
                        W,
                        H,
                        new int[] {100, 113, 126, 139, 152},
                        13f,
                        30,
                        570,
                        0f);
    }

    @Test
    public void blurredQuarterDoesNotSplitBar() throws Exception {
        assertEquals(java.util.List.of(30, 570), boundaries(page(54, 1.5, 190)));
    }

    @Test
    public void strongerBlurDoesNotSplitBar() throws Exception {
        assertEquals(java.util.List.of(30, 570), boundaries(page(54, 2, 190)));
    }

    @Test
    public void largerQuarterDoesNotSplitBar() throws Exception {
        assertEquals(java.util.List.of(30, 570), boundaries(page(58, 1.5, 190)));
    }

    @Test
    public void largerBlurredQuarterDoesNotSplitBar() throws Exception {
        assertEquals(java.util.List.of(30, 570), boundaries(page(58, 2, 190)));
    }

    @Test
    public void cleanQuarterKeepsOneBar() throws Exception {
        assertEquals(java.util.List.of(30, 570), boundaries(page(54, .7, 190)));
    }

    @Test
    public void nearbyActualBarlineSurvives() throws Exception {
        var p = page(54, 1.5, 190);
        for (int y = 100; y <= 152; y++)
            for (int x = 399; x <= 401; x++) {
                p[0][y * W + x] = 1;
                p[1][y * W + x] = 30;
            }
        assertEquals(java.util.List.of(30, 400, 570), boundaries(p));
    }
}

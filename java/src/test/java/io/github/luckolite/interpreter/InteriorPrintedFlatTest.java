// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original Bravura flat pairs crossing staff rules; no score-derived pixels. */
public class InteriorPrintedFlatTest {
    static final int W = 240, H = 180;

    static byte[][] page(int size, int paper, double sigma, int shift) throws Exception {
        var font = Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"));
        float gap = size / 4f;
        var im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        var g = im.createGraphics();
        g.setColor(new Color(paper, paper, paper));
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setFont(font.deriveFont((float) size));
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawString("\ue260", 65, 85);
        g.drawString("\ue260", 85, 85 - Math.round(size / 4f * 1.5f));
        g.dispose();
        byte[] labels = new byte[W * H];
        int area = 0, l = W, r = -1, t = H, b = -1;
        long sx = 0, sy = 0;
        double[] raw = new double[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int v = im.getRGB(x, y) & 255;
                raw[y * W + x] = v;
                if (v < paper - 30) {
                    labels[y * W + x] = 3;
                    area++;
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                    sx += x;
                    sy += y;
                }
            }
        for (int row = 0; row < 5; row++)
            for (int x = 10; x < W - 10; x++) {
                int at = (48 + shift + Math.round(row * gap)) * W + x;
                raw[at] = 0;
                if (labels[at] == 0) labels[at] = 4;
            }
        // Separate original vertical ink near the glyph models a neighboring barline.
        for (int xx : new int[] {54, 58})
            for (int y = 48 + shift; y <= 48 + shift + 4 * gap; y++) {
                raw[y * W + xx] = 0;
                labels[y * W + xx] = 1;
            }
        for (int y = 88; y <= 94; y++)
            for (int x = 146; x <= 154; x++) {
                raw[y * W + x] = 0;
                labels[y * W + x] = 2;
            }
        int radius = (int) Math.ceil(3 * sigma);
        double[] k = new double[2 * radius + 1];
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            k[i + radius] = Math.exp(-i * i / (2 * sigma * sigma));
            sum += k[i + radius];
        }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        double[] tmp = new double[raw.length];
        byte[] gray = new byte[raw.length];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                for (int dx = -radius; dx <= radius; dx++)
                    tmp[y * W + x] +=
                            raw[y * W + Math.max(0, Math.min(W - 1, x + dx))] * k[dx + radius];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                double v = 0;
                for (int dy = -radius; dy <= radius; dy++)
                    v += tmp[Math.max(0, Math.min(H - 1, y + dy)) * W + x] * k[dy + radius];
                gray[y * W + x] = (byte) Math.round(v);
            }
        return new byte[][] {labels, gray};
    }

    static java.util.List<ScoreKeyChange> keys(byte[][] p) {
        return OmrScoreInterpreter.analyze(
                        p[0],
                        p[1],
                        W,
                        H,
                        java.util.List.of(new MeasureRegion(54f / W, 220f / W, 35f / H, 125f / H)))
                .keyChanges();
    }

    @Test
    public void whiteBlurredFlatPairRetainsTwoFlats() throws Exception {
        assertEquals(java.util.List.of(new ScoreKeyChange(0, -2)), keys(page(48, 255, 1, 9)));
    }

    @Test
    public void shadedFlatPairRetainsTwoFlats() throws Exception {
        assertEquals(java.util.List.of(new ScoreKeyChange(0, -2)), keys(page(56, 190, 1, 0)));
    }

    @Test
    public void largerFlatPairRetainsTwoFlats() throws Exception {
        assertEquals(java.util.List.of(new ScoreKeyChange(0, -2)), keys(page(60, 190, 1, 3)));
    }

    @Test
    public void inputPixelsAndLabelsStayUnchanged() throws Exception {
        var p = page(48, 255, 1, 9);
        var a = p[0].clone();
        var b = p[1].clone();
        keys(p);
        assertArrayEquals(a, p[0]);
        assertArrayEquals(b, p[1]);
    }
}

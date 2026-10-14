// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.File;
import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class CrossingMeterFontTest {
    MeterFontMatcher matcher() throws Exception {
        return new MeterFontMatcher(new File("java/assets/Bravura.otf"));
    }

    static final int W = 300, H = 220, L = 100, T = 57, CW = 48, CH = 73;
    static final float GAP = 16.5f;

    static byte[] page(int radius, boolean both) throws Exception {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setFont(
                Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"))
                        .deriveFont(62f));
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < 2; i++) {
            String digits = i == 0 ? "12" : "8";
            StringBuilder s = new StringBuilder();
            for (char c : digits.toCharArray()) s.append((char) (0xe080 + c - '0'));
            var b =
                    g.getFont()
                            .createGlyphVector(g.getFontRenderContext(), s.toString())
                            .getPixelBounds(null, 0, 0);
            int y = i == 0 ? 3 : 36, bot = i == 0 ? 36 : 73;
            g.drawString(
                    s.toString(),
                    L + (CW - b.width) / 2 - b.x,
                    T + y + (bot - y - b.height) / 2 - b.y);
        }
        g.dispose();
        byte[] gray = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) gray[y * W + x] = (byte) (im.getRGB(x, y) & 255);
        for (int n = 0; n < 5; n++) {
            int y = Math.round(60 + n * GAP);
            for (int x = 20; x < 280; x++) gray[y * W + x] = 0;
        }
        if (radius >= 0)
            for (int x = 74; x < (both ? 174 : 148); x++) {
                int y = Math.round(78f + -.14f * (x - 124));
                for (int dy = -radius; dy <= radius; dy++) gray[(y + dy) * W + x] = 0;
            }
        return gray;
    }

    static int[] crop(byte[] g) {
        int[] p = new int[CW * CH];
        for (int y = 0; y < CH; y++)
            for (int x = 0; x < CW; x++) {
                int v = g[(T + y) * W + L + x] & 255;
                p[y * CW + x] = 0xff000000 | v << 16 | v << 8 | v;
            }
        MeterCropRaster.prepare(p, CW, CH, T, 60, GAP, false);
        return p;
    }

    boolean[] mask(byte[] g) {
        return MeterCrossingStroke.mask(g, 300, 220, 100, 57, 148, 130, 60, 16.5f);
    }

    @Test
    public void fullOriginalCrossedSignatureRetainsMeterAndImprovesFit() throws Exception {
        var g = page(1, true);
        var m = mask(g);
        assertNotNull(m);
        var p = crop(g);
        var f = matcher();
        var a = f.read(p, 48, 73, 36, 3, 16.5f);
        var b = f.read(p, 48, 73, 36, 3, 16.5f, "", "", m);
        assertEquals("12/8", b.text());
        assertTrue(b.topScore() > a.topScore());
        assertEquals(a.bottomScore(), b.bottomScore(), 0);
    }

    @Test
    public void cleanSignatureIsUnchanged() throws Exception {
        var g = page(-1, true);
        assertNull(mask(g));
        var p = crop(g);
        var f = matcher();
        assertEquals(
                f.read(p, 48, 73, 36, 3, 16.5f), f.read(p, 48, 73, 36, 3, 16.5f, "", "", null));
    }

    @Test
    public void wholeExcludedNumeratorCannotInventMeter() throws Exception {
        var p = crop(page(-1, true));
        boolean[] m = new boolean[p.length];
        java.util.Arrays.fill(m, 0, 48 * 36, true);
        assertEquals("", matcher().read(p, 48, 73, 36, 3, 16.5f, "", "", m).text());
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongMaskSizeRejected() throws Exception {
        var p = crop(page(-1, true));
        matcher().read(p, 48, 73, 36, 3, 16.5f, "", "", new boolean[1]);
    }

    @Test
    public void callerPixelsAndMaskRemainUnchanged() throws Exception {
        var g = page(1, true);
        var p = crop(g);
        var m = mask(g);
        var before = p.clone();
        var old = m.clone();
        matcher().read(p, 48, 73, 36, 3, 16.5f, "", "", m);
        assertArrayEquals(before, p);
        assertArrayEquals(old, m);
    }
}

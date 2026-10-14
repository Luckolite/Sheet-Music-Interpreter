// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original Bravura text with independent vector stroke weights, not score pixels. */
public class MeterStrokeWeightTest {
    static int[] page(int size, float stroke, String text) throws Exception {
        var font = Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"));
        int w = 80, h = 72;
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        var f = font.deriveFont((float) size);
        String[] parts = text.split("/");
        for (int n = 0; n < 2; n++) {
            StringBuilder symbols = new StringBuilder();
            for (char c : parts[n].toCharArray()) symbols.append((char) (0xe080 + c - '0'));
            var gv = f.createGlyphVector(g.getFontRenderContext(), symbols.toString());
            var bounds = gv.getPixelBounds(null, 0, 0);
            var shape =
                    gv.getOutline(
                            w / 2f - bounds.width / 2f - bounds.x,
                            (n == 0 ? 22 : 50) - bounds.height / 2f - bounds.y);
            g.fill(shape);
            g.setStroke(new BasicStroke(stroke));
            g.draw(shape);
        }
        for (int y = 8; y <= 64; y += 14) g.drawLine(0, y, w - 1, y);
        g.dispose();
        int[] p = new int[w * h];
        image.getRGB(0, 0, w, h, p, 0, w);
        return p;
    }

    static String read(int[] p) throws Exception {
        MeterCropRaster.prepare(p, 80, 72, 0, 8, 14, false);
        return new MeterFontMatcher(new File("java/assets/Bravura.otf"))
                .read(p, 80, 72, 36, 8, 14)
                .text();
    }

    @Test
    public void heavierTwelveEighthsRetainsItsDigits() throws Exception {
        assertEquals("12/8", read(page(48, 1, "12/8")));
    }

    @Test
    public void heavierSixEighthsRetainsItsDigits() throws Exception {
        assertEquals("6/8", read(page(48, 1, "6/8")));
    }

    @Test
    public void heavierThreeQuartersRetainsItsDigits() throws Exception {
        assertEquals("3/4", read(page(48, 1.8f, "3/4")));
    }

    @Test
    public void heavierFourQuartersRetainsItsDigits() throws Exception {
        assertEquals("4/4", read(page(48, 1.8f, "4/4")));
    }

    @Test
    public void largerTwelveEighthsRetainsItsDigits() throws Exception {
        assertEquals("12/8", read(page(52, 1.4f, "12/8")));
    }

    @Test
    public void ordinaryWeightRemainsReadable() throws Exception {
        assertEquals("3/4", read(page(52, .6f, "3/4")));
    }

    @Test
    public void missingDenominatorCannotBecomeMeter() throws Exception {
        var p = page(48, 1, "12/8");
        java.util.Arrays.fill(p, 80 * 36, p.length, 0xffffffff);
        assertEquals("", read(p));
    }

    @Test
    public void matcherPreservesPreparedPixels() throws Exception {
        var p = page(48, 1, "12/8");
        MeterCropRaster.prepare(p, 80, 72, 0, 8, 14, false);
        var before = p.clone();
        new MeterFontMatcher(new File("java/assets/Bravura.otf")).read(p, 80, 72, 36, 8, 14);
        assertArrayEquals(before, p);
    }
}

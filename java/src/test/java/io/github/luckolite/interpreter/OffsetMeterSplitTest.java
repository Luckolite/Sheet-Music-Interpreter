// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

/** Original generated numerals shifted independently from fixed printed staff rails. */
public class OffsetMeterSplitTest {
    static int[] page(String text, int shift) throws Exception {
        int w = 56, h = 92;
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        var f =
                Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"))
                        .deriveFont(52f);
        var parts = text.split("/");
        for (int n = 0; n < 2; n++) {
            StringBuilder t = new StringBuilder();
            for (char c : parts[n].toCharArray()) t.append((char) (0xe080 + c - '0'));
            var gv = f.createGlyphVector(g.getFontRenderContext(), t.toString());
            var b = gv.getPixelBounds(null, 0, 0);
            var shape =
                    gv.getOutline(
                            w / 2f - b.width / 2f - b.x,
                            (n == 0 ? 30 : 58) + shift - b.height / 2f - b.y);
            g.fill(shape);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(shape);
        }
        for (int y = 16; y <= 72; y += 14) g.drawLine(0, y, w - 1, y);
        g.dispose();
        int[] p = image.getRGB(0, 0, w, h, null, 0, w);
        MeterCropRaster.prepare(p, w, h, 0, 16, 14, false);
        return p;
    }

    private String read(String text, int shift) throws Exception {
        var pixels = page(text, shift);
        var saved = pixels.clone();
        var result =
                new MeterFontMatcher(new File("java/assets/Bravura.otf"))
                        .read(pixels, 56, 92, 44, 16, 14)
                        .text();
        org.junit.Assert.assertArrayEquals(saved, pixels);
        return result;
    }

    @org.junit.Test
    public void loweredFourQuarters() throws Exception {
        org.junit.Assert.assertEquals("4/4", read("4/4", 10));
    }

    @org.junit.Test
    public void raisedThreeQuarters() throws Exception {
        org.junit.Assert.assertEquals("3/4", read("3/4", -7));
    }

    @org.junit.Test
    public void loweredSixEighths() throws Exception {
        org.junit.Assert.assertEquals("6/8", read("6/8", 7));
    }

    @org.junit.Test
    public void missingLowerNumeralCannotBecomeMeter() throws Exception {
        var pixels = page("4/4", 0);
        Arrays.fill(pixels, 56 * 44, pixels.length, 0xffffffff);
        org.junit.Assert.assertEquals(
                "",
                new MeterFontMatcher(new File("java/assets/Bravura.otf"))
                        .read(pixels, 56, 92, 44, 16, 14)
                        .text());
    }
}

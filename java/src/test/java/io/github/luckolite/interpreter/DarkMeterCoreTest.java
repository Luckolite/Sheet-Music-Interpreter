// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original vector glyphs with Gaussian scan blur; no score-derived pixels. */
public class DarkMeterCoreTest {
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

    static int[] blur(int[] p, double sigma) {
        int[] out = p.clone();
        for (int y = 0; y < 72; y++)
            for (int x = 0; x < 80; x++) {
                double sum = 0, weight = 0;
                for (int dy = -4; dy <= 4; dy++)
                    for (int dx = -4; dx <= 4; dx++) {
                        int xx = x + dx, yy = y + dy;
                        double a = Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma));
                        sum +=
                                a
                                        * (xx < 0 || xx >= 80 || yy < 0 || yy >= 72
                                                ? 255
                                                : p[yy * 80 + xx] & 255);
                        weight += a;
                    }
                int v = (int) Math.round(sum / weight);
                out[y * 80 + x] = 0xff000000 | v << 16 | v << 8 | v;
            }
        return out;
    }

    static int[] blurred(String meter) throws Exception {
        int[] p = blur(page(48, 1.8f, meter), 1.2);
        MeterCropRaster.prepare(p, 80, 72, 0, 8, 14, false);
        return p;
    }

    static MeterFontMatcher font() throws Exception {
        return new MeterFontMatcher(new File("java/assets/Bravura.otf"));
    }

    @Test
    public void blurredThreeQuarters() throws Exception {
        assertEquals("3/4", font().read(blurred("3/4"), 80, 72, 36, 8, 14).text());
    }

    @Test
    public void blurredSixEighths() throws Exception {
        assertEquals("6/8", font().read(blurred("6/8"), 80, 72, 36, 8, 14).text());
    }

    @Test
    public void missingLowerDigitRejected() throws Exception {
        int[] p = blurred("3/4");
        Arrays.fill(p, 36 * 80, p.length, 0xffffffff);
        assertEquals("", font().read(p, 80, 72, 36, 8, 14).text());
    }

    @Test
    public void contradictoryOcrPreventsDarkRetry() throws Exception {
        assertEquals("", font().read(blurred("3/4"), 80, 72, 36, 8, 14, "7", "4").text());
    }

    @Test
    public void emptyPaperRejected() throws Exception {
        int[] p = new int[80 * 72];
        Arrays.fill(p, 0xffffffff);
        assertEquals("", font().read(p, 80, 72, 36, 8, 14).text());
    }

    @Test
    public void originalPixelsPreserved() throws Exception {
        int[] p = blurred("6/8"), before = p.clone();
        font().read(p, 80, 72, 36, 8, 14);
        assertArrayEquals(before, p);
    }

    @Test
    public void agreeingDenominatorAllowsDarkRetry() throws Exception {
        assertEquals("3/4", font().read(blurred("3/4"), 80, 72, 36, 8, 14, "", "4").text());
    }

    @Test
    public void agreeingNumeratorAllowsDarkRetry() throws Exception {
        assertEquals("6/8", font().read(blurred("6/8"), 80, 72, 36, 8, 14, "6", "").text());
    }

    @Test
    public void contradictoryDenominatorPreventsDarkRetry() throws Exception {
        assertEquals("", font().read(blurred("6/8"), 80, 72, 36, 8, 14, "", "4").text());
    }
}

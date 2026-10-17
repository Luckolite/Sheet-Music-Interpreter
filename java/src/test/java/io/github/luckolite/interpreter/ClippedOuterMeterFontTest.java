// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original Bravura/OFL-1.1 rendering, no private score masks. */
public class ClippedOuterMeterFontTest {
    @Test
    public void completeClippedStaffCorrectionReachesFontRecognition() throws Exception {
        int w = 640, h = 300, gap = 14, first = 100;
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        var font =
                Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"))
                        .deriveFont(56f);
        g.setFont(font);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String four = "\ue084";
        var bounds =
                font.createGlyphVector(g.getFontRenderContext(), four).getPixelBounds(null, 0, 0);
        for (int y : new int[] {first, first + 2 * gap})
            g.drawString(four, 310 - bounds.x, y - bounds.y);
        for (int n = 0; n < 5; n++) g.drawLine(60, first + n * gap, 579, first + n * gap);
        g.dispose();
        byte[] gray = new byte[w * h];
        for (int i = 0; i < gray.length; i++) gray[i] = (byte) image.getRGB(i % w, i / w);
        var c = new MeterChangeDetector.Crop(300, 105, 340, 170, 108, gap);
        var r = MeterCropStaff.refine(gray, w, h, c);
        assertEquals(100, r.firstLine());
        assertEquals("4/4", read(gray, w, r).text());
        assertEquals("", read(gray, w, c).text());
    }

    static MeterFontMatcher.Reading read(byte[] g, int stride, MeterChangeDetector.Crop c)
            throws Exception {
        int w = c.right() - c.left(), h = c.bottom() - c.top();
        int[] p = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = g[(c.top() + y) * stride + c.left() + x] & 255;
                p[y * w + x] = 0xff000000 | v * 0x10101;
            }
        MeterCropRaster.prepare(p, w, h, c.top(), c.firstLine(), c.gap(), false);
        return new MeterFontMatcher(new File("java/assets/Bravura.otf"))
                .read(
                        p,
                        w,
                        h,
                        Math.round(c.firstLine() + 2 * c.gap()) - c.top(),
                        c.firstLine() - c.top(),
                        c.gap());
    }
}

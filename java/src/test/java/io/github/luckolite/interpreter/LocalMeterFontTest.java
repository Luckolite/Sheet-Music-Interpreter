// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original Bravura meters show lost readings caused by shifted staff hints. */
public class LocalMeterFontTest {
    static byte[] page(int size) throws Exception {
        int w = 640, h = 300;
        var font = Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"));
        var im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(font.deriveFont((float) size));
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var bounds =
                g.getFont()
                        .createGlyphVector(g.getFontRenderContext(), "\ue084")
                        .getPixelBounds(null, 0, 0);
        for (int cy : new int[] {114, 142})
            g.drawString(
                    "\ue084", 320 - bounds.width / 2 - bounds.x, cy - bounds.height / 2 - bounds.y);
        for (int y = 100; y <= 156; y += 14) g.drawLine(60, y, 579, y);
        g.dispose();
        byte[] gray = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) gray[y * w + x] = (byte) (im.getRGB(x, y) & 255);
        return gray;
    }

    static java.util.List<String> read(int size, int shift) throws Exception {
        var font = Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"));
        var matcher = new MeterFontMatcher(new File("java/assets/Bravura.otf"));
        var result = new ArrayList<String>();
        int w = 640, h = 300;
        byte[] gray = page(size);
        var c = new MeterChangeDetector.Crop(300, 94, 340, 165, 100 + shift, 14);
        var r = MeterCropStaff.refine(gray, w, h, c);
        for (var v : new MeterChangeDetector.Crop[] {c, r}) {
            int cw = v.right() - v.left(), ch = v.bottom() - v.top();
            int[] px = new int[cw * ch];
            for (int y = 0; y < ch; y++)
                for (int x = 0; x < cw; x++) {
                    int b = gray[(v.top() + y) * w + v.left() + x] & 255;
                    px[y * cw + x] = 0xff000000 | b << 16 | b << 8 | b;
                }
            MeterCropRaster.prepare(px, cw, ch, v.top(), v.firstLine(), v.gap(), false);
            result.add(
                    matcher.read(
                                    px,
                                    cw,
                                    ch,
                                    Math.round(v.firstLine() + 2 * v.gap()) - v.top(),
                                    v.firstLine() - v.top(),
                                    v.gap())
                            .text());
        }
        return result;
    }

    @Test
    public void lowerHintRecoversWrittenMeter() throws Exception {
        assertEquals(java.util.List.of("", "4/4"), read(48, 3));
    }

    @Test
    public void upperHintRecoversWrittenMeter() throws Exception {
        assertEquals(java.util.List.of("", "4/4"), read(48, -3));
    }

    @Test
    public void largerLowerHintRecoversWrittenMeter() throws Exception {
        assertEquals(java.util.List.of("", "4/4"), read(56, 3));
    }

    @Test
    public void largerUpperHintRecoversWrittenMeter() throws Exception {
        assertEquals(java.util.List.of("", "4/4"), read(56, -3));
    }

    @Test
    public void readableNearbyHintRemainsReadable() throws Exception {
        assertEquals(java.util.List.of("4/4", "4/4"), read(52, 2));
    }

    @Test
    public void alignedHintRemainsReadable() throws Exception {
        assertEquals(java.util.List.of("4/4", "4/4"), read(52, 0));
    }
}

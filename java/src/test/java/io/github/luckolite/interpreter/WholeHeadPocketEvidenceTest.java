// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original layouts rendered from the repository's SIL-OFL Bravura font. */
public class WholeHeadPocketEvidenceTest {
    static File fontFile() {
        for (String name :
                new String[] {
                    "java/assets/Bravura.otf",
                    "app/src/main/assets/engraving/Bravura.otf",
                    "src/main/assets/engraving/Bravura.otf"
                }) {
            File file = new File(name);
            if (file.isFile()) return file;
        }
        throw new IllegalStateException("Missing licensed Bravura regression fixture");
    }

    static boolean glyph(int code, int size, boolean rail, boolean shaft) throws Exception {
        return glyph(code, size, rail, shaft, 0);
    }

    static boolean glyph(int code, int size, boolean rail, boolean shaft, double angle)
            throws Exception {
        var f = Font.createFont(Font.TRUETYPE_FONT, fontFile()).deriveFont((float) size);
        var im = new BufferedImage(160, 120, BufferedImage.TYPE_BYTE_GRAY);
        var g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 160, 120);
        g.setColor(Color.BLACK);
        g.setFont(f);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.rotate(angle, 50, 70);
        g.drawString(Character.toString((char) code), 50, 70);
        g.dispose();
        int l = 160, t = 120, r = -1, b = -1;
        byte[] gray = new byte[160 * 120];
        for (int y = 0; y < 120; y++)
            for (int x = 0; x < 160; x++) {
                int v = im.getRaster().getSample(x, y, 0);
                gray[y * 160 + x] = (byte) v;
                if (v < 170) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                }
            }
        if (rail) for (int x = 0; x < 160; x++) gray[((t + b) / 2) * 160 + x] = 40;
        if (shaft)
            for (int y = Math.max(0, t - 20); y <= Math.min(119, b + 20); y++)
                gray[y * 160 + r - 1] = 0;
        return WholeHeadPocketEvidence.proved(gray, 160, 120, l, t, r, b, size / 4f);
    }

    @Test
    public void wholeGlyphsAcrossSizes() throws Exception {
        for (int s : new int[] {32, 48, 64, 80})
            assertTrue("size" + s, glyph(0xe0a2, s, false, false));
    }

    @Test
    public void halfGlyphsRemainHalf() throws Exception {
        for (int s : new int[] {32, 48, 64, 80})
            assertFalse("size" + s, glyph(0xe0a3, s, false, false));
    }

    @Test
    public void wholeWithRuleAndShaft() throws Exception {
        for (int s : new int[] {32, 48, 64, 80})
            assertTrue("size" + s, glyph(0xe0a2, s, true, true));
    }

    @Test
    public void halfWithRuleAndShaft() throws Exception {
        for (int s : new int[] {32, 48, 64, 80})
            assertFalse("size" + s, glyph(0xe0a3, s, true, true));
    }

    @Test
    public void filledGlyphsRejected() throws Exception {
        for (int s : new int[] {32, 48, 64, 80})
            assertFalse("size" + s, glyph(0xe0a4, s, true, true));
    }

    @Test
    public void modestPageRotationPreservesWholeVsHalf() throws Exception {
        for (double angle : new double[] {-.12, .12})
            for (int size : new int[] {32, 48, 64, 80}) {
                assertTrue("whole" + angle + ":" + size, glyph(0xe0a2, size, true, true, angle));
                assertFalse("half" + angle + ":" + size, glyph(0xe0a3, size, true, true, angle));
            }
    }
}

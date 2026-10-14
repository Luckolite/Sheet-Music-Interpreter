// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural pages using existing licensed Bravura templates; no score-derived input. */
public class MediumContrastDynamicGlyphTest {
    private static final int W = 400, H = 280;
    private static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(new PlayingTechniqueDetector.Staff(80, 128, 12, 0, 1));

    private static byte[] blank(int paper) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        return p;
    }

    private static byte[] page(String mark, int paper, int ink, boolean darkHook) throws Exception {
        var original = GlyphResources.image("dynamic_templates/" + mark + ".png");
        int h = 28, w = Math.round(h * original.getWidth() / (float) original.getHeight());
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(original, 0, 0, w, h, null);
        graphics.dispose();
        byte[] p = blank(paper);
        int last = -1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int gray = (image.getRGB(x, y) >>> 16) & 255;
                int at = (180 + y) * W + 100 + x;
                p[at] = (byte) Math.round(ink + (paper - ink) * gray / 255f);
                if (gray < 50) last = at;
            }
        if (darkHook && last >= 0) {
            p[last] = 0;
            p[last - 1] = 0;
            p[last - 2] = 0;
        }
        return p;
    }

    private static List<PlayingTechniqueDetector.Word> words(byte[] p) throws Exception {
        return new NativeDynamicGlyphs().recognize(p, W, H, STAFFS, List.of());
    }

    private static void has(String mark, byte[] p) throws Exception {
        var found = words(p);
        assertEquals(1, found.size());
        assertEquals(mark, found.get(0).text());
    }

    private static byte[] blurred(String mark, int ink, int radius) throws Exception {
        byte[] a = page(mark, 255, ink, false), b = a.clone();
        for (int y = radius; y < H - radius; y++)
            for (int x = radius; x < W - radius; x++) {
                int sum = 0, n = 0;
                for (int dy = -radius; dy <= radius; dy++)
                    for (int dx = -radius; dx <= radius; dx++) {
                        sum += a[(y + dy) * W + x + dx] & 255;
                        n++;
                    }
                b[y * W + x] = (byte) (sum / n);
            }
        return b;
    }

    @Test
    public void blurredForteAndFortissimoRetainWholeGlyphs() throws Exception {
        for (String mark : new String[] {"f", "ff"}) {
            has(mark, blurred(mark, 80, 2));
            has(mark, blurred(mark, 140, 1));
        }
    }

    @Test
    public void blurredMezzoKeepsPrefixInsteadOfLoudForte() throws Exception {
        has("mf", blurred("mf", 80, 1));
        has("mf", blurred("mf", 80, 2));
        has("mf", blurred("mf", 110, 1));
        has("mp", blurred("mp", 110, 1));
        has("mp", blurred("mp", 140, 1));
    }

    @Test
    public void supplementalComponentsDoNotMutateSource() throws Exception {
        byte[] p = blurred("ff", 80, 2), copy = p.clone();
        words(p);
        assertArrayEquals(copy, p);
    }
}

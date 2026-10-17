// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

/** Original layouts using bundled SIL-OFL Bravura templates and Noto Serif; no score input. */
public class GlyphAcceptanceControlsTest {
    private static final int W = 400, H = 280;
    private static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(new PlayingTechniqueDetector.Staff(72, 128, 14, 0, 1));

    private static byte[] blank() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    private static BufferedImage tight(BufferedImage image) {
        int l = image.getWidth(), t = image.getHeight(), r = -1, b = -1;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if ((image.getRGB(x, y) & 255) < 145) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                }
        assertTrue(r >= l);
        return image.getSubimage(l, t, r - l + 1, b - t + 1);
    }

    private static void place(byte[] g, BufferedImage im, int left, int top) {
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++)
                g[(top + y) * W + left + x] = (byte) (im.getRGB(x, y) & 255);
    }

    private static BufferedImage forte() throws Exception {
        var source = GlyphResources.image("dynamic_templates/f.png");
        int h = 28, w = Math.round(h * source.getWidth() / (float) source.getHeight());
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return tight(image);
    }

    private static BufferedImage text(String text) throws Exception {
        Path path = Path.of("training/fonts/NotoSerif.ttf");
        if (!Files.exists(path)) path = Path.of("../training/fonts/NotoSerif.ttf");
        var font = Font.createFont(Font.TRUETYPE_FONT, path.toFile()).deriveFont(18f);
        var image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 80, 60);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString(text, 10, 35);
        g.dispose();
        return tight(image);
    }

    private static List<PlayingTechniqueDetector.Word> recognize(byte[] p) throws Exception {
        return new NativeDynamicGlyphs().recognize(p, W, H, STAFFS, List.of());
    }

    @Test
    public void acceptedForteSurvivesUnrelatedTouchingProposal() throws Exception {
        var f = forte();
        byte[] before = blank();
        place(before, f, 100, 180);
        assertEquals("f", recognize(before).get(0).text());
        byte[] p = before.clone();
        int left = -1;
        // Find a three-column bounding-box overlap separated from the bottom-row ink.
        for (int x = 2; x < f.getWidth() - 3; x++) {
            boolean clear = true;
            for (int xx = x - 1; xx <= x + 3; xx++)
                if ((f.getRGB(xx, f.getHeight() - 1) & 255) < 145) clear = false;
            if (clear) {
                left = x;
                break;
            }
        }
        assertTrue("Fixture must have disconnected endpoint", left >= 0);
        for (int y = 180 + f.getHeight(); y < 188 + f.getHeight(); y++)
            for (int x = 100 + left; x < 103 + left; x++) p[y * W + x] = 20;
        assertEquals(1, ScoreDynamicsDetector.symbolBoxes(before, STAFFS, W, H).size());
        // Narrow auxiliary fragment alone is below final word width; original plus union remain.
        assertEquals(
                "Union must be exercised",
                2,
                ScoreDynamicsDetector.symbolBoxes(p, STAFFS, W, H).size());
        byte[] saved = p.clone();
        var result = recognize(p);
        assertEquals(1, result.size());
        assertEquals("f", result.get(0).text());
        assertArrayEquals(saved, p);
    }

    @Test
    public void touchingPrintedSevenAndLetterCannotInventDynamic() throws Exception {
        var seven = text("7");
        var letter = text("L");
        byte[] p = blank();
        place(p, seven, 100, 180);
        place(p, letter, 105, 180 + seven.getHeight());
        assertEquals(
                "Union must be exercised",
                3,
                ScoreDynamicsDetector.symbolBoxes(p, STAFFS, W, H).size());
        byte[] saved = p.clone();
        assertTrue(recognize(p).isEmpty());
        assertArrayEquals(saved, p);
    }
}

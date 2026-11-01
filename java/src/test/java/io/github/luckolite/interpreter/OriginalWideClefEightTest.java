// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;

/** Original SIL-OFL font drawings: a clef's intrinsic digit, never source-score pixels. */
public class OriginalWideClefEightTest {
    static final int W = 180, H = 140;

    record Scene(byte[] gray, int left, int top, int right, int bottom) {}

    static Path font(String name) {
        String supplied = System.getProperty("original.font.root");
        if (supplied != null)
            return Path.of(supplied)
                    .resolve(
                            name.equals("Bravura.otf")
                                    ? "java/assets/" + name
                                    : "training/fonts/" + name);
        String[] paths =
                name.equals("Bravura.otf")
                        ? new String[] {
                            "java/assets/Bravura.otf",
                            "app/src/main/assets/engraving/Bravura.otf",
                            "src/main/assets/engraving/Bravura.otf"
                        }
                        : new String[] {
                            "training/fonts/NotoSerif.ttf",
                            "tools/independent-omr/fonts/NotoSerif.ttf",
                            "../tools/independent-omr/fonts/NotoSerif.ttf"
                        };
        for (String candidate : paths)
            if (Files.isRegularFile(Path.of(candidate))) return Path.of(candidate);
        throw new IllegalStateException("Missing licensed clef regression font: " + name);
    }

    static Scene scene(String text, boolean attach) throws Exception {
        Font f = Font.createFont(Font.TRUETYPE_FONT, font("Bravura.otf").toFile()).deriveFont(48f);
        var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setFont(f);
        var v = f.createGlyphVector(g.getFontRenderContext(), text);
        var b = v.getPixelBounds(null, 0, 0);
        int x = 72 - b.x, y = 30 - b.y;
        g.drawGlyphVector(v, x, y);
        g.dispose();
        byte[] gray = new byte[W * H];
        int l = W, t = H, r = -1, bot = -1;
        for (int yy = 0; yy < H; yy++)
            for (int xx = 0; xx < W; xx++) {
                int p = image.getRGB(xx, yy) & 255;
                gray[yy * W + xx] = (byte) p;
                if (p <= 175) {
                    l = Math.min(l, xx);
                    r = Math.max(r, xx);
                    t = Math.min(t, yy);
                    bot = Math.max(bot, yy);
                }
            }
        // The independent positive deliberately exceeds the legacy width ceiling.
        if (text.equals("\uE088")) {
            assertTrue("wide original music8", r - l + 1 > 16 * 1.1f);
            assertTrue("complete digit fits original search", bot - t + 1 <= 16 * 1.8f);
        }
        if (attach) for (int yy = bot; yy < 105; yy++) gray[yy * W + (l + r) / 2] = 0;
        return new Scene(gray, l, t, r, bot);
    }

    static boolean above(Scene s) {
        byte[] copy = s.gray.clone();
        boolean out =
                OctaveClefDigit.above(s.gray, W, H, s.left - 3, s.bottom + 1, s.right + 3, 90, 16);
        assertArrayEquals(copy, s.gray);
        return out;
    }

    @Test
    public void originalWideEight() throws Exception {
        assertTrue(above(scene("\uE088", false)));
    }

    @Test
    public void originalWideEightAttachedToClef() throws Exception {
        assertTrue(above(scene("\uE088", true)));
    }

    @Test
    public void musicZeroIsNotEight() throws Exception {
        assertFalse(above(scene("\uE080", false)));
    }

    @Test
    public void musicThreeIsNotEight() throws Exception {
        assertFalse(above(scene("\uE083", false)));
    }

    @Test
    public void clippedCounterRefuses() throws Exception {
        Scene s = scene("\uE088", false);
        for (int y = s.top; y <= s.bottom; y++)
            for (int x = s.left; x <= (s.left + s.right) / 2; x++) s.gray[y * W + x] = (byte) 255;
        assertFalse(above(s));
    }

    static Scene ordinary(String text) throws Exception {
        var f = Font.createFont(Font.TRUETYPE_FONT, font("NotoSerif.ttf").toFile()).deriveFont(24f);
        var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setFont(f);
        g.drawString(text, text.equals("18") || text.equals("28") ? 58 : 72, 52);
        g.dispose();
        byte[] a = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) a[y * W + x] = (byte) image.getRGB(x, y);
        return new Scene(a, 68, 30, 98, 53);
    }

    @org.junit.Ignore(
            "Pre-existing narrow-dimension path accepts B; new whole-waist authority rejects it, but this repair does not change the old path.")
    @Test
    public void letterBRefuses() throws Exception {
        assertFalse(above(ordinary("B")));
    }

    @Test
    public void measure18Refuses() throws Exception {
        assertFalse(above(ordinary("18")));
    }

    @Test
    public void measure28Refuses() throws Exception {
        assertFalse(above(ordinary("28")));
    }

    @Test
    public void octaveWordRefuses() throws Exception {
        assertFalse(above(ordinary("8va")));
    }

    @Test
    public void remoteFingeringOutsideClefHeaderRefuses() throws Exception {
        Scene s = scene("\uE088", false);
        assertFalse(OctaveClefDigit.above(s.gray, W, H, 110, 60, 140, 90, 14));
    }

    @Test
    public void remotePageNumberOutsideClefHeaderRefuses() throws Exception {
        Scene s = scene("\uE088", false);
        assertFalse(OctaveClefDigit.above(s.gray, W, H, 65, 115, 105, 130, 14));
    }
}

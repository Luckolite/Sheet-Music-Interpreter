// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;

/** Original thin measure-number drawings and SIL-OFL Noto Serif glyphs; no source pixels. */
public class ThinHeaderOctaveDigitTest {
    static final int W = 160, H = 160;

    private static byte[] page(boolean loop) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 30; y <= 43; y++)
            for (int x = 70; x <= 77; x++)
                if (x == 70 || x == 77 || y == 30 || y == 43 || !loop && y == 37) g[y * W + x] = 20;
        return g;
    }

    private static void two(byte[] g, int shift) {
        for (int x = 61; x <= 67; x++) {
            g[(32 + shift) * W + x] = 20;
            g[(42 + shift) * W + x] = 20;
        }
        for (int y = 33; y < 42; y++) g[(y + shift) * W + 67 - Math.round((y - 33) * 6 / 8f)] = 20;
    }

    private static boolean above(byte[] g) {
        byte[] saved = g.clone();
        boolean found = OctaveClefDigit.above(g, W, H, 65, 46, 100, 72, 18);
        assertArrayEquals(saved, g);
        return found;
    }

    @Test
    public void thinAlignedTwoMakesMeasureNumber() {
        byte[] g = page(false);
        two(g, 0);
        assertFalse(above(g));
    }

    @Test
    public void isolatedEightRemainsOctave() {
        assertTrue(above(page(false)));
    }

    @Test
    public void genuineEightTouchingClefTipRemainsOctave() {
        byte[] g = page(false);
        for (int y = 43; y < 110; y++) g[y * W + 73] = 20;
        assertTrue(above(g));
    }

    @Test
    public void ordinaryClefLoopIsNotEight() {
        assertFalse(above(page(true)));
    }

    @Test
    public void differentBaselineCharacterDoesNotSuppressEight() {
        byte[] g = page(false);
        two(g, -9);
        assertTrue(above(g));
    }

    @Test
    public void narrowNoiseDoesNotSuppressEight() {
        byte[] g = page(false);
        for (int y = 32; y <= 42; y++) g[y * W + 64] = 20;
        assertTrue(above(g));
    }

    @Test
    public void connectedClefTipCannotMasqueradeAsCompleteNeighbor() {
        byte[] g = page(false);
        two(g, 0);
        for (int y = 42; y < 110; y++) g[y * W + 61] = 20;
        assertTrue(above(g));
    }

    @Test
    public void nearbyNoteWithStemIsNotCompleteCharacter() {
        byte[] g = page(false);
        for (int y = 34; y <= 40; y++)
            for (int x = 61; x <= 67; x++)
                if (Math.pow((x - 64) / 3., 2) + Math.pow((y - 37) / 3., 2) <= 1) g[y * W + x] = 20;
        for (int y = 15; y <= 37; y++) g[y * W + 67] = 20;
        assertTrue(above(g));
    }

    @Test
    public void unrelatedRuleRemainsSeparate() {
        byte[] g = page(false);
        for (int x = 40; x < 120; x++) g[55 * W + x] = 20;
        assertTrue(above(g));
    }

    @Test
    public void completePrintedThinFontMeasureNumberRejects() throws Exception {
        Path fontPath = Path.of("training/fonts/NotoSerif.ttf");
        if (!Files.exists(fontPath)) fontPath = Path.of("../training/fonts/NotoSerif.ttf");
        var font = Font.createFont(Font.TRUETYPE_FONT, fontPath.toFile()).deriveFont(22f);
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, W, H);
        graphics.setColor(Color.BLACK);
        graphics.setFont(font);
        graphics.drawString("28", 58, 48);
        graphics.dispose();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) g[y * W + x] = (byte) (image.getRGB(x, y) & 255);
        assertFalse(OctaveClefDigit.above(g, W, H, 70, 54, 100, 83, 22));
    }

    @Test
    public void isolatedPrintedThinFontEightRemainsOctave() throws Exception {
        Path fontPath = Path.of("training/fonts/NotoSerif.ttf");
        if (!Files.exists(fontPath)) fontPath = Path.of("../training/fonts/NotoSerif.ttf");
        var font = Font.createFont(Font.TRUETYPE_FONT, fontPath.toFile()).deriveFont(22f);
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, W, H);
        graphics.setColor(Color.BLACK);
        graphics.setFont(font);
        graphics.drawString("8", 72, 48);
        graphics.dispose();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) g[y * W + x] = (byte) (image.getRGB(x, y) & 255);
        assertTrue(OctaveClefDigit.above(g, W, H, 70, 54, 100, 83, 22));
    }
}

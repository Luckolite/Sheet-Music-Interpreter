// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original SIL-OFL font placements in generic confirmed bass frames; no source pixels. */
public class BassIntrinsicNumeralContextTest {
    private static final int W = 240, H = 200, L = 92, R = 132, TOP = 110;

    private static BufferedImage mark(String value, int face, int h) throws Exception {
        var path = Path.of(face == 0 ? "java/assets/Bravura.otf" : "training/fonts/NotoSerif.ttf");
        if (!Files.exists(path)) path = Path.of("../" + path);
        var font = Font.createFont(Font.TRUETYPE_FONT, path.toFile());
        if (face == 2) font = font.deriveFont(Font.BOLD);
        font = font.deriveFont(24f);
        String text =
                face == 0
                        ? value.chars()
                                .map(c -> 0xE080 + c - '0')
                                .collect(
                                        StringBuilder::new,
                                        StringBuilder::appendCodePoint,
                                        StringBuilder::append)
                                .toString()
                        : value;
        var im = new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB);
        var g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 160, 100);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawString(text, 20, 60);
        g.dispose();
        int l = 160, t = 100, r = -1, b = -1;
        for (int y = 0; y < 100; y++)
            for (int x = 0; x < 160; x++)
                if ((im.getRGB(x, y) & 255) <= 175) {
                    l = Math.min(l, x);
                    t = Math.min(t, y);
                    r = Math.max(r, x);
                    b = Math.max(b, y);
                }
        int w = Math.round(h * (r - l + 1) / (float) (b - t + 1));
        var scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        g = scaled.createGraphics();
        g.drawImage(im.getSubimage(l, t, r - l + 1, b - t + 1), 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }

    private static byte[] page(String text, int face, int h, int dx, int dy, boolean context)
            throws Exception {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        var im = mark(text, face, h);
        int left = Math.round((L + R - im.getWidth()) * .5f) + dx,
                top = TOP - h - Math.round(h * .24f) + dy;
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++)
                p[(top + y) * W + left + x] = (byte) (im.getRGB(x, y) & 255);
        if (context)
            for (int y = top + 4; y < top + 7; y++)
                for (int x = left - 4; x < left - 1; x++) p[y * W + x] = 20;
        return p;
    }

    private static int shift(byte[] p, float gap) {
        byte[] saved = p.clone();
        int value = ClefOctaveNumerals.aboveBass(p, W, H, L, R, TOP, gap);
        assertArrayEquals(saved, p);
        return value;
    }

    @Test
    public void attachedFifteenHasTwoIntrinsicOctavesAcrossFontsAndScales() throws Exception {
        for (int face = 0; face < 3; face++)
            for (int h : new int[] {16, 22})
                assertEquals(
                        "face" + face + " h" + h, 2, shift(page("15", face, h, 0, 0, false), h));
    }

    @Test
    public void ordinaryMeasureFifteenAtLeftIsNotIntrinsic() throws Exception {
        for (int face = 0; face < 3; face++)
            assertEquals(0, shift(page("15", face, 18, -28, 0, false), 18));
    }

    @Test
    public void highTempoFifteenIsNotAttachedToClef() throws Exception {
        assertEquals(0, shift(page("15", 0, 18, 0, -18, false), 18));
    }

    @Test
    public void TempoContextNextToCenteredDigitsRejects() throws Exception {
        assertEquals(0, shift(page("15", 0, 18, 0, 0, true), 18));
    }

    @Test
    public void fingeringPairAboveNearbyAttackIsNotIntrinsic() throws Exception {
        assertEquals(0, shift(page("15", 0, 18, 34, 0, false), 18));
    }

    @Test
    public void otherTwoDigitNumbersCannotInventFifteenth() throws Exception {
        for (String text : new String[] {"14", "16", "18", "25", "51"})
            assertEquals(text, 0, shift(page(text, 0, 18, 0, 0, false), 18));
    }

    @Test
    public void isolatedEightKeepsSingleIntrinsicOctave() throws Exception {
        assertEquals(1, shift(page("8", 0, 18, 0, 0, false), 18));
    }

    @Test
    public void compactTwentyEightIsNotOctaveEight() throws Exception {
        assertEquals(0, shift(page("28", 0, 18, 0, 0, false), 18));
    }
}

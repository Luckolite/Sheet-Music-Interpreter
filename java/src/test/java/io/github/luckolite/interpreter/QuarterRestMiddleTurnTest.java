// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent SIL-OFL Bravura raster contours and original flat-middle stroke controls. */
public class QuarterRestMiddleTurnTest {
    /** Resolve the same licensed fixture from either repository root or the Android module. */
    private static Path fontFile() {
        for (Path candidate : new Path[] {
                Path.of("java/assets/Bravura.otf"),
                Path.of("app/src/main/assets/engraving/Bravura.otf"),
                Path.of("src/main/assets/engraving/Bravura.otf")})
            if (Files.isRegularFile(candidate)) return candidate;
        throw new IllegalStateException("Missing licensed Bravura regression fixture");
    }

    private static final int W = 280, H = 210;

    private record Raster(byte[] gray, int left, int right, int top, int bottom, boolean[] lines) {}

    private Raster glyph(char code, float gap, int size, float narrow, float phase, int radius)
            throws Exception {
        var font =
                Font.createFont(Font.TRUETYPE_FONT, fontFile().toFile())
                        .deriveFont(100f);
        var outline =
                font.createGlyphVector(
                                new FontRenderContext(null, true, true), String.valueOf(code))
                        .getOutline();
        var b = outline.getBounds2D();
        double sy = size / b.getHeight(), sx = sy * narrow;
        var t = new AffineTransform();
        t.translate(160 - b.getX() * sx + phase, 80 + gap * .7 - b.getY() * sy);
        t.scale(sx, sy);
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(t.createTransformedShape(outline));
        g.dispose();
        byte[] gray = new byte[W * H];
        int left = W, right = -1, top = H, bottom = -1;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int shade = 80 + image.getRaster().getSample(x, y, 0) * 170 / 255;
                gray[y * W + x] = (byte) shade;
                if (shade < 170) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        boolean[] lines = new boolean[H];
        for (int line = 0; line < 5; line++)
            for (int y = Math.round(80 + line * gap) - radius;
                    y <= Math.round(80 + line * gap) + radius;
                    y++) {
                lines[y] = true;
                for (int x = 12; x < W - 12; x++)
                    gray[y * W + x] = (byte) Math.min(130, gray[y * W + x] & 255);
            }
        return new Raster(gray, left, right, top, bottom, lines);
    }

    private boolean read(Raster p, float gap) throws Exception {
        var method =
                SixteenthRestDetector.class.getDeclaredMethod(
                        "quarterRest",
                        byte[].class,
                        int.class,
                        SixteenthRestDetector.Staff.class,
                        int.class,
                        boolean[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        method.setAccessible(true);
        var before = p.gray.clone();
        boolean value =
                (boolean)
                        method.invoke(
                                null,
                                p.gray,
                                W,
                                new SixteenthRestDetector.Staff(80, 80 + 4 * gap, gap, 0, 1),
                                0,
                                p.lines,
                                p.left,
                                p.right,
                                p.top,
                                p.bottom);
        assertArrayEquals(before, p.gray);
        return value;
    }

    @Test
    public void genuineQuartersSurviveRasterizedMiddleTurns() throws Exception {
        float[][] cases = {
            {10, 30, .75f, 0, 1},
            {14, 42, 1, 0, 3},
            {15.25f, 41, .75f, 0, 2},
            {15.25f, 46, .75f, 0, 2},
            {15.25f, 46, .85f, 0, 2},
            {15.25f, 46, 1, .5f, 3},
            {16, 48, 1, 0, 3},
            {20, 60, .75f, .5f, 3},
            {20, 60, .85f, .5f, 3}
        };
        for (var c : cases)
            assertTrue(
                    Arrays.toString(c),
                    read(glyph('\uE4E5', c[0], (int) c[1], c[2], c[3], (int) c[4]), c[0]));
    }

    @Test
    public void accidentalsDoNotGainAQuarterRestContour() throws Exception {
        for (char code : new char[] {'\uE260', '\uE261', '\uE262'})
            for (float gap : new float[] {10, 15.25f, 20})
                assertFalse(read(glyph(code, gap, Math.round(gap * 2.7f), .85f, 0, 1), gap));
    }

    @Test
    public void aFlatMiddleStrokeStillNeedsAnIndependentTurn() throws Exception {
        for (int gap : new int[] {4, 6, 8}) {
            byte[] gray = new byte[W * H];
            Arrays.fill(gray, (byte) 250);
            int top = 80 + Math.round(gap * .7f), height = Math.round(gap * 2.7f);
            int left = 160, right = 160 + gap;
            for (int row = 0; row < height; row++) {
                float fraction = row / (float) (height - 1);
                float x =
                        fraction < .25f
                                ? fraction * 3
                                : fraction < .43f
                                        ? .75f - (fraction - .25f) * 2.5f
                                        : fraction < .75f
                                                ? .3f
                                                : fraction < .9f
                                                        ? .3f - (fraction - .75f)
                                                        : .15f + (fraction - .9f) * 2;
                int center = left + Math.round(gap * x);
                for (int dx = -1; dx <= 1; dx++) gray[(top + row) * W + center + dx] = 80;
            }
            assertFalse(
                    read(
                            new Raster(
                                    gray,
                                    left - 1,
                                    right + 1,
                                    top,
                                    top + height - 1,
                                    new boolean[H]),
                            gap));
        }
    }
}

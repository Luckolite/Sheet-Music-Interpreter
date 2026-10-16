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

/** Original SIL-OFL Bravura fixtures with remote staff masks and independently supported local rules. */
public class CompressedQuarterRestContourTest {
    /** Resolve the same licensed fixture from either repository root or the Android module. */
    private static Path fontFile() {
        for (Path candidate :
                new Path[] {
                    Path.of("java/assets/Bravura.otf"),
                    Path.of("app/src/main/assets/engraving/Bravura.otf"),
                    Path.of("src/main/assets/engraving/Bravura.otf")
                }) if (Files.isRegularFile(candidate)) return candidate;
        throw new IllegalStateException("Missing licensed Bravura regression fixture");
    }

    private static final int W = 280, H = 210;

    private record Raster(byte[] gray, int left, int right, int top, int bottom, boolean[] lines) {}

    private Raster glyph(char code, float gap, int size, float narrow, float phase, int radius)
            throws Exception {
        var font = Font.createFont(Font.TRUETYPE_FONT, fontFile().toFile()).deriveFont(100f);
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

    private Raster masked(char code, float gap, float ratio, float narrow, float phase, int halo)
            throws Exception {
        Raster p = glyph(code, gap, Math.round(gap * ratio), narrow, phase, 0);
        for (int rule = 0; rule < 5; rule++)
            for (int y = Math.round(80 + rule * gap) - halo;
                    y <= Math.round(80 + rule * gap) + halo;
                    y++) p.lines[y] = true;
        return p;
    }

    @Test
    public void compressedQuarterWithTwoRowHalo() throws Exception {
        assertTrue(read(masked('\uE4E5', 10, 3.2f, .65f, 0, 2), 10));
    }

    @Test
    public void compressedQuarterWithThreeRowHalo() throws Exception {
        assertTrue(read(masked('\uE4E5', 10, 3.2f, .65f, 0, 3), 10));
    }

    @Test
    public void compressedAccidentalsRemainRejected() throws Exception {
        for (char code : new char[] {'\uE260', '\uE261', '\uE262'})
            for (float gap : new float[] {10, 14, 18})
                assertFalse(read(masked(code, gap, 3.2f, .65f, 0, 3), gap));
    }

    @Test
    public void smallRasterWaveCannotAuthorizeRescaling() {
        double[] rows = new double[40];
        for (int i = 0; i < rows.length; i++) rows[i] = 7 + Math.sin(i * .3);
        assertFalse(CompactQuarterRestContour.matches(rows, 14));
    }

    @Test
    public void continuationAboveCannotBecomeAnIsolatedQuarter() throws Exception {
        Raster p = masked('\uE4E5', 10, 3.2f, .65f, 0, 2);
        for (int y = p.top - 5; y < p.top; y++)
            for (int x = p.left + 2; x <= p.left + 5; x++) p.gray[y * W + x] = (byte) 80;
        assertFalse(read(p, 10));
    }

    @Test
    public void continuationBelowCannotBecomeAnIsolatedQuarter() throws Exception {
        Raster p = masked('\uE4E5', 10, 3.2f, .65f, 0, 2);
        for (int y = p.bottom + 1; y <= p.bottom + 5; y++)
            for (int x = p.left + 2; x <= p.left + 5; x++) p.gray[y * W + x] = (byte) 80;
        assertFalse(read(p, 10));
    }
}

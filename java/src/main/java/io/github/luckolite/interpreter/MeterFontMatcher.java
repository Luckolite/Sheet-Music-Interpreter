// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public final class MeterFontMatcher {
    public record Reading(String text, float topScore, float bottomScore) {}

    private final MeterGlyphMatcher matcher;

    public MeterFontMatcher(File file) throws Exception {
        Font font = Font.createFont(Font.TRUETYPE_FONT, file);
        matcher =
                new MeterGlyphMatcher(
                        (glyphs, size) -> {
                            Font scaled = font.deriveFont((float) size);
                            BufferedImage scratch =
                                    new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
                            Graphics2D measure = scratch.createGraphics();
                            var bounds =
                                    scaled.createGlyphVector(measure.getFontRenderContext(), glyphs)
                                            .getPixelBounds(null, 0, 0);
                            measure.dispose();
                            int gw = bounds.width, gh = bounds.height;
                            if (gw < 1 || gh < 1)
                                return new MeterGlyphMatcher.Glyph(0, 0, new boolean[0]);
                            BufferedImage glyph =
                                    new BufferedImage(gw, gh, BufferedImage.TYPE_BYTE_GRAY);
                            Graphics2D graphics = glyph.createGraphics();
                            graphics.setColor(java.awt.Color.WHITE);
                            graphics.fillRect(0, 0, gw, gh);
                            graphics.setColor(java.awt.Color.BLACK);
                            graphics.setFont(scaled);
                            graphics.setRenderingHint(
                                    RenderingHints.KEY_TEXT_ANTIALIASING,
                                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                            graphics.drawString(glyphs, -bounds.x, -bounds.y);
                            graphics.dispose();
                            boolean[] shape = new boolean[gw * gh];
                            for (int y = 0; y < gh; y++)
                                for (int x = 0; x < gw; x++)
                                    shape[y * gw + x] = (glyph.getRGB(x, y) & 255) < 150;

                            return new MeterGlyphMatcher.Glyph(gw, gh, shape);
                        });
    }

    public Reading read(int[] p, int w, int h, int m, int f, float g) {
        return read(p, w, h, m, f, g, "", "");
    }

    public Reading read(int[] p, int w, int h, int m, int f, float g, String u, String l) {
        return read(p, w, h, m, f, g, u, l, null);
    }

    public Reading read(
            int[] p, int w, int h, int m, int f, float g, String u, String l, boolean[] ignored) {
        var r = matcher.read(p, w, h, m, f, g, u, l, ignored);
        return new Reading(r.text(), r.topScore(), r.bottomScore());
    }

    static boolean accepts(int winner, float score, float runner, String corroborated) {
        return MeterGlyphMatcher.accepts(winner, score, runner, corroborated);
    }
}

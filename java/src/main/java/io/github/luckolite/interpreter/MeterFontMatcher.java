// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative fallback for stacked music-font numerals that ordinary text OCR omits. */
public final class MeterFontMatcher {
    public record Reading(String text, float topScore, float bottomScore) {}

    private record Match(int value, float score) {}

    private final Font font;

    public MeterFontMatcher(File file) throws Exception {
        font = Font.createFont(Font.TRUETYPE_FONT, file);
    }

    public Reading read(int[] pixels, int width, int height, int middle, int firstLine, float gap) {
        if (width < gap * 1.35f || height < 12 || middle < 6 || middle > height - 6)
            return new Reading("", 0, 0);
        Match top =
                match(
                        pixels,
                        width,
                        height,
                        0,
                        middle,
                        firstLine,
                        gap,
                        new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        Match bottom =
                match(
                        pixels,
                        width,
                        height,
                        middle,
                        height,
                        firstLine,
                        gap,
                        new int[] {2, 4, 8, 16, 32});
        if (top.value() == 0 || bottom.value() == 0)
            return new Reading("", top.score(), bottom.score());
        return new Reading(top.value() + "/" + bottom.value(), top.score(), bottom.score());
    }

    private Match match(
            int[] pixels,
            int width,
            int height,
            int first,
            int last,
            int firstLine,
            float gap,
            int[] values) {
        int h = last - first;
        boolean[] source = new boolean[width * h], valid = new boolean[source.length];
        int sourceCount = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < width; x++) {
                int color = pixels[(first + y) * width + x];
                source[y * width + x] = (color & 255) < 150;
                boolean onLine = false;
                for (int n = 0; n < 5; n++)
                    if (Math.abs(first + y - Math.round(firstLine + n * gap))
                            <= Math.max(1, Math.round(gap * .12f))) onLine = true;
                valid[y * width + x] = !onLine;
                if (!onLine && source[y * width + x]) sourceCount++;
            }
        if (sourceCount < gap * 3) return new Match(0, 0);
        List<Match> results = new ArrayList<>();
        int minSize = Math.round(gap * 2.1f), maxSize = Math.round(gap * 5.15f);
        for (int value : values) {
            float best = 0;
            String glyphs =
                    Integer.toString(value)
                            .codePoints()
                            .map(digit -> 0xe080 + digit - '0')
                            .collect(
                                    StringBuilder::new,
                                    StringBuilder::appendCodePoint,
                                    StringBuilder::append)
                            .toString();
            for (int size = minSize; size <= maxSize; size += Math.max(2, Math.round(gap * .18f))) {
                Font scaled = font.deriveFont((float) size);
                BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
                Graphics2D measure = scratch.createGraphics();
                var bounds =
                        scaled.createGlyphVector(measure.getFontRenderContext(), glyphs)
                                .getPixelBounds(null, 0, 0);
                measure.dispose();
                int gw = bounds.width, gh = bounds.height;
                if (gw < 1 || gh < 1 || gw > width + 8 || gh > h + 8) continue;
                BufferedImage glyph = new BufferedImage(gw, gh, BufferedImage.TYPE_BYTE_GRAY);
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
                for (int dx = -4; dx <= width - gw + 4; dx++)
                    for (int dy = -4; dy <= h - gh + 4; dy++) {
                        int tp = 0, targetCount = 0;
                        for (int gy = 0; gy < gh; gy++)
                            for (int gx = 0; gx < gw; gx++) {
                                if (!shape[gy * gw + gx]) continue;
                                int x = gx + dx, y = gy + dy;
                                if (x < 0 || x >= width || y < 0 || y >= h) continue;
                                int index = y * width + x;
                                if (!valid[index]) continue;
                                targetCount++;
                                if (source[index]) tp++;
                            }
                        float score = 2f * tp / Math.max(1, sourceCount + targetCount);
                        if (score > best) best = score;
                    }
            }
            results.add(new Match(value, best));
        }
        results.sort(Comparator.comparingDouble(Match::score).reversed());
        Match winner = results.get(0);
        if (winner.score() < .84f || winner.score() - results.get(1).score() < .05f)
            return new Match(0, winner.score());
        return winner;
    }
}

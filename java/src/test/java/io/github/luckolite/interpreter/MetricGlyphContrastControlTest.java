// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original synthetic pages; glyph sprites rendered from SIL-OFL Bravura, no score content. */
public final class MetricGlyphContrastControlTest {
    private static final int W = 600, H = 380;

    private static BufferedImage page() {
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        for (int y = 180; y <= 244; y += 16) g.drawLine(20, y, 580, y);
        g.dispose();
        return image;
    }

    private static BufferedImage glyph(String name) throws Exception {
        return ImageIO.read(
                Path.of("java/src/test/resources/expression-glyphs", name + ".png").toFile());
    }

    private static int draw(BufferedImage page, String name, int x, int y, int h) throws Exception {
        var image = glyph(name);
        int w = Math.round(image.getWidth() * (float) h / image.getHeight());
        var g = page.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, x, y, w, h, null);
        g.dispose();
        return x + w;
    }

    private static void equals(BufferedImage page, int x, int y) {
        var g = page.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(x, y, 12, 2);
        g.fillRect(x, y + 7, 12, 2);
        g.dispose();
    }

    private static ScorePageInterpretation score() {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (float position : new float[] {.2f, .43f, .67f, .85f})
            notes.add(new ScoreNoteEvent(0, position, 0, 0, 1, 244f / H, false, 0, 0, 0, 1, 1, 0));
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(20f / W, 580f / W, 150f / H, 280f / H)),
                notes,
                1,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 4, 4)),
                List.of(),
                List.of(),
                List.of());
    }

    private static ScorePageInterpretation detect(BufferedImage image, boolean rails) {
        if (!rails) {
            var g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 180, W, 80);
            g.dispose();
        }
        byte[] gray = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) gray[y * W + x] = (byte) (image.getRGB(x, y) & 255);
        return ScoreExpressionDetector.apply(
                score(),
                List.of(),
                List.of(new PlayingTechniqueDetector.Staff(180, 244, 16, 0, 1)),
                gray,
                W,
                H,
                GlyphResources.expressions());
    }

    private static BufferedImage equation(String left, String right, int h, boolean dot)
            throws Exception {
        var image = page();
        int x = 132, y = 110;
        int edge =
                draw(
                        image,
                        left,
                        x,
                        left.equals("whole") ? y + h - 12 : y,
                        left.equals("whole") ? 12 : h);
        if (dot) {
            var g = image.createGraphics();
            g.setColor(Color.BLACK);
            g.fillOval(edge + 4, y + h - 9, 4, 4);
            g.dispose();
            edge += 10;
        }
        equals(image, edge + 8, y + h - 16);
        draw(image, right, edge + 30, y, h);
        return image;
    }

    @Test
    public void largerSeparatedDotRetainsDottedPulse() throws Exception {
        var image = page();
        int edge = draw(image, "quarter", 132, 110, 36);
        var g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(edge + 4, 137, 8, 7);
        g.dispose();
        equals(image, edge + 20, 130);
        draw(image, "quarter", edge + 42, 110, 36);
        var result = detect(image, true);
        assertEquals(1, result.expressiveEvents().size());
        assertEquals(
                1.5,
                MetricModulationText.decode(result.expressiveEvents().get(0).qualifierText())
                        .orElseThrow()
                        .leftQuarterBeats(),
                0);
    }

    @Test
    public void originalFaintEquationSurvivesLowerThresholdFallback() throws Exception {
        var image = equation("quarter", "half", 36, false);
        int edge =
                132 + Math.round(glyph("quarter").getWidth() * 36f / glyph("quarter").getHeight());
        for (int y = 90; y < 165; y++)
            for (int x = edge + 8; x < edge + 20; x++) {
                int c = image.getRGB(x, y) & 255;
                if (c < 175) image.setRGB(x, y, new Color(165, 165, 165).getRGB());
            }
        var result = detect(image, true);
        assertEquals(1, result.expressiveEvents().size());
        assertEquals(
                2,
                MetricModulationText.decode(result.expressiveEvents().get(0).qualifierText())
                        .orElseThrow()
                        .ratio(),
                0);
    }

    @Test
    public void lightBridgeDoesNotFusePulseAndDot() throws Exception {
        var image = page();
        int edge = draw(image, "quarter", 132, 110, 36);
        var g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillOval(edge + 4, 137, 4, 4);
        g.setColor(new Color(165, 165, 165));
        g.fillRect(edge - 10, 139, 15, 3);
        g.dispose();
        equals(image, edge + 20, 130);
        draw(image, "quarter", edge + 42, 110, 36);
        var result = detect(image, true);
        assertEquals(1, result.expressiveEvents().size());
        assertEquals(
                1.5,
                MetricModulationText.decode(result.expressiveEvents().get(0).qualifierText())
                        .orElseThrow()
                        .leftQuarterBeats(),
                0);
    }
}

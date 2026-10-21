// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original licensed-font pages. Real glyph detection, no synthetic certified Word or Mark. */
public final class TerminalBreathOwnershipOriginalTest {
    private static final int W = 800, H = 380;
    private static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(new PlayingTechniqueDetector.Staff(180, 244, 16, 0, 1));

    private static Path originalResources() {
        return Path.of("java/src/test/resources/expression-glyphs");
    }

    private static PrintedExpressionGlyphs glyphs() {
        return GlyphResources.expressions();
    }

    private static BufferedImage page() {
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        for (int y = 180; y <= 244; y += 16) g.drawLine(20, y, 780, y);
        g.dispose();
        return image;
    }

    private static int draw(BufferedImage page, String name, int x) throws Exception {
        var sprite = ImageIO.read(originalResources().resolve(name + ".png").toFile());
        assertNotNull(sprite);
        int width = Math.round(sprite.getWidth() * 22f / sprite.getHeight());
        var g = page.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(sprite, x, 145, width, 22, null);
        g.dispose();
        return width;
    }

    private static byte[] gray(BufferedImage image) {
        var out = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) out[y * W + x] = (byte) (image.getRGB(x, y) & 255);
        return out;
    }

    private static ScoreNoteEvent note(int measure, float physicalX, float left, float right) {
        return new ScoreNoteEvent(
                measure,
                (physicalX - left) / (right - left),
                0,
                0,
                1,
                244f / H,
                false,
                0,
                0,
                0,
                1,
                1,
                0);
    }

    private static ScorePageInterpretation score(float nextX, boolean ambiguous) {
        var regions = new ArrayList<MeasureRegion>();
        regions.add(new MeasureRegion(20f / W, 620f / W, 150f / H, 280f / H));
        regions.add(new MeasureRegion(620f / W, 780f / W, 150f / H, 280f / H));
        if (ambiguous) regions.add(new MeasureRegion(500f / W, 610f / W, 150f / H, 280f / H));
        var notes = new ArrayList<ScoreNoteEvent>();
        for (float x : new float[] {100, 210, 320, 430}) notes.add(note(0, x, 20, 620));
        notes.add(note(1, nextX, 620, 780));
        return new ScorePageInterpretation(
                regions,
                notes,
                1,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 4, 4)),
                List.of(),
                List.of(),
                List.of());
    }

    private static PrintedExpressionGlyphs.Mark actualMark(
            PrintedExpressionGlyphs glyphs,
            ScorePageInterpretation score,
            byte[] gray,
            String shape) {
        var marks = glyphs.detect(score, STAFFS, gray, W, H);
        assertEquals("Natural symbol setup", 1, marks.size());
        var mark = marks.get(0);
        assertEquals(shape.equals("breath-tick") ? "breath tick" : "breath comma", mark.text());
        return mark;
    }

    private static void preserved(ScorePageInterpretation before, ScorePageInterpretation after) {
        assertEquals(before, after.withExpressiveEvents(before.expressiveEvents()));
    }

    private static void positive(String shape, int x, float nextX, boolean far, boolean nextInside)
            throws Exception {
        var input = score(nextX, false);
        var image = page();
        draw(image, shape, x);
        var raw = gray(image);
        var glyphs = glyphs();
        var mark = actualMark(glyphs, input, raw, shape);
        assertTrue(
                "Full glyph must lie after terminal430 and before boundary620: shape="
                        + shape
                        + " left="
                        + mark.left()
                        + " right="
                        + mark.right(),
                mark.left() > 430 && mark.right() < 620);
        float prior = mark.left() - 430, following = nextX - mark.right();
        if (far) {
            assertTrue("Prior beyond original3gap", prior > 48);
            assertTrue("Nextbar genuinely closer", following >= 0 && following < prior);
            assertEquals("Nextbar3gap setup", nextInside, following <= 48);
        } else assertTrue(prior <= 48);
        var result = ScoreExpressionDetector.apply(input, List.of(), STAFFS, raw, W, H, glyphs);
        preserved(input, result);
        assertEquals(1, result.expressiveEvents().size());
        var event = result.expressiveEvents().get(0);
        assertEquals(Kind.BREATH, event.kind());
        assertEquals(Scope.PART, event.scope());
        assertEquals(0, event.staffIndex());
        assertEquals(1, event.staffCount());
        assertEquals(List.of(3), ScoreExpressionDetector.targetIndices(result, event));
        assertEquals(new ScoreAnchor(1, 0), event.start().orElseThrow());
        assertEquals("printed-expression-symbol", event.evidence().get(0).sourceId());
    }

    @FunctionalInterface
    private interface Check {
        void run(String shape) throws Exception;
    }

    private static void shapes(String role, Check check) throws Exception {
        var failures = new ArrayList<String>();
        for (String shape : List.of("breath-comma", "breath-tick")) {
            try {
                check.run(shape);
                System.out.println(role + " " + shape + " PASS");
            } catch (AssertionError | Exception e) {
                System.out.println(role + " " + shape + " FAIL " + e);
                failures.add(shape + ": " + e);
            }
        }
        assertTrue(String.join("; ", failures), failures.isEmpty());
    }

    @Test
    public void fartherTerminalColumnWinsOverCloserNextbarOutsideOldRadius() throws Exception {
        shapes("far-terminal", s -> positive(s, 560, 660, true, false));
    }

    @Test
    public void certifiedTerminalColumnWinsEvenWhenNextbarIsInsideOldRadius() throws Exception {
        shapes("near-nextbar", s -> positive(s, 596, 630, true, true));
    }

    @Test
    public void originalWithinRadiusTerminalReleaseIsPreserved() throws Exception {
        shapes("within-radius", s -> positive(s, 450, 660, false, false));
    }

    @Test
    public void ambiguousContainingRegionsDoNotCreateAnOwner() throws Exception {
        var input = score(660, true);
        var image = page();
        draw(image, "breath-comma", 560);
        var raw = gray(image);
        var glyphs = glyphs();
        var mark = actualMark(glyphs, input, raw, "breath-comma");
        assertTrue(mark.left() > 500 && mark.right() < 610);
        assertTrue(mark.left() - 430 > 48 && 660 - mark.right() > 48);
        var result = ScoreExpressionDetector.apply(input, List.of(), STAFFS, raw, W, H, glyphs);
        preserved(input, result);
        assertTrue(result.expressiveEvents().isEmpty());
        System.out.println("overlapping-regions PASS");
    }

    @Test
    public void glyphCrossingMeasureBoundaryDoesNotCreateAnOwner() throws Exception {
        var input = score(740, false);
        var image = page();
        var sprite = ImageIO.read(originalResources().resolve("breath-comma.png").toFile());
        int width = Math.round(sprite.getWidth() * 22f / sprite.getHeight());
        draw(image, "breath-comma", 620 - width / 2);
        var raw = gray(image);
        var glyphs = glyphs();
        var mark = actualMark(glyphs, input, raw, "breath-comma");
        assertTrue(mark.left() < 620 && mark.right() > 620);
        assertTrue(mark.left() - 430 > 48 && 740 - mark.right() > 48);
        var result = ScoreExpressionDetector.apply(input, List.of(), STAFFS, raw, W, H, glyphs);
        preserved(input, result);
        assertTrue(result.expressiveEvents().isEmpty());
        System.out.println("crossing-boundary PASS");
    }

    @Test
    public void fontCommaBesidePrintedWordRemainsPunctuation() throws Exception {
        var input = score(660, false);
        var image = page();
        int width = draw(image, "breath-comma", 560);
        var g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 22));
        g.drawString("word", 560 + width + 3, 163);
        g.dispose();
        var raw = gray(image);
        var glyphs = glyphs();
        assertTrue(
                "Natural punctuation refusal", glyphs.detect(input, STAFFS, raw, W, H).isEmpty());
        var result = ScoreExpressionDetector.apply(input, List.of(), STAFFS, raw, W, H, glyphs);
        preserved(input, result);
        assertTrue(result.expressiveEvents().isEmpty());
        System.out.println("printed-punctuation PASS");
    }
}

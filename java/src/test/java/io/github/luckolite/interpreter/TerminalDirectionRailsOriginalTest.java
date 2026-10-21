// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original licensed-font pages. Real glyph detection, no synthetic certified Word or Mark. */
public final class TerminalDirectionRailsOriginalTest {
    private static final int W = 800, H = 380;
    private static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(new PlayingTechniqueDetector.Staff(180, 244, 16, 0, 1));

    private static Path resource(String property) {
        String value = System.getProperty(property);
        if (value != null) return Path.of(value);
        if (property.equals("td374.originalResources"))
            return Path.of("java/src/test/resources/expression-glyphs");
        throw new IllegalArgumentException("Unknown resource " + property);
    }

    private static PrintedExpressionGlyphs glyphs() throws Exception {
        if (System.getProperty("td374.templates") == null) return GlyphResources.expressions();
        try (var input = Files.newInputStream(resource("td374.templates"))) {
            return PrintedExpressionGlyphs.load(input);
        }
    }

    private static BufferedImage page() {
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        for (int y = 180; y <= 244; y += 16) g.drawLine(20, y, 620, y);
        g.dispose();
        return image;
    }

    private static int draw(BufferedImage page, String name, int x) throws Exception {
        var sprite =
                ImageIO.read(resource("td374.originalResources").resolve(name + ".png").toFile());
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

    private static final int QUERY = 596, END = 620;

    private static MeasureRegion region(float left, float right) {
        return new MeasureRegion(left / W, right / W, 150f / H, 280f / H);
    }

    private static byte[] rails(float slope, int count, int shift, boolean missingLink) {
        var raw = new byte[W * H];
        java.util.Arrays.fill(raw, (byte) 255);
        for (int line = 0; line < count; line++)
            for (int x = 20; x <= END; x++) {
                if (missingLink && line == 2 && x >= QUERY - 3 && x <= QUERY + 3) continue;
                int y = 180 + line * 16 + shift + Math.round((x - QUERY) * slope);
                raw[y * W + x] = 20;
            }
        return raw;
    }

    private static ScoreNoteEvent witness() {
        return new ScoreNoteEvent(
                0, (QUERY - 20f) / 600, 0, 0, 1, 244f / H, false, 0, 0, 2, 1, 1, 0);
    }

    private static PlayingTechniqueDetector.Staff direct(
            float slope,
            int count,
            int shift,
            boolean noHead,
            boolean ongoing,
            boolean ambiguous,
            boolean missingLink) {
        var raw = rails(slope, count, shift, missingLink);
        var before = raw.clone();
        var measures = new ArrayList<MeasureRegion>();
        measures.add(region(20, 620));
        if (ongoing) measures.add(region(620, 780));
        if (ambiguous) measures.add(region(20, 620));
        assertNull(
                "New finite-end seam, not the legacy successful route",
                PrintedDirectionStaff.printedRules(raw, W, H, QUERY, 244, 16));
        var result =
                PrintedDirectionStaff.at(
                        STAFFS,
                        measures,
                        noHead ? List.of() : List.of(witness()),
                        raw,
                        W,
                        H,
                        QUERY,
                        145,
                        167);
        assertArrayEquals(before, raw);
        return result;
    }

    @Test
    public void completeFiveRailsAtFiniteEndOwnDirection() {
        var s = direct(0, 5, 0, false, false, false, false);
        assertNotNull(s);
        assertEquals(244, s.bottom(), 1);
        assertEquals(16, s.gap(), .5);
    }

    @Test
    public void bothSlopesRetainFrameAtOriginalQuery() {
        for (float slope : new float[] {.08f, -.08f}) {
            var s = direct(slope, 5, 0, false, false, false, false);
            assertNotNull("slope=" + slope, s);
            assertEquals("No shifted-window frame substituted", 244, s.bottom(), 1);
            assertEquals(16, s.gap(), .5);
        }
    }

    @Test
    public void ongoingSystemCannotUseMissingRightRails() {
        assertNull(direct(0, 5, 0, false, true, false, false));
    }

    @Test
    public void fourRailsDoNotSupplyFifth() {
        assertNull(direct(0, 4, 0, false, false, false, false));
    }

    @Test
    public void wrongWrittenHeadPhaseStaysUnowned() {
        assertNull(direct(0, 5, 16, false, false, false, false));
    }

    @Test
    public void noWrittenHeadStaysUnowned() {
        assertNull(direct(0, 5, 0, true, false, false, false));
    }

    @Test
    public void ambiguousLastMeasureStaysUnowned() {
        assertNull(direct(0, 5, 0, false, false, true, false));
    }

    @Test
    public void remoteFiveRailsCannotReplaceMissingQueryLink() {
        assertNull(direct(0, 5, 0, false, false, false, true));
    }

    private static ScoreNoteEvent note(float x, float duration, float silence) {
        return new ScoreNoteEvent(
                0,
                (x - 20) / 600,
                0,
                0,
                1,
                244f / H,
                false,
                0,
                duration == .5f ? 1 : 0,
                2,
                duration == .5f ? 0 : duration,
                1,
                silence);
    }

    private static void natural(String shape) throws Exception {
        var notes = List.of(note(100, 1, 0), note(210, 1, 0), note(320, 1, 0), note(430, .5f, .5f));
        var rest = new ScoreRestEvent(0, (530f - 20) / 600, 212f / H, 12f / H, 0, 1, .5);
        var input =
                new ScorePageInterpretation(
                        List.of(region(20, 620)),
                        notes,
                        1,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 4, 4)),
                        List.of(rest),
                        List.of(),
                        List.of());
        var image = page();
        draw(image, shape, QUERY);
        var raw = gray(image);
        var before = raw.clone();
        var glyphs = glyphs();
        var marks = glyphs.detect(input, STAFFS, raw, W, H);
        assertEquals("Natural font symbol", 1, marks.size());
        var mark = marks.get(0);
        assertEquals(shape.equals("breath-tick") ? "breath tick" : "breath comma", mark.text());
        assertTrue(mark.left() > 530 && mark.right() < END);
        assertNull(
                "Natural case must exercise finite-end ownership",
                PrintedDirectionStaff.printedRules(raw, W, H, mark.left(), 244, 16));
        assertNotNull(
                PrintedDirectionStaff.at(
                        STAFFS,
                        input.measures(),
                        notes,
                        raw,
                        W,
                        H,
                        mark.left(),
                        mark.top(),
                        mark.bottom()));
        assertEquals(3.5, ScoreRestFermataDetector.provedOnset(rest, input.rests(), notes, 4), 0);
        var output = ScoreExpressionDetector.apply(input, List.of(), STAFFS, raw, W, H, glyphs);
        assertEquals(input, output.withExpressiveEvents(input.expressiveEvents()));
        assertArrayEquals(before, raw);
        assertEquals(1, output.expressiveEvents().size());
        var e = output.expressiveEvents().get(0);
        assertEquals(Kind.BREATH, e.kind());
        assertEquals(Scope.PART, e.scope());
        assertEquals(new ScoreAnchor(1, 0), e.start().orElseThrow());
        assertTrue(e.end().isEmpty());
        assertEquals("printed-expression-symbol", e.evidence().get(0).sourceId());
        assertTrue(e.targetEventId().orElseThrow().startsWith("expression-rest-column:0:0:1:"));
        assertTrue(ScoreExpressionDetector.targetIndices(output, e).isEmpty());
    }

    @Test
    public void naturalCommaAfterRestAtRealFiveRailEnd() throws Exception {
        natural("breath-comma");
    }

    @Test
    public void naturalTickAfterRestAtRealFiveRailEnd() throws Exception {
        natural("breath-tick");
    }
}

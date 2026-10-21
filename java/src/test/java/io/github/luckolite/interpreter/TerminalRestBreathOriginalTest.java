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
public final class TerminalRestBreathOriginalTest {
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

    private static ScoreNoteEvent note(int measure, float x, float duration, float silence) {
        float left = measure == 0 ? 20 : 620, right = measure == 0 ? 620 : 780;
        return new ScoreNoteEvent(
                measure,
                (x - left) / (right - left),
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

    private static ScoreRestEvent rest(float x, double duration) {
        return new ScoreRestEvent(0, (x - 20) / 600, 212f / H, 12f / H, 0, 1, duration);
    }

    private static ScorePageInterpretation score(
            float nextX, List<ScoreRestEvent> rests, boolean meter) {
        var regions =
                List.of(
                        new MeasureRegion(20f / W, 620f / W, 150f / H, 280f / H),
                        new MeasureRegion(620f / W, 780f / W, 150f / H, 280f / H));
        var notes =
                List.of(
                        note(0, 100, 1, 0),
                        note(0, 210, 1, 0),
                        note(0, 320, 1, 0),
                        note(0, 430, .5f, .5f),
                        note(1, nextX, 1, 0));
        return new ScorePageInterpretation(
                regions,
                notes,
                1,
                List.of(),
                List.of(),
                meter ? List.of(new ScoreMeterChange(0, 4, 4)) : List.of(),
                rests,
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

    private static void resolved(ScorePageInterpretation input, ScorePageInterpretation result) {
        preserved(input, result);
        assertEquals(1, result.expressiveEvents().size());
        var event = result.expressiveEvents().get(0);
        assertEquals(Kind.BREATH, event.kind());
        assertEquals(Scope.PART, event.scope());
        assertEquals(new ScoreAnchor(1, 0), event.start().orElseThrow());
        assertTrue("No inferred pause duration", event.end().isEmpty());
        assertEquals(0, event.staffIndex());
        assertEquals(1, event.staffCount());
        assertTrue(event.targetEventId().orElseThrow().startsWith("expression-rest-column:0:0:1:"));
        assertTrue(ScoreExpressionDetector.targetIndices(result, event).isEmpty());
        assertEquals("printed-expression-symbol", event.evidence().get(0).sourceId());
    }

    private static void positive(String shape, boolean inheritedMeter) throws Exception {
        var input = score(630, List.of(rest(530, .5)), !inheritedMeter);
        var image = page();
        draw(image, shape, 596);
        var raw = gray(image);
        var glyphs = glyphs();
        var mark = actualMark(glyphs, input, raw, shape);
        assertTrue(mark.left() > 530 && mark.right() < 620);
        assertTrue(
                "Nextbar attack closer than preceding note",
                630 - mark.right() < mark.left() - 430);
        assertTrue("Nextbar attack within old radius", 630 - mark.right() <= 48);
        assertEquals(
                "Independent complete-clock precondition",
                3.5,
                ScoreRestFermataDetector.provedOnset(
                        input.rests().get(0), input.rests(), input.notes(), 4),
                0);
        var result = ScoreExpressionDetector.apply(input, List.of(), STAFFS, raw, W, H, glyphs);
        if (inheritedMeter) {
            preserved(input, result);
            assertEquals(1, result.expressiveEvents().size());
            assertTrue(result.expressiveEvents().get(0).start().isEmpty());
            assertEquals(Scope.UNRESOLVED, result.expressiveEvents().get(0).scope());
            result = ScoreExpressionDetector.resolve(result, 4);
        }
        resolved(input, result);
    }

    @Test
    public void naturalCommaUsesProvedRestReleaseDespiteCloserNextbar() throws Exception {
        positive("breath-comma", false);
    }

    @Test
    public void naturalTickWaitsForInheritedMeterThenUsesRestRelease() throws Exception {
        positive("breath-tick", true);
    }

    private static ScorePageInterpretation adverse(List<ScoreRestEvent> rests) throws Exception {
        var input = score(740, rests, true);
        var image = page();
        draw(image, "breath-comma", 560);
        var raw = gray(image);
        var glyphs = glyphs();
        var mark = actualMark(glyphs, input, raw, "breath-comma");
        assertTrue(mark.left() > 530 && mark.right() < 620);
        assertTrue(mark.left() - 430 > 48 && 740 - mark.right() > 48);
        var result = ScoreExpressionDetector.apply(input, List.of(), STAFFS, raw, W, H, glyphs);
        preserved(input, result);
        for (var event : result.expressiveEvents()) {
            assertEquals(Kind.BREATH, event.kind());
            assertTrue("No guessed rest release", event.start().isEmpty());
            assertEquals(Scope.UNRESOLVED, event.scope());
        }
        return result;
    }

    @Test
    public void duplicateSameRestObjectCannotEstablishUniqueOwner() throws Exception {
        var r = rest(530, .5);
        assertTrue(adverse(List.of(r, r)).expressiveEvents().isEmpty());
    }

    @Test
    public void RestOverlappingFinalNoteCannotEstablishOwner() throws Exception {
        assertTrue(adverse(List.of(rest(430, .5))).expressiveEvents().isEmpty());
    }

    @Test
    public void incompleteSilentClockRetainsUnresolvedIndication() throws Exception {
        var result = adverse(List.of(rest(530, .25)));
        assertEquals(
                "Natural sign retained but no invented timestamp",
                1,
                result.expressiveEvents().size());
        assertTrue(
                result.expressiveEvents()
                        .get(0)
                        .targetEventId()
                        .orElseThrow()
                        .startsWith("expression-rest-column:0:0:1:"));
    }

    @Test
    public void ordinaryOcrWordDoesNotReceiveNaturalRestPrivilege() throws Exception {
        var input = score(740, List.of(rest(530, .5)), true);
        var raw = gray(page());
        var glyphs = glyphs();
        assertTrue(glyphs.detect(input, STAFFS, raw, W, H).isEmpty());
        var word =
                new PlayingTechniqueDetector.Word(
                        "breath comma", 560f / W, 145f / H, 574f / W, 167f / H);
        var result = ScoreExpressionDetector.apply(input, List.of(word), STAFFS, raw, W, H, glyphs);
        preserved(input, result);
        assertTrue(result.expressiveEvents().isEmpty());
    }

    @Test
    public void existingNaturalRestTargetCanResolveInsideMeasure() throws Exception {
        // Acquire the event naturally first. The second layout tests only re-resolution
        // of an existing rest target; it does not claim nonterminal glyph acquisition.
        var original = score(630, List.of(rest(530, .5)), true);
        var image = page();
        draw(image, "breath-comma", 596);
        var raw = gray(image);
        var glyphs = glyphs();
        actualMark(glyphs, original, raw, "breath-comma");
        var acquired =
                ScoreExpressionDetector.apply(original, List.of(), STAFFS, raw, W, H, glyphs);
        resolved(original, acquired);
        var notes =
                List.of(
                        note(0, 100, 1, 0),
                        note(0, 210, .5f, .5f),
                        note(0, 570, 2, 0),
                        note(1, 740, 1, 0));
        var lane =
                new ScorePageInterpretation(
                                original.measures(),
                                notes,
                                1,
                                List.of(),
                                List.of(),
                                List.of(new ScoreMeterChange(0, 4, 4)),
                                List.of(rest(530, .5)),
                                List.of(),
                                List.of())
                        .withExpressiveEvents(acquired.expressiveEvents());
        assertEquals(
                1.5,
                ScoreRestFermataDetector.provedOnset(
                        lane.rests().get(0), lane.rests(), lane.notes(), 4),
                0);
        var result = ScoreExpressionDetector.resolve(lane, 4);
        preserved(lane, result);
        assertEquals(1, result.expressiveEvents().size());
        var event = result.expressiveEvents().get(0);
        assertEquals(new ScoreAnchor(0, 2), event.start().orElseThrow());
        assertEquals(Scope.PART, event.scope());
        assertTrue(event.end().isEmpty());
        assertEquals(acquired.expressiveEvents().get(0).targetEventId(), event.targetEventId());
        assertTrue(ScoreExpressionDetector.targetIndices(result, event).isEmpty());
    }

    @Test
    public void offStaffRestCannotOwnNaturalComma() throws Exception {
        var displaced = new ScoreRestEvent(0, (530f - 20) / 600, .9f, 12f / H, 0, 1, .5);
        assertTrue(adverse(List.of(displaced)).expressiveEvents().isEmpty());
    }
}

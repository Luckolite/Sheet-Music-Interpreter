// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.junit.Assert.*;

/** Original licensed-template page. No score pixels, injected matches, or child OCR words. */
public final class PublicNativeFpOriginalFixture {
    private static final int W = 400, H = 400, TOP = 180, GLYPH_HEIGHT = 28;

    public record Produced(List<ScoreNoteEvent> notes, List<ScoreDynamicChange> changes) {}

    private static BufferedImage scaled() throws Exception {
        BufferedImage source;
        try (var stream =
                PublicNativeFpOriginalFixture.class.getResourceAsStream(
                        "/dynamic-original/fp.png")) {
            assertNotNull("licensed whole same-font-size/baseline fp resource", stream);
            source = javax.imageio.ImageIO.read(stream);
        }
        int width = Math.round(GLYPH_HEIGHT * source.getWidth() / (float) source.getHeight());
        var out = new BufferedImage(width, GLYPH_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, width, GLYPH_HEIGHT, null);
        g.dispose();
        return out;
    }

    private static void paste(byte[] page, BufferedImage glyph, int left) {
        for (int y = 0; y < glyph.getHeight(); y++)
            for (int x = 0; x < glyph.getWidth(); x++) {
                int at = (TOP + y) * W + left + x, v = (glyph.getRGB(x, y) >>> 16) & 255;
                page[at] = (byte) Math.min(page[at] & 255, v);
            }
    }

    /** Called by the sole end-to-end test, once; failures remain setup failures, never skipped. */
    public static Produced produce() throws Exception {
        var whole = scaled();
        int left = 180, right = left + whole.getWidth();
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        paste(gray, whole, left);
        byte[] before = gray.clone();
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(80, 128, 12, 0, 2),
                        new PlayingTechniqueDetector.Staff(252, 300, 12, 1, 2));
        var literal =
                new PlayingTechniqueDetector.Word(
                        "fp",
                        left / (float) W,
                        (TOP - 12) / (float) H,
                        right / (float) W,
                        (TOP + GLYPH_HEIGHT + 12) / (float) H);
        // ONLY the one literal fp is supplied. Natural symbol proposals must discover both parts.
        var words = new NativeDynamicGlyphs().recognize(gray, W, H, staffs, List.of(literal));
        System.out.println("PUBLIC_NATIVE_FP_WORDS=" + words);
        assertArrayEquals("native matcher must preserve original pixels", before, gray);
        assertEquals("natural matcher must yield one complete compound", 1, words.size());
        var word = words.get(0);
        assertEquals("fp", word.text());
        assertEquals(PlayingTechniqueDetector.WordAdmission.FP_GLYPH_QUALIFIED, word.admission());
        assertTrue(
                "original padded OCR exceeds the ordinary three-gap height",
                (literal.bottom() - literal.top()) * H > 36);
        assertTrue(word.left() >= literal.left() && word.right() <= literal.right());
        assertTrue(
                "observed union must remove blank OCR padding",
                word.top() > literal.top() && word.bottom() < literal.bottom());
        int wl = Math.round(word.left() * W), wr = Math.round(word.right() * W);
        int wt = Math.round(word.top() * H), wb = Math.round(word.bottom() * H);
        assertTrue(wl >= left && wr <= right && wt >= TOP && wb <= TOP + GLYPH_HEIGHT);
        for (int y = TOP; y < TOP + GLYPH_HEIGHT; y++)
            for (int x = left; x < right; x++)
                if ((gray[y * W + x] & 255) < 145)
                    assertTrue(
                            "compound union must retain every original dark glyph site",
                            x >= wl && x < wr && y >= wt && y < wb);
        float center = (left + right) * .5f / W;
        var measures = List.of(new MeasureRegion(center - .3f, center + .3f, .1f, .9f));
        var notes = new ArrayList<ScoreNoteEvent>();
        for (float onset : new float[] {0, .25f, .5f, .75f})
            for (int staff = 0; staff < 2; staff++)
                for (int step : new int[] {4, 6})
                    notes.add(
                            new ScoreNoteEvent(
                                    0,
                                    onset,
                                    step,
                                    staff,
                                    2,
                                    ((staff == 0 ? 128 : 300) - step * 6f) / H,
                                    false,
                                    0,
                                    0,
                                    ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                    1));
        var changes = ScoreDynamicsDetector.detect(words, staffs, measures, notes, gray, W, H);
        System.out.println("PUBLIC_NATIVE_FP_CHANGES=" + changes);
        assertEquals(2, changes.size());
        assertEquals(3, changes.get(0).decibels(), 0);
        assertEquals(-8, changes.get(1).decibels(), 0);
        for (var c : changes) {
            assertEquals(0, c.measureIndex());
            assertEquals(.5f, c.positionInMeasure(), 0);
            assertEquals(0, c.staffIndex());
            assertEquals(2, c.staffCount());
            assertEquals(0, c.direction());
            assertEquals(c.measureIndex(), c.endMeasureIndex());
            assertEquals(c.positionInMeasure(), c.endPosition(), 0);
        }
        return new Produced(List.copyOf(notes), List.copyOf(changes));
    }
}

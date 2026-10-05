// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural glyphs; no score scans or extracted font outlines. */
public class PageTrillEvidenceTest {
    private static final int W = 240, H = 100;
    private final byte[] page = new byte[W * H];
    private final byte[] glyph = new byte[12 * 16];
    private final PortableOrnamentGlyphs recognizer = new PortableOrnamentGlyphs();
    private final List<PortableNoteOrnaments.Bounds> boxes = new ArrayList<>();
    private final List<PlayingTechniqueDetector.Word> words = new ArrayList<>();
    private final PortableOrnamentGlyphs.Match weak =
            new PortableOrnamentGlyphs.Match(NoteOrnament.TRILL, .38f, .03f);

    public PageTrillEvidenceTest() {
        Arrays.fill(page, (byte) 255);
        Arrays.fill(glyph, (byte) 255);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 12; x++)
                if (x >= 3 && x <= 5 || y >= 5 && y <= 7 || x >= 9 && y >= 6) glyph[y * 12 + x] = 0;
        recognizer.add(glyph, 12, 16, NoteOrnament.TRILL, false);
        for (int left : new int[] {20, 80, 140}) {
            var box = new PortableNoteOrnaments.Bounds(left, 20, left + 12, 36);
            boxes.add(box);
            for (int y = 0; y < 16; y++)
                System.arraycopy(glyph, y * 12, page, (20 + y) * W + left, 12);
        }
        for (int i = 0; i < 2; i++) words.add(word(boxes.get(i)));
    }

    private PlayingTechniqueDetector.Word word(PortableNoteOrnaments.Bounds b) {
        return new PlayingTechniqueDetector.Word(
                "tr", (b.left - 2f) / W, (b.top - 2f) / H, (b.right + 2f) / W, (b.bottom + 2f) / H);
    }

    private PageTrillEvidence evidence(List<PlayingTechniqueDetector.Word> text) {
        return new PageTrillEvidence(recognizer, page, W, H, boxes, text);
    }

    @Test
    public void twoIndependentReadingsRecoverMatchingGlyph() {
        assertTrue(evidence(words).recognizes(page, W, boxes.get(2), weak));
    }

    @Test
    public void oneReadingCannotPropagate() {
        assertFalse(evidence(words.subList(0, 1)).recognizes(page, W, boxes.get(2), weak));
    }

    @Test
    public void duplicatedCropReadingIsOnlyOneSample() {
        assertFalse(
                evidence(List.of(words.get(0), words.get(0)))
                        .recognizes(page, W, boxes.get(2), weak));
    }

    @Test
    public void genericEvidenceMustStillSuggestTrill() {
        assertFalse(
                evidence(words)
                        .recognizes(
                                page,
                                W,
                                boxes.get(2),
                                new PortableOrnamentGlyphs.Match(NoteOrnament.TURN, .38f, .03f)));
    }

    @Test
    public void unrelatedWeakShapeCannotPropagate() {
        assertFalse(
                evidence(words)
                        .recognizes(
                                page,
                                W,
                                boxes.get(2),
                                new PortableOrnamentGlyphs.Match(NoteOrnament.TRILL, .2f, .01f)));
    }

    @Test
    public void aDifferentRasterIsNotRecovered() {
        var e = evidence(words);
        for (int y = 20; y < 36; y++) for (int x = 140; x < 152; x++) page[y * W + x] = 0;
        assertFalse(e.recognizes(page, W, boxes.get(2), weak));
    }

    @Test
    public void confirmedCropUsesInkComponentNotOcrPadding() {
        assertTrue(evidence(words).confirmed(boxes.get(0)));
    }

    @Test
    public void broadTextRegionCannotSeedRecognition() {
        var text = new PlayingTechniqueDetector.Word("tr", 0, 0, 1, 1);
        assertFalse(evidence(List.of(text)).confirmed(boxes.get(0)));
    }

    @Test
    public void samplesNeverCarryAcrossPages() {
        evidence(words);
        assertFalse(evidence(List.of()).recognizes(page, W, boxes.get(2), weak));
    }

    @Test
    public void noInkCannotSeed() {
        Arrays.fill(page, (byte) 255);
        assertFalse(evidence(words).confirmed(boxes.get(0)));
    }

    @Test
    public void anotherWordCannotSeed() {
        var w = words.get(0);
        assertFalse(
                evidence(
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "turn", w.left(), w.top(), w.right(), w.bottom())))
                        .confirmed(boxes.get(0)));
    }

    @Test
    public void callLocalQueryPreservesCompleteMatchBitsAndMutableBounds() {
        var alternative = new PortableOrnamentGlyphs();
        byte[] alternate = glyph.clone();
        for (int y = 0; y < 16; y++) alternate[y * 12 + 2] = 0;
        alternative.add(alternate, 12, 16, NoteOrnament.TRILL, false);
        alternative.add(glyph, 12, 16, NoteOrnament.TURN, false);
        var query = new PortableOrnamentGlyphs.Query();
        var box = new PortableNoteOrnaments.Bounds(boxes.get(2));
        byte[] before = page.clone();
        for (var sample : List.of(recognizer, alternative))
            assertMatchBits(sample.match(page, W, box), sample.match(page, W, box, query));
        box.left++;
        box.right++;
        for (var sample : List.of(recognizer, alternative))
            assertMatchBits(sample.match(page, W, box), sample.match(page, W, box, query));
        var invalid = new PortableNoteOrnaments.Bounds(-1, 20, 12, 36);
        assertMatchBits(
                recognizer.match(page, W, invalid), recognizer.match(page, W, invalid, query));
        assertMatchBits(recognizer.match(null, 0, null), recognizer.match(null, 0, null, query));
        assertArrayEquals(before, page);
    }

    @Test
    public void repeatedRecognitionRereadsRasterAndSelfOnlyRemainsLazy() {
        var selfOnly = evidence(words.subList(0, 1));
        assertFalse(selfOnly.recognizes(null, 0, boxes.get(0), weak));
        var independent = evidence(words);
        assertTrue(independent.recognizes(page, W, boxes.get(2), weak));
        byte[] before = page.clone();
        assertTrue(independent.recognizes(page, W, boxes.get(2), weak));
        assertArrayEquals(before, page);
        for (int y = 20; y < 36; y++) for (int x = 140; x < 152; x++) page[y * W + x] = 0;
        byte[] changed = page.clone();
        assertFalse(independent.recognizes(page, W, boxes.get(2), weak));
        assertArrayEquals(changed, page);
    }

    private static void assertMatchBits(
            PortableOrnamentGlyphs.Match expected, PortableOrnamentGlyphs.Match actual) {
        assertEquals(expected.kind(), actual.kind());
        assertEquals(
                Float.floatToRawIntBits(expected.score()), Float.floatToRawIntBits(actual.score()));
        assertEquals(
                Float.floatToRawIntBits(expected.margin()),
                Float.floatToRawIntBits(actual.margin()));
    }
}

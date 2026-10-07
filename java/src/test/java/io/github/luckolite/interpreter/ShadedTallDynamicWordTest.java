// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original italic stroke drawings; OCR identity is supplied independently of raw body proof. */
public class ShadedTallDynamicWordTest {
    static final int W = 1000, H = 700, G = 16;
    byte[] gray;

    void line(int x, int y, int endX, int endY, int value) {
        int steps = Math.max(Math.abs(endX - x), Math.abs(endY - y));
        for (int i = 0; i <= steps; i++) {
            int xx = Math.round(x + (endX - x) * i / (float) Math.max(1, steps)),
                    yy = Math.round(y + (endY - y) * i / (float) Math.max(1, steps));
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) gray[(yy + dy) * W + xx + dx] = (byte) value;
        }
    }

    void draw() {
        draw(true);
    }

    void draw(boolean crossbar) {
        gray = new byte[W * H];
        Arrays.fill(gray, (byte) 140);
        line(420, 206, 405, 242, 95);
        line(420, 206, 421, 202, 95);
        line(421, 202, 424, 200, 95);
        line(424, 200, 427, 201, 95);
        line(427, 201, 429, 204, 95);
        line(405, 242, 403, 246, 95);
        line(403, 246, 399, 247, 95);
        line(399, 247, 397, 245, 95);
        if (crossbar) line(412, 217, 425, 217, 95);
    }

    PlayingTechniqueDetector.Word word(String text) {
        return new PlayingTechniqueDetector.Word(text, 394f / W, 197f / H, 433f / W, 252f / H);
    }

    boolean proof(String text) {
        byte[] before = gray.clone();
        boolean ok = ShadedTallDynamicWord.proved(word(text), gray, W, H, G);
        assertArrayEquals(before, gray);
        return ok;
    }

    List<ScoreDynamicChange> detect(String text) {
        return ScoreDynamicsDetector.detect(
                List.of(word(text)),
                List.of(new PlayingTechniqueDetector.Staff(116, 180, G, 0, 1)),
                List.of(new MeasureRegion(.35f, .55f, .13f, .32f)),
                List.of(),
                gray,
                W,
                H);
    }

    @Test
    public void completeTallItalicFStillHasPrintedLevel() {
        draw();
        var found = detect("f");
        assertEquals(1, found.size());
        assertEquals(3, found.get(0).decibels(), 0);
        assertEquals(0, found.get(0).direction());
    }

    @Test
    public void completeTallMezzoForteStillHasReadLevel() {
        draw();
        var found = detect("mf");
        assertEquals(1, found.size());
        assertEquals(0, found.get(0).decibels(), 0);
    }

    @Test
    public void rawBodyProofLeavesPixelsUnchanged() {
        draw();
        assertTrue(proof("f"));
    }

    @Test
    public void suppliedOCRLevelDoesNotInventAbsentLetter() {
        draw();
        Arrays.fill(gray, (byte) 140);
        assertFalse(proof("f"));
        assertTrue(detect("f").isEmpty());
    }

    @Test
    public void tallItalicSlashDoesNotBecomeReadF() {
        draw();
        Arrays.fill(gray, (byte) 140);
        line(420, 200, 400, 247, 95);
        assertFalse(proof("f"));
        assertTrue(detect("f").isEmpty());
    }

    @Test
    public void middleShaftWithoutCrossbarDoesNotProveF() {
        draw();
        for (int y = 215; y <= 219; y++)
            for (int x = 410; x <= 426; x++) gray[y * W + x] = (byte) 140;
        assertFalse(proof("f"));
    }

    @Test
    public void solidRectangleDoesNotProveF() {
        draw();
        for (int y = 197; y <= 252; y++) for (int x = 394; x <= 433; x++) gray[y * W + x] = 95;
        assertFalse(proof("f"));
    }

    @Test
    public void brightWordsKeepExistingHeightLimit() {
        draw();
        for (int i = 0; i < gray.length; i++) if ((gray[i] & 255) == 140) gray[i] = (byte) 250;
        assertFalse(proof("f"));
        assertTrue(detect("f").isEmpty());
    }

    @Test
    public void ordinaryOtherWordsDoNotUseFProof() {
        draw();
        for (String token : List.of("p", "mp", "m", "for", "sfz", "Taylor"))
            assertFalse(proof(token));
    }

    @Test
    public void oneBodyCannotProveRepeatedFWord() {
        draw();
        assertFalse(proof("ff"));
        assertFalse(proof("fff"));
    }

    @Test
    public void glyphBeyondBoundedHeightStillRejects() {
        draw();
        var w = new PlayingTechniqueDetector.Word("f", 394f / W, 190f / H, 433f / W, 255f / H);
        assertFalse(ShadedTallDynamicWord.proved(w, gray, W, H, G));
    }

    @Test
    public void clippedOrInvalidImageRejects() {
        draw();
        assertFalse(ShadedTallDynamicWord.proved(word("f"), new byte[1], W, H, G));
        assertFalse(ShadedTallDynamicWord.proved(word("f"), gray, W, H, Float.NaN));
        assertFalse(ShadedTallDynamicWord.proved(null, gray, W, H, G));
    }

    @Test
    public void originalNullLabelArticulationGuardRemains() {
        draw();
        assertFalse(ShadedItalicFCap.proved(gray, null, W, H, G, 423, 199, 425, 201));
    }

    @Test
    public void narrowBlurredCrossbarStillNeedsCompleteReadF() {
        draw(false);
        line(419, 208, 404, 242, 95);
        line(412, 217, 418, 217, 95);
        assertTrue(proof("f"));
        assertEquals(1, detect("f").size());
    }

    @Test
    public void wordCrossbarDoesNotRelaxArticulationCapProof() {
        draw(false);
        line(419, 208, 404, 242, 95);
        line(412, 217, 418, 217, 95);
        assertFalse(
                ShadedItalicFCap.proved(gray, new byte[gray.length], W, H, G, 423, 199, 425, 201));
    }

    @Test
    public void continuousShaftWithoutCrossbarStillRejects() {
        draw(false);
        assertFalse(proof("f"));
        assertTrue(detect("f").isEmpty());
    }

    @Test
    public void canonicalCaseAndPunctuationKeepReadIdentity() {
        draw();
        assertTrue(proof("F."));
        assertTrue(proof("MF"));
        assertEquals(1, detect("F.").size());
    }
}

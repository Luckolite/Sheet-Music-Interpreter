// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original quarter-note equation, connected9/6glyphs and independent OCR grouping controls. */
public class NotePrefixedTempoEquationTest {
    private TempoUnitNoteTest.Page page() {
        var p = new TempoUnitNoteTest.Page();
        p.erase(164, 62, 12, 27);
        // Nine: upper counter and descending right shaft; six: lower counter and left shaft.
        p.rect(164, 62, 12, 3, (byte) 5);
        p.rect(164, 62, 3, 15, (byte) 5);
        p.rect(164, 74, 12, 3, (byte) 5);
        p.rect(173, 62, 3, 27, (byte) 5);
        p.rect(164, 86, 12, 3, (byte) 5);
        p.rect(184, 62, 3, 27, (byte) 5);
        p.rect(184, 62, 12, 3, (byte) 5);
        p.rect(184, 74, 12, 3, (byte) 5);
        p.rect(184, 86, 12, 3, (byte) 5);
        p.rect(193, 74, 3, 15, (byte) 5);
        return p;
    }

    private PlayingTechniqueDetector.Word word(String text, int width) {
        return new PlayingTechniqueDetector.Word(
                text, 98f / width, 38f / 260, 280f / width, 99f / 260);
    }

    private List<MeasureNumberReconciler.NumberToken> read(TempoUnitNoteTest.Page p, String text) {
        byte[] before = p.gray.clone();
        var result =
                TempoChangeDetector.withIsolatedDigits(
                        List.of(), List.of(word(text, p.w)), p.gray, p.w, p.h);
        assertArrayEquals(before, p.gray);
        return result;
    }

    @Test
    public void noteLikeOcrPrefixesRequireAndRetainPhysicalQuarter() {
        for (String prefix : List.of("J", "j", "♩", "♪", "J.")) {
            var p = page();
            var tokens = read(p, prefix + "=96; freely");
            assertEquals(prefix, 1, tokens.size());
            assertEquals(96, tokens.get(0).value());
            assertEquals(100f / p.w, tokens.get(0).annotationLeft(), .00001f);
            var tempos =
                    TempoChangeDetector.detect(
                            tokens,
                            p.gray,
                            p.w,
                            p.h,
                            List.of(
                                    new MeasureRegion(
                                            20f / p.w, 460f / p.w, 100f / p.h, 164f / p.h)));
            assertEquals(1, tempos.size());
            assertEquals(1, tempos.get(0).beatUnit(), 0);
        }
    }

    @Test
    public void missingPhysicalNoteRejectsEvenAnOcrNoteSymbol() {
        var p = page();
        p.erase(98, 38, 28, 61);
        assertTrue(read(p, "♩=96 freely").isEmpty());
    }

    @Test
    public void missingEqualsRejectsEvenCorrectText() {
        var p = page();
        p.erase(131, 70, 20, 20);
        assertTrue(read(p, "J=96 freely").isEmpty());
    }

    @Test
    public void rangesDecimalsAndUnrelatedPrefixesStayRejected() {
        for (String s :
                List.of(
                        "J=96-120",
                        "J=96/120",
                        "J=96.5",
                        "J=96,5",
                        "J=9600",
                        "J=450",
                        "Tempo=96",
                        "A=96",
                        "Bpm=96")) assertTrue(s, read(page(), s).isEmpty());
    }

    @Test
    public void trailingTextDoesNotReplaceNumeralBounds() {
        var p = page();
        p.rect(230, 65, 8, 25, (byte) 5);
        var t = read(p, "J=96; text");
        assertEquals(1, t.size());
        assertEquals(195f / p.w, t.get(0).right(), .00001f);
    }

    @Test
    public void alternateOcrViewsDoNotDuplicateTheDirection() {
        var p = page();
        var t = read(p, "J=96");
        assertEquals(1, t.size());
        assertEquals(
                1,
                TempoChangeDetector.withIsolatedDigits(
                                t, List.of(word("♩=96", p.w)), p.gray, p.w, p.h)
                        .size());
    }

    @Test
    public void equalsTextMayStillContainThePrintedBeatInItsBox() {
        var p = page();
        var t = read(p, "=96");
        assertEquals(1, t.size());
        assertEquals(164f / p.w, t.get(0).left(), .00001f);
    }

    @Test
    public void invalidBroadDigitEstimateCannotSuppressProvedDigits() {
        var p = page();
        var bad =
                new MeasureNumberReconciler.NumberToken(
                        96, 125f / p.w, 38f / p.h, 210f / p.w, 99f / p.h);
        var t =
                TempoChangeDetector.withIsolatedDigits(
                        List.of(bad), List.of(word("=96", p.w)), p.gray, p.w, p.h);
        assertEquals(1, t.size());
        assertEquals(164f / p.w, t.get(0).left(), .00001f);
        assertEquals(100f / p.w, t.get(0).annotationLeft(), .00001f);
    }
}

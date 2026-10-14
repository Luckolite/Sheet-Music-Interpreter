// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original quarter-note equation, connected9/6glyphs and independent OCR grouping controls. */
public class MergedTempoEquationTest {
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
                text, 131f / width, 62f / 260, 260f / width, 93f / 260);
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
    public void equalsPrefixedMergedDirectionRetainsTheTempo() {
        var p = page();
        p.rect(225, 68, 9, 20, (byte) 5);
        var tokens = read(p, "=96 freely");
        assertEquals(1, tokens.size());
        assertEquals(96, tokens.get(0).value());
        assertEquals(100f / p.w, tokens.get(0).annotationLeft(), .00001f);
        var tempos =
                TempoChangeDetector.detect(
                        tokens,
                        p.gray,
                        p.w,
                        p.h,
                        List.of(new MeasureRegion(20f / p.w, 460f / p.w, 100f / p.h, 164f / p.h)));
        assertEquals(1, tempos.size());
        assertEquals(96, tempos.get(0).bpm(), 0);
        assertEquals(1, tempos.get(0).beatUnit(), 0);
        assertEquals(0, tempos.get(0).positionInMeasure(), 0);
    }

    @Test
    public void aMisreadBowAfterTheDigitsDoesNotExtendTheirBounds() {
        var p = page();
        p.rect(203, 62, 22, 3, (byte) 5);
        p.rect(203, 62, 3, 19, (byte) 5);
        p.rect(222, 62, 3, 19, (byte) 5);
        var tokens = read(p, "=96日");
        assertEquals(1, tokens.size());
        assertEquals(195f / p.w, tokens.get(0).right(), .00001f);
    }

    @Test
    public void equalsWithoutBeatNoteCannotRecoverTempo() {
        var p = page();
        p.erase(100, 40, 25, 60);
        assertTrue(read(p, "=96 freely").isEmpty());
    }

    @Test
    public void digitsWithoutPrintedEqualsCannotRecoverTempo() {
        var p = page();
        p.erase(131, 70, 20, 20);
        assertTrue(read(p, "=96").isEmpty());
    }

    @Test
    public void labelsRangesAndOutOfRangeNumbersRemainRejected() {
        for (String text :
                List.of(
                        "C96", "Bpm96", "=96-120", "=96–120", "=96—120", "=96/120", "=96.5",
                        "=96,5", "=9600", "=450")) assertTrue(text, read(page(), text).isEmpty());
    }

    @Test
    public void duplicateOcrViewsDoNotAddASecondEvent() {
        var p = page();
        var tokens = read(p, "=96 freely");
        assertEquals(1, tokens.size());
        assertEquals(
                1,
                TempoChangeDetector.withIsolatedDigits(
                                tokens, List.of(word("=96日", p.w)), p.gray, p.w, p.h)
                        .size());
    }

    private byte[] wide(TempoUnitNoteTest.Page p) {
        byte[] gray = new byte[1000 * p.h];
        Arrays.fill(gray, (byte) 255);
        for (int y = 0; y < p.h; y++) System.arraycopy(p.gray, y * p.w, gray, y * 1000, p.w);
        return gray;
    }

    @Test
    public void physicalBeatStartOwnsItsBarInsteadOfTheNeighborMargin() {
        var p = page();
        byte[] gray = wide(p);
        var token =
                new MeasureNumberReconciler.NumberToken(
                        96, 164f / 1000, 62f / p.h, 195f / 1000, 89f / p.h, 100f / 1000);
        var tempos =
                TempoChangeDetector.detect(
                        List.of(token),
                        gray,
                        1000,
                        p.h,
                        List.of(
                                new MeasureRegion(20f / 1000, 99f / 1000, 100f / p.h, 164f / p.h),
                                new MeasureRegion(
                                        100f / 1000, 460f / 1000, 100f / p.h, 164f / p.h)));
        assertEquals(1, tempos.size());
        assertEquals(1, tempos.get(0).measureIndex());
        assertEquals(0, tempos.get(0).positionInMeasure(), 0);
    }

    @Test
    public void completeMergedEquationUsesThePhysicalBarStart() {
        var p = page();
        byte[] gray = wide(p);
        var tokens =
                TempoChangeDetector.withIsolatedDigits(
                        List.of(), List.of(word("=96 freely", 1000)), gray, 1000, p.h);
        assertEquals(1, tokens.size());
        var tempos =
                TempoChangeDetector.detect(
                        tokens,
                        gray,
                        1000,
                        p.h,
                        List.of(
                                new MeasureRegion(20f / 1000, 99f / 1000, 100f / p.h, 164f / p.h),
                                new MeasureRegion(
                                        100f / 1000, 460f / 1000, 100f / p.h, 164f / p.h)));
        assertEquals(1, tempos.get(0).measureIndex());
        assertEquals(0, tempos.get(0).positionInMeasure(), 0);
    }

    @Test
    public void threeDigitsIgnoreSeparateLowerBowInk() {
        var p = page();
        p.erase(164, 62, 32, 27);
        p.rect(164, 62, 3, 27, (byte) 5);
        for (int x : new int[] {179, 199}) {
            p.rect(x, 62, 12, 3, (byte) 5);
            p.rect(x, 86, 12, 3, (byte) 5);
            p.rect(x, 62, 3, 27, (byte) 5);
            p.rect(x + 9, 62, 3, 27, (byte) 5);
        }
        p.rect(149, 97, 22, 7, (byte) 5);
        p.rect(231, 66, 4, 24, (byte) 5);
        var word =
                new PlayingTechniqueDetector.Word(
                        "=100 hauntingly", 131f / p.w, 62f / p.h, 260f / p.w, 105f / p.h);
        var tokens =
                TempoChangeDetector.withIsolatedDigits(List.of(), List.of(word), p.gray, p.w, p.h);
        assertEquals(1, tokens.size());
        assertEquals(100, tokens.get(0).value());
        assertEquals(210f / p.w, tokens.get(0).right(), .00001f);
        assertEquals(89f / p.h, tokens.get(0).bottom(), .00001f);
    }

    @Test
    public void anEarlierPrintedDirectionStartStillOwnsTheEarlierBar() {
        var p = page();
        byte[] gray = wide(p);
        var token =
                new MeasureNumberReconciler.NumberToken(
                        96, 164f / 1000, 62f / p.h, 195f / 1000, 89f / p.h, 80f / 1000);
        var tempos =
                TempoChangeDetector.detect(
                        List.of(token),
                        gray,
                        1000,
                        p.h,
                        List.of(
                                new MeasureRegion(20f / 1000, 99f / 1000, 100f / p.h, 164f / p.h),
                                new MeasureRegion(
                                        100f / 1000, 460f / 1000, 100f / p.h, 164f / p.h)));
        assertEquals(0, tempos.get(0).measureIndex());
    }
}

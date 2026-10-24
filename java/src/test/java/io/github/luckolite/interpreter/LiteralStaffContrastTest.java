// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent literal-tone and occlusion oracles; no score pixels or owner grants. */
public class LiteralStaffContrastTest {
    static final class Scene {
        final int w, h, gap, top, left, right;
        final byte[] gray;

        Scene() {
            this(240, 160, 12, 32, 108, 132);
        }

        Scene(int w, int h, int gap, int top, int left, int right) {
            this.w = w;
            this.h = h;
            this.gap = gap;
            this.top = top;
            this.left = left;
            this.right = right;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            for (int x = 1; x < w - 1; x++)
                for (int k = 0; k < 5; k++) {
                    int y = top + k * gap;
                    if (gap >= 12) {
                        set(x, y - 2, 200);
                        set(x, y + 2, 200);
                    }
                    set(x, y - 1, 90);
                    set(x, y, 40);
                    set(x, y + 1, 90);
                }
        }

        void set(int x, int y, int v) {
            gray[y * w + x] = (byte) v;
        }

        LiteralStaffContrast.Result observe(LiteralStaffContrast.Budget b) {
            return LiteralStaffContrast.observe(gray, w, h, left, right, top, gap, b);
        }

        LiteralStaffContrast.Field field() {
            return observe(new LiteralStaffContrast.Budget(8000000)).field().orElseThrow();
        }
    }

    @Test
    public void completeFiveRulesNeedBothLiteralSides() {
        var s = new Scene();
        var r = s.observe(new LiteralStaffContrast.Budget(8000000));
        assertTrue(r.refusal(), r.field().isPresent());
        assertEquals(1, r.leftWindows().size());
        assertEquals(1, r.rightWindows().size());
        assertEquals(
                LiteralStaffContrast.Kind.MATCHED_CARRIER,
                r.field().orElseThrow().cell(120, 44).kind());
    }

    @Test
    public void opaqueExtraInkOverPaperIsCore() {
        var s = new Scene();
        s.set(120, 50, 0);
        var c = s.field().cell(120, 50);
        assertEquals(255, c.reference());
        assertEquals(LiteralStaffContrast.Kind.EXTRA_CORE, c.kind());
    }

    @Test
    public void opaqueExtraInkOverGrayRuleIsCore() {
        var s = new Scene();
        s.set(120, 44, 0);
        var c = s.field().cell(120, 44);
        assertEquals(40, c.reference());
        assertEquals(LiteralStaffContrast.Kind.EXTRA_CORE, c.kind());
    }

    @Test
    public void weakExtraInkIsFringeNotObservedWhite() {
        var s = new Scene();
        s.set(120, 50, 254);
        s.set(121, 44, 39);
        assertEquals(LiteralStaffContrast.Kind.EXTRA_FRINGE, s.field().cell(120, 50).kind());
        assertEquals(LiteralStaffContrast.Kind.EXTRA_FRINGE, s.field().cell(121, 44).kind());
    }

    @Test
    public void matchedWhiteMeansActualWhiteOnly() {
        var s = new Scene();
        var c = s.field().cell(120, 50);
        assertEquals(255, c.observed());
        assertEquals(255, c.reference());
        assertEquals(LiteralStaffContrast.Kind.OBSERVED_WHITE, c.kind());
    }

    @Test
    public void blackCarrierCannotRevealAnOccludedHead() {
        var s = new Scene();
        for (int x = 1; x < s.w - 1; x++) s.set(x, 44, 0);
        var c = s.field().cell(120, 44);
        assertEquals(0, c.observed());
        assertEquals(0, c.reference());
        assertEquals(LiteralStaffContrast.Kind.MATCHED_CARRIER, c.kind());
    }

    @Test
    public void minCompositeRetainsEveryMatchedCarrierAsUnknown() {
        for (int tone : new int[] {0, 1, 20, 39, 40, 41, 100, 200, 254}) {
            var s = new Scene();
            s.set(120, 44, Math.min(tone, 40));
            var c = s.field().cell(120, 44);
            if (tone >= 40) assertEquals(LiteralStaffContrast.Kind.MATCHED_CARRIER, c.kind());
            else assertNotEquals(LiteralStaffContrast.Kind.OBSERVED_WHITE, c.kind());
            assertEquals(Math.min(tone, 40), c.observed());
        }
    }

    @Test
    public void noRightWitnessCannotBecomeOneSidedBackground() {
        var s = new Scene();
        for (int x = s.right + 2; x < s.w - 1; x++) s.set(x, 26, 254);
        var r = s.observe(new LiteralStaffContrast.Budget(8000000));
        assertTrue(r.field().isEmpty());
        assertEquals("NO_TWO_SIDED_LITERAL_FRAME", r.refusal());
    }

    @Test
    public void aShortReferencePrefixCannotQualify() {
        var s = new Scene();
        for (int x = 1; x < s.left - 12; x++) s.set(x, 26, 254);
        var r = s.observe(new LiteralStaffContrast.Budget(8000000));
        assertTrue(r.field().isEmpty());
    }

    @Test
    public void everyToneMustMatchAcrossReferences() {
        var s = new Scene();
        for (int x = s.right + 2; x < s.w - 1; x++) s.set(x, 50, 254);
        var r = s.observe(new LiteralStaffContrast.Budget(8000000));
        assertTrue(r.field().isEmpty());
        assertEquals("NO_TWO_SIDED_LITERAL_FRAME", r.refusal());
    }

    @Test
    public void allCommonFramesAreReviewedBeforeSelection() {
        var s = new Scene();
        for (int x = 51; x < s.left - 1; x++) s.set(x, 44, 35);
        for (int x = s.right + 2; x <= 180; x++) s.set(x, 44, 35);
        var r = s.observe(new LiteralStaffContrast.Budget(8000000));
        assertTrue(r.field().isEmpty());
        assertEquals("MULTIPLE_LITERAL_FRAMES", r.refusal());
    }

    @Test
    public void brighterObservedInkContradictsChosenReference() {
        var s = new Scene();
        s.set(120, 44, 41);
        var r = s.observe(new LiteralStaffContrast.Budget(8000000));
        assertTrue(r.field().isEmpty());
        assertEquals("LITERAL_REFERENCE_CONTRADICTION", r.refusal());
    }

    @Test
    public void exhaustedBudgetCannotLaterSelectAFavorableSmallerField() {
        var b = new LiteralStaffContrast.Budget(60000);
        assertTrue(new Scene().observe(b).field().isEmpty());
        var small = new Scene(80, 80, 8, 16, 35, 44);
        assertTrue(small.observe(new LiteralStaffContrast.Budget(60000)).field().isPresent());
        assertTrue(small.observe(b).field().isEmpty());
    }

    @Test
    public void sourceAndStoredFieldAreIndependentSnapshots() {
        var s = new Scene();
        var before = s.gray.clone();
        var f = s.field();
        assertArrayEquals(before, s.gray);
        var cell = f.cell(120, 44);
        s.set(120, 44, 0);
        assertEquals(cell, f.cell(120, 44));
    }

    @Test
    public void fieldCannotInventPixelsOutsideItsObservation() {
        var s = new Scene();
        var f = s.field();
        try {
            f.cell(s.left - 1, 50);
            fail("outside field");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Outside"));
        }
    }
}

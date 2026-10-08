// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BelowStaffFingeringTest {
    final int W = 400, H = 300;
    final float G = 16;

    byte[] page(int tone) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) tone);
        return p;
    }

    List<ScoreNoteEvent> read(byte[] p, String text, int step) {
        return read(p, text, step, .45f, .55f);
    }

    List<ScoreNoteEvent> read(byte[] p, String text, int step, float left, float right) {
        return FingeringAnnotationFilter.apply(
                List.of(new PlayingTechniqueDetector.Word(text, left, 190f / H, right, 224f / H)),
                List.of(new PlayingTechniqueDetector.Staff(100, 164, G, 0, 1)),
                List.of(new MeasureRegion(0, 1, 0, 1)),
                List.of(new ScoreNoteEvent(0, .5f, step, 0, 1, 212f / H, false, 0, 0, 2, 1f)),
                p,
                W,
                H);
    }

    void stem(byte[] p) {
        for (int y = 155; y <= 216; y++) p[y * W + 210] = 40;
    }

    void ledger(byte[] p, boolean both) {
        for (int x = 165; x <= 235; x++) if (both || x < 180) p[212 * W + x] = 50;
    }

    @Test
    public void singleBelowDigitWithoutIndependentInkIsRemoved() {
        for (int tone : new int[] {255, 150}) assertTrue(read(page(tone), "2", -6).isEmpty());
    }

    @Test
    public void compactBelowDigitSequenceIsRemoved() {
        for (String t : List.of("232", "2323", "12", "321"))
            assertTrue(t, read(page(150), t, -8).isEmpty());
    }

    @Test
    public void spacedBelowFingeringsAreRemoved() {
        for (int tone : new int[] {255, 150})
            for (String t : List.of("232", "2323", "321"))
                assertTrue(read(page(tone), t, -8, .30f, .70f).isEmpty());
    }

    @Test
    public void wideSequencePreservesRealLowStem() {
        for (int tone : new int[] {255, 150}) {
            byte[] p = page(tone);
            stem(p);
            assertEquals(1, read(p, "2323", -8, .30f, .70f).size());
        }
    }

    @Test
    public void wideSequencePreservesRealLowWholeLedger() {
        for (int tone : new int[] {255, 150}) {
            byte[] p = page(tone);
            ledger(p, true);
            assertEquals(1, read(p, "2323", -8, .30f, .70f).size());
        }
    }

    @Test
    public void wideSequenceDoesNotTreatOneSidedDigitBaseAsLedger() {
        byte[] p = page(150);
        for (int x = 196; x < 211; x++) p[212 * W + x] = 50;
        assertTrue(read(p, "2323", -8, .30f, .70f).isEmpty());
    }

    @Test
    public void overwideNumberGroupRemains() {
        assertEquals(1, read(page(150), "232", -8, .05f, .95f).size());
    }

    @Test
    public void ordinaryAndShallowLedgerNotesRemain() {
        for (int s : new int[] {-2, -1, 0, 6, 7}) assertEquals(1, read(page(150), "2", s).size());
    }

    @Test
    public void realUpStemProtectsLowNote() {
        for (int tone : new int[] {255, 150}) {
            byte[] p = page(tone);
            stem(p);
            assertEquals(1, read(p, "2", -6).size());
        }
    }

    @Test
    public void realLedgerOnBothSidesProtectsLowNote() {
        for (int tone : new int[] {255, 150}) {
            byte[] p = page(tone);
            ledger(p, true);
            assertEquals(1, read(p, "2", -6).size());
        }
    }

    @Test
    public void oneSidedUnderlineDoesNotProtectDigit() {
        byte[] p = page(150);
        ledger(p, false);
        assertTrue(read(p, "2", -6).isEmpty());
    }

    @Test
    public void nonFingeringNumberAndWordsRemain() {
        for (String t : List.of("0", "62", "23232", "Allegro"))
            assertEquals(t, 1, read(page(150), t, -8).size());
    }

    @Test
    public void aboveStaffSequenceStillIsNotFingering() {
        assertFalse(FingeringAnnotationFilter.isFingering("12"));
        assertEquals(1, read(page(150), "232", 10).size());
    }

    @Test
    public void sourcePixelsAreUnchanged() {
        byte[] p = page(150), old = p.clone();
        read(p, "2323", -8);
        assertArrayEquals(old, p);
    }
}

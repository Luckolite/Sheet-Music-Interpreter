// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreTabPitchFittingTest {
    private static final int[] GUITAR = {64, 59, 55, 50, 45, 40};

    @Test
    public void guitarRangeMatchesThePrintedLowAndHighOctaves() {
        assertEquals(48, ScoreTabPitchFitting.fittedMidi(24, GUITAR).orElseThrow());
        assertEquals(96, ScoreTabPitchFitting.fittedMidi(120, GUITAR).orElseThrow());
    }

    @Test
    public void playablePitchesKeepTheirOctave() {
        for (int midi = 40; midi <= 100; midi++)
            assertEquals(midi, ScoreTabPitchFitting.fittedMidi(midi, GUITAR).orElseThrow());
    }

    @Test
    public void fittedPitchesPreserveTheirPitchClassAndArePlayable() {
        for (int[] tuning : new int[][] {GUITAR, new int[] {76, 69, 62, 55}})
            for (int midi = 0; midi < 128; midi++) {
                int fitted = ScoreTabPitchFitting.fittedMidi(midi, tuning).orElseThrow();
                assertEquals(0, Math.floorMod(fitted - midi, 12));
                assertEquals(
                        (fitted - midi) / 12 * 7, ScoreTabPitchFitting.diatonicDelta(midi, fitted));
                boolean playable = false;
                for (int open : tuning) playable |= fitted >= open && fitted <= open + 36;
                assertTrue(playable);
            }
    }

    @Test
    public void unsupportedTuningOrUnreachablePitchIsExplicit() {
        assertTrue(ScoreTabPitchFitting.fittedMidi(60, null).isEmpty());
        assertTrue(ScoreTabPitchFitting.fittedMidi(60, new int[] {40}).isEmpty());
        assertTrue(ScoreTabPitchFitting.fittedMidi(-100, GUITAR).isEmpty());
    }

    @Test
    public void nonOctavePitchChangeCannotMasqueradeAsRangeFitting() {
        assertThrows(
                IllegalArgumentException.class, () -> ScoreTabPitchFitting.diatonicDelta(60, 61));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.OptionalInt;

/** The sounding octave of a pitch fitted onto the available tablature strings. */
public final class ScoreTabPitchFitting {
    private ScoreTabPitchFitting() {}

    /** Preserves the established range fallback: exact pitch, then nearest octave up before down. */
    public static OptionalInt fittedMidi(int midi, int[] tuning) {
        if (tuning == null || (tuning.length != 4 && tuning.length != 6))
            return OptionalInt.empty();
        if (playable(midi, tuning)) return OptionalInt.of(midi);
        for (int octaves = 1; octaves <= 5; octaves++) {
            int raised = midi + octaves * 12;
            if (playable(raised, tuning)) return OptionalInt.of(raised);
            int lowered = midi - octaves * 12;
            if (playable(lowered, tuning)) return OptionalInt.of(lowered);
        }
        return OptionalInt.empty();
    }

    /** An octave-only fit preserves written letter, accidental and key-signature meaning. */
    public static int diatonicDelta(int sourceMidi, int fittedMidi) {
        int semitones = fittedMidi - sourceMidi;
        if (semitones % 12 != 0)
            throw new IllegalArgumentException("Tablature fitting must preserve the pitch class");
        return semitones / 12 * 7;
    }

    private static boolean playable(int midi, int[] tuning) {
        for (int openString : tuning) {
            int fret = midi - openString;
            if (fret >= 0 && fret <= 36) return true;
        }
        return false;
    }
}

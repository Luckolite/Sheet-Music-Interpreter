// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.math.BigDecimal;

/** Printed metronome pulse; playback BPM remains expressed in quarter notes per minute. */
public record ScoreTempoMark(int glyph, String musicXmlUnit, int dots, double markedBpm) {
    public static ScoreTempoMark from(ScoreTempoChange tempo) {
        double[] units = {4, 2, 1, .5, .25, .125, .0625, .03125, .015625, .0078125, .00390625};
        int[] glyphs = {
            0xE1D2, 0xE1D3, 0xE1D5, 0xE1D7, 0xE1D9, 0xE1DB, 0xE1DD, 0xE1DF, 0xE1E1, 0xE1E3, 0xE1E5
        };
        String[] names = {
            "whole", "half", "quarter", "eighth", "16th", "32nd", "64th", "128th", "256th", "512th",
            "1024th"
        };
        for (int i = 0; i < units.length; i++)
            for (int dots = 0; dots <= 2; dots++) {
                double pulse = units[i] * (2 - Math.pow(.5, dots));
            if (Math.abs(pulse - tempo.beatUnit()) < 1e-9) {
                try {
                    double marked = BigDecimal.valueOf(tempo.bpm())
                            .divide(BigDecimal.valueOf(pulse)).doubleValue();
                    return new ScoreTempoMark(glyphs[i], names[i], dots, marked);
                } catch (ArithmeticException repeatingDecimal) {
                    // Use an equivalent exact pulse when the displayed rate would repeat.
                    return new ScoreTempoMark(0xE1D5, "quarter", 0, tempo.bpm());
                }
            }
            }
        // An unsupported pulse is represented by an equivalent quarter mark, without rounding.
        return new ScoreTempoMark(0xE1D5, "quarter", 0, tempo.bpm());
    }

    public String text() {
        return decimal(markedBpm);
    }

    public static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}

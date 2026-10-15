// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Applies printed tuplet timing with established document meter, preserving source ownership. */
public final class ScoreTupletMeterContext {
    private ScoreTupletMeterContext() {}

    public static ScorePageInterpretation apply(ScorePageInterpretation score) {
        return apply(score, null);
    }

    public static ScorePageInterpretation apply(
            ScorePageInterpretation score, ScoreMeterChange inherited) {
        var meters = new ArrayList<>(score.meterChanges());
        if (inherited != null && meters.stream().noneMatch(m -> m.measureIndex() == 0))
            meters.add(new ScoreMeterChange(0, inherited.numerator(), inherited.denominator()));
        var notes =
                TripletRhythmDetector.contextualSevens(
                        score.notes(), meters, score.openingPickup());
        if (notes.equals(score.notes())) return score;
        return score.withNotes(
                notes, java.util.stream.IntStream.range(0, notes.size()).boxed().toList());
    }
}

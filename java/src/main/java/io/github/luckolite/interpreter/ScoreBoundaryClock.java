// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Captured first/final measure spans, separate from printed meter and note records. */
public record ScoreBoundaryClock(double openingQuarterBeats, double closingQuarterBeats) {
    public ScoreBoundaryClock {
        if (!Double.isFinite(openingQuarterBeats)
                || !Double.isFinite(closingQuarterBeats)
                || openingQuarterBeats < 0
                || closingQuarterBeats < 0
                || (openingQuarterBeats == 0) != (closingQuarterBeats == 0))
            throw new IllegalArgumentException("Invalid captured boundary clock");
    }

    /** Both zero means legacy state, whose complete retained source still needs inference. */
    public static ScoreBoundaryClock legacy() {
        return new ScoreBoundaryClock(0, 0);
    }

    public boolean retained() {
        return openingQuarterBeats > 0;
    }

    public ScoreMeterMap meter(ScorePageInterpretation source, float openingNominal) {
        var nominal = new ScoreMeterMap(openingNominal, source.meterChanges());
        if (!retained()) return nominal;
        return nominal.withBoundaryQuarterBeats(
                openingQuarterBeats, closingQuarterBeats, source.measures().size());
    }

    /** Resolve once from every current source part, before selection or engraved reflow. */
    public static ScoreBoundaryClock resolve(ScorePageInterpretation source, float openingNominal) {
        int count = source.measures().size();
        if (count == 0) return legacy();
        var nominal = new ScoreMeterMap(openingNominal, source.meterChanges());
        double opening =
                ScoreOpeningDuration.provedQuarterBeats(
                        source.notes(),
                        source.rests(),
                        source.measures(),
                        nominal.beatsInMeasure(0),
                        source.firstMeasureNumber());
        double closing =
                ScoreOpeningDuration.provedClosingQuarterBeats(
                        source.notes(), source.rests(), count, nominal.beatsInMeasure(count - 1));
        opening = Double.isFinite(opening) ? opening : nominal.beatsInMeasure(0);
        closing = Double.isFinite(closing) ? closing : nominal.beatsInMeasure(count - 1);
        if (count == 1) opening = closing;
        var result = new ScoreBoundaryClock(opening, closing);
        result.meter(source, openingNominal);
        return result;
    }
}

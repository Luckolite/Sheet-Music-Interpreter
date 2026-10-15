// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class CourtesyMeasureRemapTest {
    @org.junit.Test
    public void originalControls() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        var regions =
                List.of(
                        new MeasureRegion(.1f, .3f, .1f, .2f),
                        new MeasureRegion(.31f, .4f, .1f, .2f),
                        new MeasureRegion(.1f, .5f, .3f, .4f),
                        new MeasureRegion(.51f, .9f, .3f, .4f));
        var span =
                new ScoreExpressiveEvent(
                        "rit",
                        ScoreExpressiveEvent.Kind.RITARDANDO,
                        Optional.of(new ScoreAnchor(0, 1)),
                        Optional.of(new ScoreAnchor(3, 2)),
                        ScoreExpressiveEvent.Scope.SCORE,
                        0,
                        1,
                        Optional.empty(),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "",
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "synthetic", 0, .2f, 0, 1, "rit.")));
        String hooks = "pedal-hooks-v1:0:0:0:1036831949:3:0:0:1036831949:0:1";
        var pedal =
                new ScoreExpressiveEvent(
                        "ped",
                        ScoreExpressiveEvent.Kind.PEDAL_DOWN,
                        Optional.empty(),
                        Optional.empty(),
                        ScoreExpressiveEvent.Scope.UNRESOLVED,
                        0,
                        1,
                        Optional.of(hooks),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "",
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "printed-pedal-bracket", 0, .2f, 0, 1, "pedal")));
        var detail =
                new ScorePlaybackDirection.Details(
                        0,
                        "ending",
                        "",
                        "",
                        "",
                        2,
                        List.of(1),
                        Optional.of(new ScoreAnchor(3, 0)),
                        ScorePlaybackDirection.AfterJumpRepeats.DEFAULT,
                        "1.",
                        List.of());
        var s =
                new ScorePageInterpretation(
                        regions,
                        List.of(),
                        1,
                        List.of(new ScoreKeyChange(1, 4)),
                        List.of(new ScoreTempoChange(3, 0, 100)),
                        List.of(new ScoreMeterChange(3, 3, 4)),
                        List.of(ScoreRestEvent.fullMeasure(3, .5f, .35f, .02f, 0, 1)),
                        List.of(new ScoreTechniqueChange(3, 0, 0, 1, 0)),
                        List.of(new ScoreDynamicChange(0, 0, 0, 1, 3, .5f, 0, 1)),
                        List.of(
                                new ScorePlaybackDirection(
                                        0, ScorePlaybackDirection.Kind.ENDING, detail)),
                        List.of(span, pedal));
        var r = CourtesyMeasureRemap.remove(s, 1);
        if (r.measures().size() != 3
                || r.rests().get(0).measureIndex() != 2
                || !r.rests().get(0).isFullMeasure()) throw new AssertionError("full rest");
        if (r.dynamicChanges().get(0).endMeasureIndex() != 2
                || r.playbackDirections().get(0).details().end().orElseThrow().measureIndex() != 2)
            throw new AssertionError("crossing spans");
        if (r.expressiveEvents().get(0).start().orElseThrow().measureIndex() != 0
                || r.expressiveEvents().get(0).end().orElseThrow().measureIndex() != 2)
            throw new AssertionError("expression");
        if (!r.expressiveEvents()
                .get(1)
                .targetEventId()
                .orElseThrow()
                .equals(hooks.replace(":3:0:", ":2:0:")))
            throw new AssertionError("deferred pedal " + r.expressiveEvents().get(1));
        if (r.keyChanges().get(0).measureIndex() != 1
                || r.tempoChanges().get(0).measureIndex() != 2
                || r.meterChanges().get(0).measureIndex() != 2
                || r.techniqueChanges().get(0).measureIndex() != 2)
            throw new AssertionError("changes");
        try {
            CourtesyMeasureRemap.remove(s, 3);
            throw new AssertionError("removed genuine rest");
        } catch (IllegalArgumentException expected) {
        }
        if (s.measures().size() != 4 || s.rests().get(0).measureIndex() != 3)
            throw new AssertionError("mutated source");
        System.out.println(
                "PASS structural remapping: full rest, crossing hairpin/ending/expression, deferred pedal, signatures/tempo/technique, source immutability, genuine-rest rejection");
    }
}

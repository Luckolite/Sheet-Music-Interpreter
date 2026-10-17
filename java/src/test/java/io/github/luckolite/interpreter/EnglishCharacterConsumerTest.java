// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Consumer-only timing controls supply an explicitly qualified role; acquisition is tested separately. */
public class EnglishCharacterConsumerTest {
    static final int W = 500, H = 300;

    static byte[] rails() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 460; x++) g[y * W + x] = 0;
        return g;
    }

    static ScoreNoteEvent note(float x, int step, float duration) {
        return new ScoreNoteEvent(
                0, x, step, 0, 1, (164f - step * 8) / H, false, 0, 0, 2, duration, 1, 0, 0);
    }

    static ScorePageInterpretation score(boolean meter) {
        // Uneven engraving positions must not supply the clock: quarter, quarter chord, half = 4.
        var notes = List.of(note(.1f, 0, 1), note(.35f, 2, 1), note(.35f, 4, 1), note(.85f, 6, 2));
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.04f, .92f, .25f, .65f)),
                notes,
                1,
                List.of(),
                List.of(),
                meter ? List.of(new ScoreMeterChange(0, 4, 4)) : List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    static ScorePageInterpretation detect(String text, boolean meter) {
        float x = .04f + .35f * (.92f - .04f);
        return ScoreExpressionDetector.apply(
                score(meter),
                List.of(
                        new PlayingTechniqueDetector.Word(
                                text,
                                x,
                                195f / H,
                                x + .08f,
                                215f / H,
                                PlayingTechniqueDetector.WordAdmission.CONTEXT_QUALIFIED)),
                List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                rails(),
                W,
                H);
    }

    @Test
    public void fullyTimedChordResolvesToWrittenOnsetNotEngravingFraction() {
        for (String text :
                List.of(
                        "flowing",
                        "triumphantly",
                        "ethereal",
                        "with driving intensity",
                        "like an echo",
                        "  Fl\u00f6wing!  ")) {
            var before = detect(text, true);
            assertEquals(1, before.expressiveEvents().size());
            var after = ScoreExpressionDetector.resolve(before, Float.NaN);
            var e = after.expressiveEvents().get(0);
            assertEquals(new ScoreAnchor(0, 1), e.start().orElseThrow());
            assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, e.kind());
            assertEquals(ScoreExpressiveEvent.Strength.UNSPECIFIED, e.strength());
            assertEquals(text, e.evidence().get(0).printedText());
            assertEquals(before.notes(), after.notes());
            assertEquals(before.tempoChanges(), after.tempoChanges());
            assertEquals(before.meterChanges(), after.meterChanges());
            assertEquals(before.dynamicChanges(), after.dynamicChanges());
            assertEquals(before.rests(), after.rests());
            try (var session = ScoreNoteTiming.beginTimingSession(after)) {
                assertEquals(
                        1,
                        ScoreNoteTiming.beatInMeasure(after.notes().get(1), after.notes(), 4),
                        0);
                assertEquals(
                        1,
                        ScoreNoteTiming.beatInMeasure(after.notes().get(2), after.notes(), 4),
                        0);
                assertEquals(
                        2,
                        ScoreNoteTiming.resolvedWrittenDurationBeats(
                                after.notes().get(3), after.notes(), 4),
                        0);
            }
        }
    }

    @Test
    public void unknownMeterClockDoesNotGuessFromEngraving() {
        var before = detect("with driving intensity", false);
        var after = ScoreExpressionDetector.resolve(before, Float.NaN);
        assertEquals(before, after);
        assertTrue(after.expressiveEvents().get(0).start().isEmpty());
        assertEquals(
                ScoreExpressiveEvent.Scope.UNRESOLVED, after.expressiveEvents().get(0).scope());
        assertTrue(after.tempoChanges().isEmpty());
        assertTrue(after.meterChanges().isEmpty());
    }
}

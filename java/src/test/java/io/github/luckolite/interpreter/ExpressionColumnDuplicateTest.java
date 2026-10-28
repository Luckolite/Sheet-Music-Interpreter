// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original overlapping OCR readings and written chord columns. */
public final class ExpressionColumnDuplicateTest {
    private static final int W = 600, H = 350;

    private static ScorePageInterpretation detect(
            float[] positions,
            float[] wordPositions,
            String[] texts,
            List<ScoreExpressiveEvent> initial) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 100; x < 580; x++) gray[(200 - line * 14) * W + x] = 0;
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < positions.length; i++)
            notes.add(
                    new ScoreNoteEvent(
                            0, positions[i], i * 2, 0, 1, (200f - i * 14) / H, false, 0, 0, 2, 1));
        var score =
                new ScorePageInterpretation(
                                List.of(new MeasureRegion(.2f, .9f, .35f, .65f)),
                                notes,
                                1,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of())
                        .withExpressiveEvents(initial);
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        for (int i = 0; i < wordPositions.length; i++) {
            float x = .2f + wordPositions[i] * (.9f - .2f);
            words.add(new PlayingTechniqueDetector.Word(texts[i], x, 220f / H, x + .06f, 234f / H));
        }
        byte[] before = gray.clone();
        var result =
                ScoreExpressionDetector.apply(
                        score,
                        words,
                        List.of(new PlayingTechniqueDetector.Staff(144, 200, 14, 0, 1)),
                        gray,
                        W,
                        H);
        assertArrayEquals(before, gray);
        assertEquals(notes, result.notes());
        assertEquals(initial, score.expressiveEvents());
        return result;
    }

    private static ScoreExpressiveEvent existing(String source, float position, boolean rest) {
        String target =
                (rest ? "expression-rest-column:" : "expression-column:")
                        + "0:0:1:"
                        + Float.floatToIntBits(position);
        return new ScoreExpressiveEvent(
                "existing-" + source,
                Kind.RITARDANDO,
                Optional.empty(),
                Optional.empty(),
                Scope.UNRESOLVED,
                0,
                1,
                Optional.of(target),
                Strength.UNSPECIFIED,
                "",
                List.of(new Evidence(source, 0, .375f, 0, 1, "rit.")));
    }

    @Test
    public void twoReadingsOfTheSameChordProduceOneDirection() {
        var result =
                detect(
                        new float[] {.25f, .251f},
                        new float[] {.25f, .251f},
                        new String[] {"rit.", "rit."},
                        List.of());
        assertEquals(1, result.expressiveEvents().size());
        assertEquals(
                List.of(0, 1),
                ScoreExpressionDetector.targetIndices(result, result.expressiveEvents().get(0)));
    }

    @Test
    public void anExistingPrintedBindingKeepsItsOriginalIdentity() {
        var original = existing("printed-expression-word", .251f, false);
        var result =
                detect(
                        new float[] {.25f, .251f},
                        new float[] {.25f},
                        new String[] {"rit."},
                        List.of(original));
        assertEquals(1, result.expressiveEvents().size());
        assertSame(original, result.expressiveEvents().get(0));
    }

    @Test
    public void overlappingUnequalColumnsRemainDistinct() {
        var result =
                detect(
                        new float[] {.10f, .115f, .13f},
                        new float[] {.10f, .13f},
                        new String[] {"rit.", "rit."},
                        List.of());
        assertEquals(2, result.expressiveEvents().size());
        assertEquals(
                List.of(0, 1),
                ScoreExpressionDetector.targetIndices(result, result.expressiveEvents().get(0)));
        assertEquals(
                List.of(1, 2),
                ScoreExpressionDetector.targetIndices(result, result.expressiveEvents().get(1)));
    }

    @Test
    public void differentUnresolvedWordsRemainDistinct() {
        assertEquals(
                2,
                detect(
                                new float[] {.25f, .251f},
                                new float[] {.25f, .251f},
                                new String[] {"sub. p", "sub. mp"},
                                List.of())
                        .expressiveEvents()
                        .size());
    }

    @Test
    public void differentDirectionKindsRemainDistinct() {
        assertEquals(
                2,
                detect(
                                new float[] {.25f, .251f},
                                new float[] {.25f, .251f},
                                new String[] {"rit.", "rall."},
                                List.of())
                        .expressiveEvents()
                        .size());
    }

    @Test
    public void foreignEvidenceCannotSuppressAPrintedReading() {
        var original = existing("manual", .251f, false);
        var result =
                detect(
                        new float[] {.25f, .251f},
                        new float[] {.25f},
                        new String[] {"rit."},
                        List.of(original));
        assertEquals(2, result.expressiveEvents().size());
        assertSame(original, result.expressiveEvents().get(0));
    }

    @Test
    public void restAndAttackBindingsRemainDistinct() {
        var original = existing("printed-expression-word", .251f, true);
        assertEquals(
                2,
                detect(
                                new float[] {.25f, .251f},
                                new float[] {.25f},
                                new String[] {"rit."},
                                List.of(original))
                        .expressiveEvents()
                        .size());
    }

    @Test
    public void anEmptyColumnCannotSupplyDuplicateEvidence() {
        var original = existing("printed-expression-word", .8f, false);
        assertEquals(
                2,
                detect(
                                new float[] {.25f, .251f},
                                new float[] {.25f},
                                new String[] {"rit."},
                                List.of(original))
                        .expressiveEvents()
                        .size());
    }
}

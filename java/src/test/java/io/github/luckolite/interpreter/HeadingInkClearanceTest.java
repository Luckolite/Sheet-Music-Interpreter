// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural rails and heads at multiple scales; no source-score pixels. */
public final class HeadingInkClearanceTest {
    private void check(
            int rails, boolean absent, boolean cross, float bottom, float headX, boolean expected) {
        for (float scale : new float[] {1, 1.5f, 2}) {
            int width = Math.round(1000 * scale), height = Math.round(650 * scale);
            byte[] gray = new byte[width * height];
            Arrays.fill(gray, (byte) 255);
            for (int line = 0; line < rails; line++)
                for (int x = Math.round(180 * scale); x < Math.round(820 * scale); x++)
                    gray[Math.round((300 + line * 14) * scale) * width + x] = 20;
            var note =
                    new ScoreNoteEvent(
                            0,
                            (headX - 200) / 600,
                            8,
                            0,
                            1,
                            300f / 650,
                            false,
                            0,
                            0,
                            2,
                            1,
                            1,
                            0,
                            0);
            if (cross) note = note.withCrossStaffBeam();
            var score =
                    new ScorePageInterpretation(
                            List.of(new MeasureRegion(.2f, .8f, .43f, .64f)),
                            absent ? List.of() : List.of(note),
                            1,
                            List.of(),
                            List.of(),
                            List.of(new ScoreMeterChange(0, 4, 4)));
            byte[] before = gray.clone();
            var result =
                    ScoreExpressionDetector.apply(
                            score,
                            List.of(
                                    new PlayingTechniqueDetector.Word(
                                            "Free time",
                                            .4f,
                                            (bottom - 25) / 650,
                                            .49f,
                                            bottom / 650)),
                            List.of(
                                    new PlayingTechniqueDetector.Staff(
                                            300 * scale, 356 * scale, 14 * scale, 0, 1)),
                            gray,
                            width,
                            height);
            assertArrayEquals(before, gray);
            assertEquals(score.notes(), result.notes());
            assertEquals(score.tempoChanges(), result.tempoChanges());
            assertEquals("scale=" + scale, expected ? 1 : 0, result.expressiveEvents().size());
            if (expected) {
                var event = result.expressiveEvents().get(0);
                assertEquals("Free time", event.evidence().get(0).printedText());
                assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, event.kind());
                assertEquals(0, event.start().orElseThrow().measureIndex());
                assertEquals(List.of(0), ScoreExpressionDetector.targetIndices(result, event));
            }
        }
    }

    @Test
    public void permittedInkClearanceDoesNotIncludeRankingPenalty() {
        check(5, false, false, 238, 430, true);
    }

    @Test
    public void alreadyNearbyMarkStillAttaches() {
        check(5, false, false, 250, 430, true);
    }

    @Test
    public void fartherMarkRemainsUnowned() {
        check(5, false, false, 225, 430, false);
    }

    @Test
    public void fourRailsRemainInsufficient() {
        check(4, false, false, 238, 430, false);
    }

    @Test
    public void absentRailsRemainInsufficient() {
        check(0, false, false, 238, 430, false);
    }

    @Test
    public void absentHeadRemainsInsufficient() {
        check(5, true, false, 238, 430, false);
    }

    @Test
    public void crossStaffHeadCannotSupplyOwnership() {
        check(5, false, true, 238, 430, false);
    }

    @Test
    public void remoteHeadCannotSupplyOwnership() {
        check(5, false, false, 238, 800, false);
    }
}

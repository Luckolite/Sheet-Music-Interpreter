// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MeasureStartRestDirectionTest {
    static ScorePageInterpretation page(List<ScoreRestEvent> rests) {
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.20f, .92f, .25f, .65f)),
                List.of(),
                1,
                List.of(),
                List.of(),
                List.of(),
                rests,
                List.of(),
                List.of());
    }

    static ScorePageInterpretation detect(ScorePageInterpretation p, float left, byte[] gray) {
        return ScoreExpressionDetector.apply(
                p,
                List.of(new PlayingTechniqueDetector.Word("rit.", left, .20f, left + .04f, .26f)),
                List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                gray,
                500,
                300);
    }

    static ScoreRestEvent rest(float x, double duration) {
        return new ScoreRestEvent(0, x, 116f / 300, 8f / 300, 0, 1, duration);
    }

    @Test
    public void leadingWholeRestKeepsEvidenceAndResolvesOnlyWithMeter() {
        for (float x : new float[] {.4f, .5f, .6f}) {
            var p = detect(page(List.of(rest(x, 4))), .22f, RestExpressionOwnershipTest.rails());
            assertEquals(1, p.expressiveEvents().size());
            assertTrue(p.expressiveEvents().get(0).start().isEmpty());
            assertEquals(
                    new ScoreAnchor(0, 0),
                    ScoreExpressionDetector.resolve(p, 4)
                            .expressiveEvents()
                            .get(0)
                            .start()
                            .orElseThrow());
            assertTrue(
                    ScoreExpressionDetector.resolve(p, 3)
                            .expressiveEvents()
                            .get(0)
                            .start()
                            .isEmpty());
        }
    }

    @Test
    public void ordinaryFullMeasureRestAlsoRetainsLeadingDirection() {
        assertEquals(
                1,
                detect(
                                page(List.of(RestExpressionOwnershipTest.full())),
                                .22f,
                                RestExpressionOwnershipTest.rails())
                        .expressiveEvents()
                        .size());
    }

    @Test
    public void shorterOrOffCenterRestsDoNotExpandOwnership() {
        for (var r : List.of(rest(.5f, 2), rest(.5f, 1), rest(.8f, 4)))
            assertTrue(
                    detect(page(List.of(r)), .22f, RestExpressionOwnershipTest.rails())
                            .expressiveEvents()
                            .isEmpty());
    }

    @Test
    public void nonLeadingTextDoesNotExpandOwnership() {
        assertTrue(
                detect(page(List.of(rest(.6f, 4))), .36f, RestExpressionOwnershipTest.rails())
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void duplicateRestAndMissingRailsRejectOwnership() {
        assertTrue(
                detect(
                                page(List.of(rest(.5f, 4), rest(.6f, 4))),
                                .22f,
                                RestExpressionOwnershipTest.rails())
                        .expressiveEvents()
                        .isEmpty());
        byte[] blank = new byte[500 * 300];
        Arrays.fill(blank, (byte) 255);
        assertTrue(detect(page(List.of(rest(.5f, 4))), .22f, blank).expressiveEvents().isEmpty());
    }

    @Test
    public void soundingBarDoesNotExpandRestOwnership() {
        var p = page(List.of(rest(.5f, 4)));
        var n = new ScoreNoteEvent(0, .8f, 0, 0, 1, 164f / 300, false, 0, 0, 0, 1, 1, 0);
        p =
                new ScorePageInterpretation(
                        p.measures(),
                        List.of(n),
                        1,
                        List.of(),
                        List.of(),
                        List.of(),
                        p.rests(),
                        List.of(),
                        List.of());
        var word = new PlayingTechniqueDetector.Word("rit.", .22f, .20f, .26f, .26f);
        assertNull(
                PrintedRestDirection.find(
                        p,
                        List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                        word,
                        RestExpressionOwnershipTest.rails(),
                        500,
                        300));
    }
}

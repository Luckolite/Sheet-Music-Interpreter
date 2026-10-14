// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MetricBoundaryControlTest {
    static final int W = 600, H = 300;

    static ScoreNoteEvent note(int measure, float position, int staff) {
        return new ScoreNoteEvent(
                measure, position, 0, staff, 1, 164f / H, false, 0, 0, 0, 1, 1, 0);
    }

    static ScorePageInterpretation run(
            float left, float right, List<ScoreNoteEvent> notes, boolean rails) {
        return run(
                left,
                right,
                notes,
                rails,
                List.of(
                        new MeasureRegion(20f / W, 300f / W, .25f, .65f),
                        new MeasureRegion(300f / W, 580f / W, .25f, .65f)));
    }

    static ScorePageInterpretation run(
            float left,
            float right,
            List<ScoreNoteEvent> notes,
            boolean rails,
            List<MeasureRegion> measures) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        if (rails)
            for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 580; x++) gray[y * W + x] = 0;
        var page =
                new ScorePageInterpretation(
                        measures, notes, 0, List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of());
        return ScoreExpressionDetector.apply(
                page,
                List.of(
                        new PlayingTechniqueDetector.Word(
                                "quarter = dotted quarter", left / W, 45f / H, right / W, 80f / H)),
                List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                gray,
                W,
                H);
    }

    @Test
    public void boundaryUsesFirstNoteOfFollowingBar() {
        var n = List.of(note(0, .95f, 0), note(1, .12f, 0), note(1, .4f, 0));
        var p = run(270, 340, n, true);
        assertEquals(1, p.expressiveEvents().size());
        assertEquals(
                List.of(1), ScoreExpressionDetector.targetIndices(p, p.expressiveEvents().get(0)));
        assertEquals(n, p.notes());
    }

    @Test
    public void missingFollowingAttackNeverFallsBackToPreviousBar() {
        assertTrue(run(270, 340, List.of(note(0, .95f, 0)), true).expressiveEvents().isEmpty());
    }

    @Test
    public void remoteFollowingAttackIsNotEvidence() {
        assertTrue(
                run(270, 340, List.of(note(0, .95f, 0), note(1, .8f, 0)), true)
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void anotherStaffCannotOwnFollowingAttack() {
        assertTrue(
                run(270, 340, List.of(note(0, .95f, 0), note(1, .12f, 1)), true)
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void midBarKeepsLeftHandAttack() {
        var p = run(100, 180, List.of(note(0, 80f / 280, 0), note(0, 160f / 280, 0)), true);
        assertEquals(1, p.expressiveEvents().size());
        assertEquals(
                List.of(0), ScoreExpressionDetector.targetIndices(p, p.expressiveEvents().get(0)));
    }

    @Test
    public void absentRailsDoNotAuthorizeEquation() {
        assertTrue(
                run(270, 340, List.of(note(0, .95f, 0), note(1, .12f, 0)), false)
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void severalBoundariesDoNotChooseAnArbitraryBar() {
        assertTrue(
                run(
                                270,
                                340,
                                List.of(note(0, .95f, 0), note(1, .12f, 0), note(2, .12f, 0)),
                                true,
                                List.of(
                                        new MeasureRegion(20f / W, 300f / W, .25f, .65f),
                                        new MeasureRegion(300f / W, 320f / W, .25f, .65f),
                                        new MeasureRegion(320f / W, 580f / W, .25f, .65f)))
                        .expressiveEvents()
                        .isEmpty());
    }
}

// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual diagnostic traversal and performance consumers; no inferred score or native runtime. */
public final class NavigationPerformanceAdmissionTest {
    @Test
    public void completeDiagnosticAndValidPerformanceControls() {
        NavigationAdmissionOriginals.main(new String[0]);
    }

    @Test
    public void wholeScoreProjectionCannotExecuteUnsupportedMeasureRepeat() {
        var source =
                new ScorePageInterpretation(
                        List.of(
                                new MeasureRegion(0, 1, 0, 1),
                                new MeasureRegion(1, 2, 0, 1),
                                new MeasureRegion(2, 3, 0, 1)),
                        List.of());
        var plan =
                ScoreNavigationPlan.create(
                        3,
                        List.of(
                                new ScorePlaybackDirection(
                                        1, ScorePlaybackDirection.Kind.MEASURE_REPEAT)),
                        new ScoreMeterMap(3, List.of()));
        assertTrue(plan.traversal().complete());
        assertFalse(plan.traversal().performanceQualified());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScoreNavigationProjection.project(
                                source,
                                plan,
                                new ScoreNavigationProjection.Defaults(0, 120, 3, 4)));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScorePlaybackDirection.Kind.*;

/** Original logical repeat, preserving source-owned stem and explicit tuplet metadata. */
public class PrintedStemNavigationTest {
    @Test
    public void repeatedOccurrencesRetainPrintedStemAndSourceIdentity() {
        var n =
                new ScoreNoteEvent(1, .25f, 2, 0, 1, .3f, false, 0, 1)
                        .withStemDirection(-1)
                        .withTupletRatio(5, 3);
        var bar = new MeasureRegion(.1f, .9f, .2f, .8f);
        var source = new ScorePageInterpretation(List.of(bar, bar, bar), List.of(n));
        var plan =
                ScoreNavigationPlan.create(
                        3,
                        List.of(
                                new ScorePlaybackDirection(1, REPEAT_START),
                                new ScorePlaybackDirection(2, REPEAT_END)));
        var result =
                ScoreNavigationProjection.project(
                        source, plan, new ScoreNavigationProjection.Defaults(0, 80, 4, 4));
        assertEquals(
                List.of(1, 2), result.notes().stream().map(ScoreNoteEvent::measureIndex).toList());
        for (var actual : result.notes()) {
            assertEquals(-1, actual.stemDirection());
            assertEquals(5, actual.tupletDivisor());
            assertEquals(3, actual.tupletNormalNotes());
            assertEquals(n.staffStep(), actual.staffStep());
        }
        assertEquals(List.of(n), source.notes());
    }
}

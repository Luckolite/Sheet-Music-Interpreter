// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RetainedTempoAnchorTest extends IsolatedTempoAnchorTest {
    private List<ScoreTempoChange> retained(int left, float annotation) {
        read(left, true, true, false);
        return TempoChangeDetector.detect(
                List.of(
                        new MeasureNumberReconciler.NumberToken(
                                96,
                                left / (float) W,
                                110f / H,
                                (left + 16f) / W,
                                128f / H,
                                annotation / W)),
                gray,
                W,
                H,
                List.of(
                        new MeasureRegion(30f / W, 180f / W, 138f / H, 210f / H),
                        new MeasureRegion(180f / W, 450f / W, 138f / H, 210f / H)));
    }

    @Test
    public void retainedEqualsAnchorUsesProvedNote() {
        assertEquals(List.of(new ScoreTempoChange(1, 0, 96)), retained(260, 242));
    }

    @Test
    public void earlierPhraseAnchorRemainsEarlier() {
        assertEquals(List.of(new ScoreTempoChange(1, 0, 96)), retained(260, 190));
    }

    @Test
    public void retainedMidBarEquationStaysMidBar() {
        var changes = retained(350, 332);
        assertEquals(1, changes.size());
        assertTrue(changes.get(0).positionInMeasure() > .4f);
        assertTrue(changes.get(0).positionInMeasure() < .6f);
    }
}

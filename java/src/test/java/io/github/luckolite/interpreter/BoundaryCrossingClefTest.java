// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometry: a first-measure boundary crosses a complete header clef. */
public class BoundaryCrossingClefTest {
    private void check(float boundary) {
        var p = new HeaderAccidentalOwnershipTest.Page(true);
        var labels = p.labels.clone();
        var gray = p.gray.clone();
        var a =
                OmrScoreInterpreter.analyze(
                        p.labels,
                        p.gray,
                        420,
                        260,
                        List.of(new MeasureRegion(boundary / 420, 1, .1f, .9f)));
        assertEquals(List.of(new ScoreKeyChange(0, -2)), a.keyChanges());
        assertEquals(ScoreNoteEvent.ACCIDENTAL_FROM_KEY, a.notes().get(0).writtenAccidental());
        assertArrayEquals(labels, p.labels);
        assertArrayEquals(gray, p.gray);
    }

    @Test
    public void middleOfCompleteClef() {
        check(38);
    }

    @Test
    public void rightEdgeOfCompleteClef() {
        check(50);
    }

    @Test
    public void leftEdgeOfCompleteClef() {
        check(25);
    }

    @Test
    public void ordinaryHeaderStillWorks() {
        check(60);
    }
}

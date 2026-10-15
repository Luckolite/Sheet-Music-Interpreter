// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepeatFlankRecognitionTest {
    @Test
    public void obscuredMusicFlankUsesCompleteOppositeStaff() {
        var g = new RepeatColumnProofTest().page(true, true, false);
        var marks =
                PrintedRepeatGlyphs.detect(
                        g,
                        600,
                        240,
                        List.of(new PlayingTechniqueDetector.Staff(120, 168, 12, 0, 1)),
                        List.of(
                                new MeasureRegion(.15f, .49f, .45f, .75f),
                                new MeasureRegion(.51f, .92f, .45f, .75f)));
        assertEquals(1, marks.size());
        assertEquals(1, marks.get(0).boundary());
        assertFalse(marks.get(0).outgoing());
    }
}

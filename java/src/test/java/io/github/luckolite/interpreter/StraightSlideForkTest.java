// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Additive original endpoint-identity controls, frozen before producer edits. */
public class StraightSlideForkTest {
    @Test
    public void oneSourceTwoFlaggedTargetsAbstains() {
        var a = StraightSlideConsumerTest.event(0, 0, 2, 72);
        var b = StraightSlideConsumerTest.event(0, 2, 1, 74);
        var c = StraightSlideConsumerTest.event(0, 2, 1, 76);
        Main.attachNotePerformance(
                List.of(
                        StraightSlideConsumerTest.note(0, .2f, 5, 0),
                        StraightSlideConsumerTest.note(0, .8f, 6, 16576),
                        StraightSlideConsumerTest.note(0, .8f, 7, 16576)),
                List.of(a, b, c));
        assertFalse(b.containsKey("slide"));
        assertFalse(c.containsKey("slide"));
    }

    @Test
    public void distinctChainedEndpointsSurvive() {
        var a = StraightSlideConsumerTest.event(0, 0, 1, 72);
        var b = StraightSlideConsumerTest.event(0, 1, 1, 74);
        var c = StraightSlideConsumerTest.event(0, 2, 1, 76);
        Main.attachNotePerformance(
                List.of(
                        StraightSlideConsumerTest.note(0, .2f, 5, 0),
                        StraightSlideConsumerTest.note(0, .5f, 6, 16576),
                        StraightSlideConsumerTest.note(0, .8f, 7, 16576)),
                List.of(a, b, c));
        assertEquals(0, ((Map<?, ?>) b.get("slide")).get("sourceNoteIndex"));
        assertEquals(1, ((Map<?, ?>) c.get("slide")).get("sourceNoteIndex"));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Grace identity reaches geometric ownership without inventing musical evidence. */
public final class GraceSlurContextOriginalTest {
    @Test
    public void sourceGraceFlagsSurviveGeometryProjection() {
        var a =
                new ScoreNoteEvent(0, .2f, 2, 0, 1, .4f, false)
                        .withArticulations(NoteOrnament.GRACE);
        var b =
                new ScoreNoteEvent(0, .3f, 1, 0, 1, .41f, false)
                        .withArticulations(NoteOrnament.GRACE);
        var c = new ScoreNoteEvent(0, .4f, 0, 0, 1, .42f, false);
        var hs =
                PrintedSlurContext.geometryHeads(
                        List.of(a, b, c),
                        List.of(new MeasureRegion(.1f, .9f, .2f, .7f)),
                        1000,
                        1000);
        assertTrue(hs.get(0).grace());
        assertTrue(hs.get(1).grace());
        assertFalse(hs.get(2).grace());
        assertEquals(0, hs.get(0).owner());
        assertEquals(1, hs.get(1).owner());
        assertEquals(2, hs.get(2).owner());
    }

    @Test
    public void compatibilityGeometryStaysUnknownWhileWrittenGraceOrderIsExplicit() {
        assertFalse(new PrintedSlurOwners.GeometryHead(0, 50, 50, 0, 1).grace());
        assertFalse(new PrintedSlurOwners.Head(0, 50, 50, 0, 1, 1, 100, 60).grace());
        assertTrue(new PrintedSlurOwners.Head(0, 50, 50, 0, 1, 1, 100, -1000000, 60).grace());
        assertTrue(new PrintedSlurOwners.Head(0, 50, 50, 0, 1, 1, 100, 1, 60).grace());
    }
}

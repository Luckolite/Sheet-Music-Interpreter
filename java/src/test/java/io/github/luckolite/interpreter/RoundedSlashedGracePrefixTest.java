// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural geometry, without any source-score pixels or coordinates. */
public class RoundedSlashedGracePrefixTest {
    List<ScoreNoteEvent> example(
            boolean slash,
            boolean flag,
            boolean shaft,
            int distance,
            int principalArea,
            int staff,
            boolean dotted)
            throws Exception {
        var h = new FadedGraceOwnershipTest();
        if (shaft) h.rect(104, 105, 85, 120, 30);
        if (flag) h.line(104, 85, 113, 101, 1, 30);
        if (slash) h.line(98, 106, 115, 89, 1, 30);
        var first = new ScoreNoteEvent(0, 96 / 300f, 3, 0, 1, .5f, false, dotted ? 1 : 0, 1, 2, 0);
        return h.mark(
                List.of(
                        h.head(96, 120, 8, 7, 170),
                        h.head(96 + distance, 115, 12, 9, principalArea)),
                List.of(first, h.event(96 + distance, 0, staff)),
                "markSlashedGracePrefixes");
    }

    boolean grace(List<ScoreNoteEvent> ns) {
        return (ns.get(0).articulations() & NoteOrnament.GRACE) != 0;
    }

    @Test
    public void roundedHeadWithIndependentSlashFlagAndShortShaftIsGrace() throws Exception {
        assertTrue(grace(example(true, true, true, 36, 360, 0, false)));
    }

    @Test
    public void missingSlashKeepsSmallMetricalNote() throws Exception {
        assertFalse(grace(example(false, true, true, 36, 360, 0, false)));
    }

    @Test
    public void missingFlagRejectsSlashFragment() throws Exception {
        assertFalse(grace(example(true, false, true, 36, 360, 0, false)));
    }

    @Test
    public void detachedInkNeedsItsOwnShaft() throws Exception {
        assertFalse(grace(example(true, true, false, 36, 360, 0, false)));
    }

    @Test
    public void differentStaffCannotOwnGrace() throws Exception {
        assertFalse(grace(example(true, true, true, 36, 360, 1, false)));
    }

    @Test
    public void distantPrincipalCannotOwnGrace() throws Exception {
        assertFalse(grace(example(true, true, true, 70, 360, 0, false)));
    }

    @Test
    public void dottedNoteStaysMetrical() throws Exception {
        assertFalse(grace(example(true, true, true, 36, 360, 0, true)));
    }

    @Test
    public void equallySmallPrincipalDoesNotProveGrace() throws Exception {
        assertFalse(grace(example(true, true, true, 36, 180, 0, false)));
    }
}

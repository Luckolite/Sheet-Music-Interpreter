// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Serialized beam topology is descriptive data, not live caret ownership authority. */
public final class OwnedDistantCaretFrameTest {
    @Test
    public void aRetainedSourcePortCannotBeRebuiltIntoALiveGrant() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var topology = s.source().source();
        var retained = s.score().withBeamSource(topology);
        var result =
                PrintedBeamSource.apply(
                        retained,
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        s.staffs,
                        0);
        assertSame(retained, result);
        assertEquals(0, result.notes().get(0).articulations());
    }

    @Test
    public void valueEquivalentSourcePortOnAnotherFrameAddsNoCaret() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var topology = s.source().source();
        var other = new OwnedDistantCaretOriginalTest.Scene();
        var retained = other.score().withBeamSource(topology);
        var result =
                PrintedBeamSource.apply(
                        retained,
                        other.labels,
                        other.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        other.staffs,
                        1);
        assertSame(retained, result);
        assertEquals(0, result.notes().get(0).articulations());
    }

    @Test
    public void aFreshTrustedProducerMayUseCopiedRawValues() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var score = s.score();
        var result =
                PrintedBeamSource.apply(
                        score,
                        s.labels.clone(),
                        s.raw.clone(),
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        s.staffs,
                        0);
        assertEquals(NoteArticulation.MARCATO, result.notes().get(0).articulations());
    }
}

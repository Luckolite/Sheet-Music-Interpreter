// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** A late optional refusal cannot commit an earlier new mark or erase old source facts. */
public final class OwnedDistantCaretBudgetTest {
    private static final int OLD = NoteArticulation.STACCATO | NoteOrnament.MORDENT;

    private static OwnedDistantCaretOriginalTest.Scene scene(boolean second) {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        if (second) s.caret(250, 249, 195);
        s.notes.add(OwnedDistantCaretOriginalTest.Scene.note(600, 350, OLD));
        return s;
    }

    private static ScorePageInterpretation apply(
            OwnedDistantCaretOriginalTest.Scene s, long admission) {
        return PrintedBeamSource.applyWithCaretLimits(
                s.score(),
                s.labels,
                s.raw,
                OwnedDistantCaretOriginalTest.W,
                OwnedDistantCaretOriginalTest.H,
                s.staffs,
                0,
                1_000_000,
                1_000_000,
                512_000_000,
                Long.MAX_VALUE,
                admission,
                Long.MAX_VALUE);
    }

    @Test
    public void lateAdmissionFailureIsAtomicAndRetainsLegacySource() {
        var one = scene(false);
        assertArrayEquals(
                new int[] {2, 0, OLD},
                apply(one, 1_000_000).notes().stream()
                        .mapToInt(ScoreNoteEvent::articulations)
                        .toArray());
        var two = scene(true);
        assertArrayEquals(
                new int[] {2, 2, OLD},
                apply(two, Long.MAX_VALUE).notes().stream()
                        .mapToInt(ScoreNoteEvent::articulations)
                        .toArray());
        var before = two.score();
        var oldSource = two.source().source();
        byte[] raw = two.raw.clone(), labels = two.labels.clone();
        var refused = apply(two, 1_000_000);
        assertEquals(before.withBeamSource(oldSource), refused);
        assertArrayEquals(raw, two.raw);
        assertArrayEquals(labels, two.labels);
    }
}

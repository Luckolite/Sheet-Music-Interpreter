// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import org.junit.Test;

/** Actual bounded context capture is transported through success and optional refusal. */
public final class CaretClockBudgetTransportTest {
    private static ScorePageInterpretation captured(OwnedDistantCaretOriginalTest.Scene s) {
        var before = s.score();
        return before.withClockContextSource(
                ScoreClockContextCapture.capture(
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        before,
                        null,
                        null,
                        null));
    }

    @Test
    public void darkOwnedCaretRetainsActualCapturedRasterAndMissingFacts() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        s.caret(200, 249, 75);
        var before = captured(s);
        assertFalse(before.clockContextSource().origin().rasterSha256().isEmpty());
        var after =
                PrintedBeamSource.apply(
                        before,
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        s.staffs,
                        0);
        assertEquals(NoteArticulation.MARCATO, after.notes().get(0).articulations());
        assertEquals(before.clockContextSource(), after.clockContextSource());
        assertTrue(after.clockContextSource().printedValues().isEmpty());
    }

    @Test
    public void lateAdmissionBudgetRefusalRetainsEveryExistingFactAndNote() {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        s.caret(250, 249, 195);
        var before = captured(s);
        byte[] raw = s.raw.clone(), labels = s.labels.clone();
        var after =
                PrintedBeamSource.applyWithCaretLimits(
                        before,
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
                        1_000_000,
                        Long.MAX_VALUE);
        assertEquals(before.notes(), after.notes());
        assertEquals(before.clockContextSource(), after.clockContextSource());
        assertArrayEquals(raw, s.raw);
        assertArrayEquals(labels, s.labels);
        assertEquals(before.withBeamSource(s.source().source()), after);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class VerifiedCourtesyBoundaryTest {
    static ScorePageInterpretation score(boolean key, boolean meter, boolean rest) {
        return new ScorePageInterpretation(
                List.of(
                        new MeasureRegion(.1f, 106f / 220, .15f, .8f),
                        new MeasureRegion(106f / 220, 160f / 220, .15f, .8f)),
                List.of(),
                1,
                key ? List.of(new ScoreKeyChange(1, 0)) : List.of(),
                List.of(),
                meter ? List.of(new ScoreMeterChange(1, 4, 4)) : List.of(),
                rest ? List.of(ScoreRestEvent.fullMeasure(1, .5f, .5f, .1f, 0, 1)) : List.of(),
                List.of(),
                List.of());
    }

    static ScorePageInterpretation trim(ScorePageInterpretation s) {
        var p = CourtesyBoundaryInkTest.page();
        return VerifiedCourtesyBoundary.trim(
                s,
                new MeterChangeDetector.Crop(138, 28, 151, 85, 30, 13),
                p.labels(),
                p.gray(),
                220,
                120);
    }

    @Test
    public void removesOnlyEmptyTailAndPreservesBoundarySignatures() {
        var s = score(true, true, false);
        var r = trim(s);
        assertEquals(1, r.measures().size());
        assertEquals(s.keyChanges(), r.keyChanges());
        assertEquals(s.meterChanges(), r.meterChanges());
        assertEquals(r.measures().size(), r.meterChanges().get(0).measureIndex());
        assertEquals(2, s.measures().size());
    }

    @Test
    public void fullMeasureRestCannotBeDiscarded() {
        var s = score(true, true, true);
        assertSame(s, trim(s));
    }

    @Test
    public void missingMeterProofRetainsTail() {
        var s = score(true, false, false);
        assertSame(s, trim(s));
    }

    @Test
    public void missingKeyProofRetainsTail() {
        var s = score(false, true, false);
        assertSame(s, trim(s));
    }

    @Test
    public void separateLastSystemRetained() {
        var s = score(true, true, false);
        var p = CourtesyBoundaryInkTest.page();
        assertSame(
                s,
                VerifiedCourtesyBoundary.trim(
                        s,
                        new MeterChangeDetector.Crop(138, 95, 151, 115, 95, 13),
                        p.labels(),
                        p.gray(),
                        220,
                        120));
    }
}

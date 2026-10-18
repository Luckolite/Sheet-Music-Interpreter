// Copyright 2026 Music Sheets contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original accepted header inside a first written bar; no score pixels or OCR assumptions. */
public final class InternalOpeningHeaderRestTest {
    static final int W = 800, H = 360;
    static final MeasureRegion M = new MeasureRegion(.02f, .98f, .16f, .88f);
    static final ScoreMeterChange METER = new ScoreMeterChange(0, 12, 8);

    static MeterChangeDetector.Crop header(int left, int right, int top) {
        return new MeterChangeDetector.Crop(left, top - 3, right, top + 67, top, 16);
    }

    static ScorePageInterpretation run(String mode) {
        byte[] g =
                WholeRestClassificationControlsTest.frame(false, 542, mode.equals("dot") ? 1 : 0);
        var before = g.clone();
        var literal =
                WholeRestClassifier.detect(
                                g,
                                W,
                                H,
                                List.of(M),
                                List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                                List.of(),
                                List.of())
                        .get(0)
                        .event();
        assertFalse("Baseline raster is a noncentered literal body", literal.isFullMeasure());
        List<MeterChangeDetector.Crop> headers = List.of(header(200, 300, 100));
        if (mode.equals("absent")) headers = List.of();
        if (mode.equals("foreign")) headers = List.of(header(200, 300, 220));
        if (mode.equals("late")) headers = List.of(header(560, 600, 100));
        if (mode.equals("conflict"))
            headers = List.of(header(200, 300, 100), header(210, 310, 100));
        List<ScoreMeterChange> source =
                mode.equals("meterconflict")
                        ? List.of(new ScoreMeterChange(0, 4, 4))
                        : List.of(METER);
        var notes =
                mode.equals("sounding")
                        ? List.of(WholeRestClassificationControlsTest.note(700, 150, -1))
                        : List.<ScoreNoteEvent>of();
        var s =
                new ScorePageInterpretation(
                        List.of(M),
                        notes,
                        1,
                        List.of(),
                        List.of(),
                        List.of(METER),
                        List.of(literal));
        var out =
                WholeRestMeterContext.apply(
                        s,
                        g,
                        W,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                        source,
                        headers);
        assertArrayEquals(before, g);
        return out;
    }

    @Test
    public void acceptedMeterInsideFirstBarCentersCompletePlate() {
        assertTrue(run("valid").rests().get(0).isFullMeasure());
    }

    @Test
    public void noAcceptedHeaderDoesNotCreateWholeMeasure() {
        assertFalse(run("absent").rests().get(0).isFullMeasure());
    }

    @Test
    public void foreignStaffHeaderDoesNotBorrowGeometry() {
        assertFalse(run("foreign").rests().get(0).isFullMeasure());
    }

    @Test
    public void followingHeaderCannotOwnEarlierPlate() {
        assertFalse(run("late").rests().get(0).isFullMeasure());
    }

    @Test
    public void conflictingHeaderBoundsRemainUnresolved() {
        assertFalse(run("conflict").rests().get(0).isFullMeasure());
    }

    @Test
    public void changedMeterInvalidatesHeader() {
        assertFalse(run("meterconflict").rests().get(0).isFullMeasure());
    }

    @Test
    public void soundingLaneRemainsLiteral() {
        assertFalse(run("sounding").rests().get(0).isFullMeasure());
    }

    @Test
    public void augmentationDotRetainsLiteralValue() {
        var r = run("dot").rests().get(0);
        assertFalse(r.isFullMeasure());
        assertEquals(6, r.durationBeats(), 0);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original malformed-staff and clipped-measure controls for standalone compatibility. */
public class PolyphonicHalfRestsBoundsTest {
    static SixteenthRestDetector.Detection read(
            SixteenthRestDetector.Staff staff, MeasureRegion measure) {
        var p = new PolyphonicHalfRestsTest.Page(true, true);
        var n =
                List.of(
                        new ScoreNoteEvent(
                                        0,
                                        310f / 640,
                                        7,
                                        staff.index(),
                                        staff.count(),
                                        108f / 400,
                                        false,
                                        0,
                                        1)
                                .withStemDirection(1));
        return PolyphonicHalfRests.detectWithDots(
                p.gray, 640, 400, List.of(measure), List.of(staff), n);
    }

    @Test
    public void negativeStaffIndexDoesNotProduceAnInvalidRest() {
        assertTrue(
                read(
                                new SixteenthRestDetector.Staff(100, 164, 16, -1, 1),
                                new MeasureRegion(0, 1, .1f, .9f))
                        .rests()
                        .isEmpty());
    }

    @Test
    public void zeroStaffCountDoesNotProduceAnInvalidRest() {
        assertTrue(
                read(
                                new SixteenthRestDetector.Staff(100, 164, 16, 0, 0),
                                new MeasureRegion(0, 1, .1f, .9f))
                        .rests()
                        .isEmpty());
    }

    @Test
    public void collapsedStaffBoundsDoNotProduceARest() {
        assertTrue(
                read(
                                new SixteenthRestDetector.Staff(100, 100, 16, 0, 1),
                                new MeasureRegion(0, 1, .1f, .9f))
                        .rests()
                        .isEmpty());
    }

    @Test
    public void aMeasureEdgeCannotCropACompleteRectangleIntoExistence() {
        assertTrue(
                read(
                                new SixteenthRestDetector.Staff(100, 164, 16, 0, 1),
                                new MeasureRegion(.49f, 1, .1f, .9f))
                        .rests()
                        .isEmpty());
    }

    @Test
    public void aMeasureVerticalFrameCannotClipTheRestBody() {
        assertTrue(
                read(
                                new SixteenthRestDetector.Staff(100, 164, 16, 0, 1),
                                new MeasureRegion(0, 1, .1f, .235f))
                        .rests()
                        .isEmpty());
    }
}

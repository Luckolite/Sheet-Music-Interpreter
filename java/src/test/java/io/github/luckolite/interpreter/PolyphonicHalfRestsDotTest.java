// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original dotted-rest and note-owned-dot controls. */
public class PolyphonicHalfRestsDotTest {
    @Test
    public void independentDottedHalfRestRetainsItsThreeBeats() {
        var page = new PolyphonicHalfRestsTest.Page(true, true);
        page.oval(336, 94, 3, 3);
        var r = page.target();
        assertEquals(1, r.size());
        assertEquals(3, r.get(0).durationBeats(), 0);
    }

    @Test
    public void independentDoubleDottedHalfRestRetainsItsThreeAndAHalfBeats() {
        var page = new PolyphonicHalfRestsTest.Page(true, true);
        page.oval(336, 94, 3, 3);
        page.oval(348, 94, 3, 3);
        var r = page.target();
        assertEquals(1, r.size());
        assertEquals(3.5, r.get(0).durationBeats(), 0);
    }

    @Test
    public void dotRecognitionDoesNotRewriteSourcePixels() {
        var page = new PolyphonicHalfRestsTest.Page(true, true);
        page.oval(336, 94, 3, 3);
        var before = page.gray.clone();
        page.target();
        assertArrayEquals(before, page.gray);
    }

    @Test
    public void aNeighboringWrittenNoteRetainsItsOwnDot() {
        var page = new PolyphonicHalfRestsTest.Page(true, true);
        page.oval(336, 94, 3, 3);
        page.oval(336, 108, 11, 7);
        page.extraNotes.add(new ScoreNoteEvent(0, 336f / 640, 7, 0, 1, 108f / 400, false, 1, 1));
        var r = page.target();
        assertEquals(1, r.size());
        assertEquals(2, r.get(0).durationBeats(), 0);
    }

    @Test
    public void recoveredRestRetainsTheDotRecord() {
        var page = new PolyphonicHalfRestsTest.Page(true, true);
        page.oval(336, 94, 3, 3);
        var n =
                java.util.List.of(
                        new ScoreNoteEvent(0, 310f / 640, 7, 0, 1, 108f / 400, false, 0, 1)
                                .withStemDirection(1));
        var r =
                PolyphonicHalfRests.detectWithDots(
                        page.gray,
                        640,
                        400,
                        java.util.List.of(new MeasureRegion(0, 1, .1f, .9f)),
                        java.util.List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                        n);
        assertEquals(1, r.rests().size());
        assertEquals(1, r.dots().size());
        assertEquals(r.rests().get(0), r.dots().get(0).rest());
        assertEquals(336, r.dots().get(0).x(), 0);
    }
}

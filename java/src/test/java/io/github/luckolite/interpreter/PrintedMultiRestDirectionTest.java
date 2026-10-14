// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedMultiRestDirectionTest {
    static final int W = 500, H = 260;
    static final MeasureRegion M = new MeasureRegion(.2f, .8f, .35f, .70f);
    static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1);

    byte[] raster(boolean bar, boolean rails) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        if (rails)
            for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 480; x++) g[y * W + x] = 0;
        if (bar) for (int y = 128; y <= 136; y++) for (int x = 170; x <= 330; x++) g[y * W + x] = 0;
        return g;
    }

    ScorePageInterpretation page(int copies, List<ScoreNoteEvent> notes) {
        return new ScorePageInterpretation(
                Collections.nCopies(copies, M),
                notes,
                0,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 3, 4)),
                List.of(),
                List.of(),
                List.of());
    }

    PlayingTechniqueDetector.Word word(float x, boolean above) {
        return new PlayingTechniqueDetector.Word(
                "a tempo", x, above ? .27f : .73f, x + .12f, above ? .31f : .77f);
    }

    ScorePageInterpretation apply(
            ScorePageInterpretation p, PlayingTechniqueDetector.Word word, byte[] gray) {
        return ScoreExpressionDetector.apply(p, List.of(word), List.of(STAFF), gray, W, H);
    }

    @Test
    public void directionBelowExpandedRestStartsAtFirstBar() {
        var p = page(4, List.of());
        var r = apply(p, word(.22f, false), raster(true, true));
        assertEquals(1, r.expressiveEvents().size());
        var e = r.expressiveEvents().get(0);
        assertEquals(ScoreExpressiveEvent.Kind.A_TEMPO, e.kind());
        assertEquals(0, e.start().orElseThrow().measureIndex());
        assertEquals(0, e.start().orElseThrow().quarterBeatOffset(), 0);
        assertEquals(p.rests(), r.rests());
        assertEquals(p.notes(), r.notes());
    }

    @Test
    public void directionAboveExpandedRestHasSameOwner() {
        assertEquals(
                1,
                apply(page(4, List.of()), word(.22f, true), raster(true, true))
                        .expressiveEvents()
                        .size());
    }

    @Test
    public void ordinaryEmptyBarIsNotMultiRest() {
        assertTrue(
                apply(page(1, List.of()), word(.22f, false), raster(true, true))
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void repeatedGeometryWithoutRestInkCannotOwnDirection() {
        assertTrue(
                apply(page(4, List.of()), word(.22f, false), raster(false, true))
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void missingPrintedRailsRejectsOwner() {
        assertNull(
                PrintedMultiRestDirection.find(
                        page(4, List.of()),
                        List.of(STAFF),
                        word(.22f, false),
                        raster(true, false),
                        W,
                        H));
    }

    @Test
    public void lateTextCannotPretendToStartAtLeadingBar() {
        assertNull(
                PrintedMultiRestDirection.find(
                        page(4, List.of()),
                        List.of(STAFF),
                        word(.65f, false),
                        raster(true, true),
                        W,
                        H));
    }

    @Test
    public void noteInAnyExpandedBarRejectsSilence() {
        var n =
                new ScoreNoteEvent(
                        2, .5f, 4, 0, 1, .5f, false, 0, 0, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 1);
        assertNull(
                PrintedMultiRestDirection.find(
                        page(4, List.of(n)),
                        List.of(STAFF),
                        word(.22f, false),
                        raster(true, true),
                        W,
                        H));
    }

    @Test
    public void laterLogicalCopiesCannotClaimOnset() {
        assertTrue(
                Double.isNaN(
                        PrintedMultiRestDirection.provedOnset(page(4, List.of()), 1, 0, 1, .5f)));
    }

    @Test
    public void sourceArraysRemainUnchanged() {
        var g = raster(true, true);
        var copy = g.clone();
        apply(page(4, List.of()), word(.22f, false), g);
        assertArrayEquals(copy, g);
    }

    @Test
    public void pageOffsetPreservesLogicalOwner() {
        var event =
                apply(page(4, List.of()), word(.22f, false), raster(true, true))
                        .expressiveEvents()
                        .get(0);
        var shifted = ScoreDynamicContinuation.offsetEvidence(event, 7, 2);
        assertEquals(7, shifted.start().orElseThrow().measureIndex());
        assertEquals(2, shifted.evidence().get(0).pageIndex());
        assertTrue(shifted.targetEventId().orElseThrow().startsWith("expression-rest-column:7:"));
    }
}

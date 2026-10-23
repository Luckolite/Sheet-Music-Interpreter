// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class HairpinArrivalOriginalTest {
    static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(50, 90, 10, 0, 1);
    static final List<MeasureRegion> BARS =
            List.of(new MeasureRegion(.1f, .5f, .1f, .6f), new MeasureRegion(.51f, .9f, .1f, .6f));

    static PrintedHairpinArrival.Level level(
            float left,
            float right,
            int bar,
            float position,
            boolean column,
            PlayingTechniqueDetector.Staff owner) {
        return new PrintedHairpinArrival.Level(
                new PlayingTechniqueDetector.Word("f", left, .7f, right, .8f),
                owner,
                bar,
                position,
                column);
    }

    static PrintedHairpinArrival.Arrival resolve(PrintedHairpinArrival.Level... levels) {
        return PrintedHairpinArrival.resolve(List.of(levels), STAFF, 300, 0, .48f, BARS, 1000, 200);
    }

    @Test
    public void nearbyGlyphBodyOwnsArrivalEvenWhenHeadIsFarther() {
        assertEquals(
                new PrintedHairpinArrival.Point(0, .625f),
                resolve(level(.32f, .36f, 0, .625f, true, STAFF)).column().orElseThrow());
    }

    @Test
    public void nextBarOnSamePhysicalRowCanSupplyArrival() {
        var bars =
                List.of(
                        new MeasureRegion(.1f, .3f, .1f, .6f),
                        new MeasureRegion(.31f, .7f, .1f, .6f));
        assertEquals(
                new PrintedHairpinArrival.Point(1, .1f),
                PrintedHairpinArrival.resolve(
                                List.of(level(.32f, .36f, 1, .1f, true, STAFF)),
                                STAFF,
                                300,
                                0,
                                .95f,
                                bars,
                                1000,
                                200)
                        .column()
                        .orElseThrow());
    }

    @Test
    public void unanchoredHeldLevelBlocksInventedArrival() {
        var r = resolve(level(.32f, .36f, 0, .7f, false, STAFF));
        assertTrue(r.observed());
        assertTrue(r.column().isEmpty());
    }

    @Test
    public void unknownFirstLevelCannotBeSkippedForLaterOne() {
        var r =
                resolve(
                        level(.31f, .33f, 0, .6f, false, STAFF),
                        level(.33f, .36f, 0, .7f, true, STAFF));
        assertTrue(r.observed());
        assertTrue(r.column().isEmpty());
    }

    @Test
    public void overlappingBodyCannotBeSkippedForLaterLevel() {
        var r =
                resolve(
                        level(.29f, .33f, 0, .6f, false, STAFF),
                        level(.33f, .36f, 0, .7f, true, STAFF));
        assertTrue(r.observed());
        assertTrue(r.column().isEmpty());
    }

    @Test
    public void otherPartCannotBorrowGlyph() {
        var s = new PlayingTechniqueDetector.Staff(50, 90, 10, 1, 2);
        assertFalse(resolve(level(.32f, .36f, 0, .7f, true, s)).observed());
    }

    @Test
    public void anotherSystemWithSameLaneCannotBorrowGlyph() {
        var s = new PlayingTechniqueDetector.Staff(130, 170, 10, 0, 1);
        assertFalse(resolve(level(.32f, .36f, 0, .7f, true, s)).observed());
    }

    @Test
    public void distantGlyphCannotExtendWedge() {
        assertFalse(resolve(level(.35f, .39f, 0, .8f, true, STAFF)).observed());
    }

    @Test
    public void endpointBeforeExistingStopCannotBeAcquired() {
        assertFalse(resolve(level(.32f, .36f, 0, .4f, true, STAFF)).observed());
    }

    @Test
    public void tiedGlyphStartsWithDifferentColumnsAreAmbiguous() {
        var r =
                resolve(
                        level(.32f, .36f, 0, .625f, true, STAFF),
                        level(.32f, .36f, 0, .65f, true, STAFF));
        assertTrue(r.observed());
        assertTrue(r.column().isEmpty());
    }

    @Test
    public void duplicateSameDestinationIsNotAnExtraArrival() {
        var l = level(.32f, .36f, 0, .625f, true, STAFF);
        assertEquals(
                new PrintedHairpinArrival.Point(0, .625f), resolve(l, l).column().orElseThrow());
    }

    @Test
    public void overlappingUnprovedBodyBlocksBoundaryFallback() {
        var r = resolve(level(.28f, .33f, 0, .7f, true, STAFF));
        assertTrue(r.observed());
        assertTrue(r.column().isEmpty());
    }

    @Test
    public void noLiteralAllowsSeparateBoundaryProof() {
        var r = resolve();
        assertFalse(r.observed());
        assertTrue(r.column().isEmpty());
    }
}

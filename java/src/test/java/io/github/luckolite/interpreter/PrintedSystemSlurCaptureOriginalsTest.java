// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Both producer signatures preserve ordinary outputs and add only independently joined bows. */
public class PrintedSystemSlurCaptureOriginalsTest {
    private static PrintedSlurPageCapture.Result capture(
            PrintedSystemSlurJoinOriginalsTest.Field f, boolean notes) {
        return PrintedSlurPageCapture.captureGeometry(
                9, 4, f.gray, null, f.w, f.h, f.frames, notes ? f.notes : null);
    }

    private static void paired(boolean above, int scale, float phase) {
        var f = new PrintedSystemSlurJoinOriginalsTest.Field(above, scale, phase);
        byte[] before = f.gray.clone();
        var legacy = capture(f, false);
        var after = capture(f, true);
        assertFalse(legacy.limitReached());
        assertFalse(after.limitReached());
        assertTrue(legacy.source().candidates().isEmpty());
        assertEquals(1, after.source().candidates().size());
        var span = after.source().candidates().get(0);
        assertEquals(0, span.start().noteIndex());
        assertEquals(3, span.end().noteIndex());
        assertEquals(2, span.evidence().size());
        assertArrayEquals(before, f.gray);
    }

    @Test
    public void completeUpperProducerRetainsTheSystemContinuation() {
        paired(true, 1, 0);
    }

    @Test
    public void completeLowerProducerRetainsTheSystemContinuation() {
        paired(false, 1, 0);
    }

    @Test
    public void doubleDensityProducerRetainsTwoPieceEvidence() {
        paired(true, 2, 0);
    }

    @Test
    public void fractionalPhaseProducerRetainsPhysicalOwners() {
        paired(false, 1, .5f);
    }

    @Test
    public void absentRawBoundaryKeepsTheCompleteLegacyOutput() {
        var f = new PrintedSystemSlurJoinOriginalsTest.Field(true, 1, 0);
        f.erase(738, 155, 742, 221);
        assertLegacyOutputAndDeclinedEvidence(f);
    }

    @Test
    public void mismatchedStaffSourceIsRejectedByTheCapturePath() {
        var f = new PrintedSystemSlurJoinOriginalsTest.Field(true, 1, 0);
        var n = f.notes.get(3);
        f.notes.set(
                3,
                new ScoreNoteEvent(
                        1,
                        n.positionInMeasure(),
                        n.staffStep(),
                        1,
                        2,
                        n.pageY(),
                        false,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        1));
        assertLegacyOutputAndDeclinedEvidence(f);
    }

    private static void assertLegacyOutputAndDeclinedEvidence(
            PrintedSystemSlurJoinOriginalsTest.Field f) {
        byte[] before = f.gray.clone();
        var legacy = capture(f, false);
        var result = capture(f, true);
        assertEquals(legacy.source(), result.source());
        assertEquals(legacy.statuses(), result.statuses());
        assertEquals(legacy.limitReached(), result.limitReached());
        assertFalse(result.limitReached());
        assertTrue(result.source().spans().isEmpty());
        assertTrue(result.source().candidates().isEmpty());
        assertTrue(legacy.declined().isEmpty());
        assertEquals(2, result.declined().size());
        var expectedStatuses = List.of(
                PrintedSlurOwners.Status.NO_END_OWNER,
                PrintedSlurOwners.Status.NO_START_OWNER);
        int[][] expectedCaps = {{678, 728}, {102, 266}};
        for (int i = 0; i < result.declined().size(); i++) {
            var declined = result.declined().get(i);
            assertEquals("printed-page-9-arc-" + i, declined.id());
            assertSame(f.frames.get(i), declined.frame());
            assertEquals(expectedStatuses.get(i), declined.status());
            var bow = declined.bow();
            assertTrue(bow.above());
            assertFalse(bow.asymmetric());
            assertEquals(expectedCaps[i][0], bow.points().get(0).x());
            assertEquals(expectedCaps[i][1], bow.points().get(bow.points().size() - 1).x());
            int previousX = -1;
            for (var point : bow.points()) {
                assertTrue(point.x() > previousX);
                assertTrue(point.x() >= declined.frame().left());
                assertTrue(point.x() <= declined.frame().right());
                assertTrue(point.y() >= declined.frame().top());
                assertTrue(point.y() <= declined.frame().bottom());
                previousX = point.x();
            }
            var bound = PrintedSlurOwners.bind(
                    declined.id(), 9, bow, declined.frame().heads(),
                    f.gray, f.w, f.h, declined.frame().track());
            assertEquals(expectedStatuses.get(i), bound.status());
            assertTrue(bound.span().isEmpty());
        }
        assertArrayEquals(before, f.gray);
    }

    private static void timed(boolean above) {
        var f = new PrintedSystemSlurJoinOriginalsTest.Field(above, 1, 0);
        var frames = new ArrayList<PrintedSlurPageCapture.Frame<PrintedSlurOwners.Head>>();
        for (var original : f.frames) {
            var heads = new ArrayList<PrintedSlurOwners.Head>();
            for (var head : original.heads())
                heads.add(
                        new PrintedSlurOwners.Head(
                                head.owner(),
                                head.x(),
                                head.y(),
                                0,
                                1,
                                1,
                                head.owner() * 100L,
                                head.owner() == 2 ? 62 : 60));
            frames.add(
                    new PrintedSlurPageCapture.Frame<>(
                            original.left(),
                            original.top(),
                            original.right(),
                            original.bottom(),
                            original.track(),
                            heads));
        }
        var legacy = PrintedSlurPageCapture.capture(9, 4, f.gray, null, f.w, f.h, frames, Set.of());
        var result =
                PrintedSlurPageCapture.capture(
                        9, 4, f.gray, null, f.w, f.h, frames, Set.of(), f.notes);
        assertTrue(legacy.source().candidates().isEmpty());
        assertEquals(1, result.source().candidates().size());
        assertEquals(2, result.source().candidates().get(0).evidence().size());
    }

    @Test
    public void timedUpperCaptureRetainsGeometryUntilAssembly() {
        timed(true);
    }

    @Test
    public void timedLowerCaptureRetainsGeometryUntilAssembly() {
        timed(false);
    }
}

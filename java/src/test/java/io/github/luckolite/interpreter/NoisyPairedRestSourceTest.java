// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Two originally drawn bulbs and their complete tail retain the printed sixteenth value. */
public final class NoisyPairedRestSourceTest {
    @Test
    public void detachedSeededNoiseCannotEraseTheConstructedPairedBulbRest() {
        var fixture = RestProjectionParityTest.fixture(34);
        byte[] original = fixture.gray().clone();
        var detection = RestProjectionParityTest.detect(fixture);
        assertEquals(detection.toString(), 1, detection.rests().size());
        var rest = detection.rests().get(0);
        assertEquals(0, rest.measureIndex());
        assertEquals(0, rest.staffIndex());
        assertEquals(1, rest.staffCount());
        assertEquals(.25, rest.durationBeats(), 0);
        // The original source draws bulbs at rows99..101 and113..115, with
        // the complete diagonal spanning97..135. Expected bounds come from ink.
        assertEquals(116 / 260.0, rest.pageY(), 1e-6);
        assertEquals(39 / 260.0, rest.pageHeight(), 1e-6);
        assertEquals((195 / 400.0 - .02) / .96, rest.positionInMeasure(), 1e-6);
        assertTrue(detection.dots().isEmpty());
        assertArrayEquals(original, fixture.gray());
    }
}

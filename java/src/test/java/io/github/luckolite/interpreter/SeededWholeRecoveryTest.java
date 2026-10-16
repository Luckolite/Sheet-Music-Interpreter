// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original Bravura two-voice layout with deliberately fragmented model labels. */
public class SeededWholeRecoveryTest {
    static CrossedWholeHeadTest.Page fragmented(boolean whole, boolean seed) throws Exception {
        var p = CrossedWholeHeadTest.page(whole);
        for (int y = 58; y <= 85; y++)
            for (int x = 95; x <= 145; x++)
                if (p.labels[y * 400 + x] == 2) p.labels[y * 400 + x] = 0;
        if (seed)
            for (int y = 71; y <= 73; y++)
                for (int x = 119; x <= 121; x++) p.labels[y * 400 + x] = 2;
        return p;
    }

    @Test
    public void tinyWholeSeedRestoresIndependentVoice() throws Exception {
        var n = fragmented(true, true).notes();
        assertEquals(
                n.toString(), 1, n.stream().filter(x -> x.unbeamedDurationBeats() == 4).count());
        assertEquals(3, n.size());
    }

    @Test
    public void recoveredWholeKeepsPitch() throws Exception {
        var n = fragmented(true, true).notes();
        var whole =
                n.stream().filter(x -> x.unbeamedDurationBeats() == 4).findFirst().orElseThrow();
        assertEquals(9, whole.staffStep());
    }

    @Test
    public void companionEighthsRemainIntact() throws Exception {
        var n = fragmented(true, true).notes();
        assertEquals(2, n.stream().filter(x -> x.beamCount() == 1).count());
    }

    @Test
    public void unseededRawOvalIsNotInvented() throws Exception {
        assertEquals(2, fragmented(true, false).notes().size());
    }

    @Test
    public void halfOpeningDoesNotBecomeWhole() throws Exception {
        assertFalse(
                fragmented(false, true).notes().stream()
                        .anyMatch(x -> x.unbeamedDurationBeats() == 4));
    }

    @Test
    public void completeWholeIsNotDuplicated() throws Exception {
        var n = CrossedWholeHeadTest.page(true).notes();
        assertEquals(3, n.size());
        assertEquals(1, n.stream().filter(x -> x.unbeamedDurationBeats() == 4).count());
    }

    @Test
    public void rawAndLabelsRemainUnchanged() throws Exception {
        var p = fragmented(true, true);
        var a = p.gray.clone();
        var b = p.labels.clone();
        p.notes();
        assertArrayEquals(a, p.gray);
        assertArrayEquals(b, p.labels);
    }
}

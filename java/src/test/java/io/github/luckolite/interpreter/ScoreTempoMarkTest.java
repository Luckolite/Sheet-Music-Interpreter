// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreTempoMarkTest {
    @Test
    public void eighthMarkDisplaysPulseRateRatherThanQuarterRate() {
        var mark = ScoreTempoMark.from(new ScoreTempoChange(0, 0, 77.5, .5));
        assertEquals(0xE1D7, mark.glyph());
        assertEquals("eighth", mark.musicXmlUnit());
        assertEquals(0, mark.dots());
        assertEquals("155", mark.text());
    }

    @Test
    public void dottedAndDoubleDottedMarksRetainPulseAndFractionalRate() {
        var dotted = ScoreTempoMark.from(new ScoreTempoChange(2, .25f, 181.5, 1.5));
        assertEquals("quarter", dotted.musicXmlUnit());
        assertEquals(1, dotted.dots());
        assertEquals("121", dotted.text());
        var doubled = ScoreTempoMark.from(new ScoreTempoChange(0, 0, 43.75, .875));
        assertEquals("eighth", doubled.musicXmlUnit());
        assertEquals(2, doubled.dots());
        assertEquals("50", doubled.text());
        assertEquals("81.25", ScoreTempoMark.from(new ScoreTempoChange(0, 0, 81.25)).text());
    }

    @Test
    public void unsupportedPulseUsesEquivalentExactQuarterMark() {
        var mark = ScoreTempoMark.from(new ScoreTempoChange(0, 0, 81.25, .3));
        assertEquals("quarter", mark.musicXmlUnit());
        assertEquals(0, mark.dots());
        assertEquals("81.25", mark.text());
    }
    @Test public void repeatingPulseRateUsesEquivalentExactQuarterMark() {
        var mark = ScoreTempoMark.from(new ScoreTempoChange(0, 0, 80, .75));
        assertEquals("quarter", mark.musicXmlUnit());
        assertEquals(0, mark.dots());
        assertEquals("80", mark.text());

        var exactDotted = ScoreTempoMark.from(new ScoreTempoChange(0, 0, 60, .75));
        assertEquals("eighth", exactDotted.musicXmlUnit());
        assertEquals(1, exactDotted.dots());
        assertEquals("80", exactDotted.text());
    }
}

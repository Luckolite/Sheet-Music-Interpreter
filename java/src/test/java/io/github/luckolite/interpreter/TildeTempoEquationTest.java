// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric equation controls; no source-score pixels. */
public class TildeTempoEquationTest {
    private TempoUnitNoteTest.Page page() {
        var p = new TempoUnitNoteTest.Page();
        p.erase(164, 62, 32, 27);
        // Small lower-case c and a, then a separate period.
        p.rect(165, 70, 3, 18, (byte) 5);
        p.rect(165, 70, 11, 3, (byte) 5);
        p.rect(165, 85, 11, 3, (byte) 5);
        p.rect(183, 73, 3, 15, (byte) 5);
        p.rect(183, 73, 12, 3, (byte) 5);
        p.rect(183, 85, 12, 3, (byte) 5);
        p.rect(192, 70, 3, 18, (byte) 5);
        p.rect(201, 85, 3, 3, (byte) 5);
        // Separate connected 9 and 6 glyphs, farther from equals than the ordinary search.
        p.rect(230, 62, 12, 3, (byte) 5);
        p.rect(230, 62, 3, 15, (byte) 5);
        p.rect(230, 74, 12, 3, (byte) 5);
        p.rect(239, 62, 3, 27, (byte) 5);
        p.rect(230, 86, 12, 3, (byte) 5);
        p.rect(250, 62, 3, 27, (byte) 5);
        p.rect(250, 62, 12, 3, (byte) 5);
        p.rect(250, 74, 12, 3, (byte) 5);
        p.rect(250, 86, 12, 3, (byte) 5);
        p.rect(259, 74, 3, 15, (byte) 5);
        return p;
    }

    private List<ScoreTempoChange> read(TempoUnitNoteTest.Page p, String text) throws Exception {
        return read(p, text, 98, 40, 265, 98);
    }

    private List<ScoreTempoChange> read(
            TempoUnitNoteTest.Page p, String text, int left, int top, int right, int bottom)
            throws Exception {
        return read(
                p,
                text,
                left,
                top,
                right,
                bottom,
                List.of(new MeasureRegion(20f / p.w, 460f / p.w, 100f / p.h, 164f / p.h)));
    }

    private List<ScoreTempoChange> read(
            TempoUnitNoteTest.Page p,
            String text,
            int left,
            int top,
            int right,
            int bottom,
            List<MeasureRegion> measures)
            throws Exception {
        var words =
                List.of(
                        new PlayingTechniqueDetector.Word(
                                text,
                                left / (float) p.w,
                                top / (float) p.h,
                                right / (float) p.w,
                                bottom / (float) p.h));
        byte[] before = p.gray.clone();
        List<ScoreTempoChange> out;
        out = TempoChangeDetector.detectWithWords(List.of(), words, p.gray, p.w, p.h, measures);
        assertArrayEquals(before, p.gray);
        return out;
    }

    private TempoUnitNoteTest.Page tildePage() {
        var p = page();
        p.erase(160, 69, 48, 22);
        p.rect(174, 76, 5, 3, (byte) 5);
        p.rect(179, 73, 7, 3, (byte) 5);
        p.rect(186, 76, 7, 3, (byte) 5);
        p.rect(193, 73, 5, 3, (byte) 5);
        return p;
    }

    @Test
    public void croppedTildeEquationRetainsOpticalQuarter() throws Exception {
        var out = read(tildePage(), "= ~96", 131, 40, 265, 98);
        assertEquals(1, out.size());
        assertEquals(96, out.get(0).bpm(), 0);
    }

    @Test
    public void croppedTildeStillNeedsPrintedBeat() throws Exception {
        var p = tildePage();
        p.erase(98, 40, 28, 59);
        assertTrue(read(p, "= ~96", 131, 40, 265, 98).isEmpty());
    }

    @Test
    public void stemAtBoundaryOwnsFollowingBar() throws Exception {
        var p = tildePage();
        var out =
                read(
                        p,
                        "= ~96",
                        131,
                        40,
                        265,
                        98,
                        List.of(
                                new MeasureRegion(20f / p.w, 108f / p.w, 100f / p.h, 164f / p.h),
                                new MeasureRegion(116f / p.w, 460f / p.w, 100f / p.h, 164f / p.h)));
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).measureIndex());
        assertEquals(0, out.get(0).positionInMeasure(), 0);
    }
}

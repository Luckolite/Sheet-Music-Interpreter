// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ShortBarWholeRestTest {
    static final int W = WholeRestClassificationControlsTest.W,
            H = WholeRestClassificationControlsTest.H;

    static byte[] raster(int dots) {
        return WholeRestClassificationControlsTest.frame(false, 580, dots);
    }

    static ScoreRestEvent rest() {
        return WholeRestClassificationControlsTest.one(raster(0), 1, List.of(), List.of()).event();
    }

    static ScorePageInterpretation score(
            List<ScoreMeterChange> meters, List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests) {
        return new ScorePageInterpretation(
                List.of(WholeRestClassificationControlsTest.M),
                notes,
                1,
                List.of(),
                List.of(),
                meters,
                rests,
                List.of(),
                List.of());
    }

    static ScorePageInterpretation apply(ScorePageInterpretation s, byte[] g) {
        return WholeRestMeterContext.apply(
                s, g, W, H, List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)));
    }

    @Test
    public void shortSilentBarResolvesOffCenterWholeGlyph() {
        var r = rest();
        assertFalse(r.isFullMeasure());
        var s = score(List.of(new ScoreMeterChange(0, 1, 4)), List.of(), List.of(r));
        var fixed = apply(s, raster(0)).rests().get(0);
        assertTrue(fixed.isFullMeasure());
        assertEquals(1, fixed.resolvedDurationBeats(1), 0);
        assertEquals(r.positionInMeasure(), fixed.positionInMeasure(), 0);
    }

    @Test
    public void threeQuarterBarUsesActualSpan() {
        var fixed =
                apply(
                                score(
                                        List.of(new ScoreMeterChange(0, 3, 4)),
                                        List.of(),
                                        List.of(rest())),
                                raster(0))
                        .rests()
                        .get(0);
        assertTrue(fixed.isFullMeasure());
        assertEquals(3, fixed.resolvedDurationBeats(3), 0);
    }

    @Test
    public void noMeterCannotInventFullBar() {
        var s = score(List.of(), List.of(), List.of(rest()));
        assertSame(s, apply(s, raster(0)));
    }

    @Test
    public void fourBeatBarLeavesLiteralSemantics() {
        var s = score(List.of(new ScoreMeterChange(0, 4, 4)), List.of(), List.of(rest()));
        assertSame(s, apply(s, raster(0)));
    }

    @Test
    public void conflictingMeterAbstains() {
        var s =
                score(
                        List.of(new ScoreMeterChange(0, 1, 4), new ScoreMeterChange(0, 3, 4)),
                        List.of(),
                        List.of(rest()));
        assertSame(s, apply(s, raster(0)));
    }

    @Test
    public void soundingVoicePreventsShortcut() {
        var s =
                score(
                        List.of(new ScoreMeterChange(0, 1, 4)),
                        List.of(WholeRestClassificationControlsTest.note(200, 150, -1)),
                        List.of(rest()));
        assertSame(s, apply(s, raster(0)));
    }

    @Test
    public void nearbyDotIsNotSilentlyDiscarded() {
        var s = score(List.of(new ScoreMeterChange(0, 1, 4)), List.of(), List.of(rest()));
        assertSame(s, apply(s, raster(1)));
    }

    @Test
    public void missingPlateCannotReclassifyLegacyRecord() {
        var s = score(List.of(new ScoreMeterChange(0, 1, 4)), List.of(), List.of(rest()));
        var g = raster(0);
        WholeRestClassificationControlsTest.box(g, 565, 117, 595, 122, 255);
        assertSame(s, apply(s, g));
    }

    @Test
    public void secondRestIsNotAssumedAnotherVoice() {
        var r = rest();
        var extra = new ScoreRestEvent(0, .15f, r.pageY(), r.pageHeight(), 0, 1, 1);
        var s = score(List.of(new ScoreMeterChange(0, 1, 4)), List.of(), List.of(r, extra));
        assertSame(s, apply(s, raster(0)));
    }
}

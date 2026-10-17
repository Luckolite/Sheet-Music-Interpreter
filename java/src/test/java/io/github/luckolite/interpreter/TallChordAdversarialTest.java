// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original independent quarter/eighth shafts with actual white gaps around an intervening staff rule. */
public class TallChordAdversarialTest {
    static void setRhythm(TallCommonShaftDiagnostic d, int i, float duration, int beam)
            throws Exception {
        var n = d.notes.get(i);
        var e = TallCommonShaftChordTest.event(d, n);
        var m = d.detected.getDeclaredMethod("head");
        m.setAccessible(true);
        var c = d.detected.getDeclaredConstructors()[0];
        c.setAccessible(true);
        var v =
                new ScoreNoteEvent(
                                e.measureIndex(),
                                e.positionInMeasure(),
                                e.staffStep(),
                                e.staffIndex(),
                                e.staffCount(),
                                e.pageY(),
                                false,
                                0,
                                beam,
                                2,
                                duration)
                        .withStemDirection(-1);
        d.notes.set(i, c.newInstance(v, m.invoke(n), (float) d.G));
    }

    static TallCommonShaftDiagnostic independentVoices(boolean staff) throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        setRhythm(d, 0, 1f, 0);
        setRhythm(d, 1, 1f, 0);
        setRhythm(d, 2, 0f, 1);
        // Upper quarter-chord shaft ends178; the lower eighth shaft begins at its head186.
        // There is no common printed shaft across179..185.
        for (int y = 179; y <= 185; y++)
            for (int x = 100; x <= 101; x++) {
                d.gray[y * d.W + x] = (byte) 255;
                d.labels[y * d.W + x] = 0;
            }
        if (staff)
            for (int y = 181; y <= 183; y++) for (int x = 20; x <= 300; x++) d.pixel(x, y, 4);
        return d;
    }

    @Test
    public void independentQuarterAndEighthRemainDistinctAcrossPaperGap() throws Exception {
        assertEquals(0, independentVoices(false).run().get(0).beamCount());
    }

    @Test
    public void interveningStaffRuleCannotConnectSeparateQuarterAndEighthShafts() throws Exception {
        assertEquals(0, independentVoices(true).run().get(0).beamCount());
    }

    @Test
    public void partialSemanticMaskDoesNotBreakActualContinuousCommonShaft() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        for (int y = 130; y <= 145; y++) for (int x = 100; x <= 101; x++) d.labels[y * d.W + x] = 0;
        assertTrue(d.run().stream().allMatch(n -> n.beamCount() == 1));
    }

    @Test
    public void trueTallQuarterChordWithoutExternalBeamStaysQuarter() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        for (int i = 0; i < 3; i++) TallCommonShaftChordTest.replace(d, i, 0, 1f, 0);
        for (int y = 235; y <= 240; y++)
            for (int x = 102; x < 180; x++) {
                d.gray[y * d.W + x] = (byte) 255;
                d.labels[y * d.W + x] = 0;
            }
        assertEquals(List.of(0, 0, 0), d.run().stream().map(ScoreNoteEvent::beamCount).toList());
    }
}

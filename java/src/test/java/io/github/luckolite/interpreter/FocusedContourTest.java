// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent original controls; frozen older fixtures are not changed. */
public final class FocusedContourTest {
    static int detect(byte[] g, ScorePageInterpretation p, int gap) {
        var s =
                ScoreArpeggioDetector.apply(
                        p,
                        g,
                        260,
                        250,
                        List.of(
                                new PlayingTechniqueDetector.Staff(100 - 4 * gap, 100, gap, 0, 2),
                                new PlayingTechniqueDetector.Staff(152, 152 + 4 * gap, gap, 1, 2)),
                        0);
        assertEquals(p.notes(), s.notes());
        return s.expressiveEvents().size();
    }

    @Test
    public void translatedExactConnectedEscapeStillCannotBind() {
        var raw = RuleEscapeProbe.scene();
        var g = ExtentChallenge.paper();
        for (int y = 0; y < 250; y++)
            for (int x = 0; x < 240; x++) g[y * 260 + x + 20] = raw[y * 260 + x];
        var n = new ArrayList<ScoreNoteEvent>();
        for (int step : new int[] {1, 4, 8})
            n.add(
                    new ScoreNoteEvent(
                            0, 100f / 260, step, 0, 2, (100 - step * 8f) / 250, false, 0, 0, 0, 2));
        assertEquals(
                0,
                detect(
                        g,
                        new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), n, 1),
                        16));
    }

    @Test
    public void leftwardConnectedEscapeCannotBind() {
        var g = ExtentChallenge.paper();
        for (int y = 25; y <= 99; y++) {
            double p = Math.floorMod(y - 19, 16) / 16.;
            RuleEscapeProbe.segment(g, 60 + (int) Math.round(p < .5 ? p * 20 : (1 - p) * 20), y);
        }
        for (int y = 100; y <= 102; y++)
            for (int x = 10; x <= 220; x++) ExtentChallenge.pixel(g, x, y, 100);
        RuleEscapeProbe.segment(g, 59, 100);
        RuleEscapeProbe.segment(g, 57, 101);
        RuleEscapeProbe.segment(g, 55, 102);
        for (int y = 103; y <= 170; y++) {
            double p = (y - 103) % 16 / 16.;
            RuleEscapeProbe.segment(g, 54 - (int) Math.round(p < .5 ? p * 20 : (1 - p) * 20), y);
        }
        assertEquals(0, detect(g, RuleEscapeProbe.page(), 16));
    }

    @Test
    public void farIndependentWaveSharingRailPreservesFiniteGlyph() {
        var g = ExtentChallenge.finite();
        ExtentChallenge.wave(g, 80, 160, 65);
        for (int y = 52; y <= 100; y += 12)
            for (int x = 10; x <= 220; x++) ExtentChallenge.pixel(g, x, y, 100);
        assertEquals(1, detect(g, ExtentChallenge.page(), 12));
    }

    @Test
    public void nearbyIndependentContourOutsideQuietConePreservesFiniteGlyph() {
        var g = ExtentChallenge.finite();
        for (int y = 111; y <= 160; y++) {
            double p = (y - 111) % 12 / 12.;
            RuleEscapeProbe.segment(g, 78 + (int) Math.round(p < .5 ? p * 12 : (1 - p) * 12), y);
        }
        assertEquals(1, detect(g, ExtentChallenge.page(), 12));
    }

    @Test
    public void nearCompetingContourInsideQuietConeConservativelyAbstains() {
        var g = ExtentChallenge.finite();
        for (int y = 111; y <= 160; y++) RuleEscapeProbe.segment(g, 64, y);
        assertEquals(0, detect(g, ExtentChallenge.page(), 12));
    }

    @Test
    public void endpointHorizontalBranchCannotBeSilentlyClipped() {
        var g = ExtentChallenge.finite();
        for (int x = 62; x <= 100; x++) ExtentChallenge.pixel(g, x, 108, 25);
        assertEquals(0, detect(g, ExtentChallenge.page(), 12));
    }
}

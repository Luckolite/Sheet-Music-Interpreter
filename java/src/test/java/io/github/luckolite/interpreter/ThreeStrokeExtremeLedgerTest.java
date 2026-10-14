// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original seven-ledger head with a complete or deliberately incomplete rule chain. */
public class ThreeStrokeExtremeLedgerTest {
    List<ScoreNoteEvent> notes(boolean complete, boolean hollow) {
        return notes(complete, hollow, false);
    }

    List<ScoreNoteEvent> notes(boolean complete, boolean hollow, boolean broadShade) {
        int w = 480, h = 384;
        byte[] l = new byte[w * h], g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int y = 160; y <= 224; y += 16)
            for (int x = 20; x < 460; x++) {
                l[y * w + x] = 4;
                g[y * w + x] = 0;
            }
        for (int y = 42; y <= 54; y++)
            for (int x = 94; x <= 116; x++)
                if (Math.pow((x - 105) / 11., 2) + Math.pow((y - 48) / 6., 2) <= 1) {
                    l[y * w + x] = 2;
                    g[y * w + x] =
                            (byte)
                                    (hollow
                                                    && Math.pow((x - 105) / 5., 2)
                                                                    + Math.pow((y - 48) / 3., 2)
                                                            < 1
                                            ? 255
                                            : 0);
                }
        for (int y = 48; y < 160; y += 16)
            if (complete || y <= 80)
                for (int x = 87; x <= 123; x++) {
                    if (l[y * w + x] != 2) l[y * w + x] = 5;
                    g[y * w + x] = 0;
                }
        if (broadShade)
            for (int y = 78; y < 150; y++) for (int x = 80; x <= 130; x++) g[y * w + x] = 120;
        if (!hollow)
            for (int y = 48; y <= 100; y++) {
                l[y * w + 94] = 1;
                g[y * w + 94] = 0;
            }
        for (int y = 186; y <= 198; y++)
            for (int x = 289; x <= 311; x++)
                if (Math.pow((x - 300) / 11., 2) + Math.pow((y - 192) / 6., 2) <= 1) {
                    l[y * w + x] = 2;
                    g[y * w + x] = 0;
                }
        for (int y = 150; y < 192; y++) {
            l[y * w + 311] = 1;
            g[y * w + 311] = 0;
        }
        return OmrScoreInterpreter.extract(
                        l, g, w, h, List.of(new MeasureRegion(.03f, .97f, .4f, .7f)))
                .stream()
                .filter(n -> n.positionInMeasure() < .4f)
                .toList();
    }

    @Test
    public void seventhLedgerToneIsNotClipped() {
        var n = notes(true, false);
        assertEquals(1, n.size());
        assertEquals(22, n.get(0).staffStep());
    }

    @Test
    public void highWholeWithCompleteLedgersSurvives() {
        var n = notes(true, true);
        assertEquals(1, n.size());
        assertEquals(4, n.get(0).unbeamedDurationBeats(), 0);
    }

    @Test
    public void missingInnerRulesDoNotPromoteText() {
        assertTrue(notes(false, false).isEmpty());
    }

    @Test
    public void broadDarkDecorationCannotSupplyInterveningLedgerRules() {
        assertTrue(notes(false, false, true).isEmpty());
    }
}

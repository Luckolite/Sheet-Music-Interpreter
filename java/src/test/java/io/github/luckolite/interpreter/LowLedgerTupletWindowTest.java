// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently drawn ledger group, staff and numeral. */
public class LowLedgerTupletWindowTest {
    private void rect(byte[] g, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) g[yy * 500 + xx] = 0;
    }

    private List<ScoreNoteEvent> read(
            boolean staff, boolean beam, boolean numeral, boolean closed, int step) {
        byte[] g = new byte[500 * 400];
        Arrays.fill(g, (byte) 255);
        if (staff) for (int y = 200; y <= 280; y += 20) rect(g, 100, y, 301, 1);
        for (int x : new int[] {200, 250, 300}) {
            rect(g, x - 5, 327, 11, 6);
            rect(g, x + 5, 288, 2, 43);
        }
        if (beam) {
            rect(g, 205, 288, 102, 6);
            rect(g, 205, 300, 102, 6);
        }
        if (numeral)
            for (int y = 0; y < 24; y++)
                for (int x = 0; x < 14; x++) {
                    boolean dark =
                            y < 3 && x >= 3 && x <= 11
                                    || y >= 2 && y < 10 && x >= 11
                                    || y >= 10 && y < 13 && x >= 5 && x <= 12
                                    || y >= 13 && y < 22 && x >= 11
                                    || y >= 21 && x >= 2 && x <= 11
                                    || y >= 2 && y <= 4 && x <= 3
                                    || y >= 19 && y <= 21 && x <= 2
                                    || closed && x < 3;
                    if (dark) g[(166 + y) * 500 + 243 + x] = 0;
                }
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {200, 250, 300})
            notes.add(
                    new ScoreNoteEvent(
                                    0, (x - 100) / 300f, step, 0, 1, 330 / 400f, false, 0, 2, 2, 0f)
                            .withStemDirection(1));
        return TripletRhythmDetector.apply(
                notes, List.of(new MeasureRegion(.2f, .8f, .45f, .85f)), g, 500, 400);
    }

    private void ratios(List<ScoreNoteEvent> notes, int expected) {
        for (var n : notes) assertEquals(expected, n.tupletDivisor());
    }

    @Test
    public void staffAndBeamOwnDistantThree() {
        ratios(read(true, true, true, false, -5), 3);
    }

    @Test
    public void absentStaffAbstains() {
        ratios(read(false, true, true, false, -5), 1);
    }

    @Test
    public void absentBeamAbstains() {
        ratios(read(true, false, true, false, -5), 1);
    }

    @Test
    public void absentNumeralAbstains() {
        ratios(read(true, true, false, false, -5), 1);
    }

    @Test
    public void closedEightAbstains() {
        ratios(read(true, true, true, true, -5), 1);
    }

    @Test
    public void ordinaryStaffPitchCannotUseLedgerExtension() {
        ratios(read(true, true, true, false, 0), 1);
    }
}

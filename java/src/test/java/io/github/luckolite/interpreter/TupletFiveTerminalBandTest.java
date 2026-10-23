// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original block-curve five, with a terminal return distinct from its penultimate row. */
public class TupletFiveTerminalBandTest {
    private byte[] five() {
        byte[] p = new byte[18 * 27];
        Arrays.fill(p, (byte) 255);
        for (int y = 0; y < 27; y++) {
            int left, right;
            if (y <= 3) {
                left = 4;
                right = 16;
            } else if (y <= 10) {
                left = 3;
                right = 5;
            } else if (y == 11) {
                left = 3;
                right = 13;
            } else if (y == 12) {
                left = 3;
                right = 14;
            } else if (y <= 16) {
                left = 14;
                right = 17;
            } else if (y <= 21) {
                left = 15;
                right = 17;
            } else if (y == 22) {
                left = 14;
                right = 17;
            } else if (y == 23) {
                left = 13;
                right = 16;
            } else if (y == 24) {
                left = 0;
                right = 15;
            } else if (y == 25) {
                left = 1;
                right = 14;
            } else {
                left = 2;
                right = 13;
            }
            for (int x = left; x <= right; x++) p[y * 18 + x] = 0;
            if (y == 22 || y == 23) for (int x = 0; x <= y - 20; x++) p[y * 18 + x] = 0;
        }
        return p;
    }

    private boolean shape(byte[] p) throws Exception {
        var m =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "looksLikeFive",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        byte[] prior = p.clone();
        boolean result = (boolean) m.invoke(null, p, 18, 0, 0, 18, 27);
        assertArrayEquals(prior, p);
        return result;
    }

    @Test
    public void theFinalRasterBandProvesTheLowerBowlReturn() throws Exception {
        assertTrue(shape(five()));
    }

    @Test
    public void aFootWithoutTheBowlReturnIsNotFive() throws Exception {
        byte[] p = five();
        for (int y = 25; y <= 26; y++) for (int x = 2; x <= 17; x++) p[y * 18 + x] = 0;
        assertFalse(shape(p));
    }

    @Test
    public void aMirroredFiveCannotBorrowTheOriginalFoot() throws Exception {
        byte[] p = five(), m = p.clone();
        for (int y = 0; y < 27; y++)
            for (int x = 0; x < 18; x++) m[y * 18 + 17 - x] = p[y * 18 + x];
        assertFalse(shape(m));
    }

    @Test
    public void anAccentWithoutACapAndStemDoesNotBecomeFive() throws Exception {
        byte[] p = new byte[18 * 27];
        Arrays.fill(p, (byte) 255);
        for (int y = 0; y < 27; y++)
            for (int d = 0; d < 2; d++) {
                int x = 16 - y * 13 / 26 + d;
                if (x < 18) p[y * 18 + x] = 0;
            }
        assertFalse(shape(p));
    }

    private List<ScoreNoteEvent> read(boolean number) {
        int w = 600, h = 360;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int row : new int[] {130, 144, 158, 172, 186})
            for (int x = 20; x < 580; x++) g[row * w + x] = 0;
        for (int i = 0; i < 5; i++) {
            int x = 220 + i * 40, y = 170 - i * 4;
            for (int yy = y - 6; yy <= y + 6; yy++)
                for (int xx = x - 9; xx <= x + 9; xx++)
                    if ((xx - x) * (xx - x) / 81d + (yy - y) * (yy - y) / 36d <= 1)
                        g[yy * w + xx] = 0;
            for (int yy = y; yy <= 224; yy++) {
                g[yy * w + x - 8] = 0;
                g[yy * w + x - 9] = 0;
            }
            notes.add(
                    new ScoreNoteEvent(
                                    0,
                                    (x / (float) w - .1f) / .8f,
                                    2 + i,
                                    0,
                                    1,
                                    y / (float) h,
                                    false,
                                    0,
                                    3)
                            .withStemDirection(-1));
        }
        for (int x = 211; x <= 372; x++)
            for (int row : new int[] {204, 212, 220})
                for (int y = row; y < row + 5; y++) g[y * w + x] = 0;
        if (number) {
            byte[] p = five();
            for (int y = 0; y < 27; y++)
                for (int x = 0; x < 18; x++) if (p[y * 18 + x] == 0) g[(239 + y) * w + 284 + x] = 0;
        }
        byte[] prior = g.clone();
        var result =
                TripletRhythmDetector.apply(
                        notes, List.of(new MeasureRegion(.1f, .9f, 124f / h, 236f / h)), g, w, h);
        assertArrayEquals(prior, g);
        return result;
    }

    @Test
    public void aPrintedFiveMarksAllFiveThirtySecondNotes() {
        var n = read(true);
        assertEquals(5, n.size());
        for (var note : n) {
            assertEquals(5, note.tupletDivisor());
            assertEquals(4, note.tupletNormalNotes());
            assertEquals(.1, ScoreNoteTiming.writtenDurationBeats(note), .000001);
        }
    }

    @Test
    public void anOrdinaryFiveNoteBeamKeepsBinaryTiming() {
        for (var note : read(false)) assertEquals(1, note.tupletDivisor());
    }
}

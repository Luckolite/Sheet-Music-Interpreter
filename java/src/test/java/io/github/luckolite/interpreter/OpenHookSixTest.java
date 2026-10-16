// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original geometric lower-bowl/diagonal glyphs and synthetic six-note groups. */
public class OpenHookSixTest {
    private static byte[] glyph(boolean upperLoop, boolean hook) {
        byte[] g = new byte[18 * 28];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < 28; y++)
            for (int x = 0; x < 18; x++) {
                double outer = Math.pow((x - 8) / 6., 2) + Math.pow((y - 18) / 7., 2);
                double inner = Math.pow((x - 8) / 4., 2) + Math.pow((y - 18) / 5., 2);
                boolean ink = outer <= 1 && inner >= 1;
                if (hook && y >= 1 && y <= 17 && Math.abs(x - (14 - y * .72)) <= 1.2) ink = true;
                if (upperLoop) {
                    outer = Math.pow((x - 8) / 6., 2) + Math.pow((y - 7) / 6., 2);
                    inner = Math.pow((x - 8) / 4., 2) + Math.pow((y - 7) / 4., 2);
                    ink |= outer <= 1 && inner >= 1;
                }
                // Original curved terminal: a short return above the descending hook.
                if (hook
                        && ((y == 1 && x >= 13 && x <= 17)
                                || (y == 2 && x >= 16 && x <= 17)
                                || (y >= 3 && y <= 5 && x == 17))) ink = true;
                if (ink) g[y * 18 + x] = 0;
            }
        return g;
    }

    private static byte[] tight(byte[] source) {
        int left = 18, right = 0, top = 28, bottom = 0;
        for (int y = 0; y < 28; y++)
            for (int x = 0; x < 18; x++)
                if (source[y * 18 + x] == 0) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
        int w = right - left + 1, h = bottom - top + 1;
        byte[] result = new byte[w * h + 2];
        result[0] = (byte) w;
        result[1] = (byte) h;
        for (int y = 0; y < h; y++)
            System.arraycopy(source, (top + y) * 18 + left, result, 2 + y * w, w);
        return result;
    }

    private static boolean shape(boolean upperLoop, boolean hook) {
        byte[] t = tight(glyph(upperLoop, hook));
        int w = t[0], h = t[1];
        return TupletSixGlyph.matches(Arrays.copyOfRange(t, 2, t.length), w, 0, 0, w, h);
    }

    private static List<ScoreNoteEvent> detect(int count, boolean six, int beams) {
        return detect(count, six, beams, false);
    }

    private static List<ScoreNoteEvent> detect(
            int count, boolean six, int beams, boolean splitBeams) {
        int w = 800, h = 240;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        if (six) {
            byte[] t = tight(glyph(false, true));
            int ww = t[0], hh = t[1];
            for (int y = 0; y < hh; y++)
                for (int x = 0; x < ww; x++) g[(145 + y) * w + 334 + x] = t[2 + y * ww + x];
        }
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < count; i++)
            notes.add(
                    new ScoreNoteEvent(
                            0,
                            (240 + i * 40) / 800f,
                            2,
                            0,
                            1,
                            .4f,
                            false,
                            0,
                            beams,
                            2,
                            0,
                            1,
                            0,
                            0,
                            30,
                            false,
                            0,
                            false,
                            1));
        if (splitBeams) {
            for (int i = 0; i < count; i++) {
                int x = 240 + i * 40;
                for (int y = 56; y <= 96; y++)
                    for (int dx = -1; dx <= 1; dx++) g[y * w + x + dx] = 0;
            }
            for (int[] segment : new int[][] {{240, 280}, {320, 440}})
                for (int y = 54; y <= 58; y++)
                    for (int x = segment[0]; x <= segment[1]; x++) g[y * w + x] = 0;
        }
        return TripletRhythmDetector.apply(
                notes, List.of(new MeasureRegion(0, 1, .2f, .6f)), g, w, h);
    }

    @Test
    public void descendingHookWithLowerBowlIsSix() {
        assertTrue(shape(false, true));
    }

    @Test
    public void closedEightIsNotSix() {
        assertFalse(shape(true, false));
    }

    @Test
    public void isolatedLowerOvalIsNotSix() {
        assertFalse(shape(false, false));
    }

    @Test
    public void completeSixAttacksReceivePrintedDivisor() {
        assertTrue(detect(6, true, 2).stream().allMatch(n -> n.tupletDivisor() == 6));
    }

    @Test
    public void missingNumeralKeepsOrdinaryDurations() {
        assertTrue(detect(6, false, 2).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void incompleteFiveAttacksAreNotSextuplets() {
        assertTrue(detect(5, true, 2).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void longerUnbrokenRunIsNotTruncatedToSix() {
        assertTrue(detect(7, true, 2).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void quartersCannotUseUnbracketedSix() {
        assertTrue(detect(6, true, 0).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void sixSixteenthsFillOneQuarterBeat() {
        var notes = detect(6, true, 2);
        assertEquals(
                1, notes.stream().mapToDouble(ScoreNoteTiming::writtenDurationBeats).sum(), 1e-9);
    }

    @Test
    public void noteCopiesRetainOctaveShift() {
        assertTrue(detect(6, true, 2).stream().allMatch(n -> n.octaveShift() == 1));
    }

    @Test
    public void unsupportedDivisorsStillNormalize() {
        var n = new ScoreNoteEvent(0, .2f, 2, 0, 1, .4f, false, 0, 2, 2, 0, 8);
        assertEquals(1, n.tupletDivisor());
    }

    @Test
    public void sixCannotJoinSeparateTwoAndFourBeamGroups() {
        assertTrue(detect(6, true, 2, true).stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void sextupletGridRetainsTwelfthBeatPrecision() {
        assertEquals(1. / 12, ScoreNoteTiming.rhythmicGrid(detect(6, true, 2)), 1e-9);
    }

    @Test
    public void sixAttacksAdvanceByExactlyOneSixthBeat() {
        var notes = detect(6, true, 2);
        double first = ScoreNoteTiming.beatInMeasure(notes.get(0), notes, 4);
        for (int i = 0; i < notes.size(); i++)
            assertEquals(
                    first + i / 6., ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4), 1e-9);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original synthetic low-to-high guitar headers and string geometry, Apache-2.0. */
public class TabTuningPitchRegressionTest {
    private static final int W = 1000, H = 1000;

    private static TablatureDecoder.Staff staff(int strings) {
        return new TablatureDecoder.Staff(
                200, 20, -1, List.of(), List.of(50f, 950f), strings, List.of());
    }

    private static List<TablatureDecoder.Word> openStrings(int strings) {
        var words = new ArrayList<TablatureDecoder.Word>();
        for (int s = 0; s < strings; s++) {
            float cy = (200 + s * 20) / (float) H;
            float x = .15f + s * .10f;
            words.add(new TablatureDecoder.Word("0", x, cy - .006f, x + .01f, cy + .006f));
        }
        return words;
    }

    private static List<Integer> pitches(List<TablatureDecoder.Staff> rows) {
        var score =
                TablatureDecoder.apply(
                        new ScorePageInterpretation(List.of(), List.of()), rows, W, H);
        return score.notes().stream()
                .map(
                        n -> {
                            int d = n.clefBottomDiatonic() + n.staffStep();
                            int[] pcs = {0, 2, 4, 5, 7, 9, 11};
                            return (Math.floorDiv(d, 7) + 1 + n.octaveShift()) * 12
                                    + pcs[Math.floorMod(d, 7)]
                                    + n.writtenAccidental();
                        })
                .toList();
    }

    @Test
    public void detachedFlatGlyphsRemainPartOfTheTuningHeader() {
        var words = new ArrayList<TablatureDecoder.Word>();
        words.add(new TablatureDecoder.Word("Tuning:", .05f, .03f, .13f, .05f));
        String[] letters = {"E", "A", "D", "G", "B", "E"};
        for (int i = 0; i < letters.length; i++) {
            float x = .17f + i * .09f;
            words.add(new TablatureDecoder.Word(letters[i], x, .034f, x + .015f, .049f));
            words.add(new TablatureDecoder.Word("♭", x + .017f, .025f, x + .026f, .051f));
        }
        words.addAll(openStrings(6));
        var rows = TablatureDecoder.withWords(List.of(staff(6)), words, W, H);
        assertEquals(List.of(63, 58, 54, 49, 44, 39), rows.get(0).tuning());
        assertEquals(List.of(63, 58, 54, 49, 44, 39), pitches(rows));
    }

    @Test
    public void detachedUnicodeAndSharpAccidentalsCanIncludeOctaves() {
        assertEquals(
                List.of(63, 58, 54, 49, 44, 39),
                TabTuning.parse("Tuning: E ♭2 A ♭2 D ♭3 G ♭3 B ♭3 E ♭4", 6));
        assertEquals(
                List.of(65, 60, 56, 51, 46, 41, 36),
                TabTuning.parse("Tuning: B ♯1 E ♯2 A ♯2 D ♯3 G ♯3 B ♯3 E ♯4", 7));
        assertEquals(
                List.of(65, 60, 56, 51, 46, 41),
                TabTuning.parse("Tuning: E #2 A #2 D #3 G #3 B #3 E #4", 6));
    }

    @Test
    public void explicitBSharpAndCFlatPreserveTheirOctaveBoundary() {
        assertEquals(
                List.of(64, 59, 55, 48, 45, 40), TabTuning.parse("Tuning: E2 A2 B#2 G3 B3 E4", 6));
        assertEquals(
                List.of(62, 59, 53, 48, 43, 38), TabTuning.parse("Tuning: D2 G2 C3 F3 Cb4 D4", 6));
        assertEquals(
                List.of(64, 59, 55, 48, 45, 40, 35),
                TabTuning.parse("Tuning: Cb2 E2 A2 B#2 G3 Cb4 E4", 7));
        var words = new ArrayList<TablatureDecoder.Word>();
        words.add(
                new TablatureDecoder.Word(
                        "Tuning: Cb2 E2 A2 B#2 G3 Cb4 E4", .05f, .03f, .90f, .05f));
        words.addAll(openStrings(7));
        assertEquals(
                List.of(64, 59, 55, 48, 45, 40, 35),
                pitches(TablatureDecoder.withWords(List.of(staff(7)), words, W, H)));
    }

    @Test
    public void naturalBStaysAStringNameAndMalformedReentrantTuningStaysUnsupported() {
        assertEquals(List.of(64, 59, 55, 50, 45, 40), TabTuning.parse("Tuning: E A D G b E", 6));
        assertEquals(
                List.of(64, 59, 55, 50, 45, 40, 35), TabTuning.parse("Tuning: B E A D G B E", 7));
        for (String header :
                List.of(
                        "Tuning: E A D G B",
                        "Tuning: E A D G B E E",
                        "Tuning: E2 A2 D3 G3 B3 E3",
                        "Tuning: E2 A2 D3 G3 Cb4 B3"))
            assertTrue(header, TabTuning.parse(header, 6).isEmpty());
    }
}

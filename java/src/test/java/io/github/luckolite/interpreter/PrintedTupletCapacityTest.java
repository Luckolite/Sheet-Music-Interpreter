// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural engraving; numeral outline reused from OffsetTupletWindowTest. */
public class PrintedTupletCapacityTest {
    static final int W = 1600, H = 240;
    static final String[] THREE = {
        "..#######...",
        ".##########.",
        "###......###",
        "####.....###",
        "####.....###",
        "####.....###",
        ".##.....####",
        ".......####.",
        "......####..",
        "....#####...",
        "....#####...",
        "....#####...",
        "......####..",
        ".......####.",
        "##.....####.",
        "###....####.",
        "###....####.",
        "###....####.",
        ".###....###.",
        "..########..",
        "..########..",
        "....####...."
    };

    static MeasureRegion bar(int a, int b) {
        return new MeasureRegion(a / (float) W, b / (float) W, .25f, .7166667f);
    }

    final List<MeasureRegion> raw =
            List.of(bar(20, 380), bar(400, 760), bar(780, 1140), bar(1160, 1520));
    final List<MeasureRegion> fitted =
            List.of(bar(20, 177), bar(183, 380), raw.get(1), raw.get(2), raw.get(3));
    final List<ScoreNoteEvent> notes = new ArrayList<>();

    PrintedTupletCapacityTest init() {
        for (int i = 0; i < 4; i++)
            notes.add(
                    new ScoreNoteEvent(
                                    0,
                                    (100 + i * 25 - 20) / 360f,
                                    i == 1 ? 12 : 11,
                                    0,
                                    1,
                                    (i == 1 ? 72 : 79) / (float) H,
                                    false,
                                    0,
                                    i < 3 ? 2 : 1,
                                    2,
                                    0)
                            .withStemDirection(-1));
        for (int m = 1; m < 4; m++) {
            notes.add(
                    new ScoreNoteEvent(m, .25f, 11, 0, 1, 79f / H, false, 0, 0, 2, 1)
                            .withStemDirection(-1));
            notes.add(
                    new ScoreNoteEvent(m, .75f, 11, 0, 1, 79f / H, false, 0, 2, 2, 0)
                            .withStemDirection(-1));
        }
        return this;
    }

    byte[] ink(boolean numeral) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y : new int[] {110, 124, 138, 152, 166})
            for (int x = 20; x <= 1520; x++) g[y * W + x] = 0;
        for (int x : new int[] {390, 770, 1150}) for (int y = 110; y <= 166; y++) g[y * W + x] = 0;
        for (int x = 94; x <= 169; x++) for (int y = 112; y <= 115; y++) g[y * W + x] = 0;
        for (int x = 94; x <= 144; x++) for (int y = 104; y <= 107; y++) g[y * W + x] = 0;
        for (int x : new int[] {94, 119, 144, 169})
            for (int y = 72; y <= 115; y++) g[y * W + x] = 0;
        if (numeral)
            for (int y = 0; y < THREE.length; y++)
                for (int x = 0; x < 12; x++)
                    if (THREE[y].charAt(x) == '#') g[(143 + y) * W + 119 + x] = 0;
        return g;
    }

    List<MeasureRegion> result(byte[] g) {
        return PrintedMeasureRhythmGuard.preservePartialWrittenBars(
                raw, fitted, notes, List.of(), g, W, H);
    }

    @Test
    public void ordinaryWrittenCapacityCanPreserveThePrintedBar() throws Exception {
        init();
        assertEquals(raw, result(ink(false)));
    }

    @Test
    public void actualPrintedTupletAcquiredLaterCannotSupplyOrdinaryCapacity() throws Exception {
        init();
        byte[] g = ink(true), before = g.clone();
        var original = List.copyOf(notes);
        var actual = TripletRhythmDetector.withRests(notes, List.of(), raw, g, W, H);
        for (int i = 0; i < 3; i++) {
            assertEquals(1, notes.get(i).tupletDivisor());
            assertEquals(3, actual.notes().get(i).tupletDivisor());
        }
        assertEquals(fitted, result(g));
        assertEquals(original, notes);
        assertArrayEquals(before, g);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original numeral-shaped island crossed by a faint continuous structural shaft. */
public class ShaftCrossedTupletNumeralTest {
    static final int W = 400, H = 500;
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

    static byte[] page(int kind) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        if (kind != 0)
            for (int y = 180; y <= 245; y++) {
                if (kind == 3 && y > 199 || kind == 5 && y >= 190 && y <= 196) continue;
                g[y * W + (kind == 2 ? 196 : 190)] = (byte) (kind == 4 ? 240 : 190);
            }
        for (int y = 0; y < THREE.length; y++)
            for (int x = 0; x < 12; x++)
                if (THREE[y].charAt(x) == '#') g[(200 + y) * W + 180 + x] = 0;
        return g;
    }

    static List<ScoreNoteEvent> notes() {
        var n = new ArrayList<ScoreNoteEvent>();
        for (float x : new float[] {156, 186, 216})
            n.add(
                    new ScoreNoteEvent(0, x / W, 0, 1, 2, 260f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(1));
        n.add(new ScoreNoteEvent(0, 186f / W, 0, 0, 2, 160f / H, false, 0, 0, 2, 2, 1));
        return n;
    }

    static List<ScoreNoteEvent> apply(byte[] g) {
        return TripletRhythmDetector.apply(
                notes(), List.of(new MeasureRegion(0, 1, .06f, .604f)), g, W, H);
    }

    static void expect(int kind, int divisor) {
        var n = apply(page(kind));
        for (int i = 0; i < 3; i++) assertEquals(divisor, n.get(i).tupletDivisor());
        assertEquals(1, n.get(3).tupletDivisor());
    }

    @Test
    public void continuousShaftCannotBecomePrintedNumeral() {
        expect(1, 1);
    }

    @Test
    public void genuineOwnedNumeralRetainsTriplet() {
        expect(0, 3);
    }

    @Test
    public void nearbyShaftOutsideGlyphDoesNotVetoNumeral() {
        expect(2, 3);
    }

    @Test
    public void oneEndedShaftDoesNotProveStructuralCrossing() {
        expect(3, 3);
    }

    @Test
    public void unsupportedLowContrastShaftDoesNotVetoNumeral() {
        expect(4, 3);
    }

    @Test
    public void brokenShaftDoesNotProveStructuralCrossing() {
        expect(5, 3);
    }

    @Test
    public void callerPixelsArePreserved() {
        byte[] g = page(1), before = g.clone();
        apply(g);
        assertArrayEquals(before, g);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompetingPrintedBeamOwnerTest {
    private static final int W = 400, H = 500;

    private static byte[] ink(boolean upperShafts) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 167; y <= 173; y++) for (int x = 108; x <= 228; x++) g[y * W + x] = 0;
        for (int y = 242; y <= 248; y++) for (int x = 132; x <= 252; x++) g[y * W + x] = 0;
        if (upperShafts)
            for (int x : new int[] {108, 228})
                for (int y = 100; y <= 170; y++)
                    for (int dx = -1; dx <= 1; dx++) g[y * W + x + dx] = 0;
        for (int x : new int[] {132, 252})
            for (int y = 245; y <= 300; y++) for (int dx = -1; dx <= 1; dx++) g[y * W + x + dx] = 0;
        return g;
    }

    private static List<ScoreNoteEvent> heads() {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int staff = 0; staff < 2; staff++)
            for (float x : new float[] {120, 240})
                n.add(
                        new ScoreNoteEvent(
                                        0,
                                        x / W,
                                        0,
                                        staff,
                                        2,
                                        (staff == 0 ? 100f : 300f) / H,
                                        false,
                                        0,
                                        1,
                                        2,
                                        0,
                                        1)
                                .withStemDirection(staff == 0 ? -1 : 1));
        return n;
    }

    private static boolean owns(int index, boolean upperShafts) throws Exception {
        Class<?> glyph = Class.forName(TripletRhythmDetector.class.getName() + "$Glyph");
        var ctor = glyph.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        ctor.setAccessible(true);
        var method =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "beamOwnsNumeral",
                        glyph,
                        ScoreNoteEvent.class,
                        List.class,
                        MeasureRegion.class,
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class);
        method.setAccessible(true);
        var notes = heads();
        return (boolean)
                method.invoke(
                        null,
                        ctor.newInstance(162, 190, 174, 211),
                        notes.get(index),
                        notes,
                        new MeasureRegion(0, 1, 0, 1),
                        ink(upperShafts),
                        W,
                        H,
                        120f,
                        240f,
                        20f);
    }

    @Test
    public void nearerTrebleBeamOwnsNumeralBetweenTwoAttachedBeams() throws Exception {
        assertTrue(owns(0, true));
        assertFalse(owns(2, true));
    }

    @Test
    public void unattachedForeignBeamCannotVetoAnAttachedBassBeam() throws Exception {
        assertFalse(owns(0, false));
        assertTrue(owns(2, false));
    }
}

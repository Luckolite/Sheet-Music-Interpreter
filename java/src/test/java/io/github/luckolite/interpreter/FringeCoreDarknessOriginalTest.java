// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original certified body whose literal fringe must not replace its dark core's evidence. */
public class FringeCoreDarknessOriginalTest {
    static final int W = 1000, H = 1000;

    static byte[] scene() {
        byte[] a = ClosedDotFringeOriginalTest.image(false);
        stripe(a);
        return a;
    }

    static void stripe(byte[] a) {
        for (int x = 470; x <= 493; x++) for (int y = 217; y <= 218; y++) a[y * W + x] = 60;
    }

    static int marks(byte[] a, float y) {
        return ClosedDotFringeOriginalTest.detect(a, 481.5f, y) & 4;
    }

    static void coreTone(byte[] a, int tone) {
        for (int y = 230; y <= 233; y++)
            for (int x = 480; x <= 483; x++)
                if ((a[y * W + x] & 255) < 155) a[y * W + x] = (byte) tone;
        a[231 * W + 481] = 100;
    }

    @Test
    public void originalCoreDarknessSurvivesUncurvedShelter() {
        assertEquals(4, marks(scene(), 260));
        assertEquals(8, ClosedDotFringeOriginalTest.detect(scene(), 481.5f, 260) & ~4);
    }

    @Test
    public void existing130BoundaryIsExact() {
        byte[] a = scene();
        coreTone(a, 130);
        assertEquals(4, marks(a, 260));
        coreTone(a, 131);
        assertEquals(0, marks(a, 260));
    }

    @Test
    public void genuineCurvedRoofStillVetoes() {
        byte[] a = ClosedDotFringeOriginalTest.image(false);
        for (int x = 471; x <= 492; x++) {
            int y = 213 + Math.round((x - 481.5f) * (x - 481.5f) * .075f);
            a[y * W + x] = a[(y + 1) * W + x] = 30;
        }
        assertEquals(0, marks(a, 260));
    }

    @Test
    public void distantUnownedBodyStillVetoes() {
        assertEquals(0, marks(scene(), 271.5f));
    }

    @Test
    public void secondDarkBasinCannotSupplyEvidence() {
        byte[] a = ClosedDotFringeOriginalTest.image(true);
        stripe(a);
        a[228 * W + 481] = 100;
        assertEquals(0, marks(a, 260));
    }

    @Test
    public void ordinaryUncertifiedBodyKeepsWholeMedianVeto() {
        byte[] a = CompleteRawStaccatoRoutingTest.scene(13, 150, 30);
        stripe(a);
        assertEquals(0, marks(a, 260));
    }

    @Test
    public void grayCoreAbove130StillVetoes() {
        byte[] a = scene();
        coreTone(a, 140);
        assertEquals(0, marks(a, 260));
    }

    @Test
    public void paperGrainWithoutStrongCoreCannotAcquireEvidence() {
        byte[] a = scene();
        for (int y = 230; y <= 233; y++)
            for (int x = 480; x <= 483; x++)
                if ((a[y * W + x] & 255) < 155) a[y * W + x] = (byte) 140;
        assertEquals(0, marks(a, 260));
    }

    @Test
    public void noPixelOrLabelMutation() {
        byte[] a = scene(), copy = a.clone(), labels = new byte[a.length];
        assertEquals(
                4,
                NoteArticulationDetector.detect(
                                labels,
                                a,
                                W,
                                H,
                                List.of(
                                        new NoteArticulationDetector.Anchor(
                                                481.5f, 260, 14.25f, 0)))[0]
                        & 4);
        assertArrayEquals(copy, a);
        assertArrayEquals(new byte[a.length], labels);
    }
}

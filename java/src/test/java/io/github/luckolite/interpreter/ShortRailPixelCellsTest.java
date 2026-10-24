// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent closed-cell boundary and literal raw-path controls for short proposals. */
public class ShortRailPixelCellsTest {
    static boolean supported(
            byte[] gray,
            byte[] labels,
            float a,
            float b,
            int minimum,
            PrintedSlurContours.HeadEvidence heads)
            throws Exception {
        int w = 360, h = 320;
        float base = 240, gap = 16;
        Class<?> run = Class.forName(PrintedSlurContours.class.getName() + "$Run"),
                frames = Class.forName(PrintedSlurContours.class.getName() + "$Frames");
        var ctor = run.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object
                first =
                        ctor.newInstance(
                                300,
                                a,
                                (a - base) / gap,
                                gap,
                                (a - base) / gap,
                                (a - base) / gap,
                                List.of()),
                last =
                        ctor.newInstance(
                                302,
                                b,
                                (b - base) / gap,
                                gap,
                                (b - base) / gap,
                                (b - base) / gap,
                                List.of());
        float[] bottoms = new float[w], gaps = new float[w];
        Arrays.fill(bottoms, base);
        Arrays.fill(gaps, gap);
        var frameCtor = frames.getDeclaredConstructors()[0];
        frameCtor.setAccessible(true);
        Object frame = frameCtor.newInstance(bottoms, gaps);
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, base, gap}, new float[] {w - 1, base, gap}));
        Method m =
                PrintedSlurContours.class.getDeclaredMethod(
                        "rawRuleGap",
                        run,
                        run,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        StaffPitchTrack.class,
                        frames,
                        PrintedSlurContours.HeadEvidence.class,
                        int.class);
        m.setAccessible(true);
        return (boolean)
                m.invoke(null, first, last, gray, labels, w, h, track, frame, heads, minimum);
    }

    static byte[] rails() {
        byte[] g = new byte[360 * 320];
        Arrays.fill(g, (byte) 255);
        for (int x = 8; x < 352; x++) for (int r = 0; r < 5; r++) g[(240 - r * 16) * 360 + x] = 60;
        return g;
    }

    @Test
    public void adjacentClosedCellsSupportQuantizedSlope() throws Exception {
        assertTrue(supported(rails(), null, 196, 187, 2, null));
    }

    @Test
    public void slopeBeyondClosedCellsStillRefuses() throws Exception {
        assertFalse(supported(rails(), null, 197, 187, 2, null));
    }

    @Test
    public void oldLongDistanceFloorStaysUnchanged() throws Exception {
        assertFalse(supported(rails(), null, 196, 187, 7, null));
    }

    @Test
    public void paperGapCannotSupplyRailPath() throws Exception {
        var g = rails();
        g[192 * 360 + 301] = (byte) 255;
        assertFalse(supported(g, null, 196, 187, 2, null));
    }

    @Test
    public void isolatedDarkDotCannotProvePrintedRail() throws Exception {
        byte[] g = new byte[360 * 320];
        Arrays.fill(g, (byte) 255);
        g[192 * 360 + 301] = 0;
        assertFalse(supported(g, null, 196, 187, 2, null));
    }

    @Test
    public void semanticHeadInkCannotSupplyMissingPath() throws Exception {
        var g = rails();
        byte[] labels = new byte[g.length];
        for (int y = 189; y <= 194; y++) labels[y * 360 + 301] = OmrMeasurePostProcessor.NOTEHEAD;
        assertFalse(supported(g, labels, 196, 187, 2, null));
    }
}

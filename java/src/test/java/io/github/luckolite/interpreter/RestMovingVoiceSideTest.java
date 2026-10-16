// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original three-bulb contour and independently placed voice shafts. */
public class RestMovingVoiceSideTest {
    static final int W = 480, H = 240;

    static List<ScoreRestEvent> read(int noteY, boolean stem, boolean held, boolean ordinary)
            throws Exception {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 60; y <= 124; y += 16) for (int x = 20; x < 460; x++) g[y * W + x] = 0;
        for (int j = 0; j < 3; j++)
            for (int y = 65 + j * 16; y <= 75 + j * 16; y++)
                for (int x = 164; x <= 182; x++)
                    if (Math.pow((x - (176 - 3 * j)) / 6., 2)
                                    + Math.pow((y - (70 + j * 16)) / 5., 2)
                            <= 1) g[y * W + x] = 0;
        for (int y = 67; y <= 123; y++) {
            int x = 185 - (y - 67) * 14 / 56;
            g[y * W + x] = g[y * W + x + 1] = 0;
        }
        if (stem)
            for (int y = noteY + 6; y <= noteY + 33; y++)
                for (int x = 164; x <= 168; x++) g[y * W + x] = 0;
        var n =
                new ScoreNoteEvent(
                        0,
                        176f / W,
                        0,
                        0,
                        1,
                        noteY / (float) H,
                        false,
                        0,
                        held ? 0 : 1,
                        2,
                        held ? 4 : 0);
        var out = new ArrayList<ScoreRestEvent>();
        var dots = new ArrayList<SixteenthRestDetector.RestDot>();
        boolean[] line = new boolean[90];
        for (int y = 60; y <= 124; y += 16) line[y - 50] = true;
        var m =
                Arrays.stream(SixteenthRestDetector.class.getDeclaredMethods())
                        .filter(x -> x.getName().equals("inspect"))
                        .findFirst()
                        .get();
        m.setAccessible(true);
        byte[] before = g.clone();
        m.invoke(
                null,
                g,
                W,
                H,
                List.of(new MeasureRegion(0, 1, 0, 1)),
                List.of(n),
                new SixteenthRestDetector.Staff(60, 124, 16, 0, 1),
                50,
                139,
                line,
                164,
                186,
                out,
                dots,
                ordinary,
                false,
                false,
                false,
                false,
                false,
                false,
                92f / H,
                false);
        assertArrayEquals(before, g);
        return out;
    }

    @Test
    public void upperHeadDownwardShaftCannotProveLowerMovingVoice() throws Exception {
        assertTrue(read(10, true, false, false).isEmpty());
    }

    @Test
    public void actualLowerMovingVoiceRetainsRaisedRest() throws Exception {
        assertEquals(1, read(160, true, false, false).size());
    }

    @Test
    public void unprovedLowerVoiceDoesNotAuthorizeRest() throws Exception {
        assertTrue(read(160, false, false, false).isEmpty());
    }

    @Test
    public void unprovedUpperVoiceDoesNotAuthorizeRest() throws Exception {
        assertTrue(read(10, false, false, false).isEmpty());
    }

    @Test
    public void independentHeldVoiceBelowRetainsRest() throws Exception {
        assertEquals(1, read(160, false, true, false).size());
    }

    @Test
    public void independentHeldVoiceAboveRetainsRest() throws Exception {
        assertEquals(1, read(10, false, true, false).size());
    }

    @Test
    public void ordinaryRestPreservesItsLowerVoice() throws Exception {
        assertEquals(1, read(160, true, false, true).size());
    }
}

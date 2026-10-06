// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original miniature flag and crossing-slash rasters, without score pixels. */
public class SlashedGraceFlagInkTest {
    static final int W = 240, H = 180;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    public SlashedGraceFlagInkTest() {
        Arrays.fill(gray, (byte) 240);
    }

    private void line(int ax, int ay, int bx, int by, int radius, int value) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(ax + (bx - ax) * i / (float) steps),
                    y = Math.round(ay + (by - ay) * i / (float) steps);
            for (int dy = -radius; dy <= radius; dy++)
                for (int dx = -radius; dx <= radius; dx++)
                    gray[(y + dy) * W + x + dx] = (byte) value;
        }
    }

    private void flag() {
        line(100, 85, 109, 101, 1, 30);
    }

    private void slash() {
        line(94, 106, 111, 89, 1, 30);
    }

    private int count() {
        return SlashedGraceFlagInk.count(gray, W, H, 96, 120, 100, 16);
    }

    @Test
    public void oneFlagIsSeparateFromOppositeSlash() {
        flag();
        slash();
        assertEquals(1, count());
    }

    @Test
    public void noPrintedSlashCannotUseTheRecovery() {
        flag();
        assertEquals(0, count());
    }

    @Test
    public void slashAloneIsNotAFlag() {
        slash();
        assertEquals(0, count());
    }

    @Test
    public void blankPaperIsNotAFlag() {
        assertEquals(0, count());
    }

    @Test
    public void horizontalRulesCannotSupplyBothDirections() {
        for (int y = 72; y <= 120; y += 16) line(60, y, 145, y, 0, 40);
        assertEquals(0, count());
    }

    @Test
    public void faintPaperRetainsIndependentStrokes() {
        Arrays.fill(gray, (byte) 185);
        flag();
        slash();
        assertEquals(1, count());
    }

    @Test
    public void printedPixelsAreNeverModified() {
        flag();
        slash();
        byte[] before = gray.clone();
        count();
        assertArrayEquals(before, gray);
    }

    @Test
    public void invalidGeometryIsRejected() {
        assertEquals(0, SlashedGraceFlagInk.count(gray, W, H, 96, Float.NaN, 100, 16));
        assertEquals(0, SlashedGraceFlagInk.count(gray, W, H, 96, 120, -1, 16));
        assertEquals(0, SlashedGraceFlagInk.count(new byte[1], W, H, 96, 120, 100, 16));
    }

    private int decoded(boolean companion) throws Exception {
        flag();
        slash();
        line(100, 85, 100, 120, 0, 30);
        for (int y = 85; y <= 120; y++) labels[y * W + 100] = 5;
        for (int y = 116; y <= 124; y++)
            for (int x = 91; x <= 101; x++)
                if (Math.pow((x - 96) / 5., 2) + Math.pow((y - 120) / 4., 2) <= 1) {
                    gray[y * W + x] = 30;
                    labels[y * W + x] = 2;
                }
        var hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var cc = hc.getDeclaredConstructors()[0];
        cc.setAccessible(true);
        Object head = cc.newInstance(60, 91, 101, 116, 124, 96f, 120f);
        var sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var cs = sc.getDeclaredConstructor(float.class, float.class, float.class);
        cs.setAccessible(true);
        Object staff = cs.newInstance(56f, 120f, 16f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        hc,
                        sc,
                        List.class);
        m.setAccessible(true);
        Object other = cc.newInstance(60, 123, 133, 116, 124, 128f, 120f);
        return (int)
                m.invoke(
                        null,
                        labels,
                        gray,
                        W,
                        H,
                        head,
                        staff,
                        companion ? List.of(head, other) : List.of(head));
    }

    @Test
    public void decoderUsesSlashedFlagInsteadOfCrossingStrokeBands() throws Exception {
        assertEquals(1, decoded(false));
    }

    @Test
    public void faintCompanionStemCannotForceSolitaryFlagRecovery() throws Exception {
        assertEquals(0, decoded(true));
    }

    @Test
    public void repeatedShadowedFlagCallsDoNotRetainAnEarlierRaster() {
        for (int paper : new int[] {185, 240}) {
            Arrays.fill(gray, (byte) paper);
            flag();
            slash();
            byte[] before = gray.clone();
            assertEquals(1, count());
            assertEquals(1, count());
            assertArrayEquals(before, gray);
            Arrays.fill(gray, (byte) paper);
            before = gray.clone();
            assertEquals(0, count());
            assertArrayEquals(before, gray);
        }
    }

    @Test
    public void aMissingFlagAndRestoredFlagKeepIndependentProbeResults() {
        slash();
        byte[] before = gray.clone();
        assertEquals(0, count());
        assertEquals(0, count());
        assertArrayEquals(before, gray);
        flag();
        before = gray.clone();
        assertEquals(1, count());
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 240);
        before = gray.clone();
        assertEquals(0, count());
        assertArrayEquals(before, gray);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ShadedRailApexTest {
    @Test
    public void darkerCompactBowCanCrossTheOuterRuleOnShadedPaper() {
        int width = 240, height = 220;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 200);
        for (int y = 102; y <= 150; y += 12)
            Arrays.fill(gray, y * width, (y + 1) * width, (byte) 120);
        var track = StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, 150, 12}, new float[] {width - 1, 150, 12}));
        var headEvidence = new PrintedSlurContours.HeadEvidence() {
            public boolean contains(int x, int y) { return false; }
            public boolean closes(PrintedSlurContours.Point cap, int x, int y, boolean first) {
                return false;
            }
        };
        assertTrue(PrintedSlurContours.find(gray, null, width, height, 0, 30, width - 1,
                height - 1, track, null, headEvidence).bows().isEmpty());
        for (int x = 80; x <= 110; x++) {
            double t = (x - 95) / 15.0;
            int y = (int) Math.round(150 - 8 * t * t);
            gray[y * width + x] = 30;
        }
        assertTrue(PrintedSlurContours.find(gray, null, width, height, 0, 30, width - 1,
                height - 1, track).bows().isEmpty());
        var recovered = PrintedSlurContours.find(gray, null, width, height, 0, 30, width - 1,
                height - 1, track, null, headEvidence).bows();
        assertEquals(1, recovered.size());
        assertFalse(recovered.get(0).above());
        assertEquals(80, recovered.get(0).start().x());
        assertEquals(110, recovered.get(0).end().x());
    }
}

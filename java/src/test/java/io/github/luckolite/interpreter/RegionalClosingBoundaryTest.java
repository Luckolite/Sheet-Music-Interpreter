// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RegionalClosingBoundaryTest {
    static final int W = 720, H = 480, EDGE = 650;

    static List<MeasureRegion> run(
            float gap, float slope, int tail, int stemLength, int stemOffset) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 50; x <= EDGE; x++) {
                int y = Math.round(100 + line * 12 + .02f * (x - W * .5f));
                gray[y * W + x] = 40;
                labels[y * W + x] = 4;
                y = Math.round(300 + line * gap + slope * (x - W * .5f));
                if (x < EDGE - tail || x % 12 < 7)
                    gray[y * W + x] = (byte) (x < EDGE - tail ? 40 : 210);
                labels[(300 + line * 12) * W + x] = 4;
            }
        for (int x : new int[] {50, 340, EDGE})
            for (int dy = 0; dy <= Math.round(gap * 4); dy++) {
                int y = Math.round(300 + dy + slope * (x - W * .5f));
                gray[y * W + x] = 40;
                labels[y * W + x] = 1;
            }
        int x = EDGE - 24;
        for (int dy = stemOffset; dy <= stemOffset + stemLength; dy++) {
            int y = Math.round(300 + dy + slope * (x - W * .5f));
            gray[y * W + x] = 40;
            labels[y * W + x] = 1;
        }
        for (int cx : new int[] {150, 400})
            for (int y = 319; y <= 324; y++)
                for (int xx = cx - 5; xx <= cx + 5; xx++) {
                    gray[y * W + xx] = 0;
                    labels[y * W + xx] = 2;
                }
        // A semantic staff-mask bridge creates the need for independent regional rows.
        for (int y = 300; y <= 348; y++) for (int xx = 100; xx <= 115; xx++) labels[y * W + xx] = 4;
        var m =
                OmrMeasurePostProcessor.process(labels, gray, W, H).stream()
                        .filter(a -> a.top() > .5f)
                        .toList();
        return m;
    }

    @Test
    public void strongerRegionalFrameRetainsIndependentlyProvedClosing() {
        var measures = run(12.5f, .003f, 35, 48, 6);
        assertEquals(2, measures.size());
        assertEquals(333, measures.get(0).right() * W, .01);
        assertEquals(347, measures.get(1).left() * W, .01);
        assertEquals(643, measures.get(1).right() * W, .01);
    }
}

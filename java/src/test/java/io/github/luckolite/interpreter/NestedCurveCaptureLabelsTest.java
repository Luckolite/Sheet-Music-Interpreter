// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NestedCurveCaptureLabelsTest {
    static final int W = NestedCurveHeadTest.W, H = NestedCurveHeadTest.H;
    NestedCurveHeadTest fixture = new NestedCurveHeadTest();
    byte[] g = fixture.g;

    void arc(boolean lower) {
        fixture.arc(lower);
    }

    byte[] labels = new byte[W * H];

    public NestedCurveCaptureLabelsTest() {
        arc(false);
        arc(true);
        for (int y = 70; y <= 134; y += 16)
            for (int x = 10; x < W - 10; x++) {
                g[y * W + x] = 0;
                labels[y * W + x] = 4;
            }
        for (int y = 144; y <= 158; y++)
            for (int x = 155; x <= 171; x++)
                if (x == 155 || x == 171 || y == 144 || y == 158) labels[y * W + x] = 2;
    }

    byte[] clean(boolean retained) {
        return OmrScoreInterpreter.printedSlurLabels(
                labels,
                g,
                W,
                H,
                List.of(new MeasureRegion(.025f, .975f, .1f, .9f)),
                retained
                        ? List.of(new PrintedSlurOwners.GeometryHead(0, 163, 151, 0, 1))
                        : List.of());
    }

    int heads(byte[] a) {
        int n = 0;
        for (byte b : a) if (b == 2) n++;
        return n;
    }

    @Test
    public void provedUnretainedIslandStopsMaskingCurve() {
        assertTrue(heads(labels) > 0);
        assertEquals(0, heads(clean(false)));
    }

    @Test
    public void retainedHeadLabelsRemainProtected() {
        assertArrayEquals(labels, clean(true));
    }

    @Test
    public void sourceAndSegmentationStayUnchanged() {
        byte[] a = labels.clone(), b = g.clone();
        clean(false);
        assertArrayEquals(a, labels);
        assertArrayEquals(b, g);
    }

    @Test
    public void absenceOfRawEvidenceCannotClearLabels() {
        Arrays.fill(g, (byte) 255);
        assertArrayEquals(labels, clean(false));
    }
}

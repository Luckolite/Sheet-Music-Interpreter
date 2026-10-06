// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HandwrittenTwoHeadInkTest {
    static final int W = 300, H = 240, G = 16;
    final byte[] gray = new byte[W * H];

    public HandwrittenTwoHeadInkTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void line(int x1, int y1, int x2, int y2) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= n; i++) {
            int x = Math.round(x1 + (x2 - x1) * i / (float) n),
                    y = Math.round(y1 + (y2 - y1) * i / (float) n);
            for (int dy = -2; dy <= 2; dy++)
                for (int dx = -2; dx <= 2; dx++) gray[(y + dy) * W + x + dx] = 0;
        }
    }

    void two() {
        line(100, 92, 110, 87);
        line(110, 87, 126, 88);
        line(126, 88, 132, 96);
        line(132, 96, 130, 109);
        line(130, 109, 103, 130);
        line(103, 130, 135, 130);
    }

    boolean matches() {
        return HandwrittenTwoHeadInk.matches(gray, W, H, 98, 123, 114, 134, G);
    }

    @Test
    public void completeReturningTwoIsRecognized() {
        two();
        assertTrue(matches());
    }

    @Test
    public void upperPartCannotBeRejectedAsHead() {
        two();
        assertFalse(HandwrittenTwoHeadInk.matches(gray, W, H, 120, 87, 132, 96, G));
    }

    @Test
    public void compactOvalIsPreserved() {
        for (int y = 122; y < 135; y++)
            for (int x = 98; x < 117; x++)
                if ((x - 107) * (x - 107) / 81d + (y - 128) * (y - 128) / 36d < 1)
                    gray[y * W + x] = 0;
        assertFalse(matches());
    }

    @Test
    public void stemmedNoteIsPreserved() {
        line(106, 128, 114, 128);
        line(114, 128, 114, 82);
        assertFalse(matches());
    }

    @Test
    public void hollowHalfIsPreserved() {
        line(100, 125, 110, 122);
        line(110, 122, 115, 129);
        line(115, 129, 104, 133);
        line(104, 133, 100, 125);
        line(115, 129, 115, 82);
        assertFalse(matches());
    }

    @Test
    public void downBowIsPreserved() {
        line(101, 130, 101, 88);
        line(101, 88, 135, 88);
        line(135, 88, 135, 130);
        assertFalse(matches());
    }

    @Test
    public void threeLacksReturningBottomDiagonal() {
        line(100, 88, 130, 88);
        line(130, 88, 130, 130);
        line(130, 109, 108, 109);
        line(130, 130, 100, 130);
        assertFalse(matches());
    }

    @Test
    public void isolatedDiagonalIsInsufficient() {
        line(132, 95, 100, 130);
        assertFalse(matches());
    }

    @Test
    public void invalidScaleIsRejected() {
        two();
        assertFalse(HandwrittenTwoHeadInk.matches(gray, W, H, 98, 123, 114, 134, Float.NaN));
    }

    @Test
    public void sourcePixelsArePreserved() {
        two();
        var before = gray.clone();
        matches();
        assertArrayEquals(before, gray);
    }

    @Test
    public void lateReturningTwoUsesItsOwnPrefixAfterLargerRejectedComponent() {
        for (int ink : new int[] {66, 81, 111, 140}) {
            Arrays.fill(gray, (byte) 255);
            two();
            for (int i = 0; i < gray.length; i++) if (gray[i] == 0) gray[i] = (byte) ink;
            // The solid block is disjoint, seeded first, and rejected by the original geometry.
            for (int y = 100; y <= 123; y++) for (int x = 60; x <= 98; x++) gray[y * W + x] = 0;
            byte[] before = gray.clone();
            assertTrue(matches());
            assertTrue(matches());
            assertArrayEquals(before, gray);
            Arrays.fill(gray, (byte) 255);
            before = gray.clone();
            assertFalse(matches());
            assertArrayEquals(before, gray);
        }
    }

    @Test
    public void rejectedAndRestoredRasterNeverRetainsAnEarlierTwo() {
        two();
        byte[] before = gray.clone();
        assertTrue(matches());
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 255);
        line(132, 95, 100, 130);
        before = gray.clone();
        assertFalse(matches());
        assertFalse(matches());
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 255);
        two();
        for (int i = 0; i < gray.length; i++) if (gray[i] == 0) gray[i] = (byte) 140;
        before = gray.clone();
        assertTrue(matches());
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 255);
        before = gray.clone();
        assertFalse(matches());
        assertArrayEquals(before, gray);
    }
}

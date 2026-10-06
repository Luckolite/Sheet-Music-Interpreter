// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SegnoFragmentInkTest {
    static final int W = 240, H = 220, G = 16;
    final byte[] gray = new byte[W * H];

    public SegnoFragmentInkTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void line(int x1, int y1, int x2, int y2) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= n; i++) {
            int x = Math.round(x1 + (x2 - x1) * i / (float) n),
                    y = Math.round(y1 + (y2 - y1) * i / (float) n);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) gray[(y + dy) * W + x + dx] = 0;
        }
    }

    void dot(int x, int y) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                if (dx * dx + dy * dy <= 5) gray[(y + dy) * W + x + dx] = 0;
    }

    void body(boolean slash) {
        line(116, 94, 119, 87);
        line(119, 87, 112, 84);
        line(112, 84, 104, 90);
        line(104, 90, 107, 101);
        line(107, 101, 121, 111);
        line(121, 111, 124, 122);
        line(124, 122, 117, 128);
        line(117, 128, 110, 125);
        line(110, 125, 112, 119);
        if (slash) line(99, 128, 128, 84);
    }

    boolean match() {
        return SegnoFragmentInk.matches(gray, W, H, 105, 85, 112, 94, G);
    }

    @Test
    public void fullSegnoRequiresCrossedBodyAndOpposedDots() {
        body(true);
        dot(99, 108);
        dot(128, 102);
        assertTrue(match());
    }

    @Test
    public void absentLeftDotPreservesCandidate() {
        body(true);
        dot(128, 102);
        assertFalse(match());
    }

    @Test
    public void absentRightDotPreservesCandidate() {
        body(true);
        dot(99, 108);
        assertFalse(match());
    }

    @Test
    public void wrongDotDiagonalDoesNotMatch() {
        body(true);
        dot(99, 102);
        dot(128, 108);
        assertFalse(match());
    }

    @Test
    public void uncrossedSIsNotSegno() {
        body(false);
        dot(99, 108);
        dot(128, 102);
        assertFalse(match());
    }

    @Test
    public void stemmedHeadIsPreserved() {
        line(104, 94, 112, 94);
        line(112, 94, 112, 60);
        dot(99, 88);
        dot(128, 82);
        assertFalse(match());
    }

    @Test
    public void nullAndInvalidScaleAreRejected() {
        assertFalse(SegnoFragmentInk.matches(null, W, H, 105, 85, 112, 94, G));
        assertFalse(SegnoFragmentInk.matches(gray, W, H, 105, 85, 112, 94, Float.NaN));
    }

    @Test
    public void sourcePixelsArePreserved() {
        body(true);
        dot(99, 108);
        dot(128, 102);
        var before = gray.clone();
        match();
        assertArrayEquals(before, gray);
    }

    @Test
    public void paleSegnoKeepsBodyAndDotRecordsAfterLongerRejectedPrefix() {
        for (int ink : new int[] {66, 81, 111, 140}) {
            Arrays.fill(gray, (byte) 255);
            body(true);
            dot(99, 108);
            dot(128, 102);
            for (int i = 0; i < gray.length; i++) if (gray[i] == 0) gray[i] = (byte) ink;
            // This earlier solid component is disjoint and fails the original body/dot guards.
            for (int y = 45; y <= 70; y++) for (int x = 60; x <= 95; x++) gray[y * W + x] = 0;
            byte[] before = gray.clone();
            assertTrue(match());
            assertTrue(match());
            assertArrayEquals(before, gray);
            Arrays.fill(gray, (byte) 255);
            before = gray.clone();
            assertFalse(match());
            assertArrayEquals(before, gray);
        }
    }

    @Test
    public void changedDotAndWhitenedCallerNeverReuseAnEarlierSegno() {
        body(true);
        dot(99, 108);
        dot(128, 102);
        byte[] before = gray.clone();
        assertTrue(match());
        assertArrayEquals(before, gray);
        for (int y = 106; y <= 110; y++)
            for (int x = 97; x <= 101; x++) gray[y * W + x] = (byte) 255;
        before = gray.clone();
        assertFalse(match());
        assertFalse(match());
        assertArrayEquals(before, gray);
        dot(99, 108);
        before = gray.clone();
        assertTrue(match());
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 255);
        before = gray.clone();
        assertFalse(match());
        assertArrayEquals(before, gray);
    }
}

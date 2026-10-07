// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class HeadInkBeamExclusionTest {
    static final int W = 100, H = 80;

    byte[] paper(int left, int right) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        for (int x = left; x <= right; x++) gray[42 * W + x] = 20;
        return gray;
    }

    boolean excluded(byte[] gray, int y) {
        return HeadInkBeamExclusion.onlyHead(gray, W, H, y, 10, 90, 39, 4, 40, 60, 35, 45, 16);
    }

    @Test
    public void enclosedHeadTailIsNotAnotherBeam() {
        assertTrue(excluded(paper(39, 57), 42));
    }

    @Test
    public void beamBeyondRightEdgeIsRetained() {
        assertFalse(excluded(paper(39, 80), 42));
    }

    @Test
    public void beamBeyondLeftEdgeIsRetained() {
        assertFalse(excluded(paper(20, 57), 42));
    }

    @Test
    public void detachedBandOutsideHeadRowsIsRetained() {
        byte[] gray = paper(39, 57);
        System.arraycopy(gray, 42 * W, gray, 48 * W, W);
        assertFalse(excluded(gray, 48));
    }

    @Test
    public void blankAndInvalidWindowsCannotProveOwnership() {
        byte[] blank = paper(70, 75);
        assertFalse(excluded(blank, 42));
        assertFalse(excluded(null, 42));
        assertFalse(excluded(new byte[2], 42));
    }

    @Test
    public void sourcePixelsRemainUnchanged() {
        byte[] gray = paper(39, 57), before = gray.clone();
        assertTrue(excluded(gray, 42));
        assertArrayEquals(before, gray);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-cycle contour above a completed shaft; no scanned pixels. */
public class DetachedMordentBeamCapTest {
    static final int W = 320, H = 240, X = 166, G = 14;

    byte[] page(boolean wave) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        for (int dx = 0; dx < 30; dx++) {
            int center = 112 - (wave ? (int) Math.round(4 * Math.sin(4 * Math.PI * dx / 29)) : 0);
            for (int dy = -3; dy <= 3; dy++) gray[(center + dy) * W + 140 + dx] = 20;
        }
        for (int x = 100; x <= 200; x++) for (int y = 123; y <= 128; y++) gray[y * W + x] = 20;
        for (int y = 123; y <= 180; y++) gray[y * W + X] = 20;
        return gray;
    }

    boolean check(byte[] gray) {
        return DetachedMordentBeamCap.crosses(
                gray, W, H, 180, new int[] {X, 123, -1}, new int[] {X, 110, -1}, G, 170);
    }

    @Test
    public void detachedMordentCannotExtendCompletedShaft() {
        assertTrue(check(page(true)));
    }

    @Test
    public void straightSecondBeamIsRetained() {
        assertFalse(check(page(false)));
    }

    @Test
    public void connectedInkCannotSupplyDetachedProof() {
        byte[] gray = page(true);
        for (int y = 110; y <= 123; y++) gray[y * W + X] = 20;
        assertFalse(check(gray));
    }

    @Test
    public void incompleteWaveCannotSupplyOrnamentProof() {
        byte[] gray = page(true);
        for (int x = 140; x < 152; x++)
            for (int y = 100; y <= 120; y++) gray[y * W + x] = (byte) 250;
        assertFalse(check(gray));
    }

    @Test
    public void oppositeVoiceCannotBorrowOrnament() {
        assertFalse(
                DetachedMordentBeamCap.crosses(
                        page(true),
                        W,
                        H,
                        180,
                        new int[] {X, 123, 1},
                        new int[] {X, 110, -1},
                        G,
                        170));
    }

    @Test
    public void remoteAxisCannotBorrowOrnament() {
        assertFalse(
                DetachedMordentBeamCap.crosses(
                        page(true),
                        W,
                        H,
                        180,
                        new int[] {X - 8, 123, -1},
                        new int[] {X, 110, -1},
                        G,
                        170));
    }

    @Test
    public void callerInputsRemainExact() {
        byte[] gray = page(true), copy = gray.clone();
        int[] a = {X, 123, -1}, b = {X, 110, -1};
        assertTrue(DetachedMordentBeamCap.crosses(gray, W, H, 180, a, b, G, 170));
        assertArrayEquals(copy, gray);
        assertArrayEquals(new int[] {X, 123, -1}, a);
        assertArrayEquals(new int[] {X, 110, -1}, b);
    }

    @Test
    public void invalidRasterOrFrameRejected() {
        assertFalse(
                DetachedMordentBeamCap.crosses(
                        new byte[1],
                        W,
                        H,
                        180,
                        new int[] {X, 123, -1},
                        new int[] {X, 110, -1},
                        G,
                        170));
        assertFalse(
                DetachedMordentBeamCap.crosses(
                        page(true),
                        W,
                        H,
                        180,
                        new int[] {X, 123, -1},
                        new int[] {X, 110, -1},
                        Float.NaN,
                        170));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Original synthetic shafts; no score images or retained recognition coordinates. */
public class ShadedStemBlankCapTest {
    private static final int W = 200, H = 220, X = 100;
    private static final float GAP = 12;

    private byte[] page(int side, int paper) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) paper);
        int origin = side == 1 ? 40 : 160;
        for (int d = 0; d <= 43; d++)
            for (int x = X - 1; x <= X + 1; x++) gray[(origin + side * d) * W + x] = 20;
        for (int d = 39; d <= 43; d++)
            for (int x = 50; x <= 150; x++) gray[(origin + side * d) * W + x] = 20;
        for (int d = 46; d <= 52; d++)
            for (int x = X - 1; x <= X + 1; x++) gray[(origin + side * d) * W + x] = 80;
        return gray;
    }

    private int endpoint(byte[] gray, int side) {
        int origin = side == 1 ? 40 : 160;
        return ShadedStemBlankCap.endpoint(
                gray, W, H, X, origin, origin + side * 52, side, GAP, 115);
    }

    @Test
    public void completeShadedCapStopsDownwardTrace() {
        assertEquals(83, endpoint(page(1, 150), 1));
    }

    @Test
    public void completeShadedCapStopsUpwardTrace() {
        assertEquals(117, endpoint(page(-1, 150), -1));
    }

    @Test
    public void darkerPaperRetainsIndependentContrastProof() {
        assertEquals(83, endpoint(page(1, 100), 1));
    }

    @Test
    public void upperShadeBoundaryStillHasCapEvidence() {
        assertEquals(83, endpoint(page(1, 200), 1));
    }

    @Test
    public void brightPaperDoesNotUseShadedRecovery() {
        assertEquals(92, endpoint(page(1, 240), 1));
    }

    @Test
    public void darkPaperDoesNotSupplyClearCap() {
        assertEquals(92, endpoint(page(1, 80), 1));
    }

    @Test
    public void realSecondBeamWithContinuousShaftIsPreserved() {
        byte[] gray = page(1, 150);
        for (int y = 84; y <= 85; y++) for (int x = X - 1; x <= X + 1; x++) gray[y * W + x] = 20;
        assertEquals(92, endpoint(gray, 1));
    }

    @Test
    public void faintContinuousShaftIsPreserved() {
        byte[] gray = page(1, 150);
        for (int y = 84; y <= 85; y++)
            for (int x = X - 1; x <= X + 1; x++) gray[y * W + x] = (byte) 135;
        assertEquals(92, endpoint(gray, 1));
    }

    @Test
    public void oneClearRowDoesNotSeparateInk() {
        byte[] gray = page(1, 150);
        for (int x = X - 1; x <= X + 1; x++) gray[85 * W + x] = 20;
        assertEquals(92, endpoint(gray, 1));
    }

    @Test
    public void inkAtEitherShaftEdgePreventsTruncation() {
        for (int edge : new int[] {X - 1, X + 1}) {
            byte[] gray = page(1, 150);
            gray[84 * W + edge] = 20;
            gray[85 * W + edge] = 20;
            assertEquals(92, endpoint(gray, 1));
        }
    }

    @Test
    public void weakPrefixDoesNotEstablishStem() {
        byte[] gray = page(1, 150);
        for (int y = 40; y <= 75; y++)
            for (int x = X - 1; x <= X + 1; x++) gray[y * W + x] = (byte) 150;
        assertEquals(92, endpoint(gray, 1));
    }

    @Test
    public void missingDetachedInkRetainsEndpoint() {
        byte[] gray = page(1, 150);
        for (int y = 86; y <= 92; y++)
            for (int x = X - 1; x <= X + 1; x++) gray[y * W + x] = (byte) 150;
        assertEquals(92, endpoint(gray, 1));
    }

    @Test
    public void oneDetachedInkRowIsInsufficient() {
        byte[] gray = page(1, 150);
        for (int y = 86; y <= 91; y++)
            for (int x = X - 1; x <= X + 1; x++) gray[y * W + x] = (byte) 150;
        assertEquals(92, endpoint(gray, 1));
    }

    @Test
    public void shortenedStemIsOutsideRecoveryScope() {
        assertEquals(65, ShadedStemBlankCap.endpoint(page(1, 150), W, H, X, 40, 65, 1, GAP, 115));
    }

    @Test
    public void remoteEndpointIsOutsideRecoveryScope() {
        assertEquals(140, ShadedStemBlankCap.endpoint(page(1, 150), W, H, X, 40, 140, 1, GAP, 115));
    }

    @Test
    public void clippedPaperWindowCannotProveCap() {
        assertEquals(92, ShadedStemBlankCap.endpoint(page(1, 150), W, H, 3, 40, 92, 1, GAP, 115));
    }

    @Test
    public void missingOrMalformedRasterIsRejected() {
        assertEquals(92, ShadedStemBlankCap.endpoint(null, W, H, X, 40, 92, 1, GAP, 115));
        assertEquals(92, ShadedStemBlankCap.endpoint(new byte[3], W, H, X, 40, 92, 1, GAP, 115));
    }

    @Test
    public void invalidGeometryIsRejected() {
        for (float gap : new float[] {0, 7, Float.NaN, Float.POSITIVE_INFINITY})
            assertEquals(
                    92, ShadedStemBlankCap.endpoint(page(1, 150), W, H, X, 40, 92, 1, gap, 115));
        assertEquals(92, ShadedStemBlankCap.endpoint(page(1, 150), W, H, X, 40, 92, 0, GAP, 115));
    }
}

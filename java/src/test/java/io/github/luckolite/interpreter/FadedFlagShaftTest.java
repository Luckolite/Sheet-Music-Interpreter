// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original filled ovals, semantic shafts, pale raw ink and independent paper controls. */
public class FadedFlagShaftTest {
    private static final int W = 160, H = 180, LEFT = 70, RIGHT = 90, TOP = 115, BOTTOM = 127;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    private void setup() {
        Arrays.fill(gray, (byte) 255);
        for (int y = TOP; y <= BOTTOM; y++)
            for (int x = LEFT; x <= RIGHT; x++)
                if (Math.pow((x - 80) / 10d, 2) + Math.pow((y - 121) / 6d, 2) <= 1) {
                    gray[y * W + x] = 110;
                    labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                }
        for (int y = 66; y <= 121; y++)
            for (int x = 88; x <= 90; x++) {
                gray[y * W + x] = (byte) 220;
                labels[y * W + x] = OmrMeasurePostProcessor.STEM_OR_REST;
            }
    }

    private boolean matches() {
        return FadedFlagShaft.matches(
                gray, labels, W, H, LEFT, RIGHT, TOP, BOTTOM, 121, 20, 89, 66, true);
    }

    @Test
    public void continuousPaleShaftRetainsItsIndependentRawOwner() {
        setup();
        assertTrue(matches());
    }

    @Test
    public void darkPaperWithoutColumnContrastCannotSupplyAShaft() {
        setup();
        Arrays.fill(gray, (byte) 220);
        assertFalse(matches());
    }

    @Test
    public void semanticPredictionWithoutRawInkIsInsufficient() {
        setup();
        for (int y = 66; y <= 110; y++) for (int x = 88; x <= 90; x++) gray[y * W + x] = (byte) 255;
        assertFalse(matches());
    }

    @Test
    public void rawInkWithoutSemanticShaftOwnershipIsInsufficient() {
        setup();
        for (int y = 66; y <= 121; y++) for (int x = 88; x <= 90; x++) labels[y * W + x] = 0;
        assertFalse(matches());
    }

    @Test
    public void predictedHeadRectangleNeedsAnActualRawFilledBody() {
        setup();
        for (int i = 0; i < labels.length; i++)
            if (labels[i] == OmrMeasurePostProcessor.NOTEHEAD) labels[i] = 0;
        assertFalse(matches());
    }

    @Test
    public void fullPredictedHeadWithOnlyATinyRawSpeckIsInsufficient() {
        setup();
        for (int y = TOP; y <= BOTTOM; y++)
            for (int x = LEFT; x <= RIGHT; x++)
                if (labels[y * W + x] == OmrMeasurePostProcessor.NOTEHEAD)
                    gray[y * W + x] = (byte) 255;
        for (int y = 119; y <= 122; y++) for (int x = 79; x <= 82; x++) gray[y * W + x] = 90;
        assertFalse(matches());
    }

    @Test
    public void detachedRawPiecesCannotBridgeAThreeRowPaperGap() {
        setup();
        for (int y = 88; y <= 90; y++) for (int x = 88; x <= 90; x++) gray[y * W + x] = (byte) 255;
        assertFalse(matches());
    }

    @Test
    public void singleMissingAntialiasRowRetainsContinuousSupport() {
        setup();
        for (int x = 88; x <= 90; x++) gray[88 * W + x] = (byte) 255;
        assertTrue(matches());
    }

    @Test
    public void uniformShadingBandDoesNotBecomeAPaleShaft() {
        setup();
        for (int y = 66; y <= 110; y++)
            for (int x = 75; x <= 105; x++) gray[y * W + x] = (byte) 220;
        assertFalse(matches());
    }

    @Test
    public void tinyGraceRectangleCannotBorrowAFullSizeShaft() {
        setup();
        assertFalse(
                FadedFlagShaft.matches(
                        gray, labels, W, H, 84, 90, 118, 124, 121, 20, 89, 66, true));
    }

    @Test
    public void neighbouringShaftOutsideHeadEdgeIsRejected() {
        setup();
        assertFalse(
                FadedFlagShaft.matches(
                        gray, labels, W, H, LEFT, RIGHT, TOP, BOTTOM, 121, 20, 104, 66, true));
    }

    @Test
    public void shortHeadCornerDoesNotProveShaftLength() {
        setup();
        assertFalse(
                FadedFlagShaft.matches(
                        gray, labels, W, H, LEFT, RIGHT, TOP, BOTTOM, 121, 20, 89, 95, true));
    }

    @Test
    public void oppositeDirectionDoesNotOwnThisEndpoint() {
        setup();
        assertFalse(
                FadedFlagShaft.matches(
                        gray, labels, W, H, LEFT, RIGHT, TOP, BOTTOM, 121, 20, 89, 66, false));
    }

    @Test
    public void absentOrShortRasterIsRejected() {
        setup();
        assertFalse(
                FadedFlagShaft.matches(
                        null, labels, W, H, LEFT, RIGHT, TOP, BOTTOM, 121, 20, 89, 66, true));
        assertFalse(
                FadedFlagShaft.matches(
                        new byte[10],
                        labels,
                        W,
                        H,
                        LEFT,
                        RIGHT,
                        TOP,
                        BOTTOM,
                        121,
                        20,
                        89,
                        66,
                        true));
    }

    @Test
    public void inspectionPreservesAllSourceBytes() {
        setup();
        byte[] before = gray.clone(), mask = labels.clone();
        matches();
        assertArrayEquals(before, gray);
        assertArrayEquals(mask, labels);
    }
}

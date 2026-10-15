// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original shaft/head arrangements, independent of any source score. */
public class FlagPocketStemOwnershipTest {
    static final int W = 200, H = 180;
    byte[] labels = new byte[W * H], gray = new byte[W * H];

    void fixture(boolean opposite, int distance, boolean joined) {
        Arrays.fill(gray, (byte) 240);
        Arrays.fill(labels, (byte) 0);
        for (int y = 55; y <= 100; y++) {
            if (!joined && y >= 69 && y <= 75) continue;
            gray[y * W + 80] = 40;
            labels[y * W + 80] = 1;
        }
        int cx = opposite ? 71 : 89, cy = 60 + distance;
        for (int y = cy - 4; y <= cy + 4; y++)
            for (int x = cx - 8; x <= cx + 8; x++)
                if ((x - cx) * (x - cx) / 64. + (y - cy) * (y - cy) / 16. <= 1) {
                    gray[y * W + x] = 40;
                    labels[y * W + x] = 2;
                }
    }

    boolean rejected() {
        return FadedNoteheadRecovery.flagPocketOnOwnedStem(labels, gray, W, H, 82, 56, 89, 64, 12);
    }

    @Test
    public void flagPocketUsesOppositeRecognizedHeadOnContinuousStem() {
        fixture(true, 26, true);
        assertTrue(rejected());
    }

    @Test
    public void disconnectedNearbyHeadDoesNotOwnPocket() {
        fixture(true, 26, false);
        assertFalse(rejected());
    }

    @Test
    public void sameSideChordHeadDoesNotRejectRecoveredHead() {
        fixture(false, 26, true);
        assertFalse(rejected());
    }

    @Test
    public void adjacentSecondMayUseOppositeSide() {
        fixture(true, 8, true);
        assertFalse(rejected());
    }

    @Test
    public void ordinaryWideOvalIsPreserved() {
        fixture(true, 26, true);
        assertFalse(
                FadedNoteheadRecovery.flagPocketOnOwnedStem(
                        labels, gray, W, H, 82, 56, 99, 64, 12));
    }

    @Test
    public void invertedFlagUsesSameRule() {
        fixture(true, 26, true);
        byte[] mirroredLabels = new byte[W * H], mirroredGray = new byte[W * H];
        for (int i = 0; i < gray.length; i++) {
            mirroredLabels[gray.length - 1 - i] = labels[i];
            mirroredGray[gray.length - 1 - i] = gray[i];
        }
        assertTrue(
                FadedNoteheadRecovery.flagPocketOnOwnedStem(
                        mirroredLabels,
                        mirroredGray,
                        W,
                        H,
                        W - 1 - 89,
                        H - 1 - 64,
                        W - 1 - 82,
                        H - 1 - 56,
                        12));
    }
}

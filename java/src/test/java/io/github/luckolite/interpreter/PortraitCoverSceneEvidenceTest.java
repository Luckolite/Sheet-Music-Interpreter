// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/** Generated pixels only; no publication images or OCR labels. */
public class PortraitCoverSceneEvidenceTest {
    private static final int W = 900, H = 1200;

    private static int[] texture() {
        int[] p = new int[W * H];
        Random r = new Random(555);
        for (int i = 0; i < p.length; i++) p[i] = 0xff000000 | r.nextInt(1 << 24);
        return p;
    }

    @Test
    public void texturedPortraitWithoutStaffIsEligible() {
        assertTrue(BibliographicCoverSceneEvidence.photographicPortrait(W, H, texture(), -1));
    }

    @Test
    public void detectedStaffIsNeverOverridden() {
        int[] p = texture();
        assertFalse(BibliographicCoverSceneEvidence.photographicPortrait(W, H, p, 0));
        assertFalse(BibliographicCoverSceneEvidence.photographicPortrait(W, H, p, 400));
    }

    @Test
    public void nonfiniteStaffIsRejected() {
        int[] p = texture();
        assertFalse(BibliographicCoverSceneEvidence.photographicPortrait(W, H, p, Float.NaN));
        assertFalse(
                BibliographicCoverSceneEvidence.photographicPortrait(
                        W, H, p, Float.NEGATIVE_INFINITY));
    }

    @Test
    public void malformedRasterIsRejected() {
        assertFalse(BibliographicCoverSceneEvidence.photographicPortrait(W, H, null, -1));
        assertFalse(BibliographicCoverSceneEvidence.photographicPortrait(W, H, new int[2], -1));
    }

    @Test
    public void originalLandscapeBehaviorRemains() {
        int[] p = texture();
        assertFalse(BibliographicCoverSceneEvidence.photographicLandscape(W, H, p));
        assertTrue(BibliographicCoverSceneEvidence.photographicLandscape(H, W, p));
    }

    @Test
    public void solidColoredPaperIsNotAphotograph() {
        int[] p = new int[W * H];
        java.util.Arrays.fill(p, 0xff8844aa);
        assertFalse(BibliographicCoverSceneEvidence.photographicPortrait(W, H, p, -1));
    }

    @Test
    public void smoothGradientIsNotAphotograph() {
        int[] p = new int[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                p[y * W + x] = 0xff000000 | ((x * 255 / W) << 16) | ((y * 255 / H) << 8) | 100;
        assertFalse(BibliographicCoverSceneEvidence.photographicPortrait(W, H, p, -1));
    }

    @Test
    public void monochromeStaffPageIsNotAphotograph() {
        int[] p = new int[W * H];
        java.util.Arrays.fill(p, 0xffffffff);
        for (int y = 200; y < 800; y += 100)
            for (int rail = 0; rail < 5; rail++)
                for (int x = 30; x < W - 30; x++) p[(y + rail * 8) * W + x] = 0xff000000;
        assertFalse(BibliographicCoverSceneEvidence.photographicPortrait(W, H, p, -1));
    }
}

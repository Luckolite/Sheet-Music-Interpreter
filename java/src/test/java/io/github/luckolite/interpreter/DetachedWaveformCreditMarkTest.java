// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import io.github.luckolite.interpreter.DetachedWaveformCreditMark;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fictional original marks, with no service names or score fixtures. */
public class DetachedWaveformCreditMarkTest {
    private static int[] blank() {
        int[] pixels = new int[60 * 60];
        java.util.Arrays.fill(pixels, 0xffffffff);
        return pixels;
    }

    private static void rect(int[] pixels, int l, int t, int r, int b, int color) {
        for (int y = t; y < b; y++) for (int x = l; x < r; x++) pixels[y * 60 + x] = color;
    }

    private static int[] wave() {
        int[] pixels = blank();
        rect(pixels, 4, 25, 9, 35, 0xff000000);
        rect(pixels, 15, 18, 20, 42, 0xff000000);
        rect(pixels, 26, 8, 34, 52, 0xff000000);
        rect(pixels, 40, 18, 45, 42, 0xff000000);
        rect(pixels, 51, 25, 56, 35, 0xff000000);
        return pixels;
    }

    @Test
    public void isolatedSymmetricStemsAreSupported() {
        assertTrue(DetachedWaveformCreditMark.matches(wave(), 60, 60));
    }

    @Test
    public void shortOuterCapsKeepTheSymmetricWaveform() {
        int[] p = wave();
        rect(p, 4, 25, 9, 35, 0xffffffff);
        rect(p, 51, 25, 56, 35, 0xffffffff);
        rect(p, 3, 26, 9, 34, 0xff000000);
        rect(p, 51, 26, 57, 34, 0xff000000);
        assertTrue(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void realCapitalFIsNotDiscarded() {
        int[] p = blank();
        rect(p, 15, 8, 21, 52, 0xff000000);
        rect(p, 15, 8, 45, 14, 0xff000000);
        rect(p, 15, 26, 39, 32, 0xff000000);
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void lowercaseFIsNotDiscarded() {
        int[] p = blank();
        rect(p, 25, 8, 31, 52, 0xff000000);
        rect(p, 25, 8, 39, 14, 0xff000000);
        rect(p, 17, 24, 39, 30, 0xff000000);
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void dottedInitialIsNotAMark() {
        int[] p = blank();
        rect(p, 28, 24, 33, 52, 0xff000000);
        rect(p, 28, 10, 33, 15, 0xff000000);
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void romanStemsAreNotAMark() {
        int[] p = blank();
        for (int x : new int[] {4, 15, 26, 40, 51}) rect(p, x, 8, x + 5, 52, 0xff000000);
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void unevenBarHeightsRequireReview() {
        int[] p = wave();
        rect(p, 51, 8, 56, 52, 0xff000000);
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void connectedBarsAreNotDetachedEvidence() {
        int[] p = wave();
        rect(p, 4, 29, 56, 32, 0xff000000);
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void clippedMarkRequiresReview() {
        int[] p = wave();
        rect(p, 0, 25, 9, 35, 0xff000000);
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void thresholdDisagreementRequiresReview() {
        int[] p = wave();
        rect(p, 9, 29, 51, 32, 0xffbbbbbb);
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void misplacedStemRequiresReview() {
        int[] p = wave();
        rect(p, 51, 25, 56, 35, 0xffffffff);
        rect(p, 51, 42, 56, 52, 0xff000000);
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void coloredArtworkIsNotDarkInkEvidence() {
        int[] p = wave();
        for (int i = 0; i < p.length; i++) if (p[i] == 0xff000000) p[i] = 0xffe00000;
        assertFalse(DetachedWaveformCreditMark.matches(p, 60, 60));
    }

    @Test
    public void invalidAndOversizedInputsAreRejected() {
        assertFalse(DetachedWaveformCreditMark.matches(null, 60, 60));
        assertFalse(DetachedWaveformCreditMark.matches(wave(), 59, 60));
        assertFalse(DetachedWaveformCreditMark.matches(new int[385 * 385], 385, 385));
    }
}

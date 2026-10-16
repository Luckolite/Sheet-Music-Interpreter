// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original sloping beam fixtures; no score-derived pixels. */
public class WideSingleBeamTipTest {
    private static final int W = 300, H = 240;
    private final byte[] gray = new byte[W * H];
    private final int[] left = {100, 150, 1}, right = {160, 134, 1};

    public WideSingleBeamTipTest() {
        Arrays.fill(gray, (byte) 220);
        beam(70);
    }

    private void beam(int value) {
        for (int x = 100; x <= 160; x++) {
            int bottom = 150 - Math.round((x - 100) * 16f / 60);
            for (int y = bottom - 7; y <= bottom; y++) gray[y * W + x] = (byte) value;
        }
    }

    private boolean match() {
        return StemOwnedBeamTip.wideSingleBeam(gray, W, H, left, right, 157, 132, 16, 10, 95, 16);
    }

    @Test
    public void completeOwnedBeamRejectsTinyTip() {
        assertTrue(match());
    }

    @Test
    public void grayPaperDoesNotBecomeWideInk() {
        beam(130);
        assertTrue(match());
    }

    @Test
    public void twoStemOrderDoesNotMatter() {
        assertTrue(
                StemOwnedBeamTip.wideSingleBeam(gray, W, H, right, left, 157, 132, 16, 10, 95, 16));
    }

    @Test
    public void brokenBeamCannotEstablishOwnership() {
        for (int x = 119; x <= 145; x++)
            for (int y = 125; y <= 155; y++) gray[y * W + x] = (byte) 220;
        assertFalse(match());
    }

    @Test
    public void roundedHeadBulgeSurvives() {
        for (int y = 117; y <= 143; y++)
            for (int x = 145; x <= 169; x++)
                if (Math.pow((x - 156) / 12d, 2) + Math.pow((y - 130) / 13d, 2) <= 1)
                    gray[y * W + x] = 0;
        assertFalse(match());
    }

    @Test
    public void fullSizedHeadSurvives() {
        assertFalse(
                StemOwnedBeamTip.wideSingleBeam(
                        gray, W, H, left, right, 157, 132, 19, 13, 190, 16));
    }

    @Test
    public void oppositeStemDirectionsAreNotTheSameBeam() {
        assertFalse(
                StemOwnedBeamTip.wideSingleBeam(
                        gray, W, H, left, new int[] {160, 134, -1}, 157, 132, 16, 10, 95, 16));
    }

    @Test
    public void inputIsNotModified() {
        byte[] before = gray.clone();
        match();
        assertArrayEquals(before, gray);
    }

    @Test
    public void widerTipCentroidCanSitInsideTheBeam() {
        assertTrue(
                StemOwnedBeamTip.wideSingleBeam(
                        gray, W, H, left, right, 151, 134, 18, 10, 135, 16));
    }

    @Test
    public void detachedInteriorMaskCannotClaimAnEnd() {
        assertFalse(
                StemOwnedBeamTip.wideSingleBeam(
                        gray, W, H, left, right, 135, 137, 18, 10, 135, 16));
    }

    private void grayContact(int first, int last) {
        beam(40);
        for (int x = first; x <= last; x++) {
            int top = 150 - Math.round((x - 100) * 16f / 60) - 7;
            for (int y = top - 8; y < top; y++) gray[y * W + x] = (byte) 130;
        }
    }

    @Test
    public void narrowGrayRailContactRetainsDarkBeamOwnership() {
        grayContact(148, 150);
        assertTrue(match());
    }

    @Test
    public void broadGrayHeadContourCannotBeIgnoredForDarkCore() {
        grayContact(143, 157);
        assertFalse(match());
    }
}

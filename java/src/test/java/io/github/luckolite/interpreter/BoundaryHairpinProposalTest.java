// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-arm ink crosses a proposal window; acceptance still requires both full rails. */
public class BoundaryHairpinProposalTest {
    static class Scene {
        final int w = 620, h = 380;
        final int g;
        final int bottom;
        final byte[] gray = new byte[w * h];

        Scene(int gap) {
            g = gap;
            bottom = gap * 5;
            Arrays.fill(gray, (byte) 255);
        }

        void line(float start, float end) {
            for (int x = 50; x <= 550; x++) {
                int y = Math.round(bottom + g * (start + (end - start) * (x - 50) / 500f));
                for (int d = 0; d < 2; d++) gray[(y + d) * w + x] = 0;
            }
        }

        void wedge() {
            line(3.8f, 3.3f);
            line(4.4f, 5.1f);
            assertTrue(Math.round(bottom + g * 5.1f) + 1 - Math.round(bottom + g * 3.3f) <= 2 * g);
        }

        void mirror() {
            byte[] b = gray.clone();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) gray[y * w + w - 1 - x] = b[y * w + x];
        }

        boolean read(boolean next) {
            byte[] b = gray.clone();
            var staffs = new ArrayList<PlayingTechniqueDetector.Staff>();
            staffs.add(
                    new PlayingTechniqueDetector.Staff(bottom - 4 * g, bottom, g, 0, next ? 2 : 1));
            if (next)
                staffs.add(
                        new PlayingTechniqueDetector.Staff(
                                bottom + 5.1f * g, bottom + 9.1f * g, g, 1, 2));
            boolean found =
                    OpenHairpinPairs.detect(gray, w, h, staffs).stream()
                            .anyMatch(x -> x.staff().index() == 0);
            assertArrayEquals(b, gray);
            return found;
        }
    }

    @Test
    public void lowerCoreBeyondProposalBand13() {
        var s = new Scene(13);
        s.wedge();
        assertTrue(s.read(false));
    }

    @Test
    public void lowerCoreBeyondProposalBand16() {
        var s = new Scene(16);
        s.wedge();
        assertTrue(s.read(false));
    }

    @Test
    public void mirroredCompleteLowerCore() {
        var s = new Scene(13);
        s.wedge();
        s.mirror();
        assertTrue(s.read(false));
    }

    @Test
    public void missingContinuationCannotBeInvented() {
        var s = new Scene(13);
        s.line(3.8f, 3.3f);
        for (int x = 50; x <= 300; x++) {
            int y = Math.round(s.bottom + s.g * (4.4f + .7f * (x - 50) / 500));
            for (int d = 0; d < 2; d++) s.gray[(y + d) * s.w + x] = 0;
        }
        assertFalse(s.read(false));
    }

    @Test
    public void fullSingleRailStillCannotSupplySecond() {
        var s = new Scene(13);
        s.line(3.8f, 3.3f);
        assertFalse(s.read(false));
    }

    @Test
    public void nextStaffBoundaryStillOwnsItsSpace() {
        var s = new Scene(13);
        s.wedge();
        assertFalse(s.read(true));
    }

    @Test
    public void parallelCaptionRulesStillNotHairpin() {
        var s = new Scene(13);
        s.line(4.4f, 4.4f);
        s.line(5.2f, 5.2f);
        assertFalse(s.read(false));
    }

    @Test
    public void excessiveOpeningStillRejected() {
        var s = new Scene(13);
        s.line(3.8f, 3.3f);
        s.line(4.4f, 6.3f);
        assertFalse(s.read(false));
    }

    @Test
    public void unrelatedInteriorInkRemainsVeto() {
        var s = new Scene(13);
        s.wedge();
        for (int x = 250; x <= 450; x++)
            for (int y = s.bottom + 4 * s.g; y <= s.bottom + 5 * s.g; y++) s.gray[y * s.w + x] = 0;
        assertFalse(s.read(false));
    }

    @Test
    public void detachedFarRailCannotCompleteBoundary() {
        var s = new Scene(13);
        s.line(3.8f, 3.3f);
        for (int x = 50; x <= 330; x++) {
            int y = Math.round(s.bottom + s.g * (4.4f + .7f * (x - 50) / 500));
            s.gray[y * s.w + x] = 0;
            s.gray[(y + 1) * s.w + x] = 0;
        }
        for (int x = 390; x <= 550; x++) {
            int y = Math.round(s.bottom + s.g * (4.4f + .7f * (x - 50) / 500));
            s.gray[y * s.w + x] = 0;
            s.gray[(y + 1) * s.w + x] = 0;
        }
        assertFalse(s.read(false));
    }

    @Test
    public void oversizedOuterOpening13IsRefused() {
        var s = new Scene(13);
        s.line(3.8f, 3.2f);
        s.line(4.4f, 5.2f);
        assertTrue(
                Math.round(s.bottom + s.g * 5.2f) + 1 - Math.round(s.bottom + s.g * 3.2f)
                        > 2 * s.g);
        assertFalse(s.read(false));
    }

    @Test
    public void oversizedOuterOpening16IsRefused() {
        var s = new Scene(16);
        s.line(3.8f, 3.2f);
        s.line(4.4f, 5.2f);
        assertTrue(
                Math.round(s.bottom + s.g * 5.2f) + 1 - Math.round(s.bottom + s.g * 3.2f)
                        > 2 * s.g);
        assertFalse(s.read(false));
    }

    @Test
    public void mirroredOversizedOuterOpeningIsRefused() {
        var s = new Scene(13);
        s.line(3.8f, 3.2f);
        s.line(4.4f, 5.2f);
        assertTrue(
                Math.round(s.bottom + s.g * 5.2f) + 1 - Math.round(s.bottom + s.g * 3.2f)
                        > 2 * s.g);
        s.mirror();
        assertFalse(s.read(false));
    }
}

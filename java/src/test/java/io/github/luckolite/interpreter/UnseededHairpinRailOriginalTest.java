// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent literal two rails; lower rail lies wholly outside the ordinary proposal. */
public class UnseededHairpinRailOriginalTest {
    static class Scene {
        final int w = 620, h;
        final int g, bottom;
        final byte[] gray;

        Scene(int g) {
            this(g, 400);
        }

        Scene(int g, int h) {
            this.g = g;
            this.h = h;
            bottom = 5 * g;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
        }

        void rail(float a, float b, int start, int end) {
            for (int x = start; x <= end; x++) {
                int y = Math.round(bottom + g * (a + (b - a) * (x - 50) / 500f));
                for (int d = 0; d < 2; d++) if (y + d >= 0 && y + d < h) gray[(y + d) * w + x] = 0;
            }
        }

        void wedge() {
            rail(4.55f, 4.05f, 50, 550);
            rail(5.2f, 5.75f, 50, 550);
        }

        void mirror() {
            byte[] old = gray.clone();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) gray[y * w + w - 1 - x] = old[y * w + x];
        }

        boolean read(boolean next) {
            byte[] old = gray.clone();
            var ss = new ArrayList<PlayingTechniqueDetector.Staff>();
            ss.add(new PlayingTechniqueDetector.Staff(bottom - 4 * g, bottom, g, 0, next ? 2 : 1));
            if (next)
                ss.add(
                        new PlayingTechniqueDetector.Staff(
                                bottom + 5.4f * g, bottom + 9.4f * g, g, 1, 2));
            boolean found =
                    OpenHairpinPairs.detect(gray, w, h, ss).stream()
                            .anyMatch(v -> v.staff().index() == 0);
            assertArrayEquals(old, gray);
            return found;
        }
    }

    @Test
    public void independentCompleteRail13() {
        var s = new Scene(13);
        s.wedge();
        assertTrue(s.read(false));
    }

    @Test
    public void independentCompleteRail16() {
        var s = new Scene(16);
        s.wedge();
        assertTrue(s.read(false));
    }

    @Test
    public void mirroredIndependentRails() {
        var s = new Scene(16);
        s.wedge();
        s.mirror();
        assertTrue(s.read(false));
    }

    @Test
    public void absentLowerRail() {
        var s = new Scene(16);
        s.rail(4.55f, 4.05f, 50, 550);
        assertFalse(s.read(false));
    }

    @Test
    public void brokenLowerRail() {
        var s = new Scene(16);
        s.rail(4.55f, 4.05f, 50, 550);
        s.rail(5.2f, 5.75f, 50, 280);
        s.rail(5.2f, 5.75f, 350, 550);
        assertFalse(s.read(false));
    }

    @Test
    public void incompleteForeignInterval() {
        var s = new Scene(16);
        s.rail(4.55f, 4.05f, 50, 550);
        s.rail(5.2f, 5.75f, 120, 550);
        assertFalse(s.read(false));
    }

    @Test
    public void neighboringStaffRefusesSpace() {
        var s = new Scene(16);
        s.wedge();
        assertFalse(s.read(true));
    }

    @Test
    public void fullWidthCaptionRules() {
        var s = new Scene(16);
        s.rail(4.55f, 4.55f, 50, 550);
        s.rail(5.3f, 5.3f, 50, 550);
        assertFalse(s.read(false));
    }

    @Test
    public void foreignConnectedBranch() {
        var s = new Scene(16);
        s.wedge();
        for (int y = 160; y <= 188; y++) s.gray[y * s.w + 300] = 0;
        assertFalse(s.read(false));
    }

    @Test
    public void darkInteriorIsStillForeign() {
        var s = new Scene(16);
        s.wedge();
        for (int x = 180; x <= 450; x++) for (int y = 155; y <= 171; y++) s.gray[y * s.w + x] = 0;
        assertFalse(s.read(false));
    }

    @Test
    public void oversizedOpeningRefused() {
        var s = new Scene(16);
        s.rail(4.55f, 4.05f, 50, 550);
        s.rail(5.2f, 6.5f, 50, 550);
        assertFalse(s.read(false));
    }

    @Test
    public void pageClippedLowerRail() {
        var s = new Scene(16, 170);
        s.wedge();
        assertFalse(s.read(false));
    }
}

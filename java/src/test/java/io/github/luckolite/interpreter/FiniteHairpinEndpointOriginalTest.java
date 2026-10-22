// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original connected two-arm endpoints: every raw sample remains unchanged. */
public class FiniteHairpinEndpointOriginalTest {
    final int w = 360, h = 280;
    final byte[] gray = new byte[w * h];
    final List<PlayingTechniqueDetector.Staff> staffs =
            List.of(
                    new PlayingTechniqueDetector.Staff(20, 84, 16, 0, 2),
                    new PlayingTechniqueDetector.Staff(180, 244, 16, 1, 2));

    public FiniteHairpinEndpointOriginalTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void draw(boolean upperMissing, int missingColumns) {
        for (int x = 40; x <= 280; x++) {
            int u = Math.round(120 - (upperMissing ? 8 : 12) * (x - 40) / 240f),
                    l = Math.round(120 + (upperMissing ? 12 : 8) * (x - 40) / 240f);
            for (int y = u; y <= u + 1; y++)
                gray[y * w + x] = (byte) ((upperMissing && x > 280 - missingColumns) ? 150 : 0);
            for (int y = l; y <= l + 1; y++)
                gray[y * w + x] = (byte) ((!upperMissing && x > 280 - missingColumns) ? 150 : 0);
        }
    }

    void mirror() {
        byte[] a = gray.clone();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) gray[y * w + w - 1 - x] = a[y * w + x];
    }

    boolean found() {
        byte[] a = gray.clone();
        boolean yes =
                OpenHairpinPairs.detect(gray, w, h, staffs).stream()
                        .anyMatch(s -> s.staff().index() == 0);
        assertArrayEquals(a, gray);
        return yes;
    }

    @Test
    public void upperFiniteEndpointDoesNotBecomeLowerObservation() {
        draw(true, 1);
        assertTrue(found());
    }

    @Test
    public void lowerFiniteEndpointDoesNotBecomeUpperObservation() {
        draw(false, 1);
        assertTrue(found());
    }

    @Test
    public void leftUpperEndpointUsesSameObservedOwnership() {
        draw(true, 1);
        mirror();
        assertTrue(found());
    }

    @Test
    public void leftLowerEndpointUsesSameObservedOwnership() {
        draw(false, 1);
        mirror();
        assertTrue(found());
    }

    @Test
    public void anEntireMissingRailCannotBeInvented() {
        for (int x = 40; x <= 280; x++) {
            int y = Math.round(120 + 12 * (x - 40) / 240f);
            gray[y * w + x] = 0;
            gray[(y + 1) * w + x] = 0;
        }
        assertFalse(found());
    }

    @Test
    public void foreignDarkEndpointCannotBeReassigned() {
        draw(true, 1);
        gray[114 * w + 280] = 0;
        assertFalse(found());
    }

    @Test
    public void unexplainedFaintContinuationIsNotPaper() {
        draw(true, 1);
        for (int x = 281; x <= 284; x++)
            for (int y = 110; y <= 114; y++) gray[y * w + x] = (byte) 150;
        assertFalse(found());
    }

    @Test
    public void darkInteriorStillVetoesThePair() {
        draw(true, 1);
        for (int x = 210; x <= 260; x++) for (int y = 117; y <= 125; y++) gray[y * w + x] = 0;
        assertFalse(found());
    }

    @Test
    public void twoMissingTerminalColumnsRemainUnproved() {
        draw(true, 2);
        assertFalse(found());
    }

    @Test
    public void internalWrongArmSampleCannotBecomeAnEndpoint() {
        draw(true, 1);
        for (int y = 116; y <= 119; y++) gray[y * w + 150] = (byte) 150;
        assertFalse(found());
    }
}

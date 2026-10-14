// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original semantic projections and independently generated printed staff pixels. */
public final class UnsupportedStaffAliasTest {
    static final int W = 800, H = 900, GAP = 16, TARGET = 50;

    record Page(byte[] gray, int[] strength) {}

    static void rules(Page p, int top, int shade, int count, int left, int right, int curve) {
        for (int n = 0; n < count; n++)
            for (int x = left; x < right; x++) {
                int shift =
                        curve == 0
                                ? 0
                                : (int)
                                        Math.round(
                                                curve
                                                        * Math.sin(
                                                                (x - left)
                                                                        * Math.PI
                                                                        * 2
                                                                        / (right - left)));
                p.gray[(top + n * GAP + shift) * W + x] = (byte) shade;
            }
    }

    static Page page(int witnesses) {
        var p = new Page(new byte[W * H], new int[H]);
        Arrays.fill(p.gray, (byte) 255);
        for (int top : new int[] {TARGET, 350, 650})
            for (int n = 0; n < 5; n++) p.strength[top + n * GAP] = 600;
        if (witnesses >= 1) rules(p, 350, 30, 5, 30, 770, 0);
        if (witnesses >= 2) rules(p, 650, 30, 5, 30, 770, 0);
        return p;
    }

    static boolean contains(Page p, boolean validated) {
        var rows =
                validated
                        ? RawStaffLineDetector.detectValidatedFromStrength(
                                p.strength, 20, H, p.gray, W)
                        : RawStaffLineDetector.detectFromStrength(p.strength, 20, H, p.gray, W);
        return rows.stream().anyMatch(s -> Math.abs(s.top() - TARGET) < 5);
    }

    @Test
    public void unsupportedHeaderProjectionIsRejected() {
        assertFalse(contains(page(2), false));
    }

    @Test
    public void validatedProjectionAlsoRejectsUnsupportedHeader() {
        assertFalse(contains(page(2), true));
    }

    @Test
    public void singlePrintedWitnessDoesNotAuthorizeRemoval() {
        assertTrue(contains(page(1), false));
    }

    @Test
    public void absentGrayRetainsSemanticEvidence() {
        var p = page(2);
        assertTrue(
                RawStaffLineDetector.detectFromStrength(p.strength, 20, H, null, W).stream()
                        .anyMatch(s -> s.top() == TARGET));
    }

    @Test
    public void veryFaintParallelRulesSurvive() {
        var p = page(2);
        rules(p, TARGET, 249, 5, 30, 770, 0);
        assertTrue(contains(p, false));
    }

    @Test
    public void shortPartiallyOccludedStaffSurvives() {
        var p = page(2);
        rules(p, TARGET, 245, 4, 100, 190, 0);
        assertTrue(contains(p, false));
    }

    @Test
    public void threeLongFaintRulesSurvive() {
        var p = page(2);
        rules(p, TARGET, 246, 3, 30, 770, 0);
        assertTrue(contains(p, false));
    }

    @Test
    public void curvedPrintedStaffSurvives() {
        var p = page(2);
        rules(p, TARGET, 30, 5, 30, 770, 24);
        assertTrue(contains(p, false));
    }

    @Test
    public void isolatedDecorativeStrokesAreNotParallelRules() {
        var p = page(2);
        for (int n = 0; n < 5; n++)
            for (int x = 50 + n * 120; x < 100 + n * 120; x++)
                p.gray[(TARGET + n * GAP) * W + x] = 40;
        assertFalse(contains(p, false));
    }
}

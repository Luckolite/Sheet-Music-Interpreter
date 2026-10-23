// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original sloped beam bands attached to the existing analytical curved-approach fixture. */
public class BeamEndApproachOriginalsTest {
    static AttachedCurvedApproachTest.Scene scene(int scale, double slope) {
        var s = new AttachedCurvedApproachTest.Scene(scale, true);
        s.erase(132, 142, 283, 177);
        for (int x = 136 * scale; x <= 279 * scale; x++) {
            double end = (164 + slope * (x / (double) scale - 207)) * scale;
            for (int y = (int) Math.ceil(end - 4 * scale); y <= end; y++) s.gray[y * s.w + x] = 0;
        }
        int[] xs = {145, 214, 287}, ys = {108, 87, 94};
        for (int i = 0; i < 3; i++)
            s.box(xs[i] - 9, ys[i], xs[i] - 8, (int) Math.floor(164 + slope * (xs[i] - 9 - 207)));
        int end = (int) Math.floor(164 + slope * (278 - 207));
        // A printed shaft can protrude slightly through the end of its own beam band.
        s.box(278, end, 279, end + 3);
        return s;
    }

    private static void positive(int scale, double slope) {
        scene(scale, slope).positive();
    }

    @Test
    public void risingBeamMild() {
        positive(1, -.08);
    }

    @Test
    public void risingBeamSteeper() {
        positive(1, -.12);
    }

    @Test
    public void fallingBeamMild() {
        positive(1, .08);
    }

    @Test
    public void fallingBeamSteeper() {
        positive(1, .12);
    }

    @Test
    public void risingBeamDoubled() {
        positive(2, -.12);
    }

    @Test
    public void fallingBeamDoubled() {
        positive(2, .12);
    }

    @Test
    public void unrecognizedHeadCannotBeDiscarded() {
        var s = scene(1, -.12);
        s.heads.remove(0);
        s.negative();
    }

    @Test
    public void missingTargetCannotBorrowAnotherHead() {
        var s = scene(1, -.12);
        s.heads.remove(1);
        s.negative();
    }

    @Test
    public void duplicateDestinationIsAmbiguous() {
        var s = scene(1, -.12);
        s.heads.add(s.heads.get(1));
        s.negative();
    }

    @Test
    public void anotherPhysicalStaffIsNotAnOwnedBeamPair() {
        var s = scene(1, -.12);
        var n = s.heads.get(0);
        s.heads.set(0, new NoteSlideDetector.Head(n.x(), n.y(), n.gap(), 1, 0));
        s.negative();
    }

    @Test
    public void offBeamShaftSpurRemainsUnexplained() {
        var s = scene(1, -.12);
        s.line(275, 144, 279, 144, .9);
        s.negative();
    }

    @Test
    public void longShaftExtensionIsNotATinyCorner() {
        var s = scene(1, -.12);
        int end = (int) Math.floor(164 - .12 * (278 - 207));
        s.box(278, end, 279, end + 18);
        s.negative();
    }

    @Test
    public void missingRawTargetShaftCannotBeReplacedByMetadata() {
        var s = scene(1, -.12);
        s.erase(204, 110, 207, 145);
        s.negative();
    }

    private static boolean tip(
            AttachedCurvedApproachTest.Scene s, AttachedApproachBranches.Budget budget) {
        int end = (int) Math.floor(164 - .12 * (278 - 207));
        int[] path = new int[3];
        for (int i = 0; i < 3; i++) path[i] = (end + 1 + i) * s.w + 278;
        var down = new HashSet<Integer>();
        for (int i = 0; i < s.heads.size(); i++) {
            var n = s.heads.get(i);
            if (PrintedStemDirection.detect(s.gray, s.w, s.h, n.x(), n.y(), n.gap()) == -1)
                down.add(i);
        }
        return BeamEndBranchOwnership.owns(
                s.gray, s.w, s.h, path, s.heads, List.of(0, 1, 2), down, budget);
    }

    @Test
    public void completeRawStripAndShaftsOwnTheShortEnd() {
        assertTrue(tip(scene(1, -.12), new AttachedApproachBranches.Budget(64_000_000)));
    }

    @Test
    public void brokenConnectingStripCannotOwnTheEnd() {
        var s = scene(1, -.12);
        s.erase(238, 142, 254, 177);
        assertFalse(tip(s, new AttachedApproachBranches.Budget(64_000_000)));
    }

    @Test
    public void exhaustedWorkBoundAbstains() {
        var budget = new AttachedApproachBranches.Budget(0);
        assertFalse(tip(scene(1, -.12), budget));
        assertTrue(budget.refused);
    }
}

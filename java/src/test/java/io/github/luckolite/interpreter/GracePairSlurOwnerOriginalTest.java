// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original small grace heads and independently drawn bows; no source score pixels. */
public final class GracePairSlurOwnerOriginalTest {
    static final int W = 220, H = 260;
    static final float GAP = 12;

    static StaffPitchTrack track() {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, 150, GAP}, new float[] {W - 1, 150, GAP}));
    }

    static PrintedSlurOwners.Head head(int id, boolean end, boolean grace, boolean above) {
        float y = end ? 97 : 90;
        if (above) y = 280 - y;
        return new PrintedSlurOwners.Head(
                id,
                end ? 84 : 60,
                y,
                0,
                1,
                1,
                100,
                grace ? (end ? -999999 : -1000000) : 0,
                end ? 81 : 83);
    }

    static byte[] gray(List<PrintedSlurOwners.Head> heads) {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (var n : heads)
            for (int y = (int) n.y() - 4; y <= n.y() + 4; y++)
                for (int x = (int) n.x() - 5; x <= n.x() + 5; x++)
                    if (Math.pow((x - n.x()) / 4.5, 2) + Math.pow((y - n.y()) / 3.5, 2) <= 1)
                        g[y * W + x] = 0;
        return g;
    }

    static PrintedSlurContours.Bow bow(boolean above) {
        var points = new ArrayList<PrintedSlurContours.Point>();
        for (int x = 60; x <= 92; x++) {
            double t = (x - 60) / 32.;
            float y = (float) (106 + 9 * t + 7 * 4 * t * (1 - t));
            if (above) y = 280 - y;
            points.add(new PrintedSlurContours.Point(x, y));
        }
        return new PrintedSlurContours.Bow(points, above, .5f, 0);
    }

    static PrintedSlurOwners.Result resolve(
            List<PrintedSlurOwners.Head> ns, byte[] g, boolean above) {
        var before = g.clone();
        var r =
                PrintedSlurOwners.resolve(
                        "original-cue-pair", 0, bow(above), ns, Set.of(), g, W, H, track());
        assertArrayEquals(before, g);
        return r;
    }

    @Test
    public void lowerGracePairCanEndBesideItsSecondSmallHead() {
        var ns = List.of(head(0, false, true, false), head(1, true, true, false));
        assertEquals(PrintedSlurOwners.Status.RESOLVED, resolve(ns, gray(ns), false).status());
    }

    @Test
    public void upperGracePairUsesTheSameTypedEndpointOwnership() {
        var ns = List.of(head(0, false, true, true), head(1, true, true, true));
        assertEquals(PrintedSlurOwners.Status.RESOLVED, resolve(ns, gray(ns), true).status());
    }

    @Test
    public void ordinaryHeadsDoNotGainTheNewGraceEndpointRoute() {
        var ns = List.of(head(0, false, false, false), head(1, true, false, false));
        assertTrue(resolve(ns, gray(ns), false).span().isEmpty());
    }

    @Test
    public void graceStartDoesNotAuthorizeAnOrdinaryEnd() {
        var ns = List.of(head(0, false, true, false), head(1, true, false, false));
        assertTrue(resolve(ns, gray(ns), false).span().isEmpty());
    }

    @Test
    public void ordinaryStartDoesNotAuthorizeAGraceEnd() {
        var ns = List.of(head(0, false, false, false), head(1, true, true, false));
        assertTrue(resolve(ns, gray(ns), false).span().isEmpty());
    }

    @Test
    public void missingEndRawBodyCannotBeReplacedByGraceMetadata() {
        var ns = List.of(head(0, false, true, false), head(1, true, true, false));
        assertTrue(resolve(ns, gray(ns.subList(0, 1)), false).span().isEmpty());
    }

    @Test
    public void aBareRuleCannotReplaceTheSmallHead() {
        var ns = List.of(head(0, false, true, false), head(1, true, true, false));
        var g = gray(ns.subList(0, 1));
        for (int x = 70; x <= 100; x++) g[97 * W + x] = 0;
        assertTrue(resolve(ns, g, false).span().isEmpty());
    }

    @Test
    public void differentStaffsCannotShareTheGracePairRoute() {
        var a = head(0, false, true, false);
        var b = new PrintedSlurOwners.Head(1, 84, 97, 1, 2, 1, 100, -999999, 81);
        var ns = List.of(a, b);
        assertTrue(resolve(ns, gray(ns), false).span().isEmpty());
    }

    @Test
    public void overlappingGraceOwnersRemainAmbiguous() {
        var ns =
                List.of(
                        head(0, false, true, false),
                        head(1, true, true, false),
                        new PrintedSlurOwners.Head(2, 85, 97, 0, 1, 1, 100, -999998, 79));
        assertTrue(resolve(ns, gray(ns), false).span().isEmpty());
    }

    @Test
    public void uniqueEndpointsAndWrittenOrderDoNotRequireVoiceAssignment() {
        var ns =
                List.of(
                        head(0, false, true, false),
                        new PrintedSlurOwners.Head(1, 84, 97, 0, 1, 0, 100, -999999, 81));
        assertEquals(PrintedSlurOwners.Status.RESOLVED, resolve(ns, gray(ns), false).status());
        assertEquals(0, ns.get(1).voice());
    }

    @Test
    public void graceWrittenOrderStillRejectsAReversedPhrase() {
        var ns =
                List.of(
                        new PrintedSlurOwners.Head(0, 60, 90, 0, 1, 1, 100, -999998, 83),
                        new PrintedSlurOwners.Head(1, 84, 97, 0, 1, 1, 100, -999999, 81));
        assertTrue(resolve(ns, gray(ns), false).span().isEmpty());
    }

    @Test
    public void samePitchWithoutTieProofDoesNotBecomeASlur() {
        var ns =
                List.of(
                        head(0, false, true, false),
                        new PrintedSlurOwners.Head(1, 84, 97, 0, 1, 1, 100, -999999, 83));
        assertTrue(resolve(ns, gray(ns), false).span().isEmpty());
    }
}

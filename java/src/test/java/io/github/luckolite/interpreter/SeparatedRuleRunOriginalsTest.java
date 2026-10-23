// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original five-rule bands and unsupported lookalikes; no score pixels. */
public class SeparatedRuleRunOriginalsTest {
    private static final int W = 320, H = 200;

    private static byte[] raw(String kind) {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        if (!kind.equals("absent"))
            for (int y : new int[] {80, 94, 108, 122, 136})
                for (int x = 20; x <= (kind.equals("oneSide") ? 155 : 300); x++) raw[y * W + x] = 0;
        for (int y = 93; y <= 95; y++) raw[y * W + 160] = 0;
        return raw;
    }

    private static StaffPitchTrack track() {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {20, 136, 14}, new float[] {300, 136, 14}));
    }

    private static boolean found(String kind, int first, int last) {
        return SeparatedRuleRunOwnership.matches(
                raw(kind),
                W,
                H,
                160,
                first,
                last,
                track(),
                new AttachedApproachBranches.Budget(64_000_000));
    }

    @Test
    public void thinRuleEdgeHasWholeBandSupport() {
        assertTrue(found("staff", 93, 95));
    }

    @Test
    public void verifiedLabelWithoutRawRulesRefuses() {
        assertFalse(found("absent", 93, 95));
    }

    @Test
    public void oneSupportedSideIsInsufficient() {
        assertFalse(found("oneSide", 93, 95));
    }

    @Test
    public void separateInterlineInkIsNotRuleOwned() {
        assertFalse(found("staff", 100, 102));
    }

    @Test
    public void thickUnknownRunIsNotRuleOwned() {
        assertFalse(found("staff", 90, 98));
    }

    @Test
    public void unverifiedFrameIsInsufficient() {
        assertFalse(
                SeparatedRuleRunOwnership.matches(
                        raw("staff"),
                        W,
                        H,
                        160,
                        93,
                        95,
                        StaffPitchTrack.linear(W, 136, 14, 0),
                        new AttachedApproachBranches.Budget(64_000_000)));
    }

    @Test
    public void extrapolatedFrameIsInsufficient() {
        assertFalse(
                SeparatedRuleRunOwnership.matches(
                        raw("staff"),
                        W,
                        H,
                        5,
                        93,
                        95,
                        track(),
                        new AttachedApproachBranches.Budget(64_000_000)));
    }

    @Test
    public void exhaustedWorkBoundRefuses() {
        var b = new AttachedApproachBranches.Budget(0);
        assertFalse(SeparatedRuleRunOwnership.matches(raw("staff"), W, H, 160, 93, 95, track(), b));
        assertTrue(b.refused);
    }

    @Test
    public void outOfImageRunRefuses() {
        assertFalse(found("staff", 199, 201));
    }

    @Test
    public void localPaperCannotBecomeAnOwnedRun() {
        var g = raw("staff");
        for (int y = 93; y <= 95; y++) g[y * W + 160] = (byte) 255;
        assertFalse(
                SeparatedRuleRunOwnership.matches(
                        g,
                        W,
                        H,
                        160,
                        93,
                        95,
                        track(),
                        new AttachedApproachBranches.Budget(64_000_000)));
    }

    @Test
    public void clippedPieceOfLongerInkRefuses() {
        var g = raw("staff");
        g[92 * W + 160] = 0;
        assertFalse(
                SeparatedRuleRunOwnership.matches(
                        g,
                        W,
                        H,
                        160,
                        93,
                        95,
                        track(),
                        new AttachedApproachBranches.Budget(64_000_000)));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Source-free shaft continuity and cap-material controls. */
public final class CompleteBeamJointContinuityTest {
    private static final int W = 400, H = 240;

    private record Input(byte[] gray, byte[] labels, int[] members) {}

    private static Input field() {
        byte[] g = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int rail = 0; rail < 5; rail++)
            for (int x = 10; x <= 390; x++) g[(100 + 16 * rail) * W + x] = 0;
        for (int x = 110; x <= 166; x++) {
            int bottom = 146 - Math.round((x - 110) * 8f / 56);
            for (int y = bottom - 9; y <= bottom; y++) g[y * W + x] = 0;
        }
        for (int[] shaft :
                new int[][] {
                    {110, 84, 146},
                    {111, 84, 146},
                    {112, 84, 146},
                    {166, 76, 138},
                    {167, 76, 138},
                    {168, 76, 138}
                }) for (int y = shaft[1]; y <= shaft[2]; y++) g[y * W + shaft[0]] = 0;
        for (int[] c : new int[][] {{122, 84}, {178, 76}})
            for (int y = c[1] - 8; y <= c[1] + 8; y++)
                for (int x = c[0] - 11; x <= c[0] + 11; x++)
                    if ((x - c[0]) * (x - c[0]) / 121. + (y - c[1]) * (y - c[1]) / 64. <= 1)
                        g[y * W + x] = 0;
        int[] members = new int[12];
        int n = 0;
        for (int y = 139; y <= 141; y++)
            for (int x = 115; x <= 118; x++) {
                int p = y * W + x;
                members[n++] = p;
                labels[p] = 2;
            }
        return new Input(g, labels, members);
    }

    private static int[] reference(byte[] gray, int y0, int y1) {
        int[] values = new int[y1 - y0 + 1];
        for (int y = y0; y <= y1; y++) values[y - y0] = gray[y * W + 200] & 255;
        return values;
    }

    private static CompleteBeamCapRoles.Shafts shafts(
            Input in, int y0, int y1, int le, int re, int direction) {
        return CompleteBeamCapRoles.shafts(
                in.gray(),
                W,
                H,
                reference(in.gray(), y0, y1),
                y0,
                111,
                167,
                le,
                re,
                direction,
                16,
                new LiteralStaffCompleteBeamJoint.Budget(32_000_000));
    }

    private static Input downward() {
        byte[] g = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int rail = 0; rail < 7; rail++)
            for (int x = 10; x <= 390; x++) g[(100 + 16 * rail) * W + x] = 0;
        for (int[] s :
                new int[][] {
                    {110, 84, 190},
                    {111, 84, 190},
                    {112, 84, 190},
                    {166, 76, 190},
                    {167, 76, 190},
                    {168, 76, 190}
                }) for (int y = s[1]; y <= s[2]; y++) g[y * W + s[0]] = 0;
        return new Input(g, labels, new int[0]);
    }

    private static LiteralStaffCompleteBeamJoint.Result observeLeftCap(
            Input in, boolean aaProfile) {
        int[] members = Arrays.copyOf(in.members(), 17);
        byte[] labels = in.labels().clone();
        int n = in.members().length;
        for (int x = 110; x <= 114; x++) {
            int p = 140 * W + x;
            members[n++] = p;
            labels[p] = 2;
        }
        if (aaProfile)
            for (int y = 114; y <= 130; y++)
                if (in.gray()[y * W + 200] == (byte) 255) {
                    in.gray()[y * W + 110] = (byte) 254;
                    in.gray()[y * W + 112] = (byte) 254;
                }
        return LiteralStaffCompleteBeamJoint.observe(
                in.gray(),
                W,
                H,
                110,
                166,
                100,
                16,
                116.5f,
                140,
                110,
                139,
                118,
                141,
                -8f / 56,
                new LiteralStaffCompleteBeamJoint.Budget(32_000_000),
                new LiteralStaffCompleteBeamJoint.Ownership(labels, members, 146, 138, 1));
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    @org.junit.Test
    public void nativeShaftRemainsEightConnectedAcrossWitness() {
        require(shafts(field(), 92, 172, 146, 138, 1) != null, "upward positive");
        Input down = downward();
        require(shafts(down, 92, 212, 146, 138, -1) != null, "downward positive");
    }

    @org.junit.Test
    public void paperGapsCannotUseDistantWitness() {
        Input up = field();
        for (int y = 120; y <= 122; y++)
            for (int x = 110; x <= 112; x++) up.gray()[y * W + x] = (byte) 255;
        require(shafts(up, 92, 172, 146, 138, 1) == null, "upward gap refusal");
        Input down = downward();
        for (int y = 153; y <= 155; y++)
            for (int x = 110; x <= 112; x++) down.gray()[y * W + x] = (byte) 255;
        require(shafts(down, 92, 212, 146, 138, -1) == null, "downward gap refusal");
    }

    @org.junit.Test
    public void offsetJumpCannotBridgeShaft() {
        Input up = field();
        for (int y = 120; y <= 122; y++) {
            for (int x = 110; x <= 112; x++) up.gray()[y * W + x] = (byte) 255;
            for (int x = 114; x <= 116; x++) up.gray()[y * W + x] = 0;
        }
        require(shafts(up, 92, 172, 146, 138, 1) == null, "upward offset refusal");
        Input down = downward();
        for (int y = 153; y <= 155; y++) {
            for (int x = 110; x <= 112; x++) down.gray()[y * W + x] = (byte) 255;
            for (int x = 114; x <= 116; x++) down.gray()[y * W + x] = 0;
        }
        require(shafts(down, 92, 212, 146, 138, -1) == null, "downward offset refusal");
    }

    @org.junit.Test
    public void nativeAndFarEndpointClippingRefuse() {
        Input up = field();
        for (int x = 110; x <= 112; x++) up.gray()[146 * W + x] = (byte) 255;
        require(shafts(up, 92, 172, 146, 138, 1) == null, "upward native endpoint clip");
        Input down = downward();
        for (int x = 110; x <= 112; x++) down.gray()[178 * W + x] = (byte) 255;
        require(shafts(down, 92, 212, 146, 138, -1) == null, "downward far endpoint clip");
    }

    @org.junit.Test
    public void semanticCapMaterialNeedsMatchingProfile() {
        Input raw = field();
        var s = shafts(raw, 92, 172, 146, 138, 1);
        require(s != null, "profile available");
        int[] ref = reference(raw.gray(), 92, 172);
        require(
                CompleteBeamCapRoles.validateSemanticMembersObserved(
                                raw.gray(),
                                W,
                                H,
                                new int[] {140 * W + 110},
                                ref,
                                92,
                                s.left().right() + 1,
                                s.right().left() - 1,
                                s)
                        == null,
                "dark cap material");
        raw.gray()[140 * W + 110] = (byte) 255;
        require(
                CompleteBeamCapRoles.validateSemanticMembersObserved(
                                raw.gray(),
                                W,
                                H,
                                new int[] {140 * W + 110},
                                ref,
                                92,
                                s.left().right() + 1,
                                s.right().left() - 1,
                                s)
                        != null,
                "left cap paper member");
        Input right = field();
        var rs = shafts(right, 92, 172, 146, 138, 1);
        int[] rr = reference(right.gray(), 92, 172);
        right.gray()[133 * W + 168] = (byte) 255;
        require(
                CompleteBeamCapRoles.validateSemanticMembersObserved(
                                right.gray(),
                                W,
                                H,
                                new int[] {133 * W + 168},
                                rr,
                                92,
                                rs.left().right() + 1,
                                rs.right().left() - 1,
                                rs)
                        != null,
                "right cap paper member");
    }

    @org.junit.Test
    public void aaCapNeedsIndependentSameColumnWitness() {
        Input unsupported = field();
        var s = shafts(unsupported, 92, 172, 146, 138, 1);
        int[] ref = reference(unsupported.gray(), 92, 172);
        unsupported.gray()[140 * W + 110] = (byte) 254;
        require(
                CompleteBeamCapRoles.validateSemanticMembersObserved(
                                unsupported.gray(),
                                W,
                                H,
                                new int[] {140 * W + 110},
                                ref,
                                92,
                                s.left().right() + 1,
                                s.right().left() - 1,
                                s)
                        != null,
                "unsupported AA remains unknown");
        Input supported = field();
        for (int y = 114; y <= 130; y++)
            if (supported.gray()[y * W + 200] == (byte) 255) {
                supported.gray()[y * W + 110] = (byte) 254;
                supported.gray()[y * W + 112] = (byte) 254;
            }
        var ss = shafts(supported, 92, 172, 146, 138, 1);
        int[] sr = reference(supported.gray(), 92, 172);
        supported.gray()[140 * W + 110] = (byte) 254;
        require(
                CompleteBeamCapRoles.validateSemanticMembersObserved(
                                supported.gray(),
                                W,
                                H,
                                new int[] {140 * W + 110},
                                sr,
                                92,
                                ss.left().right() + 1,
                                ss.right().left() - 1,
                                ss)
                        == null,
                "same-column measured AA profile supports material");
    }

    @org.junit.Test
    public void fullCallerKeepsDarkCapAndRejectsPaperOrUnsupportedAa() {
        require(observeLeftCap(field(), false).complete(), "dark full-caller cap positive");
        Input paper = field();
        paper.gray()[140 * W + 110] = (byte) 255;
        require(!observeLeftCap(paper, false).complete(), "paper cap member refusal");
        Input aa = field();
        aa.gray()[140 * W + 110] = (byte) 254;
        require(observeLeftCap(aa, true).complete(), "profile-supported AA full-caller positive");
    }
}

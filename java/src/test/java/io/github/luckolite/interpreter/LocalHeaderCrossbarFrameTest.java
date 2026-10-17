// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;

/** Original curved staff frames, locally level header crossbars and independent-note controls. */
public class LocalHeaderCrossbarFrameTest {
    HeaderCrossbarOwnershipTest page(
            float slope, boolean clef, boolean connector, boolean following) {
        var p = new HeaderCrossbarOwnershipTest().page(clef, connector, following, 0);
        byte[] a = p.labels.clone(), g = p.gray.clone();
        Arrays.fill(p.labels, (byte) 0);
        Arrays.fill(p.gray, (byte) 255);
        for (int x = 0; x < 600; x++)
            for (int y = 0; y < 280; y++) {
                int yy = y + Math.round(slope * (94 - 300));
                if (yy >= 0 && yy < 280) {
                    p.labels[yy * 600 + x] = a[y * 600 + x];
                    p.gray[yy * 600 + x] = g[y * 600 + x];
                }
            }
        return p;
    }

    void expect(float slope, boolean clef, boolean connector, boolean following, boolean removed) {
        var p = page(slope, clef, connector, following);
        try {
            Class<?> root = OmrScoreInterpreter.class,
                    sc = Class.forName(root.getName() + "$Staff"),
                    hc = Class.forName(root.getName() + "$Component");
            var ctor = sc.getDeclaredConstructor(float.class, float.class, float.class);
            ctor.setAccessible(true);
            Object staff = ctor.newInstance(100f, 164f, 16f);
            var track = sc.getDeclaredField("pitchTrack");
            track.setAccessible(true);
            track.set(
                    staff,
                    StaffPitchTrack.fromVerifiedSamples(
                            java.util.List.of(
                                    new float[] {0, 164 + Math.round(slope * (94 - 300)), 16},
                                    new float[] {200, 164 + Math.round(slope * (94 - 300)), 16},
                                    new float[] {599, 164, 16})));
            var components =
                    root.getDeclaredMethod(
                            "findComponents", byte[].class, int.class, int.class, byte.class);
            components.setAccessible(true);
            var heads = (java.util.List<?>) components.invoke(null, p.labels, 600, 280, (byte) 2);
            var clefs = (java.util.List<?>) components.invoke(null, p.labels, 600, 280, (byte) 3);
            var symbols = (java.util.List<?>) components.invoke(null, p.labels, 600, 280, (byte) 5);
            var method =
                    root.getDeclaredMethod(
                            "isOwnedHeaderCrossbar",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            hc,
                            java.util.List.class,
                            java.util.List.class,
                            java.util.List.class,
                            java.util.List.class);
            method.setAccessible(true);
            var cx = hc.getDeclaredField("centerX");
            cx.setAccessible(true);
            int tested = 0;
            for (Object head : heads)
                if (cx.getFloat(head) > 85 && cx.getFloat(head) < 103) {
                    tested++;
                    assertEquals(
                            removed,
                            (boolean)
                                    method.invoke(
                                            null,
                                            p.labels,
                                            p.gray,
                                            600,
                                            280,
                                            head,
                                            java.util.List.of(staff),
                                            heads,
                                            clefs,
                                            symbols));
                }
            assertEquals(2, tested);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void raisedHeaderUsesLocalRules() {
        expect(.03f, true, true, true, true);
    }

    @Test
    public void loweredHeaderUsesLocalRules() {
        expect(-.03f, true, true, true, true);
    }

    @Test
    public void largerLocalOffsetRetainsOwnership() {
        expect(.04f, true, true, true, true);
    }

    @Test
    public void noClefKeepsIndependentInk() {
        expect(.03f, false, true, true, false);
    }

    @Test
    public void disconnectedBarsRemain() {
        expect(.03f, true, false, true, false);
    }

    @Test
    public void followingNoteRequired() {
        expect(.03f, true, true, false, false);
    }

    @Test
    public void inputUnchanged() {
        var p = page(.03f, true, true, true);
        var a = p.labels.clone();
        var g = p.gray.clone();
        p.normalized();
        assertArrayEquals(a, p.labels);
        assertArrayEquals(g, p.gray);
    }
}

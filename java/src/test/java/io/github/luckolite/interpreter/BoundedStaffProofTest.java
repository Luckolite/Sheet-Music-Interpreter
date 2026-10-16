// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original five-rule rasters with a partially observed curved staff. */
public class BoundedStaffProofTest {
    private static float[] frame(int x, boolean ink, boolean inside) throws Exception {
        int w = 600, h = 300;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        if (ink)
            for (int line = 0; line < 5; line++)
                for (int xx = 20; xx < 580; xx++) {
                    int y = 160 + line * 10;
                    gray[y * w + xx] = 30;
                    labels[y * w + xx] = 4;
                }
        Class<?> staffType = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var ctor = staffType.getDeclaredConstructor(float.class, float.class, float.class);
        ctor.setAccessible(true);
        Object staff = ctor.newInstance(160f, 200f, 10f);
        var track = staffType.getDeclaredField("pitchTrack");
        track.setAccessible(true);
        track.set(
                staff,
                StaffPitchTrack.fromVerifiedSamples(
                        inside
                                ? List.of(new float[] {20, 196, 10}, new float[] {580, 196, 10})
                                : x < 300
                                        ? List.of(
                                                new float[] {300, 200, 10},
                                                new float[] {500, 204, 10})
                                        : List.of(
                                                new float[] {100, 204, 10},
                                                new float[] {300, 200, 10})));
        Class<?> headType = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var hc = headType.getDeclaredConstructors()[0];
        hc.setAccessible(true);
        Object head = hc.newInstance(80, x - 5, x + 5, 181, 189, (float) x, 185f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "localStaffPitch",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        staffType,
                        headType);
        m.setAccessible(true);
        return (float[]) m.invoke(null, labels, gray, w, h, staff, head);
    }

    @Test
    public void observedRulesRecoverPitchBeyondLastSample() throws Exception {
        float[] f = frame(500, true, false);
        assertEquals(200, f[0], .6f);
        assertEquals(3, Math.round((f[0] - 185) * 2 / f[1]));
    }

    @Test
    public void sampledFrameRetainsAuthorityInsideObservedRange() throws Exception {
        assertArrayEquals(new float[] {196, 10}, frame(500, true, true), .01f);
    }

    @Test
    public void observedRulesRecoverPitchBeforeFirstSample() throws Exception {
        float[] f = frame(100, true, false);
        assertEquals(200, f[0], .6f);
        assertEquals(3, Math.round((f[0] - 185) * 2 / f[1]));
    }

    @Test
    public void blankTailCannotInventLocalRuleProof() throws Exception {
        assertArrayEquals(new float[] {196, 10}, frame(500, false, false), .01f);
    }
}

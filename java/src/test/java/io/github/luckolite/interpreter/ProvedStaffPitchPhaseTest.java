// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent broad physical staff with misleading short local strips. */
public class ProvedStaffPitchPhaseTest {
    private static final int W = 600, H = 320;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    private void page(boolean misleading) {
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 0; x < W; x++) {
                int row = 120 + line * 20;
                gray[row * W + x] = 30;
                for (int y = row - 3; y <= row + 3; y++) labels[y * W + x] = 4;
            }
        if (misleading) {
            for (int y = 0; y < H; y++) Arrays.fill(gray, y * W + 200, y * W + 361, (byte) 255);
            for (int line = 0; line < 5; line++)
                for (int x = 230; x <= 330; x++)
                    if (x <= 252 || x >= 308) gray[(115 + line * 20) * W + x] = 30;
        }
        for (int y = 165; y <= 177; y++)
            for (int x = 270; x <= 290; x++)
                if ((x - 280) * (x - 280) / 100.0 + (y - 171) * (y - 171) / 36.0 <= 1) {
                    gray[y * W + x] = 20;
                    labels[y * W + x] = 2;
                }
    }

    private float[] local(float bottom, float headY, boolean proved) throws Exception {
        Class<?> st = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff"),
                ht = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var sc = st.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        Object staff = sc.newInstance(bottom - 80, bottom, 20f);
        var flag = st.getDeclaredField("printedPhase");
        flag.setAccessible(true);
        flag.setBoolean(staff, proved);
        var hc = ht.getDeclaredConstructors()[0];
        hc.setAccessible(true);
        Object head = hc.newInstance(200, 270, 290, 165, 177, 280f, headY);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "localStaffPitch",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        st,
                        ht);
        method.setAccessible(true);
        byte[] g = gray.clone(), l = labels.clone();
        float[] result = (float[]) method.invoke(null, labels, gray, W, H, staff, head);
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
        return result;
    }

    private void prerequisite() {
        assertNull(StaffPitchTrack.localPrintedRules(labels, gray, W, H, 280, 270, 290, 200, 20));
        assertNull(StaffPitchTrack.localFadedRules(labels, gray, W, H, 280, 270, 290, 200, 20));
        assertArrayEquals(
                new float[] {195, 20},
                StaffPitchTrack.localOccludedRules(labels, gray, W, H, 280, 270, 290, 200, 20),
                .001f);
    }

    @Test
    public void weakLocalStripCannotChangeAlreadyProvedPitch() throws Exception {
        page(true);
        prerequisite();
        float[] a = local(200, 171, true);
        assertArrayEquals(new float[] {200, 20}, a, .001f);
        assertEquals(3, Math.round((a[0] - 171) / (a[1] * .5f)));
    }

    @Test
    public void unprovedSeedKeepsExistingOccludedCorrection() throws Exception {
        page(true);
        prerequisite();
        assertArrayEquals(new float[] {195, 20}, local(200, 171, false), .001f);
    }

    @Test
    public void smallSamePitchAdjustmentRemainsAvailable() throws Exception {
        page(true);
        prerequisite();
        assertArrayEquals(new float[] {195, 20}, local(200, 160, true), .001f);
    }

    @Test
    public void completeLocalFiveRulesCanCorrectProvedSeed() throws Exception {
        page(false);
        assertNotNull(
                StaffPitchTrack.localPrintedRules(labels, gray, W, H, 280, 270, 290, 207, 20));
        assertArrayEquals(new float[] {200, 20}, local(207, 171, true), .001f);
    }
}

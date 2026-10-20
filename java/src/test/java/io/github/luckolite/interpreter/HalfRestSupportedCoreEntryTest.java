// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Exact sourcefree optical rasters; direct operands are acquired from the original narrow projection grammar. */
@RunWith(Parameterized.class)
public final class HalfRestSupportedCoreEntryTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(
                new Object[][] {
                    {"early_remote_g14_phase0", 14f, 0f, 1, 1, 180, 195},
                    {"early_remote_g16_phase0", 16f, 0f, 1, 1, 180, 197},
                    {"early_remote_g20_phase0", 20f, 0f, 1, 1, 180, 202},
                    {"early_remote_g16_phase0.5", 16f, 0.5f, 1, 1, 180, 197},
                    {"early_adverse_stem", 16f, 0f, 0, 0, 180, 198},
                    {"early_adverse_branch", 16f, 0f, 0, 0, 180, 206},
                    {"early_adverse_lower", 16f, 0f, 0, 0, 180, 197},
                    {"early_adverse_oval", 16f, 0f, 0, 0, 180, 197}
                });
    }

    private final String name;
    private final float gap, phase;
    private final int direct, full, expectedLeft, expectedRight;

    public HalfRestSupportedCoreEntryTest(String n, float g, float p, int d, int f, int l, int r) {
        name = n;
        gap = g;
        phase = p;
        direct = d;
        full = f;
        expectedLeft = l;
        expectedRight = r;
    }

    private byte[] gray() throws IOException {
        try (InputStream in =
                getClass().getResourceAsStream("/half-rest-early-remote-blur/" + name + ".gray")) {
            assertNotNull(name, in);
            byte[] a = in.readNBytes(480 * 220 + 1);
            assertEquals(480 * 220, a.length);
            return a;
        }
    }

    private record Proposal(int left, int right, int top, int[] ink) {}

    private Proposal acquired(byte[] a) {
        float staffTop = 60 + phase;
        int top = Math.round(staffTop + gap * .25f),
                bottom = Math.round(staffTop + 4 * gap + gap * .3f);
        boolean[] mask = new boolean[bottom - top + 1];
        int[] columns = new int[480];
        for (int y = top; y <= bottom; y++) {
            int dark = 0, longest = 0, run = 0;
            for (int x = 0; x < 480; x++)
                if ((a[y * 480 + x] & 255) < 170) {
                    dark++;
                    longest = Math.max(longest, ++run);
                } else run = 0;
            float nearest = staffTop + Math.round((y - staffTop) / gap) * gap;
            mask[y - top] =
                    Math.abs(y - nearest) <= gap * .2f && (dark > 480 * .25f || longest >= gap * 5);
            if (!mask[y - top])
                for (int x = 0; x < 480; x++) if ((a[y * 480 + x] & 255) < 170) columns[x]++;
        }
        int start = -1, left = -1, right = -1, count = 0;
        float centre = (expectedLeft + expectedRight) * .5f;
        for (int x = 0; x <= 480; x++)
            if (x < 480 && columns[x] >= 2) {
                if (start < 0) start = x;
            } else if (start >= 0) {
                if (centre >= start && centre <= x - 1) {
                    left = start;
                    right = x - 1;
                    count++;
                }
                start = -1;
            }
        assertEquals(name + " actual proposal nonvacuity", 1, count);
        assertEquals(expectedLeft, left);
        assertEquals(expectedRight, right);
        if (direct == 1)
            assertTrue(
                    name + " helper width domain",
                    right - left + 1 >= gap * .7f && right - left + 1 <= gap * 1.6f);
        int[] ink = new int[mask.length];
        for (int y = top; y <= bottom; y++)
            if (!mask[y - top])
                for (int x = left; x <= right; x++)
                    if ((a[y * 480 + x] & 255) < 170) ink[y - top]++;
        return new Proposal(left, right, top, ink);
    }

    @Test
    public void explicitSupportedCoreEntry() throws Exception {
        byte[] a = gray(), before = a.clone();
        Proposal p = acquired(a);
        Method method =
                HalfRestRuleBody.class.getDeclaredMethod(
                        "findSupportedCore",
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class);
        method.setAccessible(true);
        int[] found =
                (int[])
                        method.invoke(
                                null, a, 480, 220, 60 + phase, gap, p.left, p.right, p.top, p.ink);
        assertEquals(name + " explicit additive entry", direct == 1, found != null);
        assertArrayEquals(before, a);
    }
}

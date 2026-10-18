// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class ContextualMfBudgetOriginalTest {
    @org.junit.Test
    public void repeatedAmbiguousIntervalsAbstain() {
        int w = 80, h = 2000;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int y = 1; y < h - 1; y++) {
            if (y % 5 == 0) {
                for (int x = 10; x <= 65; x++) g[y * w + x] = 20;
            } else for (int x : new int[] {10, 26, 42, 58}) g[y * w + x] = 20;
        }
        byte[] old = g.clone();
        long start = System.nanoTime();
        var e =
                ContextualMfBodyEvidence.inspect(
                        "mf",
                        g,
                        w,
                        h,
                        0,
                        0,
                        w,
                        h,
                        16,
                        ContextualMfBodyEvidence.withoutNotation(g, w, h));
        long elapsed = System.nanoTime() - start;
        if (e.supported() || !e.reason().equals("resource-limit") || !Arrays.equals(g, old))
            throw new AssertionError(
                    "Bounded unknown-body resource abstention required: " + e.reason());
        System.out.println(
                "REPEATED_AMBIGUOUS_INTERVALS resource-limit readonly=true ms="
                        + elapsed / 1000000.0);
    }
}

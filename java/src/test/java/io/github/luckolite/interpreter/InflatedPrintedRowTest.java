// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class InflatedPrintedRowTest {
    static MeasureRegion bar(float a, float b) {
        return new MeasureRegion(a, b, .2f, .8f);
    }

    @org.junit.Test
    public void originalGeometryControls() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        for (int kind = 0; kind < 5; kind++) {
            int w = 1000, h = 300;
            byte[] g = new byte[w * h];
            Arrays.fill(g, (byte) 255);
            for (int y = 100; y <= 180; y += 20) for (int x = 90; x <= 910; x++) g[y * w + x] = 0;
            for (int x : new int[] {305, 505, 705})
                for (int y = 100; y <= 180; y++) g[y * w + x] = 0;
            var raw = List.of(bar(.1f, .3f), bar(.31f, .5f), bar(.51f, .7f), bar(.71f, .9f));
            var fitted =
                    List.of(
                            bar(.1f, .198f),
                            bar(.202f, .3f),
                            bar(.31f, .398f),
                            bar(.402f, .5f),
                            bar(.51f, .598f),
                            bar(.602f, .7f),
                            bar(.71f, .798f),
                            bar(.802f, .9f));
            if (kind == 1) for (int y = 100; y <= 180; y++) g[y * w + 200] = 0;
            if (kind == 2)
                for (int y = 101; y < 180; y++) if (y % 20 != 0) g[y * w + 505] = (byte) 255;
            if (kind == 3) for (int y = 120; y <= 160; y++) g[y * w + 200] = 0;
            if (kind == 4)
                fitted =
                        List.of(
                                bar(.1f, .198f),
                                bar(.202f, .3f),
                                bar(.31f, .5f),
                                bar(.51f, .7f),
                                bar(.71f, .9f));
            var got = PrintedMeasureRhythmGuard.preserveCountNeutralRows(raw, fitted, g, w, h);
            var expected = kind == 1 || kind == 2 || kind == 4 ? fitted : raw;
            if (!got.equals(expected))
                throw new AssertionError("kind=" + kind + " got=" + got.size());
        }
        System.out.println("PASS 5 original inflated-row cases");
    }
}

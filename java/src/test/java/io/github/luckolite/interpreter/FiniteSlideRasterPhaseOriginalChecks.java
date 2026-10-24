// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class FiniteSlideRasterPhaseOriginalChecks {
    static java.util.List<NoteSlideDetector.Stroke> scene(
            int scale, int direction, float phase, int tip, boolean foreign) {
        var s =
                new FiniteConnectedSlidePortsOriginalChecks.Scene(
                        scale, false, direction, -direction);
        byte[] clean = new byte[s.w * s.h];
        Arrays.fill(clean, (byte) 255);
        for (int x = 132 * scale; x <= 148 * scale; x++) {
            float cy = 114 * scale + phase + (x - 132 * scale) * .3f;
            int lo = x < 132 * scale + tip ? (int) Math.floor(cy) - 1 : (int) Math.floor(cy),
                    hi = x < 132 * scale + tip ? lo : (int) Math.ceil(cy) + 1;
            for (int y = lo; y <= hi; y++) {
                s.gray[y * s.w + x] = 35;
                clean[y * s.w + x] = 35;
            }
        }
        if (foreign) s.heads.add(new NoteSlideDetector.Head(140 * scale, 116 * scale, s.gap, 1, 0));
        return FiniteConnectedSlidePorts.find(
                s.gray, s.w, s.h, s.staffs, s.heads, clean, null, null);
    }

    static int checks;

    static void check(boolean v, String m) {
        checks++;
        if (!v) throw new AssertionError(m);
        System.out.println("PASS " + m);
    }

    public static void main(String[] a) {
        boolean baseline = a.length > 0;
        for (int scale : new int[] {1, 2, 3})
            for (int dir : new int[] {-1, 1})
                for (int tip = 1; tip <= 3; tip++) {
                    var good = scene(scale, dir, 0, tip, false);
                    if (baseline) {
                        System.out.println(
                                "BASELINE scale="
                                        + scale
                                        + " dir="
                                        + dir
                                        + " thin tip columns="
                                        + tip
                                        + " result="
                                        + good);
                        continue;
                    }
                    check(
                            good.size() == 1
                                    && good.get(0).noteIndex() == 1
                                    && good.get(0).left() == 132 * scale
                                    && good.get(0).right() == 148 * scale,
                            "complete raster footprint retains every column scale="
                                    + scale
                                    + " dir="
                                    + dir
                                    + " tip="
                                    + tip);
                    check(
                            scene(scale, dir, 0, tip, true).isEmpty(),
                            "foreign attack still vetoes raster footprint scale="
                                    + scale
                                    + " dir="
                                    + dir
                                    + " tip="
                                    + tip);
                }
        if (!baseline)
            for (int scale : new int[] {1, 2, 3})
                for (int dir : new int[] {-1, 1}) {
                    double tolerance = 14 * scale * .1;
                    int row = 114 * scale;
                    double edge = row + dir * (tolerance + .5);
                    check(
                            FiniteConnectedSlidePorts.columnFootprintFits(
                                    row, row, Math.nextAfter(edge, row), tolerance),
                            "unit-pixel boundary inside scale=" + scale + " direction=" + dir);
                    check(
                            !FiniteConnectedSlidePorts.columnFootprintFits(
                                    row,
                                    row,
                                    Math.nextAfter(
                                            edge,
                                            dir > 0
                                                    ? Double.POSITIVE_INFINITY
                                                    : Double.NEGATIVE_INFINITY),
                                    tolerance),
                            "unit-pixel boundary just outside scale="
                                    + scale
                                    + " direction="
                                    + dir);
                }
        if (!baseline) System.out.println("CHECKS=" + checks);
    }
}

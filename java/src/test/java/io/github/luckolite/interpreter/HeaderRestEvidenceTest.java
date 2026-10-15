// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original procedural pixels; contains no score-derived fixture. */
public class HeaderRestEvidenceTest {
    @org.junit.Test
    public void proceduralControls() {
        main(new String[0]);
    }

    static final int W = 800, H = 360, GAP = 16, TOP = 100;
    static final MeasureRegion M = new MeasureRegion(.125f, .5f, .16f, .88f);

    static MeterChangeDetector.Crop crop(int right, int line) {
        return new MeterChangeDetector.Crop(70, line - 3, right, line + GAP * 4 + 3, line, GAP);
    }

    static byte[] frame(boolean dot, boolean plate) {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i < 5; i++) for (int x = 20; x < 780; x++) g[(TOP + i * GAP) * W + x] = 0;
        if (plate)
            for (int y = TOP + GAP + 1; y <= TOP + GAP + 6; y++)
                for (int x = 267; x <= 293; x++) g[y * W + x] = 0;
        if (dot)
            for (int y = TOP + GAP + 7; y <= TOP + GAP + 10; y++)
                for (int x = 302; x <= 305; x++) g[y * W + x] = 0;
        return g;
    }

    static List<WholeRestClassifier.Decision> read(
            boolean dot, boolean plate, List<MeterChangeDetector.Crop> headers) {
        return WholeRestClassifier.detect(
                frame(dot, plate),
                W,
                H,
                List.of(M),
                List.of(new SixteenthRestDetector.Staff(TOP, TOP + GAP * 4, GAP, 0, 1)),
                List.of(),
                List.of(),
                Set.of(),
                headers);
    }

    static void check(
            String name, boolean expected, boolean dot, List<MeterChangeDetector.Crop> headers) {
        var decisions = read(dot, true, headers);
        if (decisions.size() != 1) throw new AssertionError(name + " " + decisions);
        boolean actual = decisions.get(0).status() == WholeRestClassifier.Status.PROVED_FULL;
        if (actual != expected) throw new AssertionError(name + " " + decisions);
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        check("accepted header centers complete plate", true, false, List.of(crop(160, TOP)));
        check("missing header abstains", false, false, List.of());
        check("conflicting bounds abstain", false, false, List.of(crop(160, TOP), crop(164, TOP)));
        check("other staff cannot own header", false, false, List.of(crop(160, 200)));
        check("header extending into rest rejected", false, false, List.of(crop(285, TOP)));
        check("owned dot stays literal", false, true, List.of(crop(160, TOP)));
        if (!read(false, false, List.of(crop(160, TOP))).isEmpty())
            throw new AssertionError("invented plate");
        System.out.println("PASS no plate remains absent");
    }
}

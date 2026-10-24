// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original discarded-fragment, hollow-component and density-refusal controls. */
public class FiniteSlideSpanAdversarialChecks {
    static int checks;

    static void check(boolean ok, String name) {
        checks++;
        if (!ok) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        for (int scale : new int[] {1, 2, 3})
            for (int dir : new int[] {-1, 1}) {
                var s = new FiniteConnectedSlidePortsOriginalChecks.Scene(scale, false, dir, -dir);
                s.line();
                // Separate3-column low-area run is below pieces()' width gate, but contradicts
                // accepting only the later central/right surviving component.
                for (int x = 126 * scale; x <= 128 * scale; x++) {
                    float cy = 114.1f * scale + (x - 132 * scale) * .1f;
                    for (int y = Math.round(cy); y < Math.round(cy) + 2 * scale; y++)
                        s.gray[y * s.w + x] = 35;
                }
                var before = s.port();
                System.out.println(
                        "PRECEDING span scale=" + scale + " opposed=" + dir + " " + before);
                check(
                        before.isEmpty()
                                || before.stream().allMatch(x -> x.left() <= 126 * scale + scale),
                        "preceding fragment vetoed or captured in complete span scale="
                                + scale
                                + " opposed="
                                + dir);
                s = new FiniteConnectedSlidePortsOriginalChecks.Scene(scale, false, dir, -dir);
                s.line();
                for (int x = 162 * scale; x <= 164 * scale; x++) {
                    float cy = 114.1f * scale + (x - 132 * scale) * .1f;
                    for (int y = Math.round(cy); y < Math.round(cy) + 2 * scale; y++)
                        s.gray[y * s.w + x] = 35;
                }
                var after = s.port();
                System.out.println(
                        "FOLLOWING span scale=" + scale + " opposed=" + dir + " " + after);
                check(
                        after.isEmpty()
                                || after.stream().allMatch(x -> x.right() >= 164 * scale - scale),
                        "following fragment vetoed or captured in complete span scale="
                                + scale
                                + " opposed="
                                + dir);
                s = new FiniteConnectedSlidePortsOriginalChecks.Scene(scale, false, dir, -dir);
                FiniteSlideAdversarialProbe.loop(
                        s, 132 * scale, 160 * scale, 114.1f * scale, 116.9f * scale, 1.5f * scale);
                check(
                        s.port().isEmpty(),
                        "closed hollow interior cannot become line scale="
                                + scale
                                + " opposed="
                                + dir);
                s = new FiniteConnectedSlidePortsOriginalChecks.Scene(scale, true, dir, -dir);
                s.line();
                for (int x = 145 * scale; x <= 147 * scale; x++)
                    for (int y = 80 * scale; y <= 150 * scale; y++) s.gray[y * s.w + x] = 35;
                check(
                        s.port().isEmpty(),
                        "unknown tall raw crossing cannot be omitted scale="
                                + scale
                                + " opposed="
                                + dir);
            }
        var s = new FiniteConnectedSlidePortsOriginalChecks.Scene(1, false, -1, 1);
        s.line();
        var old = List.of(new NoteSlideDetector.Stroke(10, 11, 20, 12, -1, 0, true));
        var invalid =
                List.of(
                        new NoteSlideDetector.Head(110, 112, 65, 0, 0),
                        new NoteSlideDetector.Head(180, 119, 65, 0, 0));
        byte[] clean = NoteSlideDetector.removeStaffLines(s.gray, s.w, s.h, s.staffs);
        check(
                FiniteConnectedSlidePorts.add(
                                old, s.gray, s.w, s.h, s.staffs, invalid, clean, null, null)
                        .equals(old),
                "finite density refusal preserves existing JOIN result");
        var before = s.gray.clone();
        s.port();
        check(Arrays.equals(before, s.gray), "source grayscale remains unchanged");
        check(
                FiniteConnectedSlidePorts.add(
                                old, s.gray, s.w, s.h, s.staffs, s.heads, clean, null, null)
                        .get(0)
                        .equals(old.get(0)),
                "existing JOIN result retained when extra route succeeds");
        System.out.println("CHECKS=" + checks);
    }
}

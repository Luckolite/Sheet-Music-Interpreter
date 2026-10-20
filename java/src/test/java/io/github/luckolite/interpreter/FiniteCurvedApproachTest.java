// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original finite quarter-circle approaches; no commercial image samples or inherited flags. */
public class FiniteCurvedApproachTest {
    static final int W = 360, H = 260;

    record Scene(byte[] gray, List<NoteSlideDetector.Head> heads) {}

    static Scene scene(int gap, String kind) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        int x0 = 70, y0 = 125, r = gap;
        double thick = Math.max(1, gap * .08);
        List<double[]> line = new ArrayList<>();
        for (int i = 0; i <= 80; i++) {
            double t = i * Math.PI / 160;
            line.add(new double[] {x0 + r * Math.sin(t), y0 + r * Math.cos(t)});
        }
        if (kind.equals("phrase")) {
            line.clear();
            for (int i = 0; i <= 100; i++) {
                double t = i / 100.;
                line.add(new double[] {x0 + 2 * r * t, y0 + 4 * r * t * (1 - t)});
            }
        }
        if (kind.equals("oval")) {
            line.clear();
            for (int i = 0; i <= 160; i++) {
                double t = i * Math.PI / 80.;
                line.add(
                        new double[] {
                            x0 + r * .5 + r * .5 * Math.cos(t), y0 + r * .5 + r * .5 * Math.sin(t)
                        });
            }
        }
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                for (int i = 1; i < line.size(); i++) {
                    double[] a = line.get(i - 1), b = line.get(i);
                    double dx = b[0] - a[0],
                            dy = b[1] - a[1],
                            u =
                                    Math.max(
                                            0,
                                            Math.min(
                                                    1,
                                                    ((x - a[0]) * dx + (y - a[1]) * dy)
                                                            / (dx * dx + dy * dy)));
                    if (Math.hypot(x - a[0] - u * dx, y - a[1] - u * dy) <= thick) {
                        g[y * W + x] = 0;
                        break;
                    }
                }
        var heads = new ArrayList<NoteSlideDetector.Head>();
        heads.add(new NoteSlideDetector.Head(x0 + 2 * r, y0 - r, gap, 0, 0));
        if (kind.equals("ambiguous"))
            heads.add(new NoteSlideDetector.Head(x0 + 2 * r + 1, y0 - r + 1, gap, 0, 0));
        if (kind.equals("foreignHead"))
            heads.add(new NoteSlideDetector.Head(x0 + r * .5f, y0 + r * .85f, gap, 0, 0));
        if (kind.equals("wrongStaff"))
            heads.set(0, new NoteSlideDetector.Head(x0 + 2 * r, y0 - r, gap, -1, 0));
        if (kind.equals("branch")) for (int y = y0; y <= y0 + r; y++) g[y * W + x0 + r / 2] = 0;
        if (kind.equals("noTarget")) heads.clear();
        if (kind.equals("flat")) {
            Arrays.fill(g, (byte) 255);
            for (int y = y0; y <= y0 + r; y++) g[y * W + x0] = 0;
            for (int x = x0; x <= x0 + r; x++) g[(y0 + r / 2) * W + x] = 0;
        }
        for (var head : heads)
            if (head.staff() >= 0) {
                int cx = Math.round(head.x()), cy = Math.round(head.y());
                for (int y = cy - 4; y <= cy + 4; y++)
                    for (int x = cx - 7; x <= cx + 7; x++) {
                        double q = (x - cx) * (x - cx) / 49. + (y - cy) * (y - cy) / 16.;
                        if (q <= 1 && q >= .3) g[y * W + x] = 0;
                    }
            }
        return new Scene(g, heads);
    }

    static int found(int gap, String kind) {
        var s = scene(gap, kind);
        byte[] before = s.gray.clone();
        var result = NoteSlideDetector.detect(s.gray, W, H, List.of(), s.heads);
        assertArrayEquals(before, s.gray);
        return result.size();
    }

    @Test
    public void risingHook12() {
        assertEquals(1, found(12, "hook"));
    }

    @Test
    public void risingHook18() {
        assertEquals(1, found(18, "hook"));
    }

    @Test
    public void risingHook24() {
        assertEquals(1, found(24, "hook"));
    }

    @Test
    public void fullReturningPhraseIsNotAnApproach() {
        assertEquals(0, found(18, "phrase"));
    }

    @Test
    public void closedOvalIsNotAnApproach() {
        assertEquals(0, found(18, "oval"));
    }

    @Test
    public void ambiguousDestinationAbstains() {
        assertEquals(0, found(18, "ambiguous"));
    }

    @Test
    public void foreignHeadOwnsNoApproach() {
        assertEquals(0, found(18, "foreignHead"));
    }

    @Test
    public void unknownStaffOwnsNoApproach() {
        assertEquals(0, found(18, "wrongStaff"));
    }

    @Test
    public void connectedForeignBranchAbstains() {
        assertEquals(0, found(18, "branch"));
    }

    @Test
    public void absentDestinationAbstains() {
        assertEquals(0, found(18, "noTarget"));
    }

    @Test
    public void flatAccidentalIsNotAnApproach() {
        assertEquals(0, found(18, "flat"));
    }
}

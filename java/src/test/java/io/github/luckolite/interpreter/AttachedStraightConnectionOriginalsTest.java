// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original beamed head/stem trees with explicit diagonal branches and competing roles. */
public class AttachedStraightConnectionOriginalsTest {
    private static final class Scene {
        final int scale, w, h;
        final byte[] raw;
        final List<NoteSlideDetector.Head> heads = new ArrayList<>();
        final List<NoteSlideDetector.Staff> staffs;
        final boolean rising;

        Scene(int scale, boolean rising, String shape) {
            this.scale = scale;
            this.rising = rising;
            w = 360 * scale;
            h = 250 * scale;
            raw = new byte[w * h];
            Arrays.fill(raw, (byte) 255);
            staffs = List.of(new NoteSlideDetector.Staff(65 * scale, 121 * scale, 14 * scale));
            for (int y : new int[] {65, 79, 93, 107, 121}) box(10, y, 350, y);
            int[] xs = {120, 170, 220},
                    ys = rising ? new int[] {122, 101, 115} : new int[] {108, 122, 108};
            for (int i = 0; i < 3; i++) {
                oval(xs[i], ys[i]);
                box(xs[i] - 8, ys[i], xs[i] - 7, 172);
                heads.add(
                        new NoteSlideDetector.Head(xs[i] * scale, ys[i] * scale, 14 * scale, 0, 0));
            }
            box(112, 169, 213, 173);
            double ax = 134, ay = rising ? 116 : 113, bx = 162, by = rising ? 105 : 125;
            if (shape.equals("straight")) line(ax, ay, bx, by, 1.05);
            if (shape.equals("bow"))
                for (int i = 1; i <= 80; i++) {
                    double t = (i - 1) / 80., u = i / 80.;
                    line(
                            ax + (bx - ax) * t,
                            ay + (by - ay) * t + 12 * 4 * t * (1 - t),
                            ax + (bx - ax) * u,
                            ay + (by - ay) * u + 12 * 4 * u * (1 - u),
                            1.05);
                }
        }

        void box(int l, int t, int r, int b) {
            for (int y = t * scale; y < (b + 1) * scale; y++)
                for (int x = l * scale; x < (r + 1) * scale; x++) raw[y * w + x] = 0;
        }

        void oval(int cx, int cy) {
            for (int y = (cy - 6) * scale; y <= (cy + 6) * scale; y++)
                for (int x = (cx - 9) * scale; x <= (cx + 9) * scale; x++)
                    if (Math.pow((x - cx * scale) / (9. * scale), 2)
                                    + Math.pow((y - cy * scale) / (6. * scale), 2)
                            <= 1) raw[y * w + x] = 0;
        }

        void erase(int l, int t, int r, int b) {
            for (int y = t * scale; y < (b + 1) * scale; y++)
                for (int x = l * scale; x < (r + 1) * scale; x++) raw[y * w + x] = (byte) 255;
        }

        void line(double ax, double ay, double bx, double by, double radius) {
            for (int y = (int) Math.floor((Math.min(ay, by) - radius) * scale);
                    y <= (Math.max(ay, by) + radius) * scale;
                    y++)
                for (int x = (int) Math.floor((Math.min(ax, bx) - radius) * scale);
                        x <= (Math.max(ax, bx) + radius) * scale;
                        x++) {
                    double dx = bx - ax,
                            dy = by - ay,
                            u =
                                    Math.max(
                                            0,
                                            Math.min(
                                                    1,
                                                    ((x / (double) scale - ax) * dx
                                                                    + (y / (double) scale - ay)
                                                                            * dy)
                                                            / (dx * dx + dy * dy)));
                    if (Math.hypot(
                                    x / (double) scale - ax - u * dx,
                                    y / (double) scale - ay - u * dy)
                            <= radius) raw[y * w + x] = 0;
                }
        }

        List<NoteSlideDetector.Stroke> found() {
            byte[] copy = raw.clone();
            var r = NoteSlideDetector.detect(raw, w, h, staffs, heads);
            assertArrayEquals(copy, raw);
            return r;
        }

        void positive() {
            var r = found();
            assertEquals(r.toString(), 1, r.size());
            assertEquals(1, r.get(0).noteIndex());
            assertTrue(r.get(0).connected());
            assertEquals(rising ? 1 : -1, r.get(0).direction());
        }

        void negative() {
            var r = found();
            assertTrue(r.toString(), r.isEmpty());
        }
    }

    @Test
    public void descendingConnectedBranch() {
        new Scene(1, false, "straight").positive();
    }

    @Test
    public void risingConnectedBranch() {
        new Scene(1, true, "straight").positive();
    }

    @Test
    public void doubledDescendingBranch() {
        new Scene(2, false, "straight").positive();
    }

    @Test
    public void doubledRisingBranch() {
        new Scene(2, true, "straight").positive();
    }

    @Test
    public void bareBeamedNotesHaveNoSlide() {
        new Scene(1, false, "none").negative();
    }

    @Test
    public void curvedBranchIsNotAStraightConnection() {
        new Scene(1, false, "bow").negative();
    }

    @Test
    public void missingSourceMetadataDoesNotCloseTree() {
        var s = new Scene(1, false, "straight");
        s.heads.remove(0);
        s.negative();
    }

    @Test
    public void missingDestinationMetadataDoesNotCloseTree() {
        var s = new Scene(1, false, "straight");
        s.heads.remove(1);
        s.negative();
    }

    @Test
    public void unrecognizedRemainingHeadRefusesWholeComponent() {
        var s = new Scene(1, false, "straight");
        s.heads.remove(2);
        s.negative();
    }

    @Test
    public void coincidentSourceChordIsAmbiguous() {
        var s = new Scene(1, false, "straight");
        s.heads.add(s.heads.get(0));
        s.negative();
    }

    @Test
    public void coincidentDestinationIsAmbiguous() {
        var s = new Scene(1, false, "straight");
        s.heads.add(s.heads.get(1));
        s.negative();
    }

    @Test
    public void sourceInAnotherBarCannotOwnLink() {
        var s = new Scene(1, false, "straight");
        var n = s.heads.get(0);
        s.heads.set(0, new NoteSlideDetector.Head(n.x(), n.y(), n.gap(), 0, 1));
        s.negative();
    }

    @Test
    public void foreignPhysicalStaffCannotBeDiscarded() {
        var s = new Scene(1, false, "straight");
        var n = s.heads.get(0);
        s.heads.set(0, new NoteSlideDetector.Head(n.x(), n.y(), n.gap(), 1, 0));
        s.negative();
    }

    @Test
    public void interveningAttackPreventsSkippingToOlderSource() {
        var s = new Scene(1, false, "straight");
        s.heads.add(new NoteSlideDetector.Head(143, 147, 14, 0, 0));
        s.negative();
    }

    @Test
    public void unexplainedBranchRefusesWholeTree() {
        var s = new Scene(1, false, "straight");
        s.line(182, 172, 192, 156, 1.1);
        s.negative();
    }

    @Test
    public void pitchDirectionMustMatchInk() {
        var s = new Scene(1, false, "straight");
        var n = s.heads.get(0);
        s.heads.set(0, new NoteSlideDetector.Head(n.x(), 135, n.gap(), 0, 0));
        s.negative();
    }

    @Test
    public void metadataCannotReplaceRawDestinationShaft() {
        var s = new Scene(1, false, "straight");
        s.erase(161, 132, 164, 166);
        s.negative();
    }

    @Test
    public void metadataCannotReplaceRawSourceBody() {
        var s = new Scene(1, false, "straight");
        s.erase(111, 102, 129, 114);
        s.box(10, 107, 350, 107);
        s.box(112, 108, 113, 172);
        s.negative();
    }

    @Test
    public void shortOffRuleFlagIsNotAHeadRailAlias() {
        var s = new Scene(1, false, "straight");
        s.line(159, 130, 163, 130, .8);
        s.negative();
    }
}

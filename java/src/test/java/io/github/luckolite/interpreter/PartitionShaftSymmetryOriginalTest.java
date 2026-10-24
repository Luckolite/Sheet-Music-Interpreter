// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PartitionShaftSymmetryOriginalTest {
    static final int W = 220, H = 220;

    static ExactComponentMembers.Budget budget() {
        return new ExactComponentMembers.Budget(10_000_000, 10_000_000);
    }

    static final class Scene {
        final byte[] g = new byte[W * H], l = new byte[W * H];
        final Object owner = new Object();
        final ExactComponentMembers.Collector c;
        final float gap;
        final int[] members;

        Scene(int scale, int direction, int fault) {
            gap = scale;
            Arrays.fill(g, (byte) 255);
            var pixels = new ArrayList<Integer>();
            double angle = Math.toRadians(-25), co = Math.cos(angle), si = Math.sin(angle);
            int left = W, right = -1, top = H, bottom = -1;
            long sx = 0, sy = 0;
            for (int y = 80; y <= 140; y++)
                for (int x = 80; x <= 140; x++) {
                    double X = (x - 110) * co + (y - 110) * si,
                            Y = -(x - 110) * si + (y - 110) * co;
                    if (X * X / (.65 * gap * .65 * gap) + Y * Y / (.45 * gap * .45 * gap) <= 1) {
                        if (fault == 3 && (x - 110) * (x - 110) + (y - 110) * (y - 110) <= 9)
                            continue;
                        int p = y * W + x;
                        l[p] = 2;
                        g[p] = 0;
                        pixels.add(p);
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                        top = Math.min(top, y);
                        bottom = Math.max(bottom, y);
                        sx += x;
                        sy += y;
                    }
                }
            for (int dir : new int[] {-1, 1})
                if (direction == dir || fault == 2) {
                    int x = dir < 0 ? right : left;
                    int boundary = dir < 0 ? H : -1;
                    for (int y = top; y <= bottom; y++)
                        if (l[y * W + x] == 2)
                            boundary = dir < 0 ? Math.min(boundary, y) : Math.max(boundary, y);
                    if (fault == 1) x += dir < 0 ? 4 : -4;
                    for (int d = 1; d <= Math.ceil(gap * 3); d++) {
                        int y = boundary + dir * d;
                        g[y * W + x] = 0;
                        if (l[y * W + x] != 2) l[y * W + x] = 1;
                    }
                }
            if (fault == 4) {
                l[110 * W + 110] = 5;
                pixels.remove(Integer.valueOf(110 * W + 110));
                sx -= 110;
                sy -= 110;
            }
            members = pixels.stream().mapToInt(Integer::intValue).toArray();
            c = new ExactComponentMembers.Collector(g, l, W, H, budget(), budget());
            assertTrue(c.bindInput(l, g, W, H));
            var b = c.begin();
            for (int y = top; y <= bottom; y++) {
                int a = -1;
                for (int x = left; x <= right + 1; x++) {
                    boolean hit = x <= right && l[y * W + x] == 2;
                    if (hit && a < 0) a = x;
                    if (!hit && a >= 0) {
                        b.add(y, a, x - 1);
                        a = -1;
                    }
                }
            }
            c.record(
                    owner,
                    new ExactComponentMembers.Stats(
                            members.length,
                            left,
                            right,
                            top,
                            bottom,
                            sx / (float) members.length,
                            sy / (float) members.length),
                    (byte) 2,
                    b);
            assertTrue(c.seal(budget()));
        }

        Map<Object, LobeExtentProposal.Core> proposal(StringBuilder trace) {
            return LobeExtentProposal.derivePartition(
                    l, g, W, H, c.producerPartition(owner, budget()), gap, trace);
        }
    }

    @Test
    public void everyDirectionAndOriginalScaleHasAnOwnedBodyProposal() {
        for (int scale : new int[] {16, 20, 24})
            for (int dir : new int[] {-1, 1}) {
                var s = new Scene(scale, dir, 0);
                var trace = new StringBuilder();
                assertNotNull("scale=" + scale + " dir=" + dir + " " + trace, s.proposal(trace));
            }
    }

    @Test
    public void detachedOrAmbiguousShaftCannotBecomeAnOwnedBody() {
        for (int fault : new int[] {1, 2})
            for (int dir : new int[] {-1, 1}) {
                var s = new Scene(20, dir, fault);
                assertNull(s.proposal(new StringBuilder()));
            }
    }

    @Test
    public void actualClosedCounterPreservesHollow() {
        for (int dir : new int[] {-1, 1}) {
            var s = new Scene(20, dir, 3);
            assertNull(s.proposal(new StringBuilder()));
        }
    }

    @Test
    public void foreignTypedInteriorCannotBeForgivenAsFilledBody() {
        for (int dir : new int[] {-1, 1}) {
            var s = new Scene(20, dir, 4);
            assertNull(s.proposal(new StringBuilder()));
        }
    }

    static ExactComponentMembers.Stats stats(List<Integer> pixels) {
        int l = W, t = H, r = -1, b = -1;
        long sx = 0, sy = 0;
        for (int p : pixels) {
            int x = p % W, y = p / W;
            l = Math.min(l, x);
            r = Math.max(r, x);
            t = Math.min(t, y);
            b = Math.max(b, y);
            sx += x;
            sy += y;
        }
        return new ExactComponentMembers.Stats(
                pixels.size(), l, r, t, b, sx / (float) pixels.size(), sy / (float) pixels.size());
    }

    static void capture(
            ExactComponentMembers.Collector c,
            Object owner,
            List<Integer> pixels,
            ExactComponentMembers.Split split) {
        var b = split == null ? c.begin() : split.beginChild();
        for (int p : pixels) b.add(p / W, p % W, p % W);
        if (split == null) c.record(owner, stats(pixels), (byte) 2, b);
        else split.capture(owner, stats(pixels), b);
    }

    static Map<Object, LobeExtentProposal.Core> chord(
            int direction, boolean hollow, StringBuilder trace) {
        byte[] g = new byte[W * H], l = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        double co = Math.cos(Math.toRadians(-25)), si = Math.sin(Math.toRadians(-25));
        for (int cy : new int[] {100, 120})
            for (int y = 80; y <= 140; y++)
                for (int x = 85; x <= 135; x++) {
                    double X = (x - 110) * co + (y - cy) * si, Y = -(x - 110) * si + (y - cy) * co;
                    if (X * X / 169 + Y * Y / 81 <= 1) {
                        l[y * W + x] = 2;
                        g[y * W + x] = 0;
                    }
                }
        if (hollow)
            for (int y = 118; y <= 122; y++)
                for (int x = 108; x <= 112; x++) {
                    l[y * W + x] = 0;
                    g[y * W + x] = (byte) 255;
                }
        var parent = new ArrayList<Integer>();
        var aa = new ArrayList<Integer>();
        var bb = new ArrayList<Integer>();
        for (int p = 0; p < l.length; p++)
            if (l[p] == 2) {
                parent.add(p);
                (p / W <= 110 ? aa : bb).add(p);
            }
        var st = stats(parent);
        int x = direction < 0 ? st.maxX() : st.minX(), first = H, last = -1;
        for (int y = st.minY(); y <= st.maxY(); y++)
            if (l[y * W + x] == 2) {
                first = Math.min(first, y);
                last = Math.max(last, y);
            }
        for (int y = Math.max(0, first - (direction < 0 ? 60 : 0));
                y <= Math.min(H - 1, last + (direction > 0 ? 60 : 0));
                y++) {
            g[y * W + x] = 0;
            if (l[y * W + x] != 2) l[y * W + x] = 1;
        }
        var c = new ExactComponentMembers.Collector(g, l, W, H, budget(), budget());
        assertTrue(c.bindInput(l, g, W, H));
        Object root = new Object(), a = new Object(), b = new Object();
        capture(c, root, parent, null);
        var split = c.beginSplit(root, l, W);
        capture(c, a, aa, split);
        capture(c, b, bb, split);
        assertTrue(split.commit(List.of(a, b)));
        assertTrue(c.seal(budget()));
        return LobeExtentProposal.derivePartition(
                l, g, W, H, c.producerPartition(a, budget()), 20, trace);
    }

    @Test
    public void genuineTouchingChildrenBothDirectionsRetainEveryBody() {
        for (int dir : new int[] {-1, 1}) {
            var trace = new StringBuilder();
            var cores = chord(dir, false, trace);
            assertNotNull("dir=" + dir + " " + trace, cores);
            assertEquals(2, cores.size());
        }
    }

    @Test
    public void oneHollowSiblingVetoesWholeJointProposal() {
        for (int dir : new int[] {-1, 1}) assertNull(chord(dir, true, new StringBuilder()));
    }
}

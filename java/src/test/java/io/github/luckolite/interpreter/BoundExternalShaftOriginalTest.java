// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class BoundExternalShaftOriginalTest {
    static final int W = 300, H = 220;

    static ExactComponentMembers.Budget budget() {
        return new ExactComponentMembers.Budget(10000000, 10000000);
    }

    static final class Page {
        final byte[] g = new byte[W * H], l = new byte[W * H];
        final Object a = new Object(), b = new Object();
        final ExactComponentMembers.Collector source;
        final ExactComponentMembers.ProducerPartition first, second;

        Page(int dir, int offset, boolean third) {
            Arrays.fill(g, (byte) 255);
            var parts = new ArrayList<List<Integer>>();
            for (int dx : new int[] {0, offset}) {
                var glyph = new PartitionShaftSymmetryOriginalTest.Scene(20, dir, 0);
                var pixels = new ArrayList<Integer>();
                for (int y = 0; y < H; y++)
                    for (int x = 0; x < 220; x++) {
                        int old = y * 220 + x;
                        if ((glyph.g[old] & 255) == 255 && glyph.l[old] == 0) continue;
                        int p = y * W + x + dx;
                        g[p] = glyph.g[old];
                        l[p] = glyph.l[old];
                        if (glyph.l[old] == 2) pixels.add(p);
                    }
                parts.add(pixels);
            }
            if (third)
                for (int y = 80; y <= 180; y++) {
                    g[y * W + 150] = 0;
                    l[y * W + 150] = 1;
                }
            source = new ExactComponentMembers.Collector(g, l, W, H, budget(), budget());
            assertTrue(source.bindInput(l, g, W, H));
            int index = 0;
            for (Object id : new Object[] {a, b}) {
                var pixels = parts.get(index++);
                int left = W, top = H, right = -1, bottom = -1;
                long sx = 0, sy = 0;
                var body = source.begin();
                for (int p : pixels) {
                    int x = p % W, y = p / W;
                    body.add(y, x, x);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    sx += x;
                    sy += y;
                }
                source.record(
                        id,
                        new ExactComponentMembers.Stats(
                                pixels.size(),
                                left,
                                right,
                                top,
                                bottom,
                                sx / (float) pixels.size(),
                                sy / (float) pixels.size()),
                        (byte) 2,
                        body);
            }
            assertTrue(source.seal(budget()));
            first = source.producerPartition(a, budget());
            second = source.producerPartition(b, budget());
            assertNotNull(first);
            assertNotNull(second);
        }

        Map<Object, LobeExtentProposal.Core> proposal(
                List<ExactComponentMembers.ProducerPartition> parents, StringBuilder trace) {
            return LobeExtentProposal.deriveCohort(l, g, W, H, parents, 20, trace);
        }
    }

    @Test
    public void genuinePriorQualifiedOtherParentBindsExternalShaftBothDirections() {
        for (int dir : new int[] {-1, 1}) {
            var page = new Page(dir, 64, false);
            var trace = new StringBuilder();
            var body = page.proposal(List.of(page.first, page.second), trace);
            assertNotNull("dir=" + dir + " " + trace, body);
            assertEquals(2, body.size());
            var reversed = page.proposal(List.of(page.second, page.first), new StringBuilder());
            assertNotNull(reversed);
            assertEquals(body.get(page.a), reversed.get(page.a));
            assertEquals(body.get(page.b), reversed.get(page.b));
        }
    }

    @Test
    public void unboundForeignShaftRemainsUnknown() {
        for (int dir : new int[] {-1, 1}) {
            var page = new Page(dir, 64, true);
            assertNull(page.proposal(List.of(page.first, page.second), new StringBuilder()));
        }
    }

    @Test
    public void mutuallyDependentParentsCannotSupplyTheirOwnSeed() {
        for (int dir : new int[] {-1, 1}) {
            var page = new Page(dir, 58, false);
            assertNull(page.proposal(List.of(page.first, page.second), new StringBuilder()));
        }
    }

    @Test
    public void sameValueForeignProducerCannotBindDependency() {
        var page = new Page(1, 64, false);
        var foreign = new Page(1, 64, false);
        assertNull(page.proposal(List.of(page.first, foreign.second), new StringBuilder()));
    }

    @Test
    public void staleSourceCannotBindDependency() {
        var page = new Page(1, 64, false);
        page.g[0] = 0;
        assertNull(page.proposal(List.of(page.first, page.second), new StringBuilder()));
    }
}

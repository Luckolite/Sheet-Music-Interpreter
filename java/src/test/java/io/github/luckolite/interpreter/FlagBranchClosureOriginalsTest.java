// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original explicit flag graph; every supplied graph point is drawn into the full raw staff. */
public class FlagBranchClosureOriginalsTest {
    static final class Drawing {
        final AttachedCurvedApproachTest.Scene s =
                FlagRuleApproachOriginalsTest.scene(1, true, false);
        final Map<Integer, Set<Integer>> graph = new HashMap<>();
        final List<Integer> caps = new ArrayList<>();

        Drawing() {
            line(205, 98, 205, 142);
            line(205, 139, 211, 136);
            line(211, 136, 217, 130);
            line(217, 130, 221, 122);
            line(221, 122, 221, 115);
            line(221, 115, 220, 113);
            line(220, 113, 219, 112);
            line(220, 113, 220, 112);
            caps.add(at(219, 112));
            caps.add(at(220, 112));
        }

        int at(int x, int y) {
            return y * s.w + x;
        }

        void line(int x, int y, int xx, int yy) {
            int n = Math.max(Math.abs(x - xx), Math.abs(y - yy)), prior = -1;
            for (int i = 0; i <= n; i++) {
                int p =
                        at(
                                Math.round(x + (xx - x) * i / (float) n),
                                Math.round(y + (yy - y) * i / (float) n));
                graph.computeIfAbsent(p, k -> new HashSet<>());
                if (prior >= 0 && prior != p) {
                    graph.get(prior).add(p);
                    graph.get(p).add(prior);
                }
                prior = p;
                s.gray[p] = 0;
            }
            s.line(x, y, xx, yy, 1.1);
        }

        List<Integer> leaves() {
            return graph.keySet().stream().filter(p -> graph.get(p).size() == 1).toList();
        }

        Set<Integer> found(long available, boolean shaft, boolean reverse) {
            byte[] copy = s.gray.clone();
            var track = StaffPitchTrack.forPrintedSlurs(s.gray, s.w, s.h, 80, 136, 14);
            var body =
                    PrintedSlurOwners.headEvidence(
                            List.of(new PrintedSlurOwners.GeometryHead(0, 214, 94, 0, 1)),
                            s.gray,
                            s.w,
                            s.h,
                            track);
            var ls = new ArrayList<>(leaves());
            if (reverse) Collections.reverse(ls);
            var b = new AttachedApproachBranches.Budget(available);
            var found =
                    DownFlagBranchOwnership.leaves(
                            s.gray,
                            s.w,
                            s.h,
                            s.heads,
                            List.of(0),
                            shaft ? Set.of(0) : Set.of(),
                            Map.of(0, body),
                            new ArrayList<>(graph.keySet()),
                            ls,
                            p -> new ArrayList<>(graph.get(p)),
                            p -> p,
                            b);
            assertArrayEquals(copy, s.gray);
            return found;
        }
    }

    @Test
    public void completeSplitCapAndShaftTailAreOwned() {
        var d = new Drawing();
        var r = d.found(64000000, true, false);
        assertTrue(r.toString(), r.containsAll(d.caps));
        assertTrue(r.contains(d.at(205, 142)));
    }

    @Test
    public void outsideBranchCannotBePruned() {
        var d = new Drawing();
        d.line(221, 122, 238, 122);
        assertTrue(d.found(64000000, true, false).isEmpty());
    }

    @Test
    public void unexplainedInteriorTipCannotBeIgnored() {
        var d = new Drawing();
        d.line(221, 122, 223, 118);
        assertTrue(d.found(64000000, true, false).isEmpty());
    }

    @Test
    public void absentRawFlagDoesNotBorrowGraphMetadata() {
        var d = new Drawing();
        d.s.erase(209, 104, 227, 140);
        assertTrue(d.found(64000000, true, false).isEmpty());
    }

    @Test
    public void absentRawShaftRefuses() {
        var d = new Drawing();
        d.s.erase(203, 102, 208, 133);
        assertTrue(d.found(64000000, true, false).isEmpty());
    }

    @Test
    public void unprovedDownShaftRoleRefuses() {
        assertTrue(new Drawing().found(64000000, false, false).isEmpty());
    }

    @Test
    public void exhaustedBudgetCannotCreateFlagRoles() {
        assertTrue(new Drawing().found(0, true, false).isEmpty());
    }

    @Test
    public void leafPermutationPreservesExactRoles() {
        var d = new Drawing();
        var a = d.found(64000000, true, false);
        assertFalse(a.isEmpty());
        assertEquals(a, d.found(64000000, true, true));
    }
}

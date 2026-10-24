// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.BooleanSupplier;

/** Real exhausted suffixes dominate less-covered histories at the same finite DAG state. */
final class ContinuationFailureMemo {
    private record Saved(
            int generation,
            int node,
            int flags,
            int x,
            int radius,
            int begin,
            int end,
            int hash,
            BitSet frontier,
            Object representative,
            int next) {}

    private final int[] coreX;
    private final int nodeCount;
    private final int[] heads;
    private final Saved[] entries;
    private final ObservedFringeContinuation.Charge work;
    private final Runnable capacity;
    private int count;

    ContinuationFailureMemo(
            int[] quarterCoreXs, ObservedFringeContinuation.Charge work, Runnable capacity) {
        this(quarterCoreXs, quarterCoreXs == null ? 0 : quarterCoreXs.length, work, capacity);
    }

    ContinuationFailureMemo(
            int[] quarterCoreXs,
            int nodeCount,
            ObservedFringeContinuation.Charge work,
            Runnable capacity) {
        if (quarterCoreXs == null
                || quarterCoreXs.length < 1
                || quarterCoreXs.length > 16384
                || nodeCount < 1
                || nodeCount > 16384
                || work == null
                || capacity == null) throw new IllegalArgumentException("continuation domain");
        this.work = work;
        this.capacity = capacity;
        this.nodeCount = nodeCount;
        capacity.run();
        pay(quarterCoreXs.length + 4096 + 1024 + 13);
        coreX = quarterCoreXs.clone();
        heads = new int[1024];
        entries = new Saved[4096];
        pay(1024);
        Arrays.fill(heads, -1);
        for (int i = 0; i < coreX.length; i++) {
            work.tick();
            if (coreX[i] < -4 || coreX[i] > 516 || i > 0 && coreX[i] < coreX[i - 1])
                throw new IllegalArgumentException("ordered continuation observations");
        }
    }

    private void pay(int n) {
        for (int i = 0; i < n; i++) work.tick();
    }

    int completedFailures() {
        return count;
    }

    /** covered/representative are immutable real-prefix state; continuation must not mutate them. */
    boolean evaluate(
            int generation,
            int node,
            int flags,
            int x,
            int radius,
            BitSet covered,
            Object actualRepresentative,
            BooleanSupplier continuation) {
        work.tick();
        if (generation < 1
                || node < 0
                || node >= nodeCount
                || flags < 0
                || flags > 7
                || x < -4
                || x > 516
                || radius < 1
                || radius > 520
                || covered == null
                || covered.length() > coreX.length
                || actualRepresentative == null
                || continuation == null) throw new IllegalArgumentException("continuation state");
        int begin = boundary(x - radius, false), end = boundary(x + radius, true);
        // All future centre x>=currentx. An uncovered point strictly left of x-R is impossible.
        pay((coreX.length + 63) / 64 + 1);
        if (covered.nextClearBit(0) < begin) return false;
        int hash = frontierHash(generation, node, flags, x, radius, begin, end);
        int bucket = hash & 1023;
        for (int at = heads[bucket]; at >= 0; at = entries[at].next) {
            pay(10);
            Saved saved = entries[at];
            if (saved.generation != generation
                    || saved.node != node
                    || saved.flags != flags
                    || saved.x != x
                    || saved.radius != radius
                    || saved.begin != begin
                    || saved.end != end
                    || saved.hash != hash) continue;
            // An exhausted REAL history with MORE covered obligations cannot
            // admit a suffix that the current less-covered history could finish.
            // No bit unions, path replacement, or manufactured history is used.
            boolean dominated = true;
            for (int i = begin; i < end; i++) {
                pay(2);
                if (covered.get(i) && !saved.frontier.get(i - begin)) {
                    dominated = false;
                    break;
                }
            }
            if (dominated) return false;
        }
        // No catch/finally: exhaustion, errors and cancellation must never become cached failure.
        boolean supported = continuation.getAsBoolean();
        if (!supported && count < entries.length) {
            capacity.run();
            pay(3 * ((end - begin + 63) / 64 + 1) + 16);
            BitSet frontier = covered.get(begin, end);
            entries[count] =
                    new Saved(
                            generation,
                            node,
                            flags,
                            x,
                            radius,
                            begin,
                            end,
                            hash,
                            frontier,
                            actualRepresentative,
                            heads[bucket]);
            heads[bucket] = count++;
        }
        return supported;
    }

    /** false => first q>=bound; true => first q>bound. Equality stays in inclusive frontier. */
    private int boundary(int bound, boolean after) {
        int l = 0, r = coreX.length;
        while (l < r) {
            work.tick();
            int m = (l + r) >>> 1;
            if (coreX[m] < bound || after && coreX[m] == bound) l = m + 1;
            else r = m;
        }
        return l;
    }

    private int frontierHash(
            int generation, int node, int flags, int x, int radius, int begin, int end) {
        pay(7);
        int hash = generation;
        hash = 31 * hash + node;
        hash = 31 * hash + flags;
        hash = 31 * hash + x;
        hash = 31 * hash + radius;
        hash = 31 * hash + begin;
        hash = 31 * hash + end;
        return hash;
    }
}

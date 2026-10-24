// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.BitSet;
import static io.github.luckolite.interpreter.QuantizedTubeDistance.*;

/** Finite sample-feasible polygon proposal. Not numeral, source-birth, or AA authority. */
final class ObservedCentreTurnFamily {
    record Fit(
            boolean supported,
            String reason,
            int phaseX,
            int phaseY,
            int quarterRadius,
            int[] quarterPath,
            int coreSamples,
            int paperSamples) {}

    private record History(
            int node, boolean descending, boolean rose, boolean fell, BitSet covered, int[] path) {}

    private final byte[] raw;
    private final int width, height, left, top, w, h, rw, rh;
    private final BitSet curve;
    private final ObservedFringeContinuation.Charge work;
    private final Runnable capacity;
    private final boolean allowUnresolvedFringe;
    private int[] cx, cy, nx, ny, above, below, requiredX, requiredY;
    private int requiredCount;
    private int leftCount, rightStart;
    private int[] memoGeneration, memoFrom, memoTo, victim;
    private boolean[] memoSafe;
    private BitSet[] memoCore;
    private int generation;
    private long[] clearance;
    private boolean[] paper;
    private int nc, np, nn, phaseX, phaseY, radius;
    private long radiusSquared;
    private Fit answer;
    private ContinuationFailureMemo continuations;
    private CapsuleStripBounds strip;
    private CoreColumnIndex coreIndex;

    private ObservedCentreTurnFamily(
            byte[] raw,
            int width,
            int height,
            int l,
            int t,
            int r,
            int b,
            BitSet curve,
            ObservedFringeContinuation.Charge work,
            Runnable capacity,
            boolean allowUnresolvedFringe) {
        this.allowUnresolvedFringe = allowUnresolvedFringe;
        this.raw = raw;
        this.width = width;
        this.height = height;
        left = l;
        top = t;
        w = r - l + 1;
        h = b - t + 1;
        rw = w + 2;
        rh = h + 2;
        this.curve = curve;
        this.work = work;
        this.capacity = capacity;
    }

    private void pay(int n) {
        for (int i = 0; i < n; i++) work.tick();
    }

    private Fit no(String why) {
        capacity.run();
        pay(10);
        return new Fit(false, why, 0, 0, 0, null, nc, np);
    }

    static Fit inspect(
            byte[] raw,
            int width,
            int height,
            int l,
            int t,
            int r,
            int b,
            BitSet curve,
            ObservedFringeContinuation.Charge work,
            Runnable capacity) {
        if (raw == null
                || width < 1
                || height < 1
                || raw.length != (long) width * height
                || work == null
                || capacity == null
                || l < 1
                || t < 1
                || r >= width - 1
                || b >= height - 1
                || l > r
                || t > b
                || (long) r - l + 1 > MAX_SIDE
                || (long) b - t + 1 > MAX_SIDE
                || ((long) r - l + 1) * ((long) b - t + 1) > 16384
                || curve != null && curve.length() > raw.length) return null;
        capacity.run();
        for (int i = 0; i < 20; i++) work.tick();
        return new ObservedCentreTurnFamily(
                        raw, width, height, l, t, r, b, curve, work, capacity, false)
                .run();
    }

    /** Conditional proposal only: non-core/non-paper values stay unresolved, never owned. */
    static Fit proposeWithUnresolvedFringe(
            byte[] raw,
            int width,
            int height,
            int l,
            int t,
            int r,
            int b,
            ObservedFringeContinuation.Charge work,
            Runnable capacity) {
        if (raw == null
                || width < 1
                || height < 1
                || raw.length != (long) width * height
                || work == null
                || capacity == null
                || l < 1
                || t < 1
                || r >= width - 1
                || b >= height - 1
                || l > r
                || t > b
                || (long) r - l + 1 > MAX_SIDE
                || (long) b - t + 1 > MAX_SIDE
                || ((long) r - l + 1) * ((long) b - t + 1) > 16384) return null;
        capacity.run();
        for (int i = 0; i < 20; i++) work.tick();
        return new ObservedCentreTurnFamily(
                        raw, width, height, l, t, r, b, null, work, capacity, true)
                .run();
    }

    private Fit run() {
        if (w < 3 || h < 2) return no("insufficient-domain");
        int area = w * h, ring = rw * rh;
        capacity.run();
        pay(7 * area + ring + 20);
        cx = new int[area];
        cy = new int[area];
        nx = new int[area];
        ny = new int[area];
        clearance = new long[area];
        paper = new boolean[ring];
        // x-major order is also a deterministic complete ordering of candidate vertices.
        for (int x = -1; x <= w; x++)
            for (int y = -1; y <= h; y++) {
                work.tick();
                int id = (top + y) * width + left + x, v = raw[id] & 255;
                if (curve != null) {
                    work.tick();
                    if (curve.get(id)) return no("curve-in-support-domain");
                }
                if (v > 165 && v < 255 && !allowUnresolvedFringe)
                    return no("unknown-arm-or-boundary-sample");
                if (v == 255) {
                    paper[(y + 1) * rw + x + 1] = true;
                    np++;
                } else if (v <= 165 && x >= 0 && x < w && y >= 0 && y < h) {
                    cx[nc] = x;
                    cy[nc++] = y;
                }
            }
        if (nc < 3 || np < 3) return no("missing-complete-observations");
        while (leftCount < nc && cx[leftCount] == 0) {
            work.tick();
            leftCount++;
        }
        rightStart = nc;
        while (rightStart > 0 && cx[rightStart - 1] == w - 1) {
            work.tick();
            rightStart--;
        }
        capacity.run();
        pay(2 * nc + 8);
        above = new int[nc];
        below = new int[nc];
        int cursor = 0;
        for (int x = 0; x < w; x++) {
            int last = -2;
            for (int y = -1; y <= h; y++) {
                work.tick();
                if (paper[(y + 1) * rw + x + 1]) last = y;
                if (cursor < nc && cx[cursor] == x && cy[cursor] == y) {
                    work.tick();
                    above[cursor++] = last;
                }
            }
        }
        cursor = nc - 1;
        for (int x = w - 1; x >= 0; x--) {
            int next = h + 1;
            for (int y = h; y >= -1; y--) {
                work.tick();
                if (paper[(y + 1) * rw + x + 1]) next = y;
                if (cursor >= 0 && cx[cursor] == x && cy[cursor] == y) {
                    work.tick();
                    below[cursor--] = next;
                }
            }
        }
        capacity.run();
        pay(5 * 64 + 16 + 12);
        memoGeneration = new int[64];
        memoFrom = new int[64];
        memoTo = new int[64];
        memoSafe = new boolean[64];
        memoCore = new BitSet[64];
        victim = new int[16];
        CoreColumnExtrema extrema = CoreColumnExtrema.create(cx, cy, nc, w, h, work, capacity);
        requiredX = extrema.x;
        requiredY = extrema.y;
        requiredCount = extrema.count;
        capacity.run();
        pay(requiredCount + 5);
        int[] quarterCoreX = new int[requiredCount];
        for (int i = 0; i < requiredCount; i++) {
            work.tick();
            quarterCoreX[i] = 4 * requiredX[i];
        }
        continuations = new ContinuationFailureMemo(quarterCoreX, nc, work, capacity);
        strip = CapsuleStripBounds.create(work, capacity);
        coreIndex =
                CoreColumnIndex.create(requiredX, requiredY, requiredCount, w, h, work, capacity);
        // Fixed global phases are alternatives, never per-vertex fitted phases.
        for (phaseX = 0; phaseX < 4; phaseX++)
            for (phaseY = 0; phaseY < 4; phaseY++) {
                capacity.run();
                pay(8);
                nn = nc;
                long greatest = 0;
                for (int i = 0; i < nn; i++) {
                    work.tick();
                    nx[i] = 4 * cx[i] + phaseX;
                    ny[i] = 4 * cy[i] + phaseY;
                    clearance[i] = paperClearance(i, nx[i], ny[i]);
                    greatest = Math.max(greatest, clearance[i]);
                }
                int maxRadius = 0;
                while ((long) (maxRadius + 1) * (maxRadius + 1) < greatest) {
                    work.tick();
                    maxRadius++;
                }
                // Clearance ordering only. Every smaller positive quarter radius remains in family.
                for (radius = maxRadius; radius >= 1; radius--) {
                    capacity.run();
                    pay(6);
                    radiusSquared = (long) radius * radius;
                    generation++;
                    boolean endPossible = false;
                    for (int candidate = 0; candidate < nn; candidate++) {
                        work.tick();
                        if (clearance[candidate] > radiusSquared && capCovers(candidate, false)) {
                            endPossible = true;
                            break;
                        }
                    }
                    if (!endPossible) continue;
                    for (int start = 0; start < nn; start++) {
                        work.tick();
                        if (clearance[start] <= radiusSquared || !capCovers(start, true)) continue;
                        int initial = edge(start, start);
                        if (!memoSafe[initial]) continue;
                        BitSet covered = coverage(null, initial);
                        capacity.run();
                        pay(9);
                        int[] path = {start};
                        History first = new History(start, false, false, false, covered, path);
                        if (search(first)) return answer;
                    }
                }
            }
        return no("no-complete-finite-polygon-history");
    }

    /** Exact nearest observed paper. A measured nearby paper supplies only a search bound. */
    private long paperClearance(int source, int qx, int qy) {
        long bound = Long.MAX_VALUE;
        int col = cx[source];
        work.tick();
        if (above[source] >= -1) {
            long dx = 4L * col - qx, dy = 4L * above[source] - qy;
            bound = dx * dx + dy * dy;
        }
        work.tick();
        if (below[source] <= h) {
            long dx = 4L * col - qx, dy = 4L * below[source] - qy;
            bound = Math.min(bound, dx * dx + dy * dy);
        }
        if (bound == Long.MAX_VALUE) {
            // Any literal observed paper sample supplies a finite upper distance bound.
            // Requiring it in this centre's column would discard feasible centres when
            // unresolved fringe spans the column. The exact nearest-paper scan below is unchanged.
            paperSeed:
            for (int y = -1; y <= h; y++)
                for (int x = -1; x <= w; x++) {
                    work.tick();
                    if (!paper[(y + 1) * rw + x + 1]) continue;
                    long dx = 4L * x - qx, dy = 4L * y - qy;
                    bound = dx * dx + dy * dy;
                    break paperSeed;
                }
            if (bound == Long.MAX_VALUE) return 0;
        }
        int reach = 0;
        while (16L * reach * reach < bound) {
            work.tick();
            reach++;
        }
        int xl = Math.max(-1, Math.floorDiv(qx, 4) - reach - 1),
                xr = Math.min(w, Math.floorDiv(qx + 3, 4) + reach + 1);
        int yt = Math.max(-1, Math.floorDiv(qy, 4) - reach - 1),
                yb = Math.min(h, Math.floorDiv(qy + 3, 4) + reach + 1);
        for (int y = yt; y <= yb; y++)
            for (int x = xl; x <= xr; x++) {
                work.tick();
                if (!paper[(y + 1) * rw + x + 1]) continue;
                long dx = 4L * x - qx, dy = 4L * y - qy;
                bound = Math.min(bound, dx * dx + dy * dy);
            }
        return bound;
    }

    /** Necessary longitudinal endpoint domain; complete core coverage belongs to whole capsule. */
    private boolean capCovers(int node, boolean first) {
        work.tick();
        boolean observed = first ? leftCount > 0 : rightStart < nc;
        if (!observed) return false;
        work.tick();
        return first ? nx[node] <= radius : nx[node] >= 4 * (w - 1) - radius;
    }

    private boolean search(History old) {
        capacity.run();
        pay(8); // Actual single-history continuation closure, before lambda payload.
        int flags = (old.descending ? 1 : 0) | (old.rose ? 2 : 0) | (old.fell ? 4 : 0);
        return continuations.evaluate(
                generation,
                old.node,
                flags,
                nx[old.node],
                radius,
                old.covered,
                old,
                () -> expand(old));
    }

    private boolean expand(History old) {
        work.tick();
        if (old.rose && old.fell && capCovers(old.node, false)) {
            pay((requiredCount + 63) / 64 + 1);
            if (old.covered.cardinality() == requiredCount) {
                capacity.run();
                pay(old.path.length * 2 + 10);
                int[] path = new int[old.path.length * 2];
                for (int k = 0; k < old.path.length; k++) {
                    work.tick();
                    int n = old.path[k];
                    path[2 * k] = nx[n];
                    path[2 * k + 1] = ny[n];
                }
                answer =
                        new Fit(
                                true,
                                "complete-observed-polygon-history",
                                phaseX,
                                phaseY,
                                radius,
                                path,
                                nc,
                                np);
                return true;
            }
        }
        // Bind only a missing obligation in the OLD guard's newly sealed interval.
        int first = coreIndex.sealedIndex(nx[old.node] - radius);
        pay((requiredCount + 63) / 64 + 2);
        int missing = old.covered.nextClearBit(first);
        FirstMissingEndpointBound endpoint = null;
        if (missing < requiredCount)
            endpoint =
                    FirstMissingEndpointBound.create(
                            4 * requiredX[missing],
                            4 * requiredY[missing],
                            nx[old.node],
                            ny[old.node],
                            radius,
                            work,
                            capacity);
        // Farthest x first is search order, not a pruning or a merged partial history.
        for (int next = nn - 1; next >= 0; next--) {
            work.tick();
            if (nx[next] <= nx[old.node] || clearance[next] <= radiusSquared) continue;
            int dy = Integer.compare(ny[next], ny[old.node]);
            if (old.descending && dy < 0) continue;
            work.tick(); // New exact turn-language reachability check.
            if (!canReachUpperTurn(old.rose, dy)) continue;
            if (endpoint != null && !endpoint.allows(nx[next], ny[next])) continue;
            // History-specific veto BEFORE expensive edge geometry; never poison global edge memo.
            if (!SealedTransitionGuard.allows(
                    coreIndex,
                    requiredX,
                    requiredY,
                    requiredCount,
                    old.covered,
                    nx[old.node],
                    ny[old.node],
                    nx[next],
                    ny[next],
                    radius,
                    work)) continue;
            int edge = edge(old.node, next);
            if (!memoSafe[edge]) continue;
            BitSet covered = coverage(old.covered, edge);
            capacity.run();
            pay(old.path.length + 11);
            int[] path = new int[old.path.length + 1];
            for (int k = 0; k < old.path.length; k++) {
                work.tick();
                path[k] = old.path[k];
            }
            path[old.path.length] = next;
            History state =
                    new History(
                            next,
                            old.descending || dy > 0,
                            old.rose || dy < 0,
                            old.fell || dy > 0,
                            covered,
                            path);
            if (search(state)) return true;
        }
        return false;
    }

    // Before the first upward step, descending would make the required rose flag unreachable:
    // the original descending state forbids every later upward step.
    static boolean canReachUpperTurn(boolean rose, int deltaY) {
        return rose || deltaY <= 0;
    }

    /** Immutable geometry memo only: full histories and their separate coverage are never merged. */
    private int edge(int from, int to) {
        int set = (from * 31 + to) & 15, base = set * 4;
        for (int k = 0; k < 4; k++) {
            pay(3);
            int at = base + k;
            if (memoGeneration[at] == generation && memoFrom[at] == from && memoTo[at] == to)
                return at;
        }
        pay(3);
        int at = base + victim[set];
        victim[set] = (victim[set] + 1) & 3;
        boolean safe = safeChord(from, to);
        if (safe) {
            if (memoCore[at] == null) {
                capacity.run();
                pay((requiredCount + 63) / 64 + 4);
                memoCore[at] = new BitSet(requiredCount);
            } else {
                pay((requiredCount + 63) / 64 + 1);
                memoCore[at].clear();
            }
            // Full finite capsule also requires x within endpoint min/max plus radius.
            pay(10);
            int firstX = Math.max(0, -Math.floorDiv(-(Math.min(nx[from], nx[to]) - radius), 4));
            int lastX = Math.min(w - 1, Math.floorDiv(Math.max(nx[from], nx[to]) + radius, 4));
            for (int x = firstX; x <= lastX; x++) {
                work.tick();
                // safeChord completed the SAME prepared edge; no recursion/prepare before this
                // mask.
                long interval = strip.rowsAtColumn(4 * x, 0, h - 1);
                long indices = coreIndex.range(x, interval);
                if (indices == CapsuleStripBounds.NONE) continue;
                for (int q = CapsuleStripBounds.first(indices);
                        q < CapsuleStripBounds.last(indices);
                        q++) {
                    work.tick();
                    Ratio d =
                            squared(
                                    4 * requiredX[q],
                                    4 * requiredY[q],
                                    nx[from],
                                    ny[from],
                                    nx[to],
                                    ny[to],
                                    work);
                    if (d == null) throw new IllegalStateException("distance domain");
                    pay(2);
                    if (d.numerator() <= radiusSquared * d.denominator()) memoCore[at].set(q);
                }
            }
        }
        pay(4);
        memoGeneration[at] = generation;
        memoFrom[at] = from;
        memoTo[at] = to;
        memoSafe[at] = safe;
        return at;
    }

    private BitSet coverage(BitSet old, int edge) {
        capacity.run();
        pay((requiredCount + 63) / 64 + 4);
        BitSet next = old == null ? new BitSet(requiredCount) : (BitSet) old.clone();
        pay(2 * ((requiredCount + 63) / 64) + 2);
        next.or(memoCore[edge]);
        return next;
    }

    private boolean safeChord(int from, int to) {
        if (!strip.prepare(nx[from], ny[from], nx[to], ny[to], radius))
            throw new IllegalStateException("strip endpoint domain");
        int reach = (radius + 3) / 4;
        int xl = Math.max(-1, Math.floorDiv(nx[from], 4) - reach - 1),
                xr = Math.min(w, Math.floorDiv(nx[to] + 3, 4) + reach + 1);
        int yt = Math.max(-1, Math.floorDiv(Math.min(ny[from], ny[to]), 4) - reach - 1);
        int yb = Math.min(h, Math.floorDiv(Math.max(ny[from], ny[to]) + 3, 4) + reach + 1);
        for (int x = xl; x <= xr; x++) {
            // qx=4*x is a quarter-pixel coordinate; returned rows are whole pixels.
            long rows = strip.rowsAtColumn(4 * x, yt, yb);
            if (rows == CapsuleStripBounds.NONE) continue;
            for (int y = CapsuleStripBounds.first(rows); y <= CapsuleStripBounds.last(rows); y++) {
                work.tick();
                if (!paper[(y + 1) * rw + x + 1]) continue;
                Ratio d = squared(4 * x, 4 * y, nx[from], ny[from], nx[to], ny[to], work);
                if (d == null) throw new IllegalStateException("distance domain");
                work.tick();
                if (d.numerator() <= radiusSquared * d.denominator()) return false;
            }
        }
        return true;
    }
}

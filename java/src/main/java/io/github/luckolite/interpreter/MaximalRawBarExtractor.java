// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Private prototype: discovers maximal finite raw line bodies before semantic proposals. */
final class MaximalRawBarExtractor {
    private MaximalRawBarExtractor() {}

    static final int DARK_LIMIT = 185;
    static final int MAX_BODIES = 64;
    static final long MAX_PIXEL_WORK = 2_000_000L;
    // Conservative per-call cap for the two lane matrices, row-index lists, temporary
    // cross-sections, candidate bodies, alias sets, and connectivity scratch.
    static final long MAX_SCRATCH_BYTES = 64_000_000L;
    static final long MAX_JOINED_WORK_HARD_CEILING = JoinedLaneWorkBudget.JOINED_LANE_HARD_LIMIT;

    record Lane(int left, int right, int top, int bottom, int gap) {
        Lane {
            if (left < 1 || right <= left || top < 1 || bottom <= top || gap < 5 || gap > 64)
                throw new IllegalArgumentException("invalid bounded lane");
        }
    }

    record Body(
            int slopeTick,
            int firstX,
            int lastX,
            int centerAtLaneMid,
            List<Integer> rawPixels,
            int firstCapWidth,
            int lastCapWidth,
            int maxWidth,
            int unassignedContactPixels,
            int minY,
            int maxY) {
        Body {
            rawPixels = List.copyOf(rawPixels);
        }

        Body(
                int slopeTick,
                int firstX,
                int lastX,
                int centerAtLaneMid,
                List<Integer> rawPixels,
                int firstCapWidth,
                int lastCapWidth,
                int maxWidth,
                int unassignedContactPixels) {
            this(
                    slopeTick,
                    firstX,
                    lastX,
                    centerAtLaneMid,
                    rawPixels,
                    firstCapWidth,
                    lastCapWidth,
                    maxWidth,
                    unassignedContactPixels,
                    Integer.MIN_VALUE,
                    Integer.MAX_VALUE);
        }

        int length() {
            return lastX - firstX + 1;
        }

        int centerAt(int x, int laneMid) {
            return (int) Math.rint(centerAtLaneMid + (slopeTick * .05) * (x - laneMid));
        }
    }

    enum Status {
        CANDIDATES,
        BUDGET_EXHAUSTED,
        TOO_MANY_BODIES,
        INVALID_INPUT
    }

    record SourceIdentity(
            String graySha256,
            int width,
            int height,
            Lane lane,
            String staffSha256,
            boolean joined,
            String originalBodiesSha256,
            String selectedBodiesSha256) {}

    static final class FiniteFamilyProof {
        private final Discovery source;

        private FiniteFamilyProof(Discovery source) {
            this.source = source;
        }

        boolean matches(SparseStaffContact.Snapshot snapshot, Discovery discovery) {
            return source == discovery
                    && discovery.sourceIdentity() != null
                    && snapshot.rawSha.equals(discovery.sourceIdentity().graySha256())
                    && snapshot.width == discovery.sourceIdentity().width()
                    && snapshot.height == discovery.sourceIdentity().height()
                    && discovery.bodiesSha256().equals(bodiesSha256(discovery.bodies()));
        }
    }

    record Discovery(
            Status status,
            List<Body> bodies,
            long pixelWork,
            String terminalStage,
            SourceIdentity sourceIdentity,
            String bodiesSha256) {
        Discovery {
            bodies = List.copyOf(bodies);
        }

        Discovery(Status status, List<Body> bodies, long pixelWork) {
            this(
                    status,
                    bodies,
                    pixelWork,
                    "unspecified",
                    null,
                    MaximalRawBarExtractor.bodiesSha256(bodies));
        }

        Discovery(Status status, List<Body> bodies, long pixelWork, String terminalStage) {
            this(
                    status,
                    bodies,
                    pixelWork,
                    terminalStage,
                    null,
                    MaximalRawBarExtractor.bodiesSha256(bodies));
        }

        Discovery withBodies(List<Body> retained, long work) {
            String digest = MaximalRawBarExtractor.bodiesSha256(retained);
            SourceIdentity rebound =
                    sourceIdentity == null
                            ? null
                            : new SourceIdentity(
                                    sourceIdentity.graySha256(),
                                    sourceIdentity.width(),
                                    sourceIdentity.height(),
                                    sourceIdentity.lane(),
                                    sourceIdentity.staffSha256(),
                                    sourceIdentity.joined(),
                                    sourceIdentity.originalBodiesSha256(),
                                    digest);
            return new Discovery(status, retained, work, terminalStage, rebound, digest);
        }

        boolean authenticFor(
                String graySha,
                int w,
                int h,
                Lane expectedLane,
                Set<Integer> staff,
                boolean requireJoined) {
            return status == Status.CANDIDATES
                    && sourceIdentity != null
                    && sourceIdentity.width() == w
                    && sourceIdentity.height() == h
                    && sourceIdentity.lane().equals(expectedLane)
                    && sourceIdentity.joined() == requireJoined
                    && sourceIdentity.graySha256().equals(graySha)
                    && sourceIdentity.staffSha256().equals(staffSha256(staff))
                    && bodiesSha256.equals(MaximalRawBarExtractor.bodiesSha256(bodies))
                    && sourceIdentity.selectedBodiesSha256().equals(bodiesSha256);
        }

        boolean exactlyThreeSameShape(int gap) {
            if (status != Status.CANDIDATES || bodies.size() != 3) return false;
            List<Body> b =
                    bodies.stream()
                            .sorted(Comparator.comparingInt(x -> x.centerAtLaneMid))
                            .toList();
            Body a = b.get(0), m = b.get(1), c = b.get(2);
            if (a.slopeTick != m.slopeTick || m.slopeTick != c.slopeTick) return false;
            if (Math.abs(a.length() - m.length()) > 1 || Math.abs(m.length() - c.length()) > 1)
                return false;
            int d1 = m.centerAtLaneMid - a.centerAtLaneMid,
                    d2 = c.centerAtLaneMid - m.centerAtLaneMid;
            return d1 >= .5 * gap
                    && d1 <= 1.1 * gap
                    && d2 >= .5 * gap
                    && d2 <= 1.1 * gap
                    && Math.abs(d1 - d2) <= .2 * gap;
        }

        /** New physical-source route accepts either two or three complete observed bars. */
        boolean twoOrThreeSameShape(int gap) {
            if (status != Status.CANDIDATES || bodies.size() < 2 || bodies.size() > 3) return false;
            List<Body> b =
                    bodies.stream()
                            .sorted(Comparator.comparingInt(x -> x.centerAtLaneMid))
                            .toList();
            int slope = b.get(0).slopeTick;
            for (Body body : b) {
                if (body.slopeTick != slope || Math.abs(body.length() - b.get(0).length()) > 1)
                    return false;
            }
            int prior = b.get(0).centerAtLaneMid;
            for (int i = 1; i < b.size(); i++) {
                int distance = b.get(i).centerAtLaneMid - prior;
                if (distance < .5 * gap || distance > 1.1 * gap) return false;
                if (i > 1) {
                    int previousDistance = prior - b.get(i - 2).centerAtLaneMid;
                    if (Math.abs(distance - previousDistance) > .2 * gap) return false;
                }
                prior = b.get(i).centerAtLaneMid;
            }
            return true;
        }

        /** Exact raw-edge family predicate; representative search ticks are not proof. */
        boolean twoOrThreeObservedFamily(int gap, int width, Set<Integer> staffPixels) {
            if (status != Status.CANDIDATES
                    || width <= 0
                    || bodies.size() < 2
                    || bodies.size() > 3
                    || staffPixels == null
                    || staffPixels.isEmpty()) return false;
            List<java.util.Map<Integer, int[]>> edges = new ArrayList<>();
            java.util.Map<Integer, Set<Integer>> staffRows = new java.util.HashMap<>();
            for (Body body : bodies) {
                java.util.Map<Integer, int[]> byX = new java.util.TreeMap<>();
                for (int pixel : body.rawPixels) {
                    if (pixel < 0) return false;
                    int x = pixel % width, y = pixel / width;
                    int[] edge = byX.get(x);
                    if (edge == null) byX.put(x, new int[] {y, y});
                    else {
                        edge[0] = Math.min(edge[0], y);
                        edge[1] = Math.max(edge[1], y);
                    }
                }
                if (byX.size() != body.length()) return false;
                edges.add(byX);
            }
            for (int pixel : staffPixels) {
                if (pixel < 0) return false;
                int x = pixel % width, y = pixel / width;
                staffRows.computeIfAbsent(x, ignored -> new java.util.HashSet<>()).add(y);
            }
            return ObservedParallelBodyFamily.qualifies(edges, staffRows, gap);
        }

        long staffBridgeWorkUpperBound(int gap) {
            if (gap < 5 || gap > 64 || status != Status.CANDIDATES) return Long.MAX_VALUE;
            long cost = 0;
            try {
                for (Body body : bodies) {
                    long n = body.rawPixels().size();
                    int log =
                            32 - Integer.numberOfLeadingZeros((int) Math.min(Integer.MAX_VALUE, n));
                    cost =
                            Math.addExact(
                                    cost, n * (160L + 8L * log) + body.length() * (4L * gap + 64));
                }
                return cost;
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }

        Discovery completeStaffBridges(
                byte[] gray, int width, int height, Set<Integer> staff, int gap, long prepaid) {
            if (sourceIdentity == null
                    || !sourceIdentity.joined()
                    || sourceIdentity.width() != width
                    || sourceIdentity.height() != height
                    || !sourceIdentity.staffSha256().equals(staffSha256(staff))) return null;
            var completed =
                    StaffBridgeBodyCompletion.complete(
                            gray,
                            width,
                            height,
                            bodies.stream().map(Body::rawPixels).toList(),
                            staff,
                            gap,
                            prepaid);
            FringeProfileDiagnostics.record(
                    "STAFF_BRIDGE_COMPLETION\tstatus="
                            + completed.status()
                            + " added="
                            + completed.added()
                            + " work="
                            + completed.work());
            if (completed.status() != StaffBridgeBodyCompletion.Status.COMPLETE) return null;
            if (completed.added() == 0) return this;
            List<Body> updated = new ArrayList<>();
            int mid = (sourceIdentity.lane().left() + sourceIdentity.lane().right()) / 2;
            for (int i = 0; i < bodies.size(); i++) {
                Body old = bodies.get(i);
                var pixels = completed.bodies().get(i);
                int[] caps =
                        projectedCapWidths(
                                pixels, width, mid, old.slopeTick() * .05, old.centerAtLaneMid());
                if (caps == null || caps[0] < 1 || caps[1] < 1) return null;
                Set<Integer> owned = new HashSet<>(pixels), contacts = new HashSet<>();
                for (int pixel : pixels) {
                    int x = pixel % width, y = pixel / width;
                    for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
                        for (int xx = Math.max(0, x - 1); xx <= Math.min(width - 1, x + 1); xx++) {
                            int q = yy * width + xx;
                            if (!owned.contains(q)
                                    && !staff.contains(q)
                                    && (gray[q] & 255) < DARK_LIMIT) contacts.add(q);
                        }
                }
                updated.add(
                        new Body(
                                old.slopeTick(),
                                old.firstX(),
                                old.lastX(),
                                old.centerAtLaneMid(),
                                pixels,
                                caps[0],
                                caps[1],
                                caps[2],
                                contacts.size(),
                                minY(pixels, width),
                                maxY(pixels, width)));
            }
            return withBodies(updated, pixelWork + prepaid);
        }

        FiniteFamilyProof proveFiniteFamily(
                int gap, int width, Set<Integer> staff, byte[] gray, int height, long prepaid) {
            if (sourceIdentity == null
                    || !sourceIdentity.joined()
                    || !twoOrThreeFiniteFamily(gap, width, staff, gray, height, prepaid))
                return null;
            return new FiniteFamilyProof(this);
        }

        long finiteFamilyWorkUpperBound(int width, Set<Integer> staffPixels) {
            if (width <= 0 || staffPixels == null || bodies.size() < 2 || bodies.size() > 3)
                return Long.MAX_VALUE;
            try {
                long cost = staffPixels.size() * 4L;
                for (Body body : bodies) {
                    long n = body.length();
                    if (n < 2 || n > 512) return Long.MAX_VALUE;
                    cost =
                            Math.addExact(
                                    cost,
                                    Math.addExact(
                                            4096L * n + 64L * n * n,
                                            Math.multiplyExact(
                                                    192L,
                                                    Math.multiplyExact(
                                                            n, Math.multiplyExact(n, n)))));
                    cost = Math.addExact(cost, body.rawPixels().size() * 4L);
                }
                return cost;
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }

        boolean twoOrThreeFiniteFamily(
                int gap,
                int width,
                Set<Integer> staffPixels,
                byte[] gray,
                int height,
                long prepaid) {
            if (status != Status.CANDIDATES
                    || width <= 0
                    || bodies.size() < 2
                    || bodies.size() > 3
                    || staffPixels == null
                    || staffPixels.isEmpty()) return false;
            List<java.util.Map<Integer, int[]>> edges = new ArrayList<>();
            java.util.Map<Integer, Set<Integer>> staffRows = new java.util.HashMap<>();
            for (Body body : bodies) {
                java.util.Map<Integer, int[]> byX = new java.util.TreeMap<>();
                for (int pixel : body.rawPixels()) {
                    if (pixel < 0) return false;
                    int x = pixel % width, y = pixel / width;
                    int[] e = byX.get(x);
                    if (e == null) byX.put(x, new int[] {y, y});
                    else {
                        e[0] = Math.min(e[0], y);
                        e[1] = Math.max(e[1], y);
                    }
                }
                if (byX.size() != body.length()) return false;
                edges.add(byX);
            }
            for (int pixel : staffPixels) {
                if (pixel < 0) return false;
                staffRows
                        .computeIfAbsent(pixel % width, k -> new java.util.HashSet<>())
                        .add(pixel / width);
            }
            return ObservedFiniteBodyFamily.qualifies(
                    gray, width, height, edges, staffRows, gap, prepaid);
        }

        long observedFamilyWorkUpperBound(int width, Set<Integer> staffPixels) {
            return ObservedParallelBodyFamily.workUpperBound(bodies, width, staffPixels);
        }

        boolean ownershipReadyWithoutStaffProof() {
            return status == Status.CANDIDATES
                    && !bodies.isEmpty()
                    && bodies.stream().allMatch(b -> b.unassignedContactPixels == 0);
        }
    }

    /** Geometry-derived bounded allowance for one authenticated joined lane. */
    static long joinedWorkBudgetFor(Lane lane) {
        if (lane == null) return 0;
        try {
            int radius = (int) Math.rint(.5 * lane.gap);
            int maxWidth = (int) Math.rint(.75 * lane.gap);
            long width = (long) lane.right - lane.left + 1;
            long intercepts = (long) lane.bottom - lane.top + 1;
            long spanHeight = intercepts + 2L * radius;
            long cells = Math.multiplyExact(width, spanHeight);
            long hypotheses = Math.multiplyExact(24L, intercepts);
            long maxRowsPerWindow = ((2L * radius + 2) / 2) + 1;
            long maxPixels = Math.multiplyExact(width, maxWidth);
            long bodyOps =
                    Math.addExact(
                            Math.addExact(
                                    4L * width,
                                    Math.multiplyExact(maxPixels, 25L + ceilLog2(maxPixels + 1))),
                            Math.multiplyExact(maxPixels, MAX_BODIES));
            long perHypothesis =
                    Math.addExact(Math.multiplyExact(width, 1L + maxRowsPerWindow), bodyOps);
            long maxRunsPerColumn = (spanHeight + 1) / 2;
            long maxPartitionRun = 3L * maxWidth + 2L * lane.gap;
            long partitionCache =
                    Math.multiplyExact(
                            Math.multiplyExact(Math.multiplyExact(width, maxRunsPerColumn), 3L),
                            maxPartitionRun);
            long total =
                    Math.addExact(
                            Math.multiplyExact(2L, cells),
                            Math.addExact(
                                    Math.multiplyExact(hypotheses, perHypothesis), partitionCache));
            return Math.max(1L, Math.min(MAX_JOINED_WORK_HARD_CEILING, total));
        } catch (ArithmeticException overflow) {
            return MAX_JOINED_WORK_HARD_CEILING;
        }
    }

    private static String frac(Object q) throws Exception {
        var c = q.getClass();
        var nf = c.getDeclaredField("n");
        var df = c.getDeclaredField("d");
        nf.setAccessible(true);
        df.setAccessible(true);
        return nf.get(q) + "/" + df.get(q);
    }

    static Discovery discover(byte[] gray, int width, int height, Lane lane) {
        return discover(gray, width, height, lane, MAX_PIXEL_WORK);
    }

    static Discovery discover(byte[] gray, int width, int height, Lane lane, long workBudget) {
        return discover(gray, width, height, lane, workBudget, MAX_SCRATCH_BYTES);
    }

    static Discovery discover(
            byte[] gray, int width, int height, Lane lane, long workBudget, long scratchBudget) {
        return discover(gray, width, height, lane, workBudget, scratchBudget, null);
    }

    /** Exact independently owned staff pixels may be excluded from bar ownership during discovery. */
    static Discovery discover(
            byte[] gray,
            int width,
            int height,
            Lane lane,
            long workBudget,
            long scratchBudget,
            java.util.Set<Integer> staffPixels) {
        return bindIdentity(
                discoverInternal(
                        gray, width, height, lane, workBudget, scratchBudget, staffPixels, false),
                gray,
                width,
                height,
                lane,
                staffPixels,
                false);
    }

    /** Optional foreground co-membership route. Staff pixels are never background. */
    static Discovery discoverJoined(
            byte[] gray,
            int width,
            int height,
            Lane lane,
            long workBudget,
            long scratchBudget,
            java.util.Set<Integer> staffPixels) {
        if (staffPixels == null || staffPixels.isEmpty())
            return new Discovery(Status.INVALID_INPUT, List.of(), 0);
        return bindIdentity(
                discoverInternal(
                        gray, width, height, lane, workBudget, scratchBudget, staffPixels, true),
                gray,
                width,
                height,
                lane,
                staffPixels,
                true);
    }

    private static Discovery discoverInternal(
            byte[] gray,
            int width,
            int height,
            Lane lane,
            long workBudget,
            long scratchBudget,
            java.util.Set<Integer> staffPixels,
            boolean joined) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || lane.right >= width
                || lane.bottom >= height
                || workBudget < 1
                || scratchBudget < 0
                || (joined && workBudget > MAX_JOINED_WORK_HARD_CEILING))
            return new Discovery(Status.INVALID_INPUT, List.of(), 0);
        int mid = (lane.left + lane.right) / 2;
        int minWidth = 1;
        int maxWidth = (int) Math.rint(.75 * lane.gap);
        int radius = (int) Math.rint(.5 * lane.gap);
        int minLength = Math.max(2, (int) Math.rint(.45 * 1.4 * lane.gap));
        int spanTop = Math.max(0, lane.top - radius),
                spanBottom = Math.min(height - 1, lane.bottom + radius);
        int spanHeight = spanBottom - spanTop + 1, laneWidth = lane.right - lane.left + 1;
        long cells = (long) spanHeight * laneWidth;
        // Reserve worst-case temporary/candidate storage before allocating either matrix.
        // The factor covers boxed raw-pixel lists for up to MAX_BODIES candidates, active
        // membership/contact sets, and both directional lane matrices.
        long reserve;
        try {
            reserve =
                    Math.addExact(
                            Math.multiplyExact(cells, 2048L),
                            Math.multiplyExact((long) laneWidth, 512L));
        } catch (ArithmeticException overflow) {
            return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), 0);
        }
        if (cells > Integer.MAX_VALUE
                || reserve > MAX_SCRATCH_BYTES
                || reserve > scratchBudget
                || cells > workBudget) return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), 0);
        // Account for both directional raw scans before allocation; the second scan used to
        // be omitted from the logical work total.
        long work = 0;
        if (cells > workBudget / 2) return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), 0);
        int[][] runTop = new int[laneWidth][spanHeight], runBottom = new int[laneWidth][spanHeight];
        @SuppressWarnings("unchecked")
        List<Integer>[] darkRows = (List<Integer>[]) new List<?>[laneWidth];
        @SuppressWarnings("unchecked")
        List<int[]>[] staffSplitRuns = joined ? (List<int[]>[]) new List<?>[laneWidth] : null;
        for (int x = lane.left; x <= lane.right; x++) {
            darkRows[x - lane.left] = new ArrayList<>();
            int lastDark = -1;
            for (int y = spanTop; y <= spanBottom; y++) {
                if (work >= workBudget)
                    return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                work++;
                if (dark(gray, width, x, y, joined ? null : staffPixels)) {
                    if (lastDark < 0) {
                        if (work >= workBudget)
                            return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                        work++;
                        lastDark = y;
                        // Store one entry per complete raw run, not every ink pixel.
                        darkRows[x - lane.left].add(y);
                    }
                } else lastDark = -1;
                runTop[x - lane.left][y - spanTop] = lastDark;
            }
            int nextDark = -1;
            for (int y = spanBottom; y >= spanTop; y--) {
                if (work >= workBudget)
                    return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                work++;
                if (dark(gray, width, x, y, joined ? null : staffPixels)) {
                    if (nextDark < 0) nextDark = y;
                } else nextDark = -1;
                runBottom[x - lane.left][y - spanTop] = nextDark;
            }
            if (joined) {
                // A witnessed staff core can join two complete raw bars into one
                // vertical run. Preserve the full raster, but index the separate
                // nonstaff intervals as candidate body cross-sections. This does
                // not erase or relabel staff pixels; they remain in the independent
                // carrier set and are never inserted into a body member list.
                staffSplitRuns[x - lane.left] = new ArrayList<>();
                int start = -1;
                for (int y = spanTop; y <= spanBottom; y++) {
                    if (work >= workBudget)
                        return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                    work++;
                    boolean ink = dark(gray, width, x, y, staffPixels);
                    if (ink && start < 0) start = y;
                    if (!ink && start >= 0) {
                        staffSplitRuns[x - lane.left].add(new int[] {start, y - 1});
                        start = -1;
                    }
                }
                if (start >= 0) staffSplitRuns[x - lane.left].add(new int[] {start, spanBottom});
            }
        }
        List<Body> rawCandidates = new ArrayList<>();
        // Enumerate all raw intercepts over the entire lane for every already-supported slope.
        for (int tick = -13; tick <= 13; tick++) {
            if (tick == 0 || Math.abs(tick) < 2) continue;
            for (int intercept = lane.top; intercept <= lane.bottom; intercept++) {
                if (laneWidth > workBudget - work)
                    return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                work += laneWidth;
                int[] widths = new int[lane.right - lane.left + 1];
                int[] tops = new int[widths.length], bottoms = new int[widths.length];
                for (int x = lane.left; x <= lane.right; x++) {
                    int y = (int) Math.rint(intercept + tick * .05 * (x - mid));
                    int at = x - lane.left;
                    if (y - radius < spanTop || y + radius > spanBottom) continue;
                    // A centered raw cross-section is one contiguous body, not a sum of nearby ink.
                    List<Integer> ys = darkRows[at];
                    int lo = lowerBound(ys, y - radius), hi = upperBound(ys, y + radius);
                    int best = 0, bestTop = -1, bestBottom = -1, bestDistance = Integer.MAX_VALUE;
                    boolean tie = false;
                    for (int k = lo; k < hi; k++) {
                        if (work >= workBudget)
                            return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                        work++;
                        int yy = ys.get(k),
                                t = runTop[at][yy - spanTop],
                                b = runBottom[at][yy - spanTop];
                        // Never accept a clipped slice of a neighboring raw stroke. A candidate
                        // axis must contain a complete vertical cross-section at each x. When an
                        // independently witnessed staff core joins several bars into one raw run,
                        // use only complete nonstaff sub-runs inside this same axis window.
                        int top = t, bottom = b;
                        if (t < y - radius || b > y + radius) {
                            if (!joined) continue;
                            int selectedTop = -1,
                                    selectedBottom = -1,
                                    selectedDistance = Integer.MAX_VALUE;
                            boolean splitTie = false;
                            for (int[] segment : staffSplitRuns[at]) {
                                if (work >= workBudget)
                                    return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                                work++;
                                int st = segment[0], sb = segment[1];
                                if (st < y - radius || sb > y + radius) continue;
                                int sn = sb - st + 1;
                                if (sn < minWidth || sn > maxWidth) continue;
                                int sd = Math.abs((st + sb) / 2 - y);
                                if (sd < selectedDistance) {
                                    selectedTop = st;
                                    selectedBottom = sb;
                                    selectedDistance = sd;
                                    splitTie = false;
                                } else if (sd == selectedDistance
                                        && (st != selectedTop || sb != selectedBottom))
                                    splitTie = true;
                            }
                            if (splitTie || selectedTop < 0) continue;
                            top = selectedTop;
                            bottom = selectedBottom;
                        }
                        int n = bottom - top + 1;
                        if (work >= workBudget)
                            return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                        work++;
                        if (n < minWidth || n > (joined ? 2 * maxWidth : maxWidth)) continue;
                        int distance = Math.abs((top + bottom) / 2 - y);
                        if (distance < bestDistance) {
                            best = n;
                            bestTop = top;
                            bestBottom = bottom;
                            bestDistance = distance;
                            tie = false;
                        } else if (distance == bestDistance
                                && (top != bestTop || bottom != bestBottom)) tie = true;
                    }
                    if (!tie && best >= minWidth && best <= (joined ? 2 * maxWidth : maxWidth)) {
                        widths[at] = best;
                        tops[at] = bestTop;
                        bottoms[at] = bestBottom;
                    }
                }
                if (joined) {
                    long[] remaining = {workBudget - work};
                    boolean admitted =
                            RawStaffBarPartition.apply(
                                    widths,
                                    tops,
                                    bottoms,
                                    lane.left,
                                    width,
                                    lane.gap,
                                    maxWidth,
                                    staffPixels,
                                    remaining);
                    work = workBudget - remaining[0];
                    if (remaining[0] < 0)
                        return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), workBudget);
                    if (!admitted) continue;
                }
                int x = lane.left;
                while (x <= lane.right) {
                    while (x <= lane.right && widths[x - lane.left] == 0) x++;
                    int first = x;
                    while (x <= lane.right && widths[x - lane.left] > 0) x++;
                    int last = x - 1;
                    if (last - first + 1 < minLength) continue;
                    int firstWidth = widths[first - lane.left],
                            lastWidth = widths[last - lane.left];
                    int interiorMax = 0;
                    for (int q = first + 1; q < last; q++) {
                        if (work >= workBudget)
                            return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                        work++;
                        interiorMax = Math.max(interiorMax, widths[q - lane.left]);
                    }
                    // Both visible ends must taper relative to the interior. A clipped/unclosed
                    // body abstains.
                    if (interiorMax == 0) continue;
                    long sideWork = 4L * (last - first + 1);
                    if (sideWork > workBudget - work)
                        return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                    work += sideWork;
                    if (!coherentSides(
                            widths,
                            tops,
                            bottoms,
                            first,
                            last,
                            lane.left,
                            mid,
                            tick * .05,
                            intercept)) continue;
                    List<Integer> pixels = new ArrayList<>();
                    int thickMax = 0;
                    for (int q = first; q <= last; q++) {
                        if (work >= workBudget)
                            return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                        work++;
                        int at = q - lane.left;
                        thickMax = Math.max(thickMax, widths[at]);
                        for (int yy = tops[at]; yy <= bottoms[at]; yy++) {
                            if (work >= workBudget)
                                return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                            work++;
                            pixels.add(yy * width + q);
                        }
                    }
                    long bodyWork =
                            25L * pixels.size() + pixels.size() * ceilLog2(pixels.size() + 1L);
                    // Side scans, set/queue construction and neighbor probes, cap projections,
                    // plus sorting the projected cap slices.
                    if (bodyWork > workBudget - work)
                        return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                    work += bodyWork;
                    if (!connected8(pixels, width)) continue;
                    int[] caps = projectedCapWidths(pixels, width, mid, tick * .05, intercept);
                    if (caps == null
                            || caps[0] < 1
                            || caps[1] < 1
                            || first == lane.left
                            || last == lane.right) continue;
                    Body candidate =
                            new Body(
                                    tick,
                                    first,
                                    last,
                                    intercept,
                                    pixels,
                                    caps[0],
                                    caps[1],
                                    caps[2],
                                    0,
                                    minY(pixels, width),
                                    maxY(pixels, width));
                    long aliasBuild = pixels.size();
                    if (aliasBuild > workBudget - work)
                        return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                    work += aliasBuild;
                    Set<Integer> candidatePixels = new HashSet<>(pixels);
                    int aliasIndex = -1;
                    for (int i = 0; i < rawCandidates.size(); i++) {
                        Body prior = rawCandidates.get(i);
                        int overlap = 0;
                        for (int q : prior.rawPixels) {
                            if (work >= workBudget)
                                return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                            work++;
                            if (candidatePixels.contains(q)) overlap++;
                        }
                        int smaller = Math.min(prior.rawPixels.size(), pixels.size());
                        if (smaller > 0 && overlap / (double) smaller >= .50) {
                            aliasIndex = i;
                            break;
                        }
                    }
                    if (aliasIndex < 0) rawCandidates.add(candidate);
                    else if (pixels.size() > rawCandidates.get(aliasIndex).rawPixels.size())
                        rawCandidates.set(aliasIndex, candidate);
                    if (rawCandidates.size() > MAX_BODIES)
                        return new Discovery(Status.TOO_MANY_BODIES, List.of(), work);
                }
            }
        }
        // Collapse intercept/slope aliases only by strong raw-pixel overlap; retain the maximal
        // support.
        long aliasWork = (long) rawCandidates.size() * ceilLog2(rawCandidates.size() + 1L);
        if (aliasWork > workBudget - work)
            return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
        work += aliasWork;
        rawCandidates.sort(Comparator.comparingInt((Body b) -> b.rawPixels.size()).reversed());
        List<Body> maximal = new ArrayList<>();
        for (Body candidate : rawCandidates) {
            boolean alias = false;
            if (candidate.rawPixels.size() > workBudget - work)
                return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
            work += candidate.rawPixels.size(); // candidate membership set construction
            Set<Integer> pixels = new HashSet<>(candidate.rawPixels);
            for (Body kept : maximal) {
                int overlap = 0;
                for (int p : kept.rawPixels) {
                    if (work >= workBudget)
                        return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                    work++;
                    if (pixels.contains(p)) overlap++;
                }
                int smaller = Math.min(kept.rawPixels.size(), candidate.rawPixels.size());
                if (smaller > 0 && overlap / (double) smaller >= .50) {
                    alias = true;
                    break;
                }
            }
            if (!alias) maximal.add(candidate);
            if (maximal.size() > MAX_BODIES)
                return new Discovery(Status.TOO_MANY_BODIES, List.of(), work);
        }
        List<Body> withContacts = new ArrayList<>();
        for (Body b : maximal) {
            long buildWork = b.rawPixels.size();
            if (buildWork > workBudget - work)
                return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
            work += buildWork;
            Set<Integer> owned = new HashSet<>(b.rawPixels);
            Set<Integer> contacts = new HashSet<>();
            for (int pixel : b.rawPixels) {
                if (workBudget - work < 10)
                    return new Discovery(Status.BUDGET_EXHAUSTED, List.of(), work);
                work += 10; // bounded 3x3 raw contact neighborhood per body pixel
                int px = pixel % width, py = pixel / width;
                for (int yy = Math.max(0, py - 1); yy <= Math.min(height - 1, py + 1); yy++)
                    for (int xx = Math.max(0, px - 1); xx <= Math.min(width - 1, px + 1); xx++) {
                        int q = yy * width + xx;
                        if (!owned.contains(q) && dark(gray, width, xx, yy, staffPixels))
                            contacts.add(q);
                    }
            }
            withContacts.add(
                    new Body(
                            b.slopeTick,
                            b.firstX,
                            b.lastX,
                            b.centerAtLaneMid,
                            b.rawPixels,
                            b.firstCapWidth,
                            b.lastCapWidth,
                            b.maxWidth,
                            contacts.size(),
                            b.minY,
                            b.maxY));
        }
        withContacts.sort(
                Comparator.comparingInt((Body b) -> b.firstX)
                        .thenComparingInt(b -> b.centerAtLaneMid));
        return new Discovery(Status.CANDIDATES, withContacts, work);
    }

    private static boolean connected8(List<Integer> pixels, int width) {
        Set<Integer> remaining = new HashSet<>(pixels);
        if (remaining.isEmpty()) return false;
        List<Integer> todo = new ArrayList<>();
        int seed = remaining.iterator().next();
        remaining.remove(seed);
        todo.add(seed);
        for (int i = 0; i < todo.size(); i++) {
            int p = todo.get(i), x = p % width, y = p / width;
            for (int yy = y - 1; yy <= y + 1; yy++)
                for (int xx = x - 1; xx <= x + 1; xx++) {
                    if (xx < 0 || yy < 0) continue;
                    int q = yy * width + xx;
                    if (remaining.remove(q)) todo.add(q);
                }
        }
        return remaining.isEmpty();
    }

    /** Side edges follow one digital axis; caps taper into a broad, coherent interior. */
    static boolean coherentSides(
            int[] widths,
            int[] tops,
            int[] bottoms,
            int first,
            int last,
            int laneLeft,
            int mid,
            double slope,
            int intercept) {
        int n = last - first + 1;
        if (n < 2) return false;
        int maximum = 0;
        for (int i = 0; i < n; i++) {
            int at = first - laneLeft + i;
            maximum = Math.max(maximum, widths[at]);
            double center = (tops[at] + bottoms[at]) * .5;
            double expected = intercept + slope * (first + i - mid);
            if (Math.abs(center - expected) > widths[at] * .5 + .5) return false;
        }
        int coreStart = -1, coreEnd = -1;
        for (int i = 0; i < n; i++)
            if (widths[first - laneLeft + i] >= maximum - 1) {
                if (coreStart < 0) coreStart = i;
                coreEnd = i;
            }
        if (coreStart < 0 || coreEnd < coreStart || coreEnd - coreStart + 1 < (n + 1) / 2)
            return false;
        // The qualifying central support must be one continuous core. Endpoints alone
        // previously allowed two wide lobes separated by a thin neck.
        for (int i = coreStart; i <= coreEnd; i++)
            if (widths[first - laneLeft + i] < maximum - 1) return false;
        for (int i = 1; i <= coreStart; i++)
            if (widths[first - laneLeft + i] < widths[first - laneLeft + i - 1] - 1) return false;
        for (int i = coreEnd + 1; i < n; i++)
            if (widths[first - laneLeft + i] > widths[first - laneLeft + i - 1] + 1) return false;
        return true;
    }

    private static int lowerBound(List<Integer> values, int target) {
        int lo = 0, hi = values.size();
        while (lo < hi) {
            int m = (lo + hi) >>> 1;
            if (values.get(m) < target) lo = m + 1;
            else hi = m;
        }
        return lo;
    }

    private static int upperBound(List<Integer> values, int target) {
        int lo = 0, hi = values.size();
        while (lo < hi) {
            int m = (lo + hi) >>> 1;
            if (values.get(m) <= target) lo = m + 1;
            else hi = m;
        }
        return lo;
    }

    private static long ceilLog2(long n) {
        if (n <= 1) return 1;
        return 64 - Long.numberOfLeadingZeros(n - 1);
    }

    private static int[] projectedCapWidths(
            List<Integer> pixels, int width, int mid, double slope, int intercept) {
        double norm = Math.sqrt(1 + slope * slope);
        java.util.Map<Integer, Set<Integer>> slices = new java.util.HashMap<>();
        for (int p : pixels) {
            int x = p % width, y = p / width;
            double u = ((x - mid) + slope * (y - intercept)) / norm;
            double v = ((y - intercept) - slope * (x - mid)) / norm;
            slices.computeIfAbsent((int) Math.rint(u), k -> new HashSet<>())
                    .add((int) Math.rint(v));
        }
        if (slices.size() < 3) return null;
        List<Integer> keys = slices.keySet().stream().sorted().toList();
        int left = slices.get(keys.get(0)).size(),
                right = slices.get(keys.get(keys.size() - 1)).size(),
                max = 0;
        for (int key : keys) max = Math.max(max, slices.get(key).size());
        return new int[] {left, right, max};
    }

    private static int minY(List<Integer> pixels, int width) {
        int min = Integer.MAX_VALUE;
        for (int pixel : pixels) min = Math.min(min, pixel / width);
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private static int maxY(List<Integer> pixels, int width) {
        int max = Integer.MIN_VALUE;
        for (int pixel : pixels) max = Math.max(max, pixel / width);
        return max == Integer.MIN_VALUE ? 0 : max;
    }

    private static Discovery bindIdentity(
            Discovery discovery,
            byte[] gray,
            int width,
            int height,
            Lane lane,
            Set<Integer> staff,
            boolean joined) {
        if (discovery.status() != Status.CANDIDATES || gray == null || lane == null)
            return discovery;
        String digest = bodiesSha256(discovery.bodies());
        SourceIdentity identity =
                new SourceIdentity(
                        sha256(gray),
                        width,
                        height,
                        lane,
                        staffSha256(staff),
                        joined,
                        digest,
                        digest);
        return new Discovery(
                discovery.status(),
                discovery.bodies(),
                discovery.pixelWork(),
                discovery.terminalStage(),
                identity,
                digest);
    }

    private static String sha256(byte[] bytes) {
        try {
            return RasterEvidenceIdentity.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String staffSha256(Set<Integer> staff) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (staff != null)
                for (int value : staff.stream().mapToInt(Integer::intValue).sorted().toArray()) {
                    md.update((byte) (value >>> 24));
                    md.update((byte) (value >>> 16));
                    md.update((byte) (value >>> 8));
                    md.update((byte) value);
                }
            return RasterEvidenceIdentity.hex(md.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static String bodiesSha256(List<Body> bodies) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Body body : bodies) {
                updateInt(md, body.slopeTick());
                updateInt(md, body.firstX());
                updateInt(md, body.lastX());
                updateInt(md, body.centerAtLaneMid());
                updateInt(md, body.firstCapWidth());
                updateInt(md, body.lastCapWidth());
                updateInt(md, body.maxWidth());
                updateInt(md, body.unassignedContactPixels());
                updateInt(md, body.minY());
                updateInt(md, body.maxY());
                updateInt(md, body.rawPixels().size());
                for (int pixel : body.rawPixels()) updateInt(md, pixel);
            }
            return RasterEvidenceIdentity.hex(md.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void updateInt(MessageDigest md, int value) {
        md.update((byte) (value >>> 24));
        md.update((byte) (value >>> 16));
        md.update((byte) (value >>> 8));
        md.update((byte) value);
    }

    private static boolean dark(
            byte[] gray, int width, int x, int y, java.util.Set<Integer> staffPixels) {
        int index = y * width + x;
        return (staffPixels == null || !staffPixels.contains(index))
                && (gray[index] & 255) < DARK_LIMIT;
    }
}

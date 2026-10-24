// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Positive complete-member geometry only. The caller separately authenticates
 * producer identities and every head/recovery/shape KEEP obligation. This is an
 * existential explanation, not ownership consensus or a negative head theorem. */
final class CompleteSlurExplanation {
    static final class Geometry {
        private final int[] target, first, second, used;
        private final LiteralStaffCarrierFamily carrier;
        private final List<int[]> retainedLiteralPaths, retainedLiteralProfiles;
        private final List<Portal> retainedPortalDependencies;
        final int completeStaffFrames, literalPaths, aaPortals;
        final FiniteSlurRibbon.Certificate firstStrip, secondStrip;

        private Geometry(
                int[] t,
                Set<Integer> a,
                Set<Integer> b,
                LiteralStaffCarrierFamily staff,
                List<int[]> paths,
                List<Portal> portals,
                FiniteSlurRibbon.Certificate ca,
                FiniteSlurRibbon.Certificate cb) {
            target = t.clone();
            first = sorted(a);
            second = sorted(b);
            var u = new TreeSet<Integer>(a);
            u.addAll(b);
            for (int p : t) u.add(p);
            used = sorted(u);
            carrier = staff;
            retainedLiteralPaths = new ArrayList<>();
            for (int[] p : paths) retainedLiteralPaths.add(p.clone());
            retainedLiteralProfiles = new ArrayList<>();
            for (Portal p : portals) retainedLiteralProfiles.add(p.interval.clone());
            retainedPortalDependencies = List.copyOf(portals);
            completeStaffFrames = staff.completeFrames();
            literalPaths = paths.size();
            aaPortals = portals.size();
            firstStrip = ca;
            secondStrip = cb;
        }

        int[] target() {
            return target.clone();
        }

        int[] first() {
            return first.clone();
        }

        int[] second() {
            return second.clone();
        }

        int[] used() {
            return used.clone();
        }

        boolean exclusiveOwnership() {
            return false;
        }

        boolean observedCarrier(int p) {
            return carrier.observed(p);
        }
    }

    private record Portal(int member, int[] interval, Set<Integer> prefix, int core) {}

    private record Addition(Set<Integer> members, List<Portal> portals, List<int[]> paths) {}

    private final byte[] gray;
    private final int w, h;
    private final SlurEvidenceBudget budget;
    private final LiteralStaffCarrierFamily staff;
    private final Set<Integer> upper, lower, receivers;
    private final List<int[]> paths;
    private final Map<Integer, List<Portal>> portalCache = new HashMap<>();

    private CompleteSlurExplanation(
            byte[] g,
            int width,
            int height,
            LiteralStaffCarrierFamily s,
            Set<Integer> a,
            Set<Integer> b,
            Set<Integer> heads,
            SlurEvidenceBudget bd) {
        gray = g;
        w = width;
        h = height;
        staff = s;
        upper = a;
        lower = b;
        receivers = heads;
        budget = bd;
        paths = new ArrayList<>();
    }

    static Geometry propose(
            byte[] gray,
            int width,
            int height,
            int[] exactTarget,
            List<int[]> positiveReceivers,
            float nativeTop,
            float gap,
            SlurEvidenceBudget budget) {
        if (gray == null
                || exactTarget == null
                || positiveReceivers == null
                || (long) width * height != gray.length
                || exactTarget.length == 0)
            throw new SlurEvidenceBudget.Unknown("SLUR_SOURCE_UNKNOWN");
        var target = new TreeSet<Integer>();
        for (int p : exactTarget) {
            budget.use(1, 48);
            if (p < 0 || p >= gray.length || !target.add(p))
                throw new SlurEvidenceBudget.Unknown("SLUR_TARGET_MEMBERS_UNKNOWN");
        }
        var heads = new HashSet<Integer>();
        for (int[] r : positiveReceivers)
            for (int p : r) {
                budget.use(1, 48);
                if (p < 0 || p >= gray.length)
                    throw new SlurEvidenceBudget.Unknown("SLUR_RECEIVER_MEMBERS_UNKNOWN");
                heads.add(p);
            }
        for (int p : target)
            if (heads.contains(p))
                throw new SlurEvidenceBudget.Unknown("INDEPENDENT_HEAD_OVERLAP_KEEP");
        var staff = LiteralStaffCarrierFamily.observe(gray, width, height, nativeTop, gap, budget);
        // Complete raw traversal, rather than bbox re-collection. Every noncarrier
        // member reached from the target is retained, including beyond its bbox.
        budget.use(0, (gray.length + 7L) / 8 + 8192);
        var seen = new BitSet(gray.length);
        var pieces = new ArrayList<Set<Integer>>();
        for (int seed : target) {
            if ((gray[seed] & 255) >= 165 || staff.observed(seed) || seen.get(seed)) continue;
            var part = new TreeSet<Integer>();
            var q = new ArrayDeque<Integer>();
            seen.set(seed);
            q.add(seed);
            while (!q.isEmpty()) {
                int p = q.remove();
                part.add(p);
                budget.use(9, 64);
                int x = p % width, y = p / width;
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1)
                    throw new SlurEvidenceBudget.Unknown("RAW_COMPONENT_PAGE_CLIPPED");
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int z = p + dy * width + dx;
                        if (!seen.get(z)
                                && (gray[z] & 255) < 165
                                && !staff.observed(z)
                                && !heads.contains(z)) {
                            seen.set(z);
                            q.add(z);
                        }
                    }
            }
            pieces.add(part);
        }
        // This producer has a two-branch domain. Other shapes/branches stay KEEP;
        // an incomplete grammar is never treated as negative family closure.
        if (pieces.size() != 2) throw new SlurEvidenceBudget.Unknown("OTHER_RAW_BRANCH_KEEP");
        Set<Integer> a = pieces.get(0), b = pieces.get(1);
        boolean ca = touches(a, heads, width, budget), cb = touches(b, heads, width, budget);
        if (ca == cb) throw new SlurEvidenceBudget.Unknown("RECEIVER_BRANCH_UNKNOWN");
        if (!ca) {
            var swap = a;
            a = b;
            b = swap;
        }
        var producer = new CompleteSlurExplanation(gray, width, height, staff, a, b, heads, budget);
        producer.observePaths(gap);
        if (producer.paths.isEmpty())
            throw new SlurEvidenceBudget.Unknown("NO_LITERAL_TRANSVERSE_PATH");
        var contact = new TreeSet<Integer>();
        for (int p : a) if (touches(Set.of(p), heads, width, budget)) contact.add(p);
        var first = certificate(a, width, gap);
        if (!first.complete())
            throw new SlurEvidenceBudget.Unknown("COMPLETE_RAW_BRANCH_NOT_RIBBON");
        for (int[] path : producer.paths) {
            var trail = producer.upperPath(path[0], contact);
            if (trail == null) continue;
            var curve = new TreeSet<Integer>(b);
            for (int p : path) curve.add(p);
            curve.addAll(trail);
            var ports = new ArrayList<Portal>();
            var usedPaths = new ArrayList<int[]>();
            usedPaths.add(path);
            boolean failed = false;
            for (int p : target) {
                if (a.contains(p) || staff.observed(p)) continue;
                var add = producer.addition(p, curve);
                if (add == null) {
                    failed = true;
                    break;
                }
                producer.apply(add, curve, ports, usedPaths);
            }
            for (int iteration = 0; !failed; iteration++) {
                if (iteration == 512) {
                    failed = true;
                    break;
                }
                budget.use(curve.size(), 0);
                Addition add = null;
                var columns = new TreeMap<Integer, int[]>();
                for (int p : curve) {
                    int x = p % width, y = p / width;
                    var range = columns.get(x);
                    if (range == null) columns.put(x, new int[] {y, y});
                    else {
                        range[0] = Math.min(range[0], y);
                        range[1] = Math.max(range[1], y);
                    }
                }
                outer:
                for (var e : columns.entrySet())
                    for (int y = e.getValue()[0]; y <= e.getValue()[1]; y++) {
                        int p = y * width + e.getKey();
                        if (!curve.contains(p)) {
                            add = producer.addition(p, curve);
                            if (add == null) failed = true;
                            break outer;
                        }
                    }
                if (add == null && !failed) {
                    var vertices = new TreeSet<Long>();
                    for (int p : curve)
                        for (int dy = 0; dy <= 1; dy++)
                            for (int dx = 0; dx <= 1; dx++)
                                vertices.add(((long) (p % width + dx) << 32) | (p / width + dy));
                    for (long vertex : vertices) {
                        int x = (int) (vertex >>> 32), y = (int) vertex;
                        int p = (y - 1) * width + x - 1, q = p + 1, r = p + width, s = r + 1;
                        int[] missing = null;
                        if (curve.contains(p)
                                && curve.contains(s)
                                && !curve.contains(q)
                                && !curve.contains(r)) missing = new int[] {q, r};
                        else if (curve.contains(q)
                                && curve.contains(r)
                                && !curve.contains(p)
                                && !curve.contains(s)) missing = new int[] {p, s};
                        if (missing == null) continue;
                        Addition one = producer.addition(missing[0], curve),
                                two = producer.addition(missing[1], curve);
                        if (one == null && two == null) {
                            failed = true;
                            break;
                        }
                        add =
                                one == null
                                        ? two
                                        : two == null
                                                ? one
                                                : producer.extraCount(one, curve)
                                                                <= producer.extraCount(two, curve)
                                                        ? one
                                                        : two;
                        break;
                    }
                }
                if (add == null) break;
                if (producer.extraCount(add, curve) == 0) {
                    failed = true;
                    break;
                }
                producer.apply(add, curve, ports, usedPaths);
            }
            if (failed) continue;
            // Validate the whole proposal independently. AA/paper/context is not
            // trimmed for the strip kernel. Both raw branches remain complete.
            for (int p : curve)
                if ((gray[p] & 255) == 255)
                    throw new SlurEvidenceBudget.Unknown("PAPER_CANNOT_BE_CURVE");
            boolean complete = true;
            for (int p : target)
                if (!a.contains(p) && !curve.contains(p) && !staff.observed(p)) complete = false;
            if (!complete || !curve.containsAll(b)) continue;
            var second = certificate(curve, width, gap);
            if (second.complete())
                return new Geometry(exactTarget, a, curve, staff, usedPaths, ports, first, second);
        }
        throw new SlurEvidenceBudget.Unknown("NO_COMPLETE_EXPLANATION_KEEP");
    }

    private static FiniteSlurRibbon.Certificate certificate(Set<Integer> p, int w, float gap) {
        var squares = new HashSet<Long>();
        for (int n : p) squares.add(FiniteSlurRibbon.pixel(n % w, n / w));
        // The unchanged kernel retains its own explicit cell/state/clip caps.
        return FiniteSlurRibbon.certify(squares, Math.max(2, Math.round(gap * .42f)));
    }

    private static int[] sorted(Collection<Integer> s) {
        return s.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    private static boolean touches(
            Set<Integer> a, Set<Integer> b, int w, SlurEvidenceBudget budget) {
        for (int p : a) {
            budget.use(9, 0);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) if (b.contains(p + dy * w + dx)) return true;
        }
        return false;
    }

    private void observePaths(float gap) {
        for (int p : upper)
            for (int dx = -1; dx <= 1; dx++) {
                int q = p + w + dx;
                if (staff.observed(q)) walk(new int[] {p}, q, (int) Math.ceil(gap * .35) + 2);
            }
        paths.sort(
                (a, b) -> {
                    int c = Integer.compare(b[0] % w, a[0] % w);
                    if (c != 0) return c;
                    return Arrays.compare(a, b);
                });
    }

    private void walk(int[] prefix, int p, int maximum) {
        budget.use(1, 0);
        if (paths.size() == 4096)
            throw new SlurEvidenceBudget.Unknown("TRANSVERSE_PATH_FAMILY_UNKNOWN");
        if (p < 0 || p >= gray.length || (gray[p] & 255) >= 165) return;
        int[] path = Arrays.copyOf(prefix, prefix.length + 1);
        path[prefix.length] = p;
        if (!staff.observed(p)) {
            if (lower.contains(p)) {
                budget.use(path.length, 4L * path.length + 32);
                paths.add(path);
            }
            return;
        }
        if (path.length > maximum)
            throw new SlurEvidenceBudget.Unknown("TRANSVERSE_PATH_DEPTH_UNKNOWN");
        for (int dx = -1; dx <= 1; dx++) walk(path, p + w + dx, maximum);
    }

    private Set<Integer> upperPath(int start, Set<Integer> goals) {
        var q = new ArrayDeque<Integer>();
        var parents = new HashMap<Integer, Integer>();
        q.add(start);
        parents.put(start, -1);
        while (!q.isEmpty()) {
            int p = q.remove();
            budget.use(4, 32);
            if (goals.contains(p)) {
                var result = new TreeSet<Integer>();
                while (p != -1) {
                    result.add(p);
                    p = parents.get(p);
                }
                return result;
            }
            int[] neighbors = {p - w, p - 1, p + 1, p + w};
            for (int z : neighbors)
                if (upper.contains(z) && !parents.containsKey(z)) {
                    parents.put(z, p);
                    q.add(z);
                }
        }
        return null;
    }

    private List<Portal> portals(int p) {
        var cached = portalCache.get(p);
        if (cached != null) return cached;
        var result = new ArrayList<Portal>();
        int x = p % w, y = p / w;
        for (int[] d : new int[][] {{1, 0}, {0, 1}, {1, 1}, {1, -1}}) {
            int ax = x, ay = y, bx = x, by = y, dx = d[0], dy = d[1];
            while (inside(ax - dx, ay - dy) && (gray[(ay - dy) * w + ax - dx] & 255) < 255) {
                ax -= dx;
                ay -= dy;
                budget.use(1, 0);
            }
            while (inside(bx + dx, by + dy) && (gray[(by + dy) * w + bx + dx] & 255) < 255) {
                bx += dx;
                by += dy;
                budget.use(1, 0);
            }
            if (!inside(ax - dx, ay - dy) || !inside(bx + dx, by + dy))
                throw new SlurEvidenceBudget.Unknown("LITERAL_PROFILE_PAGE_CLIPPED");
            var values = new ArrayList<Integer>();
            boolean foreign = false;
            int at = -1;
            for (int xx = ax, yy = ay; ; xx += dx, yy += dy) {
                int z = yy * w + xx;
                budget.use(1, 4);
                if (z == p) at = values.size();
                values.add(z);
                if ((gray[z] & 255) < 165
                        && !upper.contains(z)
                        && !lower.contains(z)
                        && !staff.observed(z)) foreign = true;
                if (xx == bx && yy == by) break;
            }
            if (foreign) continue;
            int[] interval = sortedInOrder(values);
            for (int sign : new int[] {-1, 1}) {
                var prefix = new TreeSet<Integer>();
                prefix.add(p);
                for (int j = at + sign; j >= 0 && j < values.size(); j += sign) {
                    int z = values.get(j);
                    if ((gray[z] & 255) >= 165) prefix.add(z);
                    else if (!staff.observed(z)) {
                        if (upper.contains(z) || lower.contains(z))
                            result.add(new Portal(p, interval, Set.copyOf(prefix), z));
                        break;
                    }
                }
            }
        }
        portalCache.put(p, List.copyOf(result));
        return result;
    }

    private static int[] sortedInOrder(List<Integer> a) {
        return a.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    private Addition addition(int p, Set<Integer> curve) {
        if (p < 0 || p >= gray.length) return null;
        if (curve.contains(p)) return new Addition(Set.of(), List.of(), List.of());
        if (upper.contains(p) || lower.contains(p))
            return new Addition(Set.of(p), List.of(), List.of());
        Addition best = null;
        if ((gray[p] & 255) >= 165 && (gray[p] & 255) < 255) {
            for (Portal portal : portals(p)) {
                var extra = new TreeSet<Integer>(portal.prefix);
                if (!curve.contains(portal.core)) {
                    if (!upper.contains(portal.core)) continue;
                    var goals = new HashSet<Integer>(curve);
                    goals.retainAll(upper);
                    var trail = upperPath(portal.core, goals);
                    if (trail == null) continue;
                    extra.addAll(trail);
                }
                var proposal = new Addition(extra, List.of(portal), List.of());
                if (best == null || extraCount(proposal, curve) < extraCount(best, curve))
                    best = proposal;
            }
        } else if (staff.observed(p)) {
            for (int[] path : paths) {
                boolean found = false;
                for (int z : path) if (z == p) found = true;
                if (!found) continue;
                var extra = new TreeSet<Integer>();
                for (int z : path) extra.add(z);
                if (!curve.contains(path[0])) {
                    var goals = new HashSet<Integer>(curve);
                    goals.retainAll(upper);
                    var trail = upperPath(path[0], goals);
                    if (trail == null) continue;
                    extra.addAll(trail);
                }
                var proposal = new Addition(extra, List.of(), List.of(path));
                if (best == null || extraCount(proposal, curve) < extraCount(best, curve))
                    best = proposal;
            }
        }
        return best;
    }

    private int extraCount(Addition add, Set<Integer> curve) {
        int n = 0;
        for (int p : add.members) {
            budget.use(1, 0);
            if (!curve.contains(p)) n++;
        }
        return n;
    }

    private void apply(
            Addition add, Set<Integer> curve, List<Portal> ports, List<int[]> usedPaths) {
        curve.addAll(add.members);
        ports.addAll(add.portals);
        usedPaths.addAll(add.paths);
    }
}

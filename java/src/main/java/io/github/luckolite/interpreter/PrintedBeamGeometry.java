// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Generic bounded physical grouping. Inputs are authenticated physical columns;
 * scalar maximumRails is only a search bound. No durations, song coordinates,
 * manual topology or model output order determine a positive connection. */
final class PrintedBeamGeometry {
    record End(int x, int endY) {}

    record Column(
            int id,
            int measure,
            int staff,
            int staffCount,
            int direction,
            int left,
            int right,
            int cap,
            double gap,
            int maximumRails,
            List<End> shaftColumns,
            int shaftStart,
            PrintedBeamShaftPixels.Targets targets) {
        Column(
                int id,
                int measure,
                int staff,
                int staffCount,
                int direction,
                int left,
                int right,
                int cap,
                double gap,
                int maximumRails,
                List<End> shaftColumns,
                int shaftStart) {
            this(
                    id,
                    measure,
                    staff,
                    staffCount,
                    direction,
                    left,
                    right,
                    cap,
                    gap,
                    maximumRails,
                    shaftColumns,
                    shaftStart,
                    null);
        }

        Column(
                int id,
                int measure,
                int staff,
                int staffCount,
                int direction,
                int left,
                int right,
                int cap,
                double gap,
                int maximumRails) {
            this(
                    id,
                    measure,
                    staff,
                    staffCount,
                    direction,
                    left,
                    right,
                    cap,
                    gap,
                    maximumRails,
                    flatEnds(left, right, cap),
                    direction < 0
                            ? cap + (int) Math.ceil(12 * gap)
                            : Math.max(0, cap - (int) Math.ceil(12 * gap)));
        }

        Column {
            if (id < 0
                    || measure < 0
                    || staff < 0
                    || staffCount < 1
                    || staff >= staffCount
                    || (direction != -1 && direction != 1)
                    || left < 0
                    || right < left
                    || right - left > 8
                    || cap < 0
                    || !Double.isFinite(gap)
                    || gap < 3
                    || gap > 64
                    || maximumRails < 0
                    || maximumRails > 8) throw new IllegalArgumentException();
            shaftColumns = List.copyOf(shaftColumns);
            if (shaftColumns.isEmpty() || shaftColumns.size() > 9 || shaftStart < 0)
                throw new IllegalArgumentException();
            var xs = new HashSet<Integer>();
            for (var e : shaftColumns)
                if (e == null
                        || e.x() < left
                        || e.x() > right
                        || e.endY() < 0
                        || !xs.add(e.x())
                        || (direction < 0 ? e.endY() > shaftStart : e.endY() < shaftStart))
                    throw new IllegalArgumentException();
        }

        boolean owns(int x, int y) {
            for (var e : shaftColumns)
                if (e.x() == x)
                    return direction < 0
                            ? y >= e.endY() && y <= shaftStart
                            : y <= e.endY() && y >= shaftStart;
            return false;
        }
    }

    private static List<End> flatEnds(int left, int right, int cap) {
        var out = new ArrayList<End>();
        if (right - left > 8 || right < left) return out;
        for (int x = left; x <= right; x++) out.add(new End(x, cap));
        return out;
    }

    interface CarrierPlane {
        boolean at(Column a, Column b, int x, int y);

        default boolean unknown(Column a, Column b, int x, int y) {
            return false;
        }
    }

    record Bridge(int leftId, int rightId, int mask) {}

    record Hook(int columnId, int level, int direction) {}

    record Group(List<Integer> columns, List<Integer> bridges, List<Hook> hooks) {
        Group {
            columns = List.copyOf(columns);
            bridges = List.copyOf(bridges);
            hooks = List.copyOf(hooks);
        }
    }

    record Result(
            List<Group> groups, List<Integer> unlinkedColumns, boolean budgetExceeded, long work) {
        Result {
            groups = List.copyOf(groups);
            unlinkedColumns = List.copyOf(unlinkedColumns);
        }
    }

    static final class Budget {
        private final long limit;
        private long used;
        private boolean exhausted;

        Budget(long limit) {
            if (limit < 1) throw new IllegalArgumentException();
            this.limit = limit;
        }

        void spend(long n) {
            if (exhausted) throw new Exhausted();
            if (n < 0 || n > limit - used) {
                exhausted = true;
                throw new Exhausted();
            }
            used += n;
        }

        long used() {
            return used;
        }
    }

    private static final class Exhausted extends RuntimeException {}

    private record Lane(int measure, int staff, int count, int direction) {}

    private record Edge(int a, int b) {}

    private record Run(int top, int bottom) {}

    private record Stub(
            int side, int first, int last, double offset, FiniteRawBeamStrip.Proof proof) {}

    private record Observation(int first, FiniteRawBeamStrip.Proof proof) {}

    private record CandidateKey(int edge, int candidate) {}

    private record StubKey(int edge, int candidate, int side) {}

    static Result detect(
            byte[] raw,
            int width,
            int height,
            List<Column> columns,
            CarrierPlane plane,
            Budget budget) {
        if (raw == null
                || columns == null
                || plane == null
                || budget == null
                || width < 1
                || height < 1
                || (long) width * height != raw.length
                || columns.size() > 4096) throw new IllegalArgumentException();
        var seen = new HashSet<Integer>();
        for (var c : columns)
            if (c == null || !seen.add(c.id()) || c.right() >= width || c.cap() >= height)
                throw new IllegalArgumentException();
        try {
            return new Detector(raw, width, height, columns, plane, budget).run();
        } catch (Exhausted exhausted) {
            return new Result(
                    List.of(), columns.stream().map(Column::id).toList(), true, budget.used());
        }
    }

    private static final class Detector {
        final byte[] raw;
        final int width, height;
        final List<Column> nodes;
        final CarrierPlane plane;
        final Budget budget;
        final ArrayList<Edge> edges = new ArrayList<>();
        final ArrayList<FiniteRawBeamStrip.Proof> primary = new ArrayList<>();
        final int[] parent;

        Detector(byte[] r, int w, int h, List<Column> n, CarrierPlane p, Budget b) {
            raw = r;
            width = w;
            height = h;
            nodes = List.copyOf(n);
            plane = p;
            budget = b;
            parent = new int[n.size()];
            for (int i = 0; i < n.size(); i++) parent[i] = i;
        }

        int root(int i) {
            while (parent[i] != i) {
                parent[i] = parent[parent[i]];
                i = parent[i];
            }
            return i;
        }

        FiniteRawBeamStrip.Carrier carrier(Edge e) {
            var a = nodes.get(e.a());
            var b = nodes.get(e.b());
            return new FiniteRawBeamStrip.Carrier() {
                public boolean at(int x, int y) {
                    budget.spend(1);
                    return plane.at(a, b, x, y);
                }

                public boolean unknown(int x, int y) {
                    budget.spend(1);
                    return plane.unknown(a, b, x, y);
                }
            };
        }

        double gap(Edge e) {
            return (nodes.get(e.a()).gap() + nodes.get(e.b()).gap()) * .5;
        }

        int first(Edge e) {
            return nodes.get(e.a()).right() + 2;
        }

        int last(Edge e) {
            return nodes.get(e.b()).left() - 2;
        }

        double cap(Edge e, int x) {
            var a = nodes.get(e.a());
            var b = nodes.get(e.b());
            return a.cap()
                    + (double) (b.cap() - a.cap()) * (x - a.right()) / (b.left() - a.right());
        }

        FiniteRawBeamStrip.Proof prove(Edge e, int lo, int hi, double offset, int min) {
            if (hi - lo + 1 < 2 || hi - lo + 1 > 512) return null;
            budget.spend((long) (hi - lo + 1) * ((int) Math.ceil(gap(e) * 3) + 3));
            var p =
                    FiniteRawBeamStrip.prove(
                            raw,
                            carrier(e),
                            width,
                            height,
                            lo,
                            hi,
                            cap(e, lo),
                            cap(e, hi),
                            gap(e),
                            nodes.get(e.a()).direction(),
                            offset,
                            min);
            if (p == null) return null;
            if (lo == first(e) && !contact(e, nodes.get(e.a()), lo, p, 0)) return null;
            if (hi == last(e) && !contact(e, nodes.get(e.b()), hi, p, p.columns().size() - 1))
                return null;
            return p;
        }

        /** Every admitted terminal top cell must have a literal non-carrier
         * 8-connected connector to the authenticated shaft, through the one
         * column excluded from the finite interior fit. No favorable slope,
         * carrier-only bridge, white fill, or distant foreground is a contact. */
        boolean contact(
                Edge e,
                Column shaft,
                int boundary,
                FiniteRawBeamStrip.Proof proof,
                int columnIndex) {
            var bounds = proof.columns().get(columnIndex);
            int lowTop = (bounds.low() + 512) / 1024, highTop = (bounds.high() + 512) / 1024;
            int left = Math.min(boundary, shaft.left()), right = Math.max(boundary, shaft.right());
            int spread = (int) Math.ceil(.8 * (right - left)) + 1;
            var c = carrier(e);
            for (int top = lowTop; top <= highTop; top++) {
                int low = Math.max(0, top - spread),
                        high = Math.min(height - 1, top + proof.thickness() - 1 + spread),
                        cw = right - left + 1,
                        ch = high - low + 1;
                boolean[] visited = new boolean[cw * ch];
                int[] queue = new int[cw * ch];
                int head = 0, tail = 0;
                for (int y = top; y < top + proof.thickness(); y++)
                    if (y >= low
                            && y <= high
                            && (raw[y * width + boundary] & 255) < 165
                            && ((!c.at(boundary, y) && !c.unknown(boundary, y))
                                    || certifiedTerminal(shaft, boundary, y))) {
                        int slot = (y - low) * cw + boundary - left;
                        visited[slot] = true;
                        queue[tail++] = slot;
                    }
                boolean reached = false;
                while (head < tail) {
                    int slot = queue[head++], x = left + slot % cw, y = low + slot / cw;
                    if (shaft.owns(x, y)) {
                        reached = true;
                        break;
                    }
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (dx == 0 && dy == 0
                                    || nx < left
                                    || nx > right
                                    || ny < low
                                    || ny > high) continue;
                            int next = (ny - low) * cw + nx - left;
                            if (visited[next]) continue;
                            visited[next] = true;
                            budget.spend(1);
                            if ((raw[ny * width + nx] & 255) < 165
                                    && ((!c.at(nx, ny) && !c.unknown(nx, ny))
                                            || certifiedTerminal(shaft, nx, ny)))
                                queue[tail++] = next;
                        }
                }
                if (!reached) return false;
            }
            return true;
        }

        // Only a rejected raw owned endpoint may use its original head-connected certificate.
        // No-certificate columns retain the exact old plane-query path.
        boolean certifiedTerminal(Column shaft, int x, int y) {
            return shaft.owns(x, y)
                    && shaft.targets() != null
                    && shaft.targets().has(raw, width, height, shaft.id(), x, y);
        }

        List<Run> runs(int x, int low, int high) {
            low = Math.max(0, low);
            high = Math.min(height - 1, high);
            var out = new ArrayList<Run>();
            int start = -1;
            for (int y = low; y <= high + 1; y++) {
                budget.spend(1);
                boolean ink = y <= high && (raw[y * width + x] & 255) < 165;
                if (ink) {
                    if (start < 0) start = y;
                } else if (start >= 0) {
                    if (start > low && y - 1 < high) out.add(new Run(start, y - 1));
                    start = -1;
                }
            }
            return out;
        }

        int evidence(Edge e, int x, int top, int bottom) {
            int n = 0;
            var c = carrier(e);
            for (int y = top; y <= bottom; y++) if (!c.at(x, y) && !c.unknown(x, y)) n++;
            return n;
        }

        boolean coreEligible(Edge e, int x, Run r, double base, int k) {
            int minimum = Math.max(2, (int) Math.ceil(gap(e) * .18)),
                    direction = nodes.get(e.a()).direction(),
                    shift = direction > 0 ? k - 1 : 0;
            for (int t = r.top(); t <= r.bottom() - k + 1; t++) {
                int lo =
                        Math.max(
                                1024 * t - 512,
                                (int) Math.ceil(1024 * (base - gap(e) * .3 - shift)));
                int hi =
                        Math.min(
                                1024 * t + 511,
                                (int) Math.floor(1024 * (base + gap(e) * .3 - shift)));
                if (lo <= hi && evidence(e, x, t, t + k - 1) >= minimum) return true;
            }
            return false;
        }

        boolean rawEligible(Edge e, Run r, double base, int k) {
            int shift = nodes.get(e.a()).direction() > 0 ? k - 1 : 0;
            int lo =
                    Math.max(
                            1024 * r.top() - 512,
                            (int) Math.ceil(1024 * (base - gap(e) * .3 - shift)));
            int hi =
                    Math.min(
                            1024 * (r.bottom() - k + 1) + 511,
                            (int) Math.floor(1024 * (base + gap(e) * .3 - shift)));
            return lo <= hi;
        }

        List<Stub> stubs(int edgeIndex, double offset) {
            Edge e = edges.get(edgeIndex);
            int first = first(e), last = last(e), n = last - first + 1;
            double gap = gap(e);
            int direction = nodes.get(e.a()).direction();
            if (n < 5 || n > 512) return List.of();
            int minimum = Math.max(2, (int) Math.ceil(gap * .18)),
                    thickness = primary.get(edgeIndex).thickness(),
                    kmin = Math.max(minimum, thickness - 2);
            int rootLength = Math.max(3, (int) Math.ceil(gap * .3)), initial = 0;
            for (int side : new int[] {1, -1}) {
                int lo = side > 0 ? first : last - rootLength + 1,
                        hi = side > 0 ? first + rootLength - 1 : last;
                var p = prove(e, lo, hi, offset, kmin);
                if (p != null && p.thickness() >= thickness - 1)
                    initial = Math.max(initial, Math.min(p.thickness(), thickness + 1));
            }
            if (initial == 0) return List.of();
            kmin = Math.max(kmin, initial - 1);
            boolean[] present = new boolean[n], rawPresent = new boolean[n];
            for (int x = first; x <= last; x++) {
                double base = cap(e, x) - direction * offset;
                for (var r :
                        runs(
                                x,
                                (int) Math.floor(base - gap * 1.5),
                                (int) Math.ceil(base + gap * 1.5)))
                    if (rawEligible(e, r, base, kmin)) {
                        rawPresent[x - first] = true;
                        if (coreEligible(e, x, r, base, kmin)) present[x - first] = true;
                    }
            }
            int prefix = 0, suffix = 0;
            while (prefix < n && present[prefix]) prefix++;
            while (suffix < n && present[n - suffix - 1]) suffix++;
            if (n - prefix - suffix < 2) return List.of();
            for (int i = prefix; i < n - suffix; i++) if (rawPresent[i]) return List.of();
            var result = new ArrayList<Stub>();
            for (int side : new int[] {1, -1}) {
                int length = side > 0 ? prefix : suffix;
                if (length < rootLength) continue;
                int lo = side > 0 ? first : last - suffix + 1,
                        hi = side > 0 ? first + prefix - 1 : last;
                var p = prove(e, lo, hi, offset, kmin);
                if (p != null) result.add(new Stub(side, lo, hi, offset, p));
            }
            if (prefix > 0 && result.stream().noneMatch(s -> s.side() > 0)
                    || suffix > 0 && result.stream().noneMatch(s -> s.side() < 0)) return List.of();
            return result;
        }

        Result run() {
            var lanes = new LinkedHashMap<Lane, ArrayList<Integer>>();
            for (int i = 0; i < nodes.size(); i++) {
                var c = nodes.get(i);
                lanes.computeIfAbsent(
                                new Lane(c.measure(), c.staff(), c.staffCount(), c.direction()),
                                k -> new ArrayList<>())
                        .add(i);
            }
            for (var lane : lanes.values()) {
                lane.sort(
                        Comparator.comparingInt((Integer i) -> nodes.get(i).left())
                                .thenComparingInt(i -> nodes.get(i).cap())
                                .thenComparingInt(i -> nodes.get(i).id()));
                for (int i = 0; i + 1 < lane.size(); i++) {
                    int a = lane.get(i), b = lane.get(i + 1);
                    if (nodes.get(a).maximumRails() < 1
                            || nodes.get(b).maximumRails() < 1
                            || nodes.get(b).left() <= nodes.get(a).right()) continue;
                    var e = new Edge(a, b);
                    edges.add(e);
                    var p = prove(e, first(e), last(e), 0, 0);
                    primary.add(p);
                    if (p != null) parent[root(b)] = root(a);
                }
            }
            var groups = new LinkedHashMap<Integer, ArrayList<Integer>>();
            for (int i = 0; i < edges.size(); i++)
                if (primary.get(i) != null)
                    groups.computeIfAbsent(root(edges.get(i).a()), k -> new ArrayList<>()).add(i);
            var output = new ArrayList<Group>();
            var linked = new HashSet<Integer>();
            for (var indices : groups.values()) output.add(group(indices, linked));
            var detached = new ArrayList<Integer>();
            for (var c : nodes) if (!linked.contains(c.id())) detached.add(c.id());
            return new Result(output, detached, false, budget.used());
        }

        Group group(List<Integer> indices, Set<Integer> linked) {
            var members =
                    new TreeSet<Integer>(
                            Comparator.comparingInt((Integer i) -> nodes.get(i).left())
                                    .thenComparingInt(i -> nodes.get(i).id()));
            int maximum = 1;
            for (int i : indices) {
                var e = edges.get(i);
                members.add(e.a());
                members.add(e.b());
            }
            for (int i : members) maximum = Math.max(maximum, nodes.get(i).maximumRails());
            var values = new ArrayList<Double>();
            for (int index : indices) {
                var e = edges.get(index);
                double gap = gap(e);
                int direction = nodes.get(e.a()).direction(),
                        minimum = Math.max(2, (int) Math.ceil(gap * .18));
                for (int x = first(e); x <= last(e); x++) {
                    double base = cap(e, x);
                    for (var r :
                            runs(
                                    x,
                                    (int) Math.floor(base - gap * (maximum + 1)),
                                    (int) Math.ceil(base + gap * (maximum + 1)))) {
                        int size = r.bottom() - r.top() + 1;
                        if (size < minimum
                                || size > gap
                                || evidence(e, x, r.top(), r.bottom()) < minimum) continue;
                        double offset =
                                ((direction < 0 ? r.top() : r.bottom()) - base)
                                        * (-direction)
                                        / gap;
                        if (offset > .4 && offset < (maximum - 1) * 1.25) values.add(offset);
                    }
                }
            }
            Collections.sort(values);
            var clusters = new ArrayList<ArrayList<Double>>();
            for (double v : values) {
                if (clusters.isEmpty()
                        || v
                                        - clusters.get(clusters.size() - 1)
                                                .get(clusters.get(clusters.size() - 1).size() - 1)
                                > .15) clusters.add(new ArrayList<>());
                clusters.get(clusters.size() - 1).add(v);
            }
            var offsets = new ArrayList<Double>();
            for (var c : clusters) offsets.add(median(c));
            var full = new HashMap<CandidateKey, FiniteRawBeamStrip.Proof>();
            var partial = new HashMap<StubKey, Stub>();
            for (int index : indices)
                for (int j = 0; j < offsets.size(); j++) {
                    var e = edges.get(index);
                    double off = offsets.get(j) * gap(e);
                    var p =
                            prove(
                                    e,
                                    first(e),
                                    last(e),
                                    off,
                                    Math.max(0, primary.get(index).thickness() - 2));
                    if (p != null) full.put(new CandidateKey(index, j), p);
                    for (var s : stubs(index, off)) {
                        int node = s.side() > 0 ? e.a() : e.b();
                        if (nodes.get(node).maximumRails() >= 2)
                            partial.put(new StubKey(index, j, s.side()), s);
                    }
                }
            int[] aliases = new int[clusters.size()];
            for (int i = 0; i < aliases.length; i++) aliases[i] = i;
            for (int index : indices)
                for (int j = 0; j < clusters.size(); j++)
                    for (int k = j + 1; k < clusters.size(); k++) {
                        var left = observations(index, j, full, partial);
                        var right = observations(index, k, full, partial);
                        for (var a : left)
                            for (var b : right)
                                if (sameComponent(a, b))
                                    aliases[find(aliases, k)] = find(aliases, j);
                    }
            var merged = new LinkedHashMap<Integer, ArrayList<Double>>();
            for (int j = 0; j < clusters.size(); j++) {
                boolean supported = false;
                for (int index : indices)
                    if (full.containsKey(new CandidateKey(index, j))
                            || partial.containsKey(new StubKey(index, j, 1))
                            || partial.containsKey(new StubKey(index, j, -1))) supported = true;
                if (supported)
                    merged.computeIfAbsent(find(aliases, j), k -> new ArrayList<>())
                            .addAll(clusters.get(j));
            }
            var physicalOffsets = new ArrayList<Double>();
            for (var c : merged.values()) {
                Collections.sort(c);
                physicalOffsets.add(median(c));
            }
            Collections.sort(physicalOffsets);
            if (physicalOffsets.size() > maximum - 1) physicalOffsets.clear();
            var masks = new HashMap<Integer, Integer>();
            var hooks = new ArrayList<Hook>();
            for (int index : indices) {
                var e = edges.get(index);
                int mask = 1;
                for (int j = 0; j < physicalOffsets.size(); j++)
                    if (prove(
                                    e,
                                    first(e),
                                    last(e),
                                    physicalOffsets.get(j) * gap(e),
                                    Math.max(0, primary.get(index).thickness() - 2))
                            != null) mask |= 1 << (j + 1);
                masks.put(index, mask);
                for (int j = 0; j < physicalOffsets.size(); j++) {
                    int level = j + 2;
                    if ((mask & (1 << (level - 1))) != 0) continue;
                    for (var s : stubs(index, physicalOffsets.get(j) * gap(e))) {
                        int node = s.side() > 0 ? e.a() : e.b();
                        if (level <= nodes.get(node).maximumRails())
                            hooks.add(new Hook(nodes.get(node).id(), level, s.side()));
                    }
                }
            }
            var ids = new ArrayList<Integer>();
            for (int i : members) {
                ids.add(nodes.get(i).id());
                linked.add(nodes.get(i).id());
            }
            var bridges = new ArrayList<Integer>();
            var ordered = new ArrayList<>(members);
            for (int i = 0; i + 1 < ordered.size(); i++) {
                int mask = 0;
                for (int index : indices) {
                    var e = edges.get(index);
                    if (e.a() == ordered.get(i) && e.b() == ordered.get(i + 1))
                        mask = masks.get(index);
                }
                bridges.add(mask);
            }
            // A hook cannot own a level with any incident full bridge in its group.
            hooks.removeIf(
                    h -> {
                        int i = ids.indexOf(h.columnId()), bit = 1 << (h.level() - 1);
                        return i > 0 && (bridges.get(i - 1) & bit) != 0
                                || i < bridges.size() && (bridges.get(i) & bit) != 0;
                    });
            return new Group(ids, bridges, hooks);
        }

        List<Observation> observations(
                int index,
                int candidate,
                Map<CandidateKey, FiniteRawBeamStrip.Proof> full,
                Map<StubKey, Stub> partial) {
            var out = new ArrayList<Observation>();
            var p = full.get(new CandidateKey(index, candidate));
            if (p != null) out.add(new Observation(first(edges.get(index)), p));
            for (int side : new int[] {-1, 1}) {
                var s = partial.get(new StubKey(index, candidate, side));
                if (s != null) out.add(new Observation(s.first(), s.proof()));
            }
            return out;
        }

        boolean sameComponent(Observation a, Observation b) {
            int ae = a.first() + a.proof().rasterTops().size() - 1,
                    be = b.first() + b.proof().rasterTops().size() - 1;
            if (!(a.first() <= b.first() && be <= ae || b.first() <= a.first() && ae <= be))
                return false;
            int lo = Math.max(a.first(), b.first()), hi = Math.min(ae, be);
            for (int x = lo; x <= hi; x++) {
                var ap = a.proof().rasterTops().get(x - a.first());
                var bp = b.proof().rasterTops().get(x - b.first());
                int top = Math.min(ap.low(), bp.low()),
                        bottom =
                                Math.max(
                                        ap.high() + a.proof().thickness() - 1,
                                        bp.high() + b.proof().thickness() - 1);
                for (int y = top; y <= bottom; y++) {
                    budget.spend(1);
                    if ((raw[y * width + x] & 255) >= 165) return false;
                }
            }
            return true;
        }
    }

    private static int find(int[] parents, int i) {
        while (parents[i] != i) {
            parents[i] = parents[parents[i]];
            i = parents[i];
        }
        return i;
    }

    private static double median(List<Double> sorted) {
        int n = sorted.size();
        return (n & 1) == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) * .5;
    }
}

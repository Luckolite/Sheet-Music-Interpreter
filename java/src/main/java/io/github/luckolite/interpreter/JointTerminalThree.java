// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Conditional original-data role certificate; no raster rewrite or musical admission. */
final class JointTerminalThree {
    record Box(int l, int t, int r, int b) {}

    record Port(int fromSegment, int toSegment, int fromPixel, int toPixel) {}

    record Face(int edgeFrom, int edgeTo, int side, int paperPixel) {}

    record RunEvidence(int id, int row, int left, int right, boolean arc) {}

    record Witness(
            int waistRow,
            int lowerRow,
            List<Integer> completeNonC,
            List<Integer> returnSegments,
            List<Port> ports,
            List<Face> paperFaces,
            List<Integer> explainedExcess,
            List<Integer> retainedNonCExcess,
            List<Integer> sharedSites,
            List<RunEvidence> completeRunGraph,
            FilledTurnRibbon.Proof upperTurn) {}

    record Outcome(
            boolean supported,
            String reason,
            Witness witness,
            Box glyphExtent,
            int classifiedDigit) {
        Outcome(boolean supported, String reason, Witness witness, Box glyphExtent) {
            this(supported, reason, witness, glyphExtent, supported ? 3 : -1);
        }

        Outcome(boolean supported, String reason, Witness witness) {
            this(supported, reason, witness, null);
        }
    }

    private record Segment(int id, int y, int l, int r, boolean arc) {}

    private record Walk(int[] path, BitSet seen, int minX) {}

    private record PhaseKey(int low, int high, int boundary, boolean upperCap) {}

    private final byte[] raw;
    private final int width, height;
    private final Box box, window;
    private final BitSet curve;
    private final float gap;
    private final ObservedFringeContinuation.Charge charge;
    private final Runnable state;
    private final ArrayList<Segment> nodes = new ArrayList<>();
    private ArrayList<Integer>[] edges;
    private int[] min, max, count;
    private int paper, ink, indent, required;
    private boolean upper, lower;
    private final HashMap<PhaseKey, Boolean> phaseCache = new HashMap<>();
    // Fixed source/graph/C for this helper invocation; no bbox-only cross-role cache.
    private boolean turnAttempted, turnAccepted, turnGlobalAccepted;
    private FilledTurnRibbon.Proof turnProof;
    private JoinedDigitTemplates.Family fontThree;
    private int opposingDigit = -1;

    private JointTerminalThree(
            byte[] raw,
            int width,
            int height,
            Box box,
            Box window,
            BitSet curve,
            float gap,
            ObservedFringeContinuation.Charge charge,
            Runnable state) {
        this.raw = raw;
        this.width = width;
        this.height = height;
        this.box = box;
        this.window = window;
        this.curve = curve;
        this.gap = gap;
        this.charge = charge;
        this.state = state;
    }

    static Outcome inspect(
            byte[] raw,
            int width,
            int height,
            int l,
            int t,
            int r,
            int b,
            int left,
            int top,
            int right,
            int bottom,
            BitSet curve,
            float gap,
            ObservedFringeContinuation.Charge charge,
            Runnable state) {
        if (raw == null
                || width < 1
                || height < 1
                || raw.length != (long) width * height
                || curve == null
                || curve.length() > raw.length
                || charge == null
                || state == null
                || !Float.isFinite(gap)
                || gap < 5
                || gap > 100
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || left > right
                || top > bottom
                || l <= left
                || t <= top
                || r >= right
                || b >= bottom
                || l > r
                || t > b) return new Outcome(false, "invalid-or-clipped", null);
        if ((long) (r - l + 1) * (b - t + 1) > 16384) return new Outcome(false, "window-cap", null);
        return new JointTerminalThree(
                        raw,
                        width,
                        height,
                        new Box(l, t, r, b),
                        new Box(left, top, right, bottom),
                        curve,
                        gap,
                        charge,
                        state)
                .run();
    }

    private void pay(int n) {
        for (int i = 0; i < n; i++) charge.tick();
    }

    private int value(int x, int y) {
        charge.tick();
        return raw[y * width + x] & 255;
    }

    private int pixel(int x, int y) {
        return y * width + x;
    }

    private Outcome no(String why) {
        JoinedTupletObservation.event("JOINT_TERMINAL_NO", why.hashCode());
        return new Outcome(false, why, null);
    }

    private Outcome run() {
        int w = box.r - box.l + 1, h = box.b - box.t + 1;
        indent = Math.max(1, (int) Math.floor(w * .08f));
        required = Math.max(2, h / 12);
        if (h < gap * .7f || h > gap * 2.3f || w < h * .30f || w > h * .95f) return no("geometry");
        pay(h * 5);
        min = new int[h];
        max = new int[h];
        count = new int[h];
        Arrays.fill(min, w);
        Arrays.fill(max, -1);
        int total = 0;
        pay(512);
        int[] inside = new int[256], outside = new int[256];
        int ni = 0, np = 0;
        int margin = Math.max(3, Math.round(gap * .45f));
        for (int y = Math.max(0, box.t - margin); y <= Math.min(height - 1, box.b + margin); y++)
            for (int x = Math.max(0, box.l - margin);
                    x <= Math.min(width - 1, box.r + margin);
                    x++) {
                int v = value(x, y);
                if (x >= box.l && x <= box.r && y >= box.t && y <= box.b) {
                    inside[v]++;
                    ni++;
                    if (v <= 165) {
                        int row = y - box.t;
                        min[row] = Math.min(min[row], x - box.l);
                        max[row] = Math.max(max[row], x - box.l);
                        count[row]++;
                        total++;
                    }
                } else {
                    outside[v]++;
                    np++;
                }
            }
        paper = percentile(outside, np, .85f);
        ink = percentile(inside, ni, .10f);
        if (ni < 6 || np < 6 || paper - ink < 32 || total < w * h * .15f || total > w * h * .70f)
            return no("contrast-fill");
        int uo = 0, lo = 0, up = 0, lp = 0;
        for (int y = 0; y < h; y++) {
            charge.tick();
            if (max[y] < 0) continue;
            float f = y / (float) h, n = (y + 1) / (float) h;
            if (n > .15f && f <= .36f + 1f / h) {
                if (min[y] >= w * .40f) uo++;
                if (pocket(y, false)) up++;
            }
            if (n > .60f && f <= .82f) {
                if (max[y] >= w * .5f && min[y] >= Math.min(w * .4f, max[y] * .5f)) lo++;
                if (pocket(y, false)) lp++;
            }
        }
        upper = uo >= required || uo >= 1 && up >= required;
        lower = lo >= required || lo >= 1 && lp >= required;
        JoinedTupletObservation.event("JOINT_OPEN", uo, lo, up, lp, paper, ink);
        if (!upper || !lower) return no("open-pockets");
        build();
        if (nodes.isEmpty()) return no("empty");
        pay(nodes.size());
        boolean[] full = new boolean[nodes.size()];
        Arrays.fill(full, true);
        if (!branchSafe(box.t, box.b, -1, true, full)) return no("foreign-global-branch");
        turnGlobalAccepted = turnAccepted;
        // The countermodel uses the complete same graph and is not disabled by a3 witness.
        if (two()) {
            String why = opposingDigit < 0 ? "unresolved-two-fullfoot" : "observed-opposing-digit";
            JoinedTupletObservation.event("JOINT_TERMINAL_RIVAL", opposingDigit);
            return new Outcome(false, why, null, null, opposingDigit);
        }
        if (fontThree != null) {
            // The retained complete non-C graph has passed the same foreign-branch gate.
            // Classification now comes from the complete bounded public-font rival family,
            // rather than demanding a second geometric lower-return proof of the same digit.
            // The caller still checks Four, both bracket arms, text, all weak continuations,
            // exact head ownership and competing glyph/curve hypotheses.
            return new Outcome(
                    true,
                    "observed-font-three-with-complete-graph",
                    null,
                    new Box(
                            fontThree.left(),
                            fontThree.top(),
                            fontThree.right(),
                            fontThree.bottom()));
        }
        for (int waist = box.t; waist <= box.b; waist++) {
            charge.tick();
            float wf = (waist - box.t) / (float) h;
            if (wf < .37f || wf > .55f) continue;
            for (int low = waist + 1; low <= box.b; low++) {
                charge.tick();
                float lf = (low - box.t) / (float) h;
                if (lf < .60f || lf > .75f) continue;
                state.run();
                if (!phase(box.t, waist, waist, true)
                        || !phase(waist, low, waist, false)
                        || !phase(low, box.b, low, false)) continue;
                int upperRight = -1, lowerRight = -1, waistRight = w;
                for (int y = 0; y < h; y++) {
                    charge.tick();
                    if (max[y] < 0) continue;
                    if (y / (float) h >= .15f && y / (float) h <= .36f + 1f / h)
                        upperRight = Math.max(upperRight, max[y]);
                    if (y + box.t == waist) waistRight = Math.min(waistRight, max[y]);
                    if (y + box.t >= low && y / (float) h <= .75f)
                        lowerRight = Math.max(lowerRight, max[y]);
                }
                if (upperRight - waistRight < indent || lowerRight - waistRight < indent) continue;
                for (var start : nodes) {
                    charge.tick();
                    if (start.arc
                            || start.y < low
                            || start.y - box.t > h * .75f
                            || start.r - box.l < lowerRight) continue;
                    var result = returns(start, waist, low);
                    if (result != null) return new Outcome(true, "observed-joint-terminal", result);
                }
            }
        }
        return no("no-covered-return");
    }

    private int percentile(int[] a, int n, float fraction) {
        int sum = 0;
        for (int i = 0; i < 256; i++) {
            charge.tick();
            sum += a[i];
            if (sum >= n * fraction) return i;
        }
        return 255;
    }

    private boolean pocket(int y, boolean unused) {
        int w = box.r - box.l + 1, run = 0;
        for (int x = Math.round(w * .25f); x < w; x++) {
            if (value(box.l + x, box.t + y) > 165) run++;
            else {
                if (run >= Math.max(2, Math.round(w * .22f)) && x + .5f >= w * .55f) return true;
                run = 0;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void build() {
        for (int y = box.t; y <= box.b; y++)
            for (int x = box.l; x <= box.r; ) {
                if (value(x, y) > 165) {
                    x++;
                    continue;
                }
                int l = x;
                charge.tick();
                boolean c = curve.get(pixel(x, y));
                x++;
                while (x <= box.r && value(x, y) <= 165) {
                    charge.tick();
                    if (curve.get(pixel(x, y)) != c) break;
                    x++;
                }
                state.run();
                pay(nodes.size() + 5);
                nodes.add(new Segment(nodes.size(), y, l, x - 1, c));
            }
        pay(nodes.size());
        edges = (ArrayList<Integer>[]) new ArrayList<?>[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            charge.tick();
            edges[i] = new ArrayList<>();
        }
        for (int i = 0; i < nodes.size(); i++)
            for (int j = i + 1; j < nodes.size(); j++) {
                charge.tick();
                var a = nodes.get(i);
                var b = nodes.get(j);
                if (b.y > a.y + 1) break;
                if ((a.y == b.y && a.r + 1 == b.l)
                        || (b.y == a.y + 1 && a.l <= b.r + 1 && b.l <= a.r + 1)) {
                    pay(edges[i].size() + edges[j].size() + 4);
                    edges[i].add(j);
                    edges[j].add(i);
                }
            }
    }

    private boolean contact(int id) {
        var n = nodes.get(id);
        if (n.arc) return true;
        for (int j : edges[id]) {
            charge.tick();
            if (nodes.get(j).arc) return true;
        }
        return false;
    }

    /** Full retained phase coverage, separate from the return witness. No monotone all-ink walk. */
    private boolean phase(int lo, int hi, int boundary, boolean allowUpperCap) {
        // Full source/graph/C are immutable for this proof; no path-history-dependent result
        // cached.
        pay(phaseCache.size() + 5);
        var key = new PhaseKey(lo, hi, boundary, allowUpperCap);
        Boolean prior = phaseCache.get(key);
        if (prior != null) return prior;
        state.run();
        boolean result = phaseUncached(lo, hi, boundary, allowUpperCap);
        pay(phaseCache.size() + 2);
        phaseCache.put(key, result);
        return result;
    }

    private boolean phaseUncached(int lo, int hi, int boundary, boolean allowUpperCap) {
        int n = nodes.size();
        pay(n * 5);
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        int seed = -1, mandatory = 0;
        for (var a : nodes) {
            charge.tick();
            if (a.y >= lo && a.y <= hi && !a.arc) {
                mandatory++;
                seed = a.id;
            }
        }
        if (mandatory == 0) return false;
        int take = 0, size = 1, got = 0;
        queue[0] = seed;
        seen[seed] = true;
        state.run();
        while (take < size) {
            charge.tick();
            int id = queue[take++];
            var a = nodes.get(id);
            if (!a.arc) got++;
            for (int j : edges[id]) {
                charge.tick();
                var b = nodes.get(j);
                if (b.y < lo || b.y > hi || seen[j]) continue;
                state.run();
                seen[j] = true;
                queue[size++] = j;
            }
        }
        if (got != mandatory) return false;
        return branchSafe(lo, hi, boundary, allowUpperCap, seen);
    }

    /** Full biconnected leaf-block and required-port path accounting on retained support. */
    private boolean branchSafe(int lo, int hi, int boundary, boolean upperPhase, boolean[] active) {
        int n = nodes.size();
        state.run();
        pay(n * 4);
        int[][] graph = new int[n][];
        boolean[] mandatory = new boolean[n], ports = new boolean[n], exposed = new boolean[n];
        for (var a : nodes) {
            charge.tick();
            pay(edges[a.id].size() * 2);
            graph[a.id] = new int[edges[a.id].size()];
            for (int i = 0; i < graph[a.id].length; i++) {
                charge.tick();
                graph[a.id][i] = edges[a.id].get(i);
            }
            mandatory[a.id] = !a.arc;
            ports[a.id] =
                    contact(a.id) || boundary >= 0 && (a.y == boundary || a.y == lo || a.y == hi);
            if (!active[a.id] || a.arc || !upperPhase || a.y - box.t > (box.b - box.t + 1) * .36f)
                continue;
            int degree = 0;
            for (int j : graph[a.id]) {
                charge.tick();
                if (active[j]) degree++;
            }
            // An undeclared cycle is not an exposed cap merely because some perimeter has paper.
            if (degree <= 1) {
                for (int side = 0; side < 2; side++) {
                    pay(2);
                    int x = side == 0 ? a.l - 1 : a.r + 1;
                    if (x >= window.l
                            && x <= window.r
                            && value(x, a.y) >= paper
                            && paperExit(x, a.y)) exposed[a.id] = true;
                }
                // The entire terminal cross-section must brighten outwards to measured paper.
                // This observes an AA cap, not ownership of its fringe. The full original graph,
                // same-graph Two veto, lower return and whole-glyph fringe checks remain required.
                if (!exposed[a.id] && a.y == box.t)
                    exposed[a.id] =
                            completeTopCap(
                                    raw,
                                    width,
                                    height,
                                    curve,
                                    a.l,
                                    a.r,
                                    a.y,
                                    window.t,
                                    paper,
                                    Math.max(2, Math.round(gap * .30f)),
                                    charge);
            }
        }
        if (blockPortsSafe(
                graph, active, mandatory, ports, exposed, upperPhase ? 1 : 0, charge, state))
            return true;
        return upperPhase && turnRoleSafe(graph, active, mandatory, ports, exposed, upperPhase);
    }

    /** Every original core column in the top cap needs its own monotone outward AA-to-paper ray. */
    static boolean completeTopCap(
            byte[] raw,
            int width,
            int height,
            BitSet curve,
            int left,
            int right,
            int row,
            int top,
            int paper,
            int distance,
            ObservedFringeContinuation.Charge work) {
        if (raw == null
                || width < 1
                || height < 1
                || raw.length != (long) width * height
                || curve == null
                || curve.length() > raw.length
                || work == null
                || left < 0
                || right >= width
                || left > right
                || row < 1
                || row >= height
                || top < 0
                || top >= row
                || paper <= 165
                || paper > 255
                || distance < 1
                || distance > 100) return false;
        for (int x = left; x <= right; x++) {
            work.tick();
            int previous = raw[row * width + x] & 255;
            if (previous > 165 || curve.get(row * width + x)) return false;
            boolean escaped = false;
            for (int dy = 1; dy <= distance; dy++) {
                work.tick();
                int y = row - dy;
                if (y < top) return false;
                int at = y * width + x, value = raw[at] & 255;
                if (curve.get(at) || value <= 165 || value < previous) return false;
                if (value >= paper) {
                    escaped = true;
                    break;
                }
                previous = value;
            }
            if (!escaped) return false;
        }
        return true;
    }

    private boolean turnRoleSafe(
            int[][] graph,
            boolean[] active,
            boolean[] mandatory,
            boolean[] ports,
            boolean[] exposed,
            boolean upperPhase) {
        if (!turnAttempted) {
            state.run();
            pay(nodes.size() * 5 + 12);
            int[][] rawRuns = new int[nodes.size()][];
            for (var a : nodes) {
                charge.tick();
                rawRuns[a.id] = new int[] {a.y, a.l, a.r, a.arc ? 1 : 0};
            }
            turnProof =
                    FilledTurnRibbon.inspect(
                            raw, width, height, box.l, box.t, box.r, box.b, rawRuns, charge, state);
            turnAttempted = true;
        }
        var proof = turnProof;
        if (proof == null) return false;
        int n = nodes.size(), v = n;
        state.run();
        pay((n + 63) / 64 + 12);
        BitSet covered = new BitSet(n);
        for (int id : proof.coveredRuns()) {
            charge.tick();
            if (id < 0 || id >= n || !active[id] || !mandatory[id]) return false;
            covered.set(id);
        }
        if (!active[proof.leftArm()] || !active[proof.rightArm()]) return false;
        boolean foldedPort = false;
        for (int id = covered.nextSetBit(0); id >= 0; id = covered.nextSetBit(id + 1)) {
            charge.tick();
            foldedPort |= ports[id];
            for (int j : graph[id]) {
                charge.tick();
                if (!covered.get(j) && j != proof.leftArm() && j != proof.rightArm()) return false;
            }
        }
        // Every original non-C run is still mandatory: covered runs map to the complete role,
        // every other original run maps identically. No source node/pixel is discarded.
        state.run();
        pay((n + 1) * 5 + 16);
        int[][] role = new int[n + 1][];
        boolean[] on = new boolean[n + 1],
                need = new boolean[n + 1],
                port = new boolean[n + 1],
                visible = new boolean[n + 1];
        for (int i = 0; i < n; i++) {
            charge.tick();
            on[i] = active[i] && !covered.get(i);
            need[i] = mandatory[i] && !covered.get(i);
            port[i] = ports[i];
            visible[i] = exposed[i];
            pay(graph[i].length + 4);
            int[] row = new int[graph[i].length];
            int size = 0;
            if (!covered.get(i))
                for (int j : graph[i]) {
                    charge.tick();
                    int k = covered.get(j) ? v : j;
                    boolean duplicate = false;
                    for (int z = 0; z < size; z++) {
                        charge.tick();
                        if (row[z] == k) duplicate = true;
                    }
                    if (!duplicate) row[size++] = k;
                }
            pay(size);
            role[i] = Arrays.copyOf(row, size);
        }
        pay(2);
        role[v] = new int[] {proof.leftArm(), proof.rightArm()};
        on[v] = true;
        need[v] = true;
        port[v] = foldedPort;
        boolean result =
                blockPortsSafe(role, on, need, port, visible, upperPhase ? 1 : 0, charge, state);
        if (result) turnAccepted = true;
        JoinedTupletObservation.event(
                "JOINT_MEASURED_TURN_ROLE",
                proof.cells(),
                proof.coveredRuns().length,
                proof.leftArm(),
                proof.rightArm(),
                proof.measuredFit().quarterRadius(),
                result ? 1 : 0);
        return result;
    }

    private Port port(int from, int to) {
        var a = nodes.get(from);
        var b = nodes.get(to);
        charge.tick();
        int x, y;
        if (a.r < b.l) {
            x = a.r;
            y = b.l;
        } else if (b.r < a.l) {
            x = a.l;
            y = b.r;
        } else {
            x = Math.max(a.l, b.l);
            y = x;
        }
        return new Port(from, to, pixel(x, a.y), pixel(y, b.y));
    }

    private Witness returns(Segment start, int waist, int low) {
        state.run(); // Capacity check precedes initial state-owned payloads.
        pay((nodes.size() + 63) / 64 + 40);
        ArrayDeque<Walk> todo = new ArrayDeque<>();
        BitSet first = new BitSet(nodes.size());
        first.set(start.id);
        todo.add(new Walk(new int[] {start.id}, first, start.l));
        while (!todo.isEmpty()) {
            charge.tick();
            Walk walk = todo.remove();
            int last = walk.path[walk.path.length - 1];
            var current = nodes.get(last);
            for (int next : edges[last]) {
                charge.tick();
                var dest = nodes.get(next);
                if (dest.y < low) continue;
                if (dest.arc) {
                    Port end = port(last, next);
                    if (start.r - walk.minX < indent || end.toPixel % width > start.r - indent)
                        continue;
                    var witness = terminalWitness(walk, end, waist, low);
                    if (witness != null) return witness;
                    continue;
                }
                if (walk.seen.get(next)) continue;
                state.run();
                pay(walk.path.length + 1 + (nodes.size() + 63) / 64);
                int[] path = Arrays.copyOf(walk.path, walk.path.length + 1);
                path[path.length - 1] = next;
                BitSet seen = (BitSet) walk.seen.clone();
                seen.set(next);
                pay(todo.size() + 3);
                todo.add(new Walk(path, seen, Math.min(walk.minX, dest.l)));
            }
        }
        return null;
    }

    private Witness terminalWitness(Walk walk, Port end, int waist, int low) {
        ArrayList<Port> ports = new ArrayList<>();
        ArrayList<Face> faces = new ArrayList<>();
        int entry = pixel(nodes.get(walk.path[0]).r, nodes.get(walk.path[0]).y);
        boolean positive = false, negative = false;
        for (int i = 0; i < walk.path.length; i++) {
            var n = nodes.get(walk.path[i]);
            Port out = i + 1 < walk.path.length ? port(n.id, walk.path[i + 1]) : end;
            pay(ports.size() + 4);
            ports.add(out);
            int exit = out.fromPixel;
            // Literal horizontal edges inside this original run join the exact incoming/outgoing
            // ports.
            int x0 = entry % width, x1 = exit % width, step = Integer.compare(x1, x0);
            if (step != 0)
                for (int x = x0; x != x1; x += step) {
                    charge.tick();
                    int a = pixel(x, n.y), b = pixel(x + step, n.y);
                    if (!negative) {
                        Face f = face(a, b, -1);
                        if (f != null) {
                            pay(faces.size() + 4);
                            faces.add(f);
                            negative = true;
                        }
                    }
                    if (!positive) {
                        Face f = face(a, b, 1);
                        if (f != null) {
                            pay(faces.size() + 4);
                            faces.add(f);
                            positive = true;
                        }
                    }
                }
            if (!negative) {
                Face f = face(out.fromPixel, out.toPixel, -1);
                if (f != null) {
                    pay(faces.size() + 4);
                    faces.add(f);
                    negative = true;
                }
            }
            if (!positive) {
                Face f = face(out.fromPixel, out.toPixel, 1);
                if (f != null) {
                    pay(faces.size() + 4);
                    faces.add(f);
                    positive = true;
                }
            }
            entry = out.toPixel;
        }
        if (!positive || !negative || !bothArcSides(end.toPixel)) return null;
        // Enumerate every offending original site: exact C is compatible shared support,
        // non-C is retained mandatory phase support, never forgiven as arc. Neither list is paper.
        ArrayList<Integer> excess = new ArrayList<>(),
                nonCExcess = new ArrayList<>(),
                shared = new ArrayList<>(),
                mandatory = new ArrayList<>(),
                returnIds = new ArrayList<>();
        int terminalX = end.toPixel % width, lowerRight = nodes.get(walk.path[0]).r;
        for (int y = box.t; y <= box.b; y++)
            for (int x = box.l; x <= box.r; x++) {
                int v = value(x, y);
                if (v > 165) continue;
                int at = pixel(x, y);
                charge.tick();
                boolean c = curve.get(at);
                if (c) {
                    pay(shared.size() + 1);
                    shared.add(at);
                } else {
                    pay(mandatory.size() + 1);
                    mandatory.add(at);
                }
                boolean tail = y >= box.t + Math.ceil((box.b - box.t + 1) * .92) && x >= lowerRight;
                boolean full = fullFoot(y - box.t) && x > terminalX;
                if (tail || full) {
                    if (c) {
                        pay(excess.size() + 1);
                        excess.add(at);
                    } else {
                        pay(nonCExcess.size() + 1);
                        nonCExcess.add(at);
                    }
                    // Non-C sites are NEVER arc-exempted: they remain mandatory complete-phase
                    // support.
                    // The full same-graph2 veto already ran; paper/return does not erase these
                    // observations.
                }
            }
        for (int id : walk.path) {
            pay(returnIds.size() + 1);
            returnIds.add(id);
        }
        pay(
                mandatory.size()
                        + returnIds.size()
                        + ports.size()
                        + faces.size()
                        + excess.size()
                        + nonCExcess.size()
                        + shared.size());
        JoinedTupletObservation.event(
                "JOINT_TERMINAL_WITNESS",
                waist,
                low,
                mandatory.size(),
                walk.path.length,
                faces.size(),
                excess.size(),
                shared.size(),
                end.fromPixel,
                end.toPixel);
        pay(nodes.size() * 7);
        ArrayList<RunEvidence> runEvidence = new ArrayList<>(nodes.size());
        for (var node : nodes) {
            charge.tick();
            runEvidence.add(new RunEvidence(node.id, node.y, node.l, node.r, node.arc));
        }
        return new Witness(
                waist,
                low,
                List.copyOf(mandatory),
                List.copyOf(returnIds),
                List.copyOf(ports),
                List.copyOf(faces),
                List.copyOf(excess),
                List.copyOf(nonCExcess),
                List.copyOf(shared),
                List.copyOf(runEvidence),
                turnGlobalAccepted ? turnProof : null);
    }

    private boolean bothArcSides(int at) {
        int x = at % width, y = at / width;
        boolean left = false, right = false;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                charge.tick();
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= width || yy >= height) continue;
                if (curve.get(pixel(xx, yy))) {
                    if (dx < 0) left = true;
                    if (dx > 0) right = true;
                }
            }
        return left && right;
    }

    private Face face(int a, int b, int side) {
        if (!nonArcReturnEdge(curve, a, b)) return null;
        int ax = a % width, ay = a / width, bx = b % width, by = b / width;
        int dx = Integer.compare(bx, ax), dy = Integer.compare(by, ay);
        if (dx == 0 && dy == 0) return null;
        int x = ax - side * dy, y = ay + side * dx;
        if (x < window.l || x > window.r || y < window.t || y > window.b || value(x, y) < paper)
            return null;
        if (!paperExit(x, y)) return null;
        return new Face(a, b, side, pixel(x, y));
    }

    private boolean paperExit(int x, int y) {
        int l = Math.max(window.l, box.l - 1),
                r = Math.min(window.r, box.r + 1),
                t = Math.max(window.t, box.t - 1),
                b = Math.min(window.b, box.b + 1);
        if (x < l || x > r || y < t || y > b) return false;
        int w = r - l + 1, h = b - t + 1;
        state.run(); // Existing reservation before payload allocation and seed writes.
        pay(w * h + ((w * h + 63) / 64));
        int[] queue = new int[w * h];
        BitSet seen = new BitSet(w * h);
        int size = 1, take = 0;
        queue[0] = (y - t) * w + x - l;
        seen.set(queue[0]);
        while (take < size) {
            charge.tick();
            int id = queue[take++], xx = l + id % w, yy = t + id / w;
            if (xx == l || xx == r || yy == t || yy == b) return true;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    charge.tick();
                    int nx = xx + dx, ny = yy + dy;
                    if (nx < l || nx > r || ny < t || ny > b) continue;
                    int next = (ny - t) * w + nx - l;
                    if (seen.get(next) || value(nx, ny) < paper) continue;
                    state.run();
                    seen.set(next);
                    queue[size++] = next;
                }
        }
        return false;
    }

    private boolean fullFoot(int row) {
        int w = box.r - box.l + 1;
        charge.tick();
        return min[row] <= w * .4f
                && max[row] - min[row] + 1 >= w * .6f
                && count[row] >= (max[row] - min[row] + 1) * .9f;
    }

    /** Complete same-graph diagonal/full-foot witness, with optional attached hook retained as support. */
    private boolean two() {
        int h = box.b - box.t + 1, w = box.r - box.l + 1;
        for (int row = (int) Math.ceil(h * .55f); row < h; row++) {
            charge.tick();
            if (!fullFoot(row)) continue;
            for (var upper : nodes) {
                charge.tick();
                float f = (upper.y - box.t) / (float) h;
                if (upper.arc || f < .15f || f > .36f + 1f / h || upper.r - box.l < w * .5f)
                    continue;
                // Foot/hook are complete original phase graphs, never a cleaned monotone all-ink
                // walk.
                if (!phase(box.t, box.t + row, box.t + row, true)
                        || !phase(box.t + row, box.b, box.t + row, true)) continue;
                for (int startX = upper.l; startX <= upper.r; startX++) {
                    charge.tick();
                    if (startX - box.l < w * .5f) continue;
                    int[] witness =
                            diagonalWitness(
                                    raw,
                                    width,
                                    height,
                                    box.l,
                                    box.t,
                                    box.r,
                                    box.b,
                                    startX,
                                    upper.y,
                                    box.t + row,
                                    box.l + (int) Math.floor(w * .4f),
                                    indent,
                                    charge,
                                    state);
                    if (witness != null) {
                        var family =
                                JoinedDigitTemplates.family(
                                        raw, width, height, curve, box.l, box.t, box.r, box.b, gap,
                                        charge);
                        var reading = family == null ? null : family.reading();
                        if (reading != null) {
                            JoinedTupletObservation.event(
                                    "JOINED_DIGIT_EXTENT",
                                    family.left(),
                                    family.top(),
                                    family.right(),
                                    family.bottom(),
                                    family.frames());
                            JoinedTupletObservation.event(
                                    "JOINED_DIGIT_READING",
                                    reading.digit(),
                                    Float.floatToRawIntBits(reading.best()),
                                    Float.floatToRawIntBits(reading.runner()),
                                    reading.independentInk(),
                                    reading.observedCells());
                            for (int digit = 0; digit < 10; digit++)
                                JoinedTupletObservation.event(
                                        "JOINED_DIGIT_SCORE",
                                        digit,
                                        Float.floatToRawIntBits(reading.scores()[digit]));
                        }
                        JoinedTupletObservation.event(
                                "JOINT_TWO_PIXEL",
                                upper.id,
                                row,
                                witness[0],
                                witness[witness.length - 1],
                                witness.length);
                        // The complete original-raster glyph family compares every rival digit.
                        // A path through a known slur is insufficient Two evidence when an
                        // independently visible, complete font model confidently reads Three.
                        // This resolves only the numeral class; full graph/return/fringe and
                        // bracket/owner/ambiguity gates still decide the caller's outcome.
                        if (reading != null && reading.digit() == 3) {
                            JoinedTupletObservation.event("JOINT_TWO_PATH_DISAMBIGUATED_THREE");
                            fontThree = family;
                            return false;
                        }
                        // A complete public-font rival is classified evidence. Otherwise a
                        // geometric Two needs a whole foot independent of the bound curve.
                        // Unknown font output remains unknown when the curve supplies the foot;
                        // this local candidate still cannot become a positive Three.
                        if (reading != null && reading.digit() >= 0) {
                            opposingDigit = reading.digit();
                        } else if (independentFoot(row)) {
                            opposingDigit = 2;
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean independentFoot(int row) {
        int y = box.t + row;
        for (int x = box.l + min[row]; x <= box.l + max[row]; x++) {
            charge.tick();
            if ((raw[y * width + x] & 255) <= 165 && curve.get(y * width + x)) return false;
        }
        return true;
    }

    static boolean nonArcReturnEdge(BitSet curve, int a, int b) {
        return curve != null && a >= 0 && b >= 0 && !curve.get(a) && !curve.get(b);
    }

    private static void debit(ObservedFringeContinuation.Charge work, int n) {
        for (int i = 0; i < n; i++) work.tick();
    }

    /** Exact observed pixel position plus actual running-minimum frontier; no run-extrema shortcut. */
    static int[] diagonalWitness(
            byte[] raw,
            int width,
            int height,
            int l,
            int t,
            int r,
            int b,
            int startX,
            int startY,
            int footY,
            int leftLimit,
            int indentation,
            ObservedFringeContinuation.Charge work,
            Runnable capacity) {
        if (raw == null
                || width < 1
                || height < 1
                || raw.length != (long) width * height
                || l < 0
                || t < 0
                || r >= width
                || b >= height
                || l > r
                || t > b
                || startX < l
                || startX > r
                || startY < t
                || startY > b
                || footY < startY
                || footY > b
                || work == null
                || capacity == null) return null;
        int w = r - l + 1, h = b - t + 1;
        if ((long) w * h > 16384) return null;
        work.tick();
        if ((raw[startY * width + startX] & 255) > 165) return null;
        capacity.run();
        int states = w * h * 2;
        debit(work, states * 3);
        boolean[] seen = new boolean[states];
        int[] parent = new int[states], queue = new int[states];
        int initial = ((startY - t) * w + startX - l) * 2, take = 0, size = 1;
        debit(work, 4);
        seen[initial] = true;
        parent[initial] = -1;
        queue[0] = initial;
        while (take < size) {
            work.tick();
            int at = queue[take++],
                    local = at / 2,
                    x = l + local % w,
                    y = t + local / w,
                    minimum = x - (at & 1);
            if (y == footY) {
                if (x <= leftLimit && startX - x >= indentation) {
                    int length = 0;
                    for (int q = at; q >= 0; q = parent[q]) {
                        work.tick();
                        length++;
                    }
                    debit(work, length * 2);
                    int[] result = new int[length];
                    int q = at;
                    for (int i = length - 1; i >= 0; i--) {
                        work.tick();
                        int pos = q / 2;
                        result[i] = (t + pos / w) * width + l + pos % w;
                        q = parent[q];
                    }
                    return result;
                }
                // Frozen y-nondecreasing law permits observed horizontal foot-row edges too.
            }
            for (int dy = 0; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    work.tick();
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx < l || nx > r || ny > footY || nx > minimum + 1) continue;
                    work.tick();
                    if ((raw[ny * width + nx] & 255) > 165) continue;
                    int nextMinimum = Math.min(minimum, nx),
                            id = ((ny - t) * w + nx - l) * 2 + nx - nextMinimum;
                    work.tick();
                    if (seen[id]) continue;
                    capacity.run();
                    debit(work, 3);
                    seen[id] = true;
                    parent[id] = at;
                    queue[size++] = id;
                }
        }
        return null;
    }

    /** Source-free topology primitive; boolean roles are derived from actual graph in production. */
    @SuppressWarnings("unchecked")
    static boolean blockPortsSafe(
            int[][] graph,
            boolean[] active,
            boolean[] mandatory,
            boolean[] port,
            boolean[] exposed,
            int freeExposed,
            ObservedFringeContinuation.Charge work,
            Runnable capacity) {
        if (graph == null
                || active == null
                || mandatory == null
                || port == null
                || exposed == null
                || work == null
                || capacity == null) return false;
        int n = graph.length;
        if (n < 1
                || n > 4096
                || active.length != n
                || mandatory.length != n
                || port.length != n
                || exposed.length != n) return false;
        int edgeCount = 0;
        for (int i = 0; i < n; i++) {
            work.tick();
            if (graph[i] == null) return false;
            for (int j : graph[i]) {
                work.tick();
                if (j < 0 || j >= n) return false;
                if (active[i] && active[j] && j > i) edgeCount++;
            }
        }
        capacity.run();
        debit(work, n * 7 + edgeCount * 2);
        int[] disc = new int[n],
                low = new int[n],
                parent = new int[n],
                next = new int[n],
                stack = new int[n],
                eu = new int[edgeCount],
                ev = new int[edgeCount];
        debit(work, n);
        Arrays.fill(parent, -1);
        debit(work, 12);
        ArrayList<BitSet> blocks = new ArrayList<>();
        int clock = 0, edgeTop = 0;
        for (int root = 0; root < n; root++) {
            work.tick();
            if (!active[root] || disc[root] != 0) continue;
            int size = 1;
            stack[0] = root;
            disc[root] = low[root] = ++clock;
            capacity.run();
            while (size > 0) {
                work.tick();
                int v = stack[size - 1];
                if (next[v] < graph[v].length) {
                    int j = graph[v][next[v]++];
                    work.tick();
                    if (!active[j] || j == parent[v]) continue;
                    if (disc[j] == 0) {
                        if (edgeTop >= edgeCount) return false;
                        capacity.run();
                        debit(work, 7);
                        eu[edgeTop] = v;
                        ev[edgeTop++] = j;
                        parent[j] = v;
                        disc[j] = low[j] = ++clock;
                        stack[size++] = j;
                    } else if (disc[j] < disc[v]) {
                        if (edgeTop >= edgeCount) return false;
                        debit(work, 3);
                        eu[edgeTop] = v;
                        ev[edgeTop++] = j;
                        low[v] = Math.min(low[v], disc[j]);
                    }
                } else {
                    size--;
                    int par = parent[v];
                    if (par >= 0) {
                        low[par] = Math.min(low[par], low[v]);
                        if (low[v] >= disc[par]) {
                            capacity.run();
                            debit(work, (n + 63) / 64 + blocks.size() + 3);
                            BitSet block = new BitSet(n);
                            while (edgeTop > 0) {
                                work.tick();
                                int a = eu[--edgeTop], b = ev[edgeTop];
                                block.set(a);
                                block.set(b);
                                if (a == par && b == v) break;
                            }
                            blocks.add(block);
                        }
                    } else {
                        boolean isolated = true;
                        for (int j : graph[v]) {
                            work.tick();
                            if (active[j]) isolated = false;
                        }
                        if (isolated) {
                            capacity.run();
                            debit(work, (n + 63) / 64 + blocks.size() + 2);
                            BitSet block = new BitSet(n);
                            block.set(v);
                            blocks.add(block);
                        }
                    }
                }
            }
        }
        debit(work, n * 2);
        int[] memberships = new int[n], artId = new int[n];
        debit(work, n);
        Arrays.fill(artId, -1);
        for (BitSet block : blocks)
            for (int id = block.nextSetBit(0); id >= 0; id = block.nextSetBit(id + 1)) {
                work.tick();
                memberships[id]++;
            }
        int total = blocks.size();
        for (int i = 0; i < n; i++) {
            work.tick();
            if (memberships[i] > 1) artId[i] = total++;
        }
        capacity.run();
        debit(work, total * 6);
        ArrayList<Integer>[] tree = (ArrayList<Integer>[]) new ArrayList<?>[total];
        boolean[] required = new boolean[total], keep = new boolean[total];
        int[] degree = new int[total], queue = new int[total];
        for (int i = 0; i < total; i++) {
            work.tick();
            debit(work, 12);
            tree[i] = new ArrayList<>();
            keep[i] = true;
        }
        for (int k = 0; k < blocks.size(); k++) {
            BitSet block = blocks.get(k);
            for (int id = block.nextSetBit(0); id >= 0; id = block.nextSetBit(id + 1)) {
                work.tick();
                if (artId[id] >= 0) {
                    int a = artId[id];
                    debit(work, tree[k].size() + tree[a].size() + 4);
                    tree[k].add(a);
                    tree[a].add(k);
                    if (mandatory[id]) required[a] = true;
                } else if (mandatory[id]) required[k] = true;
            }
        }
        int size = 0, take = 0;
        for (int i = 0; i < total; i++) {
            work.tick();
            degree[i] = tree[i].size();
            if (!required[i] && degree[i] <= 1) {
                capacity.run();
                queue[size++] = i;
                keep[i] = false;
            }
        }
        while (take < size) {
            work.tick();
            int id = queue[take++];
            for (int j : tree[id]) {
                work.tick();
                if (!keep[j]) continue;
                degree[j]--;
                if (!required[j] && degree[j] <= 1) {
                    capacity.run();
                    keep[j] = false;
                    queue[size++] = j;
                }
            }
        }
        int remaining = 0, free = freeExposed;
        for (int id = 0; id < total; id++) {
            work.tick();
            if (!keep[id]) continue;
            remaining++;
            if (degree[id] > 2) return false;
            if (degree[id] > 1) continue;
            if (id >= blocks.size()) {
                boolean declared = false;
                for (int v = 0; v < n; v++) {
                    work.tick();
                    if (artId[v] == id && port[v]) declared = true;
                }
                if (!declared) return false;
                continue;
            }
            int attachment = -1;
            if (degree[id] == 1)
                for (int j : tree[id])
                    if (keep[j]) {
                        attachment = j;
                        break;
                    }
            boolean declared = false, visible = false;
            BitSet block = blocks.get(id);
            for (int v = block.nextSetBit(0); v >= 0; v = block.nextSetBit(v + 1)) {
                work.tick();
                if (attachment >= 0 && artId[v] == attachment)
                    continue; // A sole entry attachment is not a terminal port.
                if (port[v]) declared = true;
                if (exposed[v]) visible = true;
            }
            if (!declared) {
                if (!visible || free <= 0) return false;
                free--;
            }
        }
        return remaining > 0;
    }
}

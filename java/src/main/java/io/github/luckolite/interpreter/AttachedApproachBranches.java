// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Separates a free approach branch only after closing the entire attached ink tree's roles. */
final class AttachedApproachBranches {
    record Part(
            int[] pixels,
            int owner,
            int left,
            int right,
            int top,
            int bottom,
            double cx,
            double cy,
            double slope) {}

    static final class Budget {
        long left;
        boolean refused;

        Budget(long left) {
            this.left = left;
        }

        boolean take(long amount) {
            if (amount < 0 || left < amount) {
                refused = true;
                return false;
            }
            left -= amount;
            return true;
        }
    }

    private record Trace(List<Integer> path, int owner) {}

    private AttachedApproachBranches() {}

    static List<Part> find(
            byte[] raw,
            int w,
            int h,
            int[] component,
            int count,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            Map<Integer, StaffPitchTrack> tracks,
            Budget budget) {
        if (raw == null
                || raw.length != (long) w * h
                || component == null
                || count < 32
                || count > 16384
                || count > component.length
                || staffs.isEmpty()
                || heads.size() < 2
                || budget.refused) return List.of();
        int left = w, right = 0, top = h, bottom = 0;
        for (int i = 0; i < count; i++) {
            int p = component[i];
            if (p < 0 || p >= raw.length || (raw[p] & 255) >= 150) return List.of();
            int x = p % w, y = p / w;
            left = Math.min(left, x);
            right = Math.max(right, x);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
        }
        int sw = right - left + 5, sh = bottom - top + 5, area = sw * sh;
        if (sw < 8 || sh < 8 || sw > 1024 || sh > 512 || area > 65536) return List.of();
        var localHeads = new ArrayList<Integer>();
        int lane = -1;
        for (int i = 0; i < heads.size(); i++) {
            var head = heads.get(i);
            float gap = head.gap();
            if (head.staff() < 0
                    || head.staff() >= staffs.size()
                    || !Float.isFinite(head.x() + head.y() + gap)
                    || gap < 6
                    || gap > 64) continue;
            if (head.x() < left - gap
                    || head.x() > right + gap
                    || head.y() < top - gap
                    || head.y() > bottom + gap) continue;
            // A complete tree may not silently discard a competing physical lane.
            if (lane >= 0 && lane != head.staff()) return List.of();
            lane = head.staff();
            localHeads.add(i);
        }
        if (localHeads.size() < 2
                || localHeads.size() > 32
                || !budget.take((long) count * localHeads.size() * 2)) return List.of();
        if (!possibleFreeApproach(component, count, w, heads, localHeads)
                || !budget.take(area + count)) return List.of();
        boolean[] ink = new boolean[area];
        for (int i = 0; i < count; i++) {
            int p = component[i], at = (p / w - top + 2) * sw + p % w - left + 2;
            if (ink[at]) return List.of();
            ink[at] = true;
        }
        boolean[] skeleton = ink.clone();
        int[] remove = new int[area];
        boolean changed = true;
        int iterations = 0;
        while (changed) {
            if (++iterations > 128 || !budget.take((long) area * 16)) return List.of();
            changed = false;
            for (int step = 0; step < 2; step++) {
                int n = 0;
                for (int y = 1; y < sh - 1; y++)
                    for (int x = 1; x < sw - 1; x++) {
                        int p = y * sw + x;
                        if (!skeleton[p]) continue;
                        boolean[] v = {
                            skeleton[p - sw],
                            skeleton[p - sw + 1],
                            skeleton[p + 1],
                            skeleton[p + sw + 1],
                            skeleton[p + sw],
                            skeleton[p + sw - 1],
                            skeleton[p - 1],
                            skeleton[p - sw - 1]
                        };
                        int neighbors = 0, transitions = 0;
                        for (int k = 0; k < 8; k++) {
                            if (v[k]) neighbors++;
                            if (!v[k] && v[(k + 1) % 8]) transitions++;
                        }
                        if (neighbors < 2 || neighbors > 6 || transitions != 1) continue;
                        if (step == 0
                                ? (v[0] && v[2] && v[4] || v[2] && v[4] && v[6])
                                : (v[0] && v[2] && v[6] || v[0] && v[4] && v[6])) continue;
                        remove[n++] = p;
                    }
                if (n > 0) changed = true;
                for (int i = 0; i < n; i++) skeleton[remove[i]] = false;
            }
        }
        var nodes = new ArrayList<Integer>();
        for (int p = 0; p < area; p++) if (skeleton[p]) nodes.add(p);
        var cuts = new HashSet<Long>();
        var staff = staffs.get(lane);
        // Staff cleanup can retain a real horizontal rule bridge between the curve and shaft.
        // Remove only a cycle edge whose entire row is independently raw-proved as a long rule.
        for (int cycle = 0; cycle <= 8; cycle++) {
            long links = 0;
            for (int p : nodes) links += neighbors(skeleton, sw, p, cuts).size();
            if (links == 2L * (nodes.size() - 1)) break;
            if (cycle == 8) return List.of();
            boolean removed = false;
            for (int p : nodes) {
                if (removed) break;
                for (int next : neighbors(skeleton, sw, p, cuts)) {
                    if (next <= p || next / sw != p / sw) continue;
                    int x = left + p % sw - 2, y = top + p / sw - 2;
                    if (!rawRule(raw, w, h, x, y, staff)) continue;
                    if (!budget.take((long) nodes.size() * 12)) return List.of();
                    long edge = edge(p, next);
                    cuts.add(edge);
                    if (reachable(skeleton, sw, p, next, cuts)) {
                        removed = true;
                        break;
                    }
                    cuts.remove(edge);
                }
            }
            if (!removed) return List.of();
        }
        var leaves = new ArrayList<Integer>();
        int[] degree = new int[area];
        long edges = 0;
        for (int p : nodes) {
            degree[p] = neighbors(skeleton, sw, p, cuts).size();
            edges += degree[p];
            if (degree[p] == 1) leaves.add(p);
        }
        if (nodes.size() < 8
                || leaves.size() < 3
                || leaves.size() > 32
                || edges != 2L * (nodes.size() - 1)) return List.of();
        // The skeleton is diagnostic topology. Raw pixels and independent head/shaft proofs own
        // roles.
        boolean[] connected = new boolean[area];
        var q = new ArrayDeque<Integer>();
        q.add(nodes.get(0));
        connected[nodes.get(0)] = true;
        int visited = 0;
        while (!q.isEmpty()) {
            int p = q.remove();
            visited++;
            for (int next : neighbors(skeleton, sw, p, cuts))
                if (!connected[next]) {
                    connected[next] = true;
                    q.add(next);
                }
        }
        if (visited != nodes.size()) return List.of();
        if (!tracks.containsKey(lane))
            tracks.put(
                    lane,
                    StaffPitchTrack.forPrintedSlurs(
                            raw, w, h, staff.top(), staff.bottom(), staff.gap()));
        var track = tracks.get(lane);
        if (track == null) return List.of();
        var evidence = new HashMap<Integer, PrintedSlurContours.HeadEvidence>();
        for (int i : localHeads) {
            var head = heads.get(i);
            evidence.put(
                    i,
                    PrintedSlurOwners.headEvidence(
                            List.of(
                                    new PrintedSlurOwners.GeometryHead(
                                            i, head.x(), head.y(), lane, staffs.size())),
                            raw,
                            w,
                            h,
                            track));
        }
        boolean[] allSkeleton = skeleton.clone();
        // Contract only independently typed staff-rule stubs. Their pixels
        // remain OTHER seeds in raw ownership assignment; a curve never acquires a cut rule.
        int staffStubLimit = Math.min(nodes.size(), localHeads.size() * 10 + cuts.size() * 2);
        for (int pass = 0; pass <= staffStubLimit; pass++) {
            boolean removed = false;
            for (int leaf : leaves) {
                int x = left + leaf % sw - 2, y = top + leaf / sw - 2;
                boolean head = false;
                for (int i : localHeads)
                    if (evidence.get(i).contains(x, y)) {
                        head = true;
                        break;
                    }
                if (head
                        || !staffTip(skeleton, degree, sw, leaf, cuts, raw, w, h, left, top, staff))
                    continue;
                if (pass == staffStubLimit || !budget.take((long) nodes.size() * 8))
                    return List.of();
                var path = trace(skeleton, degree, sw, leaf, cuts);
                for (int i = 0; i < path.size() - 1; i++) skeleton[path.get(i)] = false;
                removed = true;
                break;
            }
            if (!removed) break;
            leaves.clear();
            for (int p : nodes) {
                degree[p] = skeleton[p] ? neighbors(skeleton, sw, p, cuts).size() : 0;
                if (degree[p] == 1) leaves.add(p);
            }
        }
        var leafOwners = new HashMap<Integer, Integer>();
        for (int leaf : leaves) {
            int owner = -1, x = left + leaf % sw - 2, y = top + leaf / sw - 2;
            for (int i : localHeads)
                if (evidence.get(i).contains(x, y)) {
                    if (owner >= 0) return List.of();
                    owner = i;
                }
            if (owner < 0
                    && staffTip(skeleton, degree, sw, leaf, cuts, raw, w, h, left, top, staff))
                owner = -3;
            leafOwners.put(leaf, owner);
        }
        var bare = new ArrayList<Trace>();
        for (int leaf : leaves) {
            if (leafOwners.get(leaf) >= 0 || leafOwners.get(leaf) == -3) continue;
            var path = trace(skeleton, degree, sw, leaf, cuts);
            int end = path.get(path.size() - 1);
            if (degree[end] != 3) return List.of();
            int ax = leaf % sw, ay = leaf / sw, bx = end % sw, by = end / sw, owner = -1;
            for (int branch : neighbors(skeleton, sw, end, cuts)) {
                if (branch == path.get(path.size() - 2)) continue;
                var headPath = traceFrom(skeleton, degree, sw, end, branch, cuts);
                int terminal = headPath.get(headPath.size() - 1);
                int candidate = leafOwners.getOrDefault(terminal, -1);
                if (candidate < 0) continue;
                var head = heads.get(candidate);
                float gap = head.gap();
                if (headPath.size() > gap * 2
                        || bx - ax < gap * .65f
                        || bx - ax > gap * 3.5f
                        || ay - by < gap * .6f
                        || ay - by > gap * 2.5f) continue;
                int jx = left + bx - 2, jy = top + by - 2;
                // A thick collar can move the attachment node along the already proved shaft.
                // The actual short head branch and unchanged approach sector own its extent.
                if (Math.abs(jx - (head.x() - gap * .6f)) > gap * .3f
                        || jy < head.y() - gap * .2f
                        || PrintedStemDirection.detect(raw, w, h, head.x(), head.y(), gap) != -1)
                    continue;
                boolean foreign = false;
                for (int i : localHeads) {
                    var other = heads.get(i);
                    int x = left + ax - 2, y = top + ay - 2;
                    if (Math.abs(x - other.x()) < gap * .8f && Math.abs(y - other.y()) < gap * .8f)
                        foreign = true;
                }
                if (foreign) continue;
                if (owner >= 0) return List.of();
                owner = candidate;
            }
            if (owner < 0) return List.of();
            bare.add(new Trace(path, owner));
        }
        if (bare.isEmpty() || bare.size() > 4) return List.of();
        var result = new ArrayList<Part>();
        for (var branch : bare) {
            if (!budget.take((long) count * 36 + area * 4L)) return List.of();
            boolean[] isBranch = new boolean[area];
            for (int i = 0; i < branch.path.size() - 1; i++) isBranch[branch.path.get(i)] = true;
            int[] near = distance(ink, sw, allSkeleton, isBranch, true),
                    far = distance(ink, sw, allSkeleton, isBranch, false);
            boolean[] chosen = new boolean[area];
            int[] lo = new int[sw], hi = new int[sw];
            Arrays.fill(lo, sh);
            Arrays.fill(hi, -1);
            // Equal-distance collar ink has both roles; do not invent a paper gap at the joint.
            for (int i = 0; i < count; i++) {
                int original = component[i],
                        x = original % w,
                        y = original / w,
                        p = (y - top + 2) * sw + x - left + 2;
                if (near[p] > far[p]) continue;
                chosen[p] = true;
                lo[p % sw] = Math.min(lo[p % sw], p / sw);
                hi[p % sw] = Math.max(hi[p % sw], p / sw);
            }
            // A real rule crossing has two roles. Reuse only original component ink strictly
            // between the curve's independently retained upper/lower pixels in that column.
            for (int x = 0; x < sw; x++)
                if (hi[x] >= lo[x])
                    for (int y = lo[x]; y <= hi[x]; y++) {
                        int p = y * sw + x;
                        if (!chosen[p]
                                && ink[p]
                                && rawRule(raw, w, h, left + x - 2, top + y - 2, staff))
                            chosen[p] = true;
                    }
            // A staff-only island separated by real paper is not part of the curve in that
            // column. Keep all non-rule runs; a remaining split contour must still abstain.
            for (int x = 0; x < sw; x++)
                if (hi[x] >= lo[x]) {
                    var runs = new ArrayList<int[]>();
                    int meaningful = 0;
                    for (int y = lo[x]; y <= hi[x]; ) {
                        if (!ink[y * sw + x]) {
                            y++;
                            continue;
                        }
                        int start = y;
                        boolean selected = false, nonRule = false;
                        while (y <= hi[x] && ink[y * sw + x]) {
                            selected |= chosen[y * sw + x];
                            nonRule |= !rawRule(raw, w, h, left + x - 2, top + y - 2, staff);
                            y++;
                        }
                        runs.add(new int[] {start, y - 1, nonRule ? 1 : 0});
                        if (selected && nonRule) meaningful++;
                    }
                    if (meaningful == 1)
                        for (var run : runs)
                            if (run[2] == 0)
                                for (int y = run[0]; y <= run[1]; y++) chosen[y * sw + x] = false;
                }
            int[] selected = new int[count];
            int n = 0, l = w, r = 0, t = h, b = 0;
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            for (int i = 0; i < count; i++) {
                int original = component[i],
                        x = original % w,
                        y = original / w,
                        p = (y - top + 2) * sw + x - left + 2;
                if (!chosen[p]) continue;
                selected[n++] = original;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                sx += x;
                sy += y;
                sxx += (double) x * x;
                sxy += (double) x * y;
            }
            if (n < 8) return List.of();
            double cx = sx / n, cy = sy / n, vx = sxx / n - cx * cx;
            if (vx <= 0) return List.of();
            result.add(
                    new Part(
                            Arrays.copyOf(selected, n),
                            branch.owner,
                            l,
                            r,
                            t,
                            b,
                            cx,
                            cy,
                            (sxy / n - cx * cy) / vx));
        }
        return List.copyOf(result);
    }

    /** Negative work preflight only. These generous envelopes never own or erase raw pixels. */
    private static boolean possibleFreeApproach(
            int[] component,
            int count,
            int w,
            List<NoteSlideDetector.Head> heads,
            List<Integer> localHeads) {
        for (int owner : localHeads) {
            var target = heads.get(owner);
            float gap = target.gap();
            int free = 0, left = w, right = 0;
            for (int i = 0; i < count; i++) {
                int x = component[i] % w, y = component[i] / w;
                if (x > target.x() - gap * .65f
                        || x < target.x() - gap * 3.5f
                        || y < target.y() + gap * .6f
                        || y > target.y() + gap * 2.5f) continue;
                boolean possibleHeadOrShaft = false;
                for (int h : localHeads) {
                    var head = heads.get(h);
                    float g = head.gap();
                    if (Math.abs(x - head.x()) <= g * .85f && Math.abs(y - head.y()) <= g * .65f
                            || Math.abs(x - (head.x() - g * .6f)) <= g * .2f
                            || Math.abs(x - (head.x() + g * .6f)) <= g * .2f) {
                        possibleHeadOrShaft = true;
                        break;
                    }
                }
                if (!possibleHeadOrShaft) {
                    free++;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            if (free >= Math.max(8, Math.ceil(gap * .8f)) && right - left >= gap * .65f)
                return true;
        }
        return false;
    }

    private static long edge(int a, int b) {
        return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xffffffffL);
    }

    private static boolean rawRule(
            byte[] raw, int w, int h, int cx, int y, NoteSlideDetector.Staff staff) {
        float gap = staff.gap();
        boolean band = false;
        for (int line = 0; line < 5; line++)
            if (Math.abs(y - Math.round(staff.top() + line * gap)) <= 3) band = true;
        if (!band) return false;
        if (y < 0 || y >= h) return false;
        for (int direction : new int[] {-1, 1}) {
            int samples = 0, ink = 0;
            for (int dx = Math.round(gap * 2); dx <= Math.round(gap * 4); dx++) {
                int x = cx + direction * dx;
                if (x < 0 || x >= w) continue;
                samples++;
                if ((raw[y * w + x] & 255) < 150) ink++;
            }
            if (samples < Math.max(6, Math.ceil(gap)) || ink < samples * .85) return false;
        }
        return true;
    }

    private static boolean reachable(boolean[] s, int sw, int a, int b, Set<Long> cuts) {
        boolean[] seen = new boolean[s.length];
        var q = new ArrayDeque<Integer>();
        q.add(a);
        seen[a] = true;
        while (!q.isEmpty()) {
            int p = q.remove();
            if (p == b) return true;
            for (int next : neighbors(s, sw, p, cuts))
                if (!seen[next]) {
                    seen[next] = true;
                    q.add(next);
                }
        }
        return false;
    }

    private static boolean staffTip(
            boolean[] s,
            int[] degree,
            int sw,
            int leaf,
            Set<Long> cuts,
            byte[] raw,
            int w,
            int h,
            int left,
            int top,
            NoteSlideDetector.Staff staff) {
        var path = trace(s, degree, sw, leaf, cuts);
        boolean cut = false;
        for (int p : path) {
            if (!rawRule(raw, w, h, left + p % sw - 2, top + p / sw - 2, staff)) return false;
            for (long edge : cuts) if ((int) (edge >> 32) == p || (int) edge == p) cut = true;
        }
        return path.size() > 1;
    }

    private static List<Integer> neighbors(boolean[] s, int sw, int p, Set<Long> cuts) {
        var result = new ArrayList<Integer>(4);
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int next = p + dy * sw + dx;
                if (next < 0 || next >= s.length || !s[next]) continue;
                if (dx != 0 && dy != 0 && (s[p + dx] || s[p + dy * sw])
                        || cuts.contains(edge(p, next))) continue;
                result.add(next);
            }
        return result;
    }

    private static List<Integer> trace(
            boolean[] s, int[] degree, int sw, int start, Set<Long> cuts) {
        return traceFrom(s, degree, sw, -1, start, cuts);
    }

    private static List<Integer> traceFrom(
            boolean[] s, int[] degree, int sw, int previous, int start, Set<Long> cuts) {
        var path = new ArrayList<Integer>();
        path.add(start);
        int at = start;
        while (true) {
            var ns = neighbors(s, sw, at, cuts);
            ns.remove(Integer.valueOf(previous));
            if (ns.size() != 1) break;
            previous = at;
            at = ns.get(0);
            path.add(at);
            if (degree[at] != 2) break;
        }
        return path;
    }

    private static int[] distance(
            boolean[] ink, int sw, boolean[] skeleton, boolean[] branch, boolean curve) {
        int[] d = new int[ink.length];
        Arrays.fill(d, Integer.MAX_VALUE);
        var q = new ArrayDeque<Integer>();
        for (int p = 0; p < ink.length; p++)
            if (skeleton[p] && (branch[p] == curve)) {
                d[p] = 0;
                q.add(p);
            }
        while (!q.isEmpty()) {
            int p = q.remove();
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int next = p + dy * sw + dx;
                    if (next < 0
                            || next >= ink.length
                            || !ink[next]
                            || d[next] != Integer.MAX_VALUE) continue;
                    d[next] = d[p] + 1;
                    q.add(next);
                }
        }
        return d;
    }
}

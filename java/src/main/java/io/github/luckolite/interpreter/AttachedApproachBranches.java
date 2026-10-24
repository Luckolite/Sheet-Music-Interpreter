// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Separates typed slide branches only after closing the entire attached ink tree's roles. */
final class AttachedApproachBranches {
    record Part(
            int[] pixels,
            int[] exclusivePixels,
            int owner,
            int left,
            int right,
            int top,
            int bottom,
            double cx,
            double cy,
            double slope,
            int sourceIndex) {}

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

    private record Trace(List<Integer> path, int owner, int sourceIndex, boolean includeTerminal) {}

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
                || heads.isEmpty()
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
        if (localHeads.isEmpty()
                || localHeads.size() > 32
                || !budget.take(freeApproachWorkBound(count, localHeads.size()))) return List.of();
        if (!possibleFreeApproach(component, count, w, heads, localHeads)
                || !budget.take(area + count)) return List.of();
        if (!ScoopAttachmentRoles.componentOwner(
                raw, w, h, component, count, staffs, heads, localHeads, tracks, budget))
            return List.of();
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
        var downShafts = new HashSet<Integer>();
        for (int i : localHeads) {
            var head = heads.get(i);
            if (!budget.take((long) Math.ceil(head.gap() * head.gap() * 2 + 16))) return List.of();
            if (evidence.get(i).returningOwnerCost() == 1
                    && PrintedStemDirection.detect(raw, w, h, head.x(), head.y(), head.gap()) == -1)
                downShafts.add(i);
        }
        boolean[] allSkeleton = skeleton.clone();
        // Contract only independently typed staff-rule stubs. Their pixels
        // remain OTHER seeds in raw ownership assignment; a curve never acquires a cut rule.
        final int componentLeft = left, componentTop = top;
        final int componentLane = lane;
        var barlinePixels = new HashSet<Integer>();
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
                var typedTipPath = trace(skeleton, degree, sw, leaf, cuts);
                if (head
                        || (!staffTip(skeleton, degree, sw, leaf, cuts, raw, w, h, left, top, staff)
                                && !ScoopAttachmentRoles.barlinePath(
                                        raw,
                                        w,
                                        h,
                                        typedTipPath,
                                        p ->
                                                (componentTop + p / sw - 2) * w
                                                        + componentLeft
                                                        + p % sw
                                                        - 2,
                                        staff,
                                        componentLane,
                                        heads,
                                        track,
                                        budget,
                                        barlinePixels))) continue;
                if (pass == staffStubLimit || !budget.take((long) nodes.size() * 8))
                    return List.of();
                var path = typedTipPath;
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
        int graphLeft = left, graphTop = top;
        if (!budget.take((long) nodes.size() * 16)) return List.of();
        var flagLeaves =
                DownFlagBranchOwnership.leaves(
                        raw,
                        w,
                        h,
                        heads,
                        localHeads,
                        downShafts,
                        evidence,
                        nodes.stream().filter(p -> skeleton[p]).toList(),
                        leaves,
                        p -> neighbors(skeleton, sw, p, cuts),
                        p -> (graphTop + p / sw - 2) * w + graphLeft + p % sw - 2,
                        budget);
        var leafOwners = new HashMap<Integer, Integer>();
        for (int leaf : leaves) {
            int owner = -1, x = left + leaf % sw - 2, y = top + leaf / sw - 2;
            for (int i : localHeads)
                if (ScoopAttachmentRoles.headPixel(
                        evidence.get(i), heads.get(i), downShafts.contains(i), x, y, raw, w, h)) {
                    if (owner >= 0) return List.of();
                    owner = i;
                }
            if (owner < 0)
                owner =
                        headRuleCollarOwner(
                                skeleton,
                                degree,
                                sw,
                                leaf,
                                cuts,
                                raw,
                                w,
                                h,
                                left,
                                top,
                                staff,
                                heads,
                                localHeads,
                                evidence,
                                downShafts,
                                track,
                                budget);
            if (owner < 0
                    && staffTip(skeleton, degree, sw, leaf, cuts, raw, w, h, left, top, staff))
                owner = -3;
            if (owner < 0) {
                var tipPath = trace(skeleton, degree, sw, leaf, cuts);
                int[] originalPath = new int[tipPath.size()];
                for (int k = 0; k < originalPath.length; k++) {
                    int p = tipPath.get(k);
                    originalPath[k] = (top + p / sw - 2) * w + left + p % sw - 2;
                }
                if (BeamEndBranchOwnership.owns(
                        raw, w, h, originalPath, heads, localHeads, downShafts, budget)) owner = -4;
            }
            if (owner < 0 && owner != -3 && owner != -4) {
                var shaftPath = trace(skeleton, degree, sw, leaf, cuts);
                int[] originalPath = new int[shaftPath.size()];
                for (int k = 0; k < originalPath.length; k++) {
                    int p = shaftPath.get(k);
                    originalPath[k] = (top + p / sw - 2) * w + left + p % sw - 2;
                }
                if (StemEndBranchOwnership.owns(
                        raw, w, h, originalPath, heads, localHeads, downShafts, budget)) owner = -5;
            }
            if (owner == -1 && flagLeaves.contains(leaf)) owner = -6;
            if (owner == -1
                    && ScoopAttachmentRoles.shaftPath(
                            raw,
                            w,
                            h,
                            trace(skeleton, degree, sw, leaf, cuts),
                            p -> (componentTop + p / sw - 2) * w + componentLeft + p % sw - 2,
                            heads,
                            localHeads,
                            downShafts,
                            budget)) owner = -5;
            leafOwners.put(leaf, owner);
        }
        var bare =
                ruleJoinedCurves(
                        leaves,
                        leafOwners,
                        skeleton,
                        degree,
                        sw,
                        cuts,
                        left,
                        top,
                        raw,
                        w,
                        h,
                        staff,
                        heads,
                        localHeads,
                        downShafts,
                        budget);
        if (bare == null || budget.refused) return List.of();
        for (int leaf : leaves) {
            if (leafOwners.get(leaf) >= 0
                    || leafOwners.get(leaf) == -3
                    || leafOwners.get(leaf) == -4
                    || leafOwners.get(leaf) == -5
                    || leafOwners.get(leaf) == -6
                    || leafOwners.get(leaf) == -7) continue;
            var path = trace(skeleton, degree, sw, leaf, cuts);
            int end = path.get(path.size() - 1);
            if (degree[end] != 3) return List.of();
            int ax = leaf % sw,
                    ay = leaf / sw,
                    bx = end % sw,
                    by = end / sw,
                    owner = -1,
                    sourceIndex = -1;
            for (int branch : neighbors(skeleton, sw, end, cuts)) {
                if (branch == path.get(path.size() - 2)) continue;
                var headPath = traceFrom(skeleton, degree, sw, end, branch, cuts);
                int terminal = headPath.get(headPath.size() - 1);
                int candidate = leafOwners.getOrDefault(terminal, -1);
                if (candidate < 0) {
                    int tx = left + terminal % sw - 2, ty = top + terminal / sw - 2;
                    for (int i : localHeads)
                        if (headCollarContains(
                                evidence.get(i), heads.get(i), downShafts.contains(i), tx, ty)) {
                            if (candidate >= 0) return List.of();
                            candidate = i;
                        }
                }
                if (candidate < 0) continue;
                var head = heads.get(candidate);
                float gap = head.gap();
                boolean curvedGeometry = ay - by >= gap * .6f && ay - by <= gap * 2.5f;
                int prior =
                        previousEndpoint(
                                heads,
                                localHeads,
                                leafOwners,
                                evidence,
                                candidate,
                                left + ax - 2,
                                top + ay - 2,
                                left + bx - 2,
                                top + by - 2);
                if (headPath.size() > gap * 2
                        || bx - ax < gap * .65f
                        || bx - ax > gap * 3.5f
                        || (!curvedGeometry && prior < 0)) continue;
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
                if (owner >= 0 && owner != candidate) return List.of();
                owner = candidate;
                sourceIndex = prior;
            }
            if (owner < 0) return List.of();
            bare.add(new Trace(path, owner, sourceIndex, false));
        }
        if (bare.isEmpty() || bare.size() > 4) return List.of();
        var result = new ArrayList<Part>();
        for (var branch : bare) {
            if (!budget.take((long) count * 36 + area * 4L)) return List.of();
            boolean[] isBranch = new boolean[area];
            for (int i = 0; i < branch.path.size() - (branch.includeTerminal ? 0 : 1); i++)
                isBranch[branch.path.get(i)] = true;
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
                        if (selected
                                && nonRule
                                && (SeparatedRuleRunOwnership.matches(
                                                raw,
                                                w,
                                                h,
                                                left + x - 2,
                                                top + start - 2,
                                                top + y - 3,
                                                track,
                                                budget)
                                        || verifiedRulePixel(
                                                raw,
                                                w,
                                                h,
                                                left + x - 2,
                                                top + start - 2,
                                                track,
                                                budget))) nonRule = false;
                        runs.add(new int[] {start, y - 1, nonRule ? 1 : 0});
                        if (selected && nonRule) meaningful++;
                    }
                    if (meaningful == 1)
                        for (var run : runs)
                            if (run[2] == 0)
                                for (int y = run[0]; y <= run[1]; y++) chosen[y * sw + x] = false;
                }
            ScoopAttachmentRoles.withoutSeparateBarlineIslands(
                    chosen, isBranch, sw, sh, left, top, raw, w, barlinePixels, budget);
            if (budget.refused) return List.of();
            int[] selected = new int[count], exclusive = new int[count];
            int exclusiveN = 0;
            int n = 0, l = w, r = 0, t = h, b = 0;
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            for (int i = 0; i < count; i++) {
                int original = component[i],
                        x = original % w,
                        y = original / w,
                        p = (y - top + 2) * sw + x - left + 2;
                if (!chosen[p]) continue;
                selected[n++] = original;
                if (near[p] < far[p]) exclusive[exclusiveN++] = original;
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
                            Arrays.copyOf(exclusive, exclusiveN),
                            branch.owner,
                            l,
                            r,
                            t,
                            b,
                            cx,
                            cy,
                            (sxy / n - cx * cy) / vx,
                            branch.sourceIndex));
        }
        return List.copyOf(result);
    }

    /** Keep both physical ends of a curve when a proved staff bridge joins its middle to a shaft. */
    private static List<Trace> ruleJoinedCurves(
            List<Integer> leaves,
            Map<Integer, Integer> roles,
            boolean[] skeleton,
            int[] degree,
            int sw,
            Set<Long> cuts,
            int left,
            int top,
            byte[] raw,
            int w,
            int h,
            NoteSlideDetector.Staff staff,
            List<NoteSlideDetector.Head> heads,
            List<Integer> localHeads,
            Set<Integer> downShafts,
            Budget budget) {
        var free = new ArrayList<>(leaves.stream().filter(p -> roles.get(p) == -1).toList());
        var result = new ArrayList<Trace>();
        if (free.size() < 2) return result;
        long bound =
                (long) free.size() * skeleton.length * 8 + (long) free.size() * free.size() * 16;
        if (!budget.take(bound)) return null;
        var paths = new HashMap<Integer, List<Integer>>();
        for (int leaf : free) paths.put(leaf, trace(skeleton, degree, sw, leaf, cuts));
        var capMembers = new HashMap<Integer, List<Integer>>();
        for (int leaf : free) capMembers.put(leaf, List.of(leaf));
        float capGap = staff.gap();
        for (int a = 0; a < free.size(); a++)
            for (int b = a + 1; b < free.size(); b++) {
                int fa = free.get(a), fb = free.get(b);
                var pa = paths.get(fa);
                var pb = paths.get(fb);
                int joint = pa.get(pa.size() - 1);
                if (joint != pb.get(pb.size() - 1)
                        || degree[joint] != 3
                        || pa.size() > capGap * .6f
                        || pb.size() > capGap * .6f
                        || Math.abs(fa % sw - fb % sw) > capGap * .4f
                        || Math.abs(fa / sw - fb / sw) > capGap * .4f) continue;
                int third = -1;
                for (int next : neighbors(skeleton, sw, joint, cuts))
                    if (next != pa.get(pa.size() - 2) && next != pb.get(pb.size() - 2))
                        third = next;
                if (third < 0) continue;
                var tail = traceFrom(skeleton, degree, sw, joint, third, cuts);
                int terminal = tail.get(tail.size() - 1);
                if (degree[terminal] != 3
                        || tail.size() > capGap * 2
                        || !budget.take((long) (pa.size() + pb.size() + tail.size()) * 8)) continue;
                boolean swap = fa % sw < fb % sw || fa % sw == fb % sw && fa / sw > fb / sw;
                var first = swap ? pb : pa;
                var second = swap ? pa : pb;
                var complete = new ArrayList<Integer>(first);
                for (int k = second.size() - 2; k >= 0; k--) complete.add(second.get(k));
                for (int k = 1; k < second.size(); k++) complete.add(second.get(k));
                complete.addAll(tail);
                int representative = swap ? fb : fa, other = swap ? fa : fb;
                paths.put(representative, List.copyOf(complete));
                capMembers.put(representative, List.of(fa, fb));
                free.set(a, representative);
                free.remove(b--);
            }
        var used = new HashSet<Integer>();
        for (int a = 0; a < free.size(); a++)
            for (int b = a + 1; b < free.size(); b++) {
                var pa = paths.get(free.get(a));
                var pb = paths.get(free.get(b));
                int joint = pa.get(pa.size() - 1);
                if (joint != pb.get(pb.size() - 1) || degree[joint] != 3) continue;
                int third = -1;
                for (int next : neighbors(skeleton, sw, joint, cuts))
                    if (next != pa.get(pa.size() - 2) && next != pb.get(pb.size() - 2))
                        third = next;
                if (third < 0) continue;
                if (!budget.take((long) skeleton.length * 8)) return null;
                var bridge = traceFrom(skeleton, degree, sw, joint, third, cuts);
                int terminal = bridge.get(bridge.size() - 1),
                        tx = left + terminal % sw - 2,
                        ty = top + terminal / sw - 2;
                if (!budget.take(
                        (long) bridge.size() * (long) Math.ceil(staff.gap() * 8 + 64)
                                + localHeads.size() * 32L)) return null;
                boolean rule = true;
                for (int p : bridge)
                    if (!rawRule(raw, w, h, left + p % sw - 2, top + p / sw - 2, staff)) {
                        rule = false;
                        break;
                    }
                if (!rule) continue;
                var lp = pa;
                var rp = pb;
                if (lp.get(0) % sw > rp.get(0) % sw) {
                    lp = pb;
                    rp = pa;
                }
                int lx = left + lp.get(0) % sw - 2, ly = top + lp.get(0) / sw - 2;
                int rx = left + rp.get(0) % sw - 2, ry = top + rp.get(0) / sw - 2, owner = -1;
                for (int candidate : localHeads) {
                    if (!downShafts.contains(candidate)) continue;
                    var head = heads.get(candidate);
                    float gap = head.gap();
                    if (rx - lx < gap * .65f
                            || rx - lx > gap * 3.5f
                            || ly - ry < gap * .6f
                            || ly - ry > gap * 2.5f
                            || head.x() - rx < gap * .5f
                            || head.x() - rx > gap * 3
                            || head.y() >= ry
                            || bridge.size() > gap
                            || ty < head.y() + gap * .2f
                            || ty > head.y() + gap * 1.5f) continue;
                    if (!budget.take((long) Math.ceil(gap * gap * 6))) return null;
                    int[] shaft = DownFlagBranchOwnership.shaft(raw, w, h, head);
                    if (shaft == null
                            || Math.abs(tx - shaft[0]) > Math.max(1, gap * .2f)
                            || ty > shaft[1]) continue;
                    if (owner >= 0) return null;
                    owner = candidate;
                }
                if (owner < 0) continue;
                for (int member : capMembers.get(free.get(a))) if (!used.add(member)) return null;
                for (int member : capMembers.get(free.get(b))) if (!used.add(member)) return null;
                var path = new ArrayList<Integer>(lp);
                for (int k = rp.size() - 2; k >= 0; k--) path.add(rp.get(k));
                result.add(new Trace(List.copyOf(path), owner, -1, true));
            }
        for (int leaf : used) roles.put(leaf, -7);
        return result;
    }

    /** A rail-touching spur has only independently proved head, shaft-collar and rule roles. */
    private static int headRuleCollarOwner(
            boolean[] skeleton,
            int[] degree,
            int sw,
            int leaf,
            Set<Long> cuts,
            byte[] raw,
            int w,
            int h,
            int left,
            int top,
            NoteSlideDetector.Staff staff,
            List<NoteSlideDetector.Head> heads,
            List<Integer> localHeads,
            Map<Integer, PrintedSlurContours.HeadEvidence> evidence,
            Set<Integer> downShafts,
            StaffPitchTrack track,
            Budget budget) {
        int x = left + leaf % sw - 2, y = top + leaf / sw - 2;
        if (!rawRule(raw, w, h, x, y, staff) && !verifiedRulePixel(raw, w, h, x, y, track, budget))
            return -1;
        var path = trace(skeleton, degree, sw, leaf, cuts);
        int owner = -1;
        for (int candidate : localHeads) {
            var head = heads.get(candidate);
            float gap = head.gap();
            if (path.size() > gap * .75f) continue;
            boolean complete = true, body = false;
            for (int p : path) {
                int px = left + p % sw - 2, py = top + p / sw - 2;
                if (headCollarContains(
                        evidence.get(candidate), head, downShafts.contains(candidate), px, py))
                    body = true;
                else if (!rawRule(raw, w, h, px, py, staff)
                        && !verifiedRulePixel(raw, w, h, px, py, track, budget)) {
                    complete = false;
                    break;
                }
            }
            if (complete && body) {
                if (owner >= 0) return -1;
                owner = candidate;
            }
        }
        return owner;
    }

    private static boolean verifiedRulePixel(
            byte[] raw, int w, int h, int x, int y, StaffPitchTrack track, Budget budget) {
        if (x < 0 || x >= w || y < 0 || y >= h || (raw[y * w + x] & 255) >= 150) return false;
        int first = y, last = y, limit = Math.max(3, (int) Math.ceil(track.at(x)[1] * .24));
        if (!budget.take(limit * 2L + 4)) return false;
        while (first > 0 && y - first < limit && (raw[(first - 1) * w + x] & 255) < 150) first--;
        while (last < h - 1 && last - y < limit && (raw[(last + 1) * w + x] & 255) < 150) last++;
        return SeparatedRuleRunOwnership.matches(raw, w, h, x, first, last, track, budget);
    }

    /** Raw head and continuous down-shaft proofs may share a short collar with a staff rule. */
    private static boolean headCollarContains(
            PrintedSlurContours.HeadEvidence evidence,
            NoteSlideDetector.Head head,
            boolean downShaft,
            int x,
            int y) {
        if (evidence.contains(x, y)) return true;
        float gap = head.gap();
        return downShaft
                && Math.abs(x - (head.x() - gap * .6f)) <= gap * .2f
                && y >= head.y() - gap * .2f
                && y <= head.y() + gap * .8f;
    }

    /** A whole-tree leaf may connect only the unique preceding physical attack to its destination. */
    private static int previousEndpoint(
            List<NoteSlideDetector.Head> heads,
            List<Integer> localHeads,
            Map<Integer, Integer> leafOwners,
            Map<Integer, PrintedSlurContours.HeadEvidence> evidence,
            int owner,
            int left,
            int leftY,
            int right,
            int rightY) {
        var target = heads.get(owner);
        float gap = target.gap();
        double slope = (rightY - leftY) / (double) (right - left);
        if (right <= left
                || Math.abs(slope) < .15
                || Math.abs(slope) > 2
                || Math.abs(rightY - leftY) < gap * .25
                || Math.abs(rightY - leftY) > gap * 2.5) return -1;
        float latest = Float.NEGATIVE_INFINITY;
        for (var head : heads)
            if (head.staff() == target.staff()
                    && head.measure() == target.measure()
                    && head.x() < target.x() - gap * .35f) latest = Math.max(latest, head.x());
        int previous = -1;
        for (int i = 0; i < heads.size(); i++) {
            var head = heads.get(i);
            if (head.staff() == target.staff()
                    && head.measure() == target.measure()
                    && head.x() < target.x() - gap * .35f
                    && latest - head.x() <= gap * .35f) {
                if (previous >= 0) return -1;
                previous = i;
            }
        }
        if (previous < 0 || !localHeads.contains(previous) || !leafOwners.containsValue(previous))
            return -1;
        var source = heads.get(previous);
        if (left - source.x() < gap * .5
                || left - source.x() > gap * 3
                || (target.y() - source.y()) * slope <= 0) return -1;
        return supportedLineEndpoint(evidence.get(previous), source, left, leftY, slope, true)
                        && supportedLineEndpoint(
                                evidence.get(owner), target, left, leftY, slope, false)
                ? previous
                : -1;
    }

    private static boolean supportedLineEndpoint(
            PrintedSlurContours.HeadEvidence evidence,
            NoteSlideDetector.Head head,
            double originX,
            double originY,
            double slope,
            boolean rightSide) {
        float gap = head.gap();
        int first = Math.round(head.x() + (rightSide ? 0 : -gap * .72f));
        int last = Math.round(head.x() + (rightSide ? gap * .72f : 0));
        for (int x = first; x <= last; x++) {
            int y = (int) Math.round(originY + (x - originX) * slope);
            for (int dy = -(int) Math.ceil(gap * .15); dy <= Math.ceil(gap * .15); dy++)
                if (evidence.contains(x, y + dy)) return true;
        }
        return false;
    }

    /** Upper bound in pixel visits, cached head tests and owner/pixel visits. */
    static long freeApproachWorkBound(int pixels, int owners) {
        if (pixels < 0 || pixels > 16384 || owners < 1 || owners > 32) return Long.MAX_VALUE;
        return (long) owners + pixels + 2L * pixels * owners;
    }

    /** Negative work preflight only. These generous envelopes never own or erase raw pixels. */
    private static boolean possibleFreeApproach(
            int[] component,
            int count,
            int w,
            List<NoteSlideDetector.Head> heads,
            List<Integer> localHeads) {
        // This predicate depends only on the pixel and physical heads, never on the owner.
        boolean[] occupied = new boolean[count];
        for (int i = 0; i < count; i++) {
            int x = component[i] % w, y = component[i] / w;
            for (int index : localHeads) {
                var head = heads.get(index);
                float gap = head.gap();
                if (Math.abs(x - head.x()) <= gap * .85f && Math.abs(y - head.y()) <= gap * .65f
                        || Math.abs(x - (head.x() - gap * .6f)) <= gap * .2f
                        || Math.abs(x - (head.x() + gap * .6f)) <= gap * .2f) {
                    occupied[i] = true;
                    break;
                }
            }
        }
        for (int owner : localHeads) {
            var target = heads.get(owner);
            float gap = target.gap();
            int free = 0, left = w, right = 0;
            for (int i = 0; i < count; i++) {
                if (occupied[i]) continue;
                int x = component[i] % w, y = component[i] / w;
                if (x > target.x() - gap * .65f
                        || x < target.x() - gap * 3.5f
                        || y < target.y() - gap * 2.5f
                        || y > target.y() + gap * 2.5f) continue;
                free++;
                left = Math.min(left, x);
                right = Math.max(right, x);
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

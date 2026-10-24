// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Background/head/shaft roles only; never admits a curve or chooses its destination. */
final class ScoopAttachmentRoles {
    private ScoopAttachmentRoles() {}

    static boolean componentOwner(
            byte[] raw,
            int w,
            int h,
            int[] component,
            int count,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            List<Integer> local,
            Map<Integer, StaffPitchTrack> tracks,
            AttachedApproachBranches.Budget budget) {
        for (int owner : local) {
            var n = heads.get(owner);
            float g = n.gap();
            if (!budget.take((long) Math.ceil(g * g * 40 + g * 16 + 32 + w) + count * 12L))
                return false;
            if (PrintedStemDirection.detect(raw, w, h, n.x(), n.y(), g) != -1) continue;
            if (!tracks.containsKey(n.staff())) {
                var s = staffs.get(n.staff());
                tracks.put(
                        n.staff(),
                        StaffPitchTrack.forPrintedSlurs(raw, w, h, s.top(), s.bottom(), s.gap()));
            }
            var track = tracks.get(n.staff());
            if (track == null) continue;
            var e =
                    PrintedSlurOwners.headEvidence(
                            List.of(
                                    new PrintedSlurOwners.GeometryHead(
                                            owner, n.x(), n.y(), n.staff(), staffs.size())),
                            raw,
                            w,
                            h,
                            track);
            if (e.returningOwnerCost() != 1) continue;
            int[] shaft = DownFlagBranchOwnership.shaft(raw, w, h, n);
            for (int i = 0; i < count; i++) {
                int p = component[i], x = p % w, y = p / w;
                if (headPixel(e, n, true, x, y, raw, w, h)) return true;
                // ruleJoinedCurves can terminate anywhere on this complete raw-proved shaft.
                // Its bounds, not a short head collar, are the necessary contact envelope.
                if (shaft != null
                        && Math.abs(x - shaft[0]) <= Math.max(1, g * .2f) + 1
                        && y >= n.y() - g * .2f - 1
                        && y <= shaft[1] + 1) return true;
            }
        }
        return false;
    }

    static boolean headPixel(
            PrintedSlurContours.HeadEvidence evidence,
            NoteSlideDetector.Head head,
            boolean downShaft,
            int x,
            int y,
            byte[] raw,
            int w,
            int h) {
        if (x < 1 || x >= w - 1 || y < 1 || y >= h - 1 || (raw[y * w + x] & 255) >= 150)
            return false;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) if (evidence.contains(x + dx, y + dy)) return true;
        return false;
    }

    static boolean shaftPath(
            byte[] raw,
            int w,
            int h,
            List<Integer> path,
            java.util.function.IntUnaryOperator original,
            List<NoteSlideDetector.Head> heads,
            List<Integer> local,
            Set<Integer> downShafts,
            AttachedApproachBranches.Budget budget) {
        int owner = -1;
        for (int candidate : local) {
            if (!downShafts.contains(candidate)) continue;
            var head = heads.get(candidate);
            float g = head.gap();
            if (!budget.take((long) Math.ceil(g * g * 6) + path.size() * 4L)) return false;
            int[] shaft = DownFlagBranchOwnership.shaft(raw, w, h, head);
            if (shaft == null || path.size() > g * 5) continue;
            if (!budget.take((long) path.size() * local.size() * 8)) return false;
            boolean complete = true;
            for (int node : path) {
                int at = original.applyAsInt(node), x = at % w, y = at / w;
                if (Math.abs(x - shaft[0]) > Math.max(1, g * .2f) + 1
                        || y < head.y() + g * .15f
                        || y > shaft[1] + 1
                        || (raw[at] & 255) >= 150) {
                    complete = false;
                    break;
                }
                // A physical rival is not background merely because its stem opposes this shaft.
                for (int rival : local)
                    if (rival != candidate) {
                        var other = heads.get(rival);
                        double dx = (x - other.x()) / (other.gap() * .72 + 1),
                                dy = (y - other.y()) / (other.gap() * .55 + 1);
                        if (dx * dx + dy * dy <= 1) {
                            complete = false;
                            break;
                        }
                    }
                if (!complete) break;
            }
            if (!complete) continue;
            // Same conservative profile-work envelope as DownFlagBranchOwnership.leaves.
            if (!budget.take((long) Math.ceil(g * g * (g * 16 + 32)))) return false;
            if (!flagProfile(raw, w, h, head, shaft[0], shaft[1], g)) continue;
            if (owner >= 0) return false;
            owner = candidate;
        }
        return owner >= 0;
    }

    /** Identical raw returning-flag profile to DownFlagBranchOwnership; no curve threshold. */
    private static boolean flagProfile(
            byte[] raw, int w, int h, NoteSlideDetector.Head head, int sx, int end, float gap) {
        int left = Math.max(0, (int) Math.floor(sx + gap * .25f)),
                right = Math.min(w - 1, Math.round(sx + gap * 1.5f));
        int rows = 0, near = 0, bulge = 0, exterior = 0, roots = 0;
        for (int y = Math.max(0, Math.round(end - gap * 2.3f)); y <= end && y < h; y++) {
            if (Math.abs(y - head.y()) < gap * .65f
                    || FlagRootStaffRule.matches(raw, w, h, y, sx, gap, 150)) continue;
            int first = right + 1, last = left - 1;
            for (int x = left; x <= right; x++)
                if ((raw[y * w + x] & 255) < 150) {
                    if (last >= left && x - last > 2) break;
                    first = Math.min(first, x);
                    last = x;
                }
            if (last < first || Math.abs(y - end) < gap * .5f && first - sx > gap * .6f) continue;
            rows++;
            if (end - y <= gap * 1.45f) near++;
            if (end - y <= gap * 1.45f && first - sx <= gap * .5f) roots++;
            if (last - sx >= gap * .55f) bulge++;
            if (last >= right - 1) exterior++;
        }
        return roots >= Math.max(2, Math.round(gap * .16f))
                && rows >= Math.max(4, Math.round(gap * .65f))
                && near >= Math.max(2, Math.round(gap * .16f))
                && bulge >= Math.max(3, Math.round(gap * .3f))
                && exterior <= Math.max(1, Math.round(rows * .15f));
    }

    static boolean barlinePath(
            byte[] raw,
            int w,
            int h,
            List<Integer> path,
            java.util.function.IntUnaryOperator original,
            NoteSlideDetector.Staff staff,
            int lane,
            List<NoteSlideDetector.Head> heads,
            StaffPitchTrack track,
            AttachedApproachBranches.Budget budget,
            Set<Integer> barlinePixels) {
        if (path.size() < 3 || track == null || path.isEmpty()) return false;
        int first = original.applyAsInt(path.get(0)), sx = first % w, sy = first / w;
        if (!track.verifiedAt(sx)) return false;
        float[] frame = track.at(sx);
        float g = frame[1], bottom = frame[0], top = bottom - 4 * g;
        if (!Float.isFinite(g + top + bottom)
                || g < 6
                || g > 64
                || Math.abs(g - staff.gap()) > g * .15f
                || Math.min(Math.abs(sy - top), Math.abs(sy - bottom)) > g * .75f) return false;
        if (!budget.take(
                (long) Math.ceil(g * g * 8 + g * 12) + path.size() * 4L + heads.size() * 4L))
            return false;
        int leftMeasure = -1, rightMeasure = -1;
        float lx = -Float.MAX_VALUE, rx = Float.MAX_VALUE;
        for (var head : heads) {
            if (head.staff() != lane) continue;
            if (Math.abs(head.x() - sx) < g * .8f && head.y() > top - g && head.y() < bottom + g)
                return false;
            if (head.x() < sx && head.x() > lx) {
                lx = head.x();
                leftMeasure = head.measure();
            }
            if (head.x() > sx && head.x() < rx) {
                rx = head.x();
                rightMeasure = head.measure();
            }
        }
        if (leftMeasure < 0 || rightMeasure != leftMeasure + 1) return false;
        int prefix = 0;
        for (int node : path) {
            int p = original.applyAsInt(node);
            if (Math.abs(p % w - sx) > Math.max(1, g * .2f)
                    || p / w < top - g * .25f
                    || p / w > bottom + g * .25f) break;
            prefix++;
        }
        if (prefix < 3) return false;
        int hits = 0, total = 0;
        for (int y = Math.max(0, Math.round(top)); y <= Math.min(h - 1, Math.round(bottom)); y++) {
            boolean ink = false;
            for (int dx = -1; dx <= 1; dx++)
                if (sx + dx >= 0 && sx + dx < w && (raw[y * w + sx + dx] & 255) < 150) ink = true;
            total++;
            if (ink) hits++;
        }
        if (total < g * 3.8f || hits < total * .95f) return false;
        for (int x = Math.max(0, sx - (int) Math.ceil(g * .3f));
                x <= Math.min(w - 1, sx + (int) Math.ceil(g * .3f));
                x++) {
            int n = 0, k = 0;
            for (int y = Math.max(0, Math.round(top));
                    y <= Math.min(h - 1, Math.round(bottom));
                    y++) {
                k++;
                if ((raw[y * w + x] & 255) < 150) n++;
            }
            if (n < k * .95f) continue;
            for (int y = Math.max(0, Math.round(top));
                    y <= Math.min(h - 1, Math.round(bottom));
                    y++) if ((raw[y * w + x] & 255) < 150) barlinePixels.add(y * w + x);
        }
        // Retain the barline/curve junction; only the independently proved vertical prefix
        // contracts.
        if (prefix < path.size()) path.subList(prefix, path.size()).clear();
        return true;
    }

    static void withoutSeparateBarlineIslands(
            boolean[] chosen,
            boolean[] branch,
            int sw,
            int sh,
            int left,
            int top,
            byte[] raw,
            int w,
            Set<Integer> barlinePixels,
            AttachedApproachBranches.Budget budget) {
        if (barlinePixels.isEmpty() || !budget.take((long) sw * sh * 4)) return;
        for (int y = 0; y < sh; y++) {
            var runs = new ArrayList<int[]>();
            int meaningful = 0;
            for (int x = 0; x < sw; ) {
                if (!chosen[y * sw + x]) {
                    x++;
                    continue;
                }
                int start = x;
                boolean background = true;
                while (x < sw && chosen[y * sw + x]) {
                    int p = y * sw + x, at = (top + y - 2) * w + left + x - 2;
                    if (branch[p] || !barlinePixels.contains(at)) background = false;
                    x++;
                }
                runs.add(new int[] {start, x - 1, background ? 1 : 0});
                if (!background) meaningful++;
            }
            if (meaningful != 1) continue;
            for (int i = 0; i < runs.size(); i++)
                if (runs.get(i)[2] == 1) {
                    var r = runs.get(i);
                    boolean realPaper = false;
                    if (i > 0)
                        for (int x = runs.get(i - 1)[1] + 1; x < r[0]; x++)
                            realPaper |= (raw[(top + y - 2) * w + left + x - 2] & 255) >= 150;
                    if (i + 1 < runs.size())
                        for (int x = r[1] + 1; x < runs.get(i + 1)[0]; x++)
                            realPaper |= (raw[(top + y - 2) * w + left + x - 2] & 255) >= 150;
                    if (realPaper) for (int x = r[0]; x <= r[1]; x++) chosen[y * sw + x] = false;
                }
        }
    }
}

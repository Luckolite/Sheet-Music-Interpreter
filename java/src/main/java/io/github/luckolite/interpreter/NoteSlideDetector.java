// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Isolated approaches: complete straight strokes or finite curved strokes owned by one target. */
final class NoteSlideDetector {
    record Head(float x, float y, float gap, int staff, int measure) {}

    record Staff(float top, float bottom, float gap) {}

    record Stroke(
            float left,
            float leftY,
            float right,
            float rightY,
            int direction,
            int noteIndex,
            boolean connected) {}

    private NoteSlideDetector() {}

    static byte[] removeStaffLines(byte[] gray, int w, int h, List<Staff> staffs) {
        byte[] clean = gray.clone();
        // A diagonal survives a staff crossing when ink continues immediately above and below.
        for (var s : staffs)
            for (int line = 0; line < 5; line++) {
                int center = Math.round(s.top + line * s.gap), top = h, bottom = -1;
                int radius =
                        Float.isFinite(s.gap) && s.gap >= 3 && s.gap <= 64
                                ? Math.max(3, Math.min(16, (int) Math.ceil(s.gap * .24f)))
                                : 3;
                for (int y = Math.max(2, center - radius);
                        y <= Math.min(h - 3, center + radius);
                        y++) {
                    int count = 0;
                    for (int x = 0; x < w; x++) if ((gray[y * w + x] & 255) < 150) count++;
                    if (count > w * .45) {
                        top = Math.min(top, y);
                        bottom = Math.max(bottom, y);
                    }
                }
                if (bottom < top) continue;
                for (int x = 0; x < w; x++) {
                    boolean above = false, below = false;
                    for (int dx = -2; dx <= 2; dx++)
                        if (x + dx >= 0 && x + dx < w)
                            for (int dy = 1; dy <= 2; dy++) {
                                above |= (gray[(top - dy) * w + x + dx] & 255) < 150;
                                below |= (gray[(bottom + dy) * w + x + dx] & 255) < 150;
                            }
                    if (!above || !below)
                        for (int y = top; y <= bottom; y++) clean[y * w + x] = (byte) 255;
                }
            }
        return clean;
    }

    static List<Stroke> detect(byte[] gray, int w, int h, List<Staff> staffs, List<Head> heads) {
        return detectWithRemovedStaffLines(
                gray, w, h, staffs, heads, removeStaffLines(gray, w, h, staffs));
    }

    /** Consumes an owned staff-cleaned raster; original gray remains read-only. */
    static List<Stroke> detectWithRemovedStaffLines(
            byte[] gray, int w, int h, List<Staff> staffs, List<Head> heads, byte[] clean) {
        removeHeadLedgerLines(clean, gray, w, h, staffs, heads);
        boolean[] ink = new boolean[gray.length];
        for (int i = 0; i < ink.length; i++) ink[i] = (clean[i] & 255) < 150;
        int[] queue = new int[ink.length];
        List<Stroke> result = new ArrayList<>();
        List<Stroke> attachedApproaches = new ArrayList<>();
        var attachedBudget = new AttachedApproachBranches.Budget(64_000_000);
        var staffJoinedBudget = new AttachedApproachBranches.Budget(12_000_000);
        var compactGlyphInk = new HashSet<Integer>();
        List<Fragment> fragments = new ArrayList<>();
        Map<Integer, StaffPitchTrack> curveTracks = new HashMap<>();
        for (int pixel = 0; pixel < ink.length; pixel++) {
            if (!ink[pixel]) continue;
            int start = 0, end = 1;
            queue[0] = pixel;
            ink[pixel] = false;
            int l = w, r = 0, t = h, b = 0;
            double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
            while (start < end) {
                int at = queue[start++], x = at % w, y = at / w;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                sx += x;
                sy += y;
                sxx += (double) x * x;
                syy += (double) y * y;
                sxy += (double) x * y;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (ink[next]) {
                            ink[next] = false;
                            queue[end++] = next;
                        }
                    }
            }
            if (end < 8 || r - l < 3 || b - t < 5 || b - t > (r - l) * 5) continue;
            double cx = sx / end,
                    cy = sy / end,
                    vx = sxx / end - cx * cx,
                    vy = syy / end - cy * cy,
                    cov = sxy / end - cx * cy;
            if (vx <= 0 || vy <= 0) continue;
            double slope = cov / vx, correlation = cov / Math.sqrt(vx * vy);
            // Absolute residual accounts for the raster thickness of a very small slash.
            double residual =
                    Math.sqrt(
                            Math.max(
                                    0,
                                    (vx + vy - Math.sqrt((vx - vy) * (vx - vy) + 4 * cov * cov))
                                            / 2));
            var compact =
                    CompactApproachStrokes.find(
                            gray,
                            w,
                            h,
                            queue,
                            end,
                            l,
                            r,
                            t,
                            b,
                            cx,
                            cy,
                            slope,
                            correlation,
                            residual,
                            staffs,
                            heads,
                            curveTracks);
            if (compact != null) {
                boolean overlap = false;
                for (int i = 0; i < end; i++) overlap |= !compactGlyphInk.add(queue[i]);
                if (!overlap) result.add(compact);
                continue;
            }
            var joined =
                    StaffJoinedCompactApproach.find(
                            gray,
                            w,
                            h,
                            queue,
                            end,
                            l,
                            r,
                            t,
                            b,
                            staffs,
                            heads,
                            curveTracks,
                            staffJoinedBudget);
            if (joined != null) {
                boolean overlap = false;
                for (int joinedPixel : joined.pixels())
                    overlap |= !compactGlyphInk.add(joinedPixel);
                if (!overlap) result.add(joined.stroke());
            }
            if (r - l < 6) continue;
            // A nearly vertical raster has little horizontal variance, so its
            // stroke thickness lowers Pearson correlation. Both printed heads
            // are mandatory below for these steep strokes.
            if (Math.abs(correlation) >= .90
                    && residual <= 1.3
                    && Math.abs(slope) >= .08
                    && Math.abs(slope) <= 1.5)
                fragments.add(
                        new Fragment(
                                l,
                                r,
                                end,
                                sx,
                                sy,
                                sxx,
                                syy,
                                sxy,
                                slope,
                                cy + (l - cx) * slope,
                                cy + (r - cx) * slope));
            boolean steep = Math.abs(slope) > 1.5;
            if (Math.abs(correlation) < (steep ? .85 : .90)
                    || residual > (steep ? 1.4 : 1.3)
                    || Math.abs(slope) < .08
                    || Math.abs(slope) > 5) {
                Stroke curved =
                        finiteCurvedApproachBudgeted(
                                gray,
                                w,
                                h,
                                queue,
                                end,
                                l,
                                r,
                                t,
                                b,
                                cx,
                                cy,
                                slope,
                                staffs,
                                heads,
                                curveTracks,
                                attachedBudget);
                if (curved != null
                        && !PhraseOwnedSlideComponents.owned(
                                gray, w, h, staffs, heads, curved.noteIndex(), queue, end))
                    result.add(curved);
                if (curved == null && !attachedBudget.refused) {
                    var branches =
                            AttachedApproachBranches.find(
                                    gray,
                                    w,
                                    h,
                                    queue,
                                    end,
                                    staffs,
                                    heads,
                                    curveTracks,
                                    attachedBudget);
                    var accepted = new ArrayList<Stroke>();
                    for (var part : branches) {
                        var approach =
                                finiteCurvedApproachBudgeted(
                                        gray,
                                        w,
                                        h,
                                        part.pixels(),
                                        part.pixels().length,
                                        part.left(),
                                        part.right(),
                                        part.top(),
                                        part.bottom(),
                                        part.cx(),
                                        part.cy(),
                                        part.slope(),
                                        staffs,
                                        heads,
                                        curveTracks,
                                        attachedBudget);
                        if (approach == null && part.sourceIndex() >= 0)
                            approach = finiteConnectedStraight(part, w, heads);
                        if (approach != null
                                && approach.noteIndex() == part.owner()
                                && (approach.connected()
                                        || !PhraseOwnedSlideComponents.owned(
                                                gray,
                                                w,
                                                h,
                                                staffs,
                                                heads,
                                                approach.noteIndex(),
                                                part.pixels(),
                                                part.pixels().length))) accepted.add(approach);
                    }
                    if (accepted.size() == branches.size()) attachedApproaches.addAll(accepted);
                }
                continue;
            }
            double rightY = cy + (r - cx) * slope;
            int owner = -1;
            boolean ownerSourceProved = false;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < heads.size(); i++) {
                var n = heads.get(i);
                float g = n.gap;
                double dx = (n.x - r) / g, dy = (n.y - rightY) / g;
                // Long or very shallow ink is meaningful only between two uniquely
                // proved consecutive attacks. Keep isolated approach limits unchanged.
                boolean extended = r - l > g * 6 || Math.abs(slope) < .2;
                if (n.staff < 0
                        || dx < .5
                        || dx > 3
                        || Math.abs(dy) > 1
                        || r - l < g * .65
                        || r - l > g * 12
                        || (b - t < (r - l) * .2 && !extended)
                        || (extended
                                && !immediateSource(heads, n, l, cy + (l - cx) * slope, slope)))
                    continue;
                boolean touchesHead = false;
                for (int j = 0; j < heads.size(); j++)
                    if (j != i) {
                        var other = heads.get(j);
                        if (other.x > l - g * .6f
                                && other.x < r + g * .6f
                                && Math.abs(other.y - (cy + (other.x - cx) * slope)) < g * .6f) {
                            touchesHead = true;
                            break;
                        }
                    }
                if (touchesHead) continue;
                // Small note-to-note wavy glissandi may have a shallower overall
                // slope than an isolated approach slash. Their preceding printed
                // endpoint must agree with the line; a slur still fails residuals.
                if (Math.abs(slope) < .55 || Math.abs(slope) > 1.5 || r - l > g * 3.5) {
                    boolean prior = false;
                    double leftY = cy + (l - cx) * slope;
                    for (Head other : heads) {
                        if (other == n || other.staff != n.staff || other.measure != n.measure)
                            continue;
                        double distance = (l - other.x) / g;
                        if (distance >= .5
                                && distance <= 3
                                && Math.abs(other.y - leftY) < g
                                && (n.y - other.y) * slope > 0) prior = true;
                    }
                    if (!prior) continue;
                }
                double distance = dx + Math.abs(dy) * 2;
                if (distance < best) {
                    best = distance;
                    owner = i;
                    // Extended candidates already proved this exact immutable source lane.
                    ownerSourceProved = extended;
                }
            }
            if (owner >= 0) {
                var candidate =
                        new Stroke(
                                l,
                                (float) (cy + (l - cx) * slope),
                                r,
                                (float) rightY,
                                slope < 0 ? 1 : -1,
                                owner,
                                ownerSourceProved
                                        || immediateSource(
                                                heads,
                                                heads.get(owner),
                                                l,
                                                cy + (l - cx) * slope,
                                                slope));
                if (!candidate.connected()
                        && PhraseOwnedSlideComponents.owned(
                                gray, w, h, staffs, heads, owner, queue, end)) continue;
                result.add(candidate);
            }
        }
        if (!staffs.isEmpty()) {
            byte[] joined = joinStaffCrossings(clean, gray, w, h, staffs, heads, fragments, result);
            if (joined != null)
                for (var stroke : detect(joined, w, h, List.of(), heads))
                    if (result.stream().noneMatch(s -> s.noteIndex == stroke.noteIndex))
                        result.add(stroke);
        }
        if (!attachedBudget.refused)
            for (var approach : attachedApproaches)
                if (result.stream().noneMatch(old -> old.noteIndex() == approach.noteIndex()))
                    result.add(approach);
        return List.copyOf(result);
    }

    /** Validate complete branch-owned ink before emitting an exact previous-attack link. */
    private static Stroke finiteConnectedStraight(
            AttachedApproachBranches.Part part, int w, List<Head> heads) {
        if (part.owner() < 0
                || part.owner() >= heads.size()
                || part.sourceIndex() < 0
                || part.sourceIndex() >= heads.size()) return null;
        var target = heads.get(part.owner());
        var source = heads.get(part.sourceIndex());
        float gap = target.gap;
        // Equal-distance collar ink retains both independently proved roles. Fit only ink
        // strictly nearer the free branch, while retaining the complete part for ownership.
        int count = part.exclusivePixels().length;
        if (count < 8) return null;
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        for (int at : part.exclusivePixels()) {
            double x = at % w, y = at / w;
            sx += x;
            sy += y;
            sxx += x * x;
            syy += y * y;
            sxy += x * y;
        }
        double cx = sx / count,
                cy = sy / count,
                vx = sxx / count - cx * cx,
                vy = syy / count - cy * cy,
                cov = sxy / count - cx * cy;
        if (vx <= 0 || vy <= 0) return null;
        double slope = cov / vx, correlation = cov / Math.sqrt(vx * vy);
        double residual =
                Math.sqrt(
                        Math.max(
                                0,
                                (vx + vy - Math.sqrt((vx - vy) * (vx - vy) + 4 * cov * cov)) / 2));
        double leftY = cy + (part.left() - cx) * slope, rightY = cy + (part.right() - cx) * slope;
        if (Math.abs(slope) < .15
                || Math.abs(slope) > 2
                || Math.abs(correlation) < .90
                || residual > Math.max(1.3, gap * .075)
                || part.right() - part.left() < gap * .65
                || part.right() - part.left() > gap * 3.5
                || source.staff != target.staff
                || source.measure != target.measure
                || !immediateSource(heads, target, part.left(), leftY, slope)) return null;
        return new Stroke(
                part.left(),
                (float) leftY,
                part.right(),
                (float) rightY,
                slope < 0 ? 1 : -1,
                part.owner(),
                true);
    }

    private record Fragment(
            int left,
            int right,
            int pixels,
            double sx,
            double sy,
            double sxx,
            double syy,
            double sxy,
            double slope,
            double leftY,
            double rightY) {}

    /** Rejoin only original straight ink removed where it crosses a physical staff rule. */
    private static byte[] joinStaffCrossings(
            byte[] clean,
            byte[] gray,
            int w,
            int h,
            List<Staff> staffs,
            List<Head> heads,
            List<Fragment> fragments,
            List<Stroke> found) {
        byte[] joined = null;
        float maxGap = 0;
        for (var head : heads) maxGap = Math.max(maxGap, head.gap);
        for (var a : fragments)
            for (var b : fragments) {
                if (b.left <= a.right
                        || b.left - a.right > maxGap * 2
                        || Math.abs(a.rightY - b.leftY) > maxGap * 1.2
                        || Math.abs(a.slope - b.slope) > .08) continue;
                int pixels = a.pixels + b.pixels;
                double cx = (a.sx + b.sx) / pixels, cy = (a.sy + b.sy) / pixels;
                double vx = (a.sxx + b.sxx) / pixels - cx * cx,
                        vy = (a.syy + b.syy) / pixels - cy * cy;
                double cov = (a.sxy + b.sxy) / pixels - cx * cy;
                if (vx <= 0 || vy <= 0) continue;
                double slope = cov / vx;
                double residual =
                        Math.sqrt(
                                Math.max(
                                        0,
                                        (vx + vy - Math.sqrt((vx - vy) * (vx - vy) + 4 * cov * cov))
                                                / 2));
                if (Math.abs(cov / Math.sqrt(vx * vy)) < .90 || residual > 1.3) continue;
                for (int owner = 0; owner < heads.size(); owner++) {
                    var target = heads.get(owner);
                    float gap = target.gap;
                    if (target.staff < 0
                            || target.staff >= staffs.size()
                            || b.left - a.right > gap * 2
                            || b.right - a.left > gap * 12
                            || a.right - a.left < gap * .65
                            || b.right - b.left < gap * .65) continue;
                    boolean already = false;
                    for (var stroke : found) if (stroke.noteIndex == owner) already = true;
                    if (already) continue;
                    double dx = (target.x - b.right) / gap, rightY = cy + (b.right - cx) * slope;
                    if (dx < .5
                            || dx > 3
                            || Math.abs(target.y - rightY) > gap
                            || !immediateSource(
                                    heads, target, a.left, cy + (a.left - cx) * slope, slope))
                        continue;
                    var staff = staffs.get(target.staff);
                    boolean crossing = false;
                    for (int line = 0; line < 5; line++) {
                        float y = staff.top + line * staff.gap;
                        if ((a.rightY - y) * (b.leftY - y) < 0
                                && Math.abs(a.rightY - y) < gap * .6
                                && Math.abs(b.leftY - y) < gap * .6) crossing = true;
                    }
                    if (!crossing) continue;
                    int supported = 0, samples = 0;
                    for (int x = a.right + 1; x < b.left; x++) {
                        int y = (int) Math.round(cy + (x - cx) * slope);
                        if (x < 0 || x >= w || y < 1 || y >= h - 1) continue;
                        samples++;
                        if ((gray[(y - 1) * w + x] & 255) < 150
                                || (gray[y * w + x] & 255) < 150
                                || (gray[(y + 1) * w + x] & 255) < 150) supported++;
                    }
                    if (samples == 0 || supported < samples * .95) continue;
                    if (joined == null) joined = clean.clone();
                    for (int x = a.right + 1; x < b.left; x++) {
                        int y = (int) Math.round(cy + (x - cx) * slope);
                        if (x < 0 || x >= w || y < 1 || y >= h - 1) continue;
                        for (int dy = -1; dy <= 1; dy++)
                            if ((gray[(y + dy) * w + x] & 255) < 150)
                                joined[(y + dy) * w + x] = gray[(y + dy) * w + x];
                    }
                }
            }
        return joined;
    }

    /** A short ledger rule can join a genuine slide to its source head and stem. */
    /** A whole thin quarter-bow may be a printed unpitched approach even though it is not straight.
     * The original straight classifier remains unchanged. No start pitch or envelope is inferred. */
    private static Stroke finiteCurvedApproachBudgeted(
            byte[] gray,
            int w,
            int h,
            int[] pixels,
            int count,
            int left,
            int right,
            int top,
            int bottom,
            double cx,
            double cy,
            double slope,
            List<Staff> staffs,
            List<Head> heads,
            Map<Integer, StaffPitchTrack> tracks,
            AttachedApproachBranches.Budget budget) {
        var found =
                finiteCurvedApproach(
                        gray, w, h, pixels, count, left, right, top, bottom, cx, cy, slope, staffs,
                        heads, tracks);
        if (found != null) return found;
        if (budget.refused) return null;
        if (count < 8 || count > 4096 || right - left > 127 || bottom - top > 127) return null;
        // Staff frames are nominated by the full component bounds, before any destination is
        // chosen.
        int lane = -1;
        if (!budget.take(staffs.size() * 16L)) return null;
        for (int i = 0; i < staffs.size(); i++) {
            var staff = staffs.get(i);
            float gap = staff.gap();
            if (!Float.isFinite(staff.top() + staff.bottom() + gap) || gap < 6 || gap > 64)
                continue;
            if (top >= staff.top() - gap && bottom <= staff.bottom() + gap) {
                if (lane >= 0) return null;
                lane = i;
            }
        }
        if (lane < 0) return null;
        var track = curveTrack(gray, w, h, staffs, lane, tracks);

        int[] selected =
                SeparatedRuleRunOwnership.withoutSeparateRuleIslands(
                        gray, w, h, pixels, count, left, right, top, bottom, track, budget);
        if (selected == null || budget.refused || selected.length < 8 || selected.length >= count)
            return null;
        if (!budget.take(selected.length * 16L + heads.size() * 32L)) return null;
        int l = w, r = 0, t = h, b = 0;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int p : selected) {
            int x = p % w, y = p / w;
            l = Math.min(l, x);
            r = Math.max(r, x);
            t = Math.min(t, y);
            b = Math.max(b, y);
            sx += x;
            sy += y;
            sxx += (double) x * x;
            sxy += (double) x * y;
        }
        double ax = sx / selected.length,
                ay = sy / selected.length,
                vx = sxx / selected.length - ax * ax;
        if (vx <= 0) return null;
        int candidates = 0, foreign = 0;
        for (var possible : heads) {
            if (possible.staff() >= 0
                    && Float.isFinite(possible.x() + possible.y() + possible.gap())
                    && possible.gap() >= 3
                    && possible.gap() <= 64
                    && possible.x() >= r - 1
                    && possible.x() <= r + 193
                    && possible.y() <= t + 1) candidates++;
            // The classifier's largest permitted gap is64; include one extra pixel for rounding.
            if (curveBoxContains(possible, l, r, t, b, 64 * .6f + 1)) foreign++;
        }
        if (!budget.take(
                (long) (r - l + 1) * (b - t + 1) * 8L
                        + candidates * (r - l + 1) * 32L
                        + 4L * foreign * selected.length)) return null;
        var normalized =
                finiteCurvedApproach(
                        gray,
                        w,
                        h,
                        selected,
                        selected.length,
                        l,
                        r,
                        t,
                        b,
                        ax,
                        ay,
                        (sxy / selected.length - ax * ay) / vx,
                        staffs,
                        heads,
                        tracks);
        if (normalized == null) return null;
        var owner = heads.get(normalized.noteIndex());
        if (owner.staff() != lane
                || !budget.take(
                        (long) Math.ceil(owner.gap() * owner.gap() * 38 + owner.gap() * 16)))
            return null;
        var body =
                PrintedSlurOwners.headEvidence(
                        List.of(
                                new PrintedSlurOwners.GeometryHead(
                                        normalized.noteIndex(),
                                        owner.x(),
                                        owner.y(),
                                        lane,
                                        staffs.size())),
                        gray,
                        w,
                        h,
                        track);
        if (!body.contains(Math.round(owner.x()), Math.round(owner.y()))
                || PrintedStemDirection.detect(gray, w, h, owner.x(), owner.y(), owner.gap()) != -1
                || DownFlagBranchOwnership.shaft(gray, w, h, owner) == null) return null;
        return normalized;
    }

    private static Stroke finiteCurvedApproach(
            byte[] gray,
            int w,
            int h,
            int[] pixels,
            int count,
            int left,
            int right,
            int top,
            int bottom,
            double cx,
            double cy,
            double slope,
            List<Staff> staffs,
            List<Head> heads,
            Map<Integer, StaffPitchTrack> tracks) {
        int columns = right - left + 1, rows = bottom - top + 1;
        if (count > 4096
                || columns > 128
                || rows > 128
                || !Double.isFinite(slope)
                || slope >= 0
                || (long) count * heads.size() > 1000000) return null;
        int[] lo = new int[columns], hi = new int[columns], size = new int[columns];
        int[] rowLo = new int[rows], rowHi = new int[rows], rowSize = new int[rows];
        Arrays.fill(lo, h);
        Arrays.fill(rowLo, w);
        for (int i = 0; i < count; i++) {
            int x = pixels[i] % w, y = pixels[i] / w, j = x - left, k = y - top;
            lo[j] = Math.min(lo[j], y);
            hi[j] = Math.max(hi[j], y);
            size[j]++;
            rowLo[k] = Math.min(rowLo[k], x);
            rowHi[k] = Math.max(rowHi[k], x);
            rowSize[k]++;
        }
        for (int x = 0; x < columns; x++)
            if (size[x] == 0 || size[x] != hi[x] - lo[x] + 1) return null;
        for (int y = 0; y < rows; y++)
            if (rowSize[y] == 0 || rowSize[y] != rowHi[y] - rowLo[y] + 1) return null;
        float[] centers = new float[columns];
        for (int x = 0; x < columns; x++) centers[x] = (lo[x] + hi[x]) * .5f;
        for (int x = 1; x < columns; x++) if (centers[x] > centers[x - 1] + 1) return null;
        int owner = -1;
        for (int n = 0; n < heads.size(); n++) {
            Head target = heads.get(n);
            float gap = target.gap;
            if (target.staff < 0
                    || !Float.isFinite(target.x + target.y + gap)
                    || gap < 3
                    || gap > 64
                    || columns < gap * .65f
                    || columns > gap * 3.5f
                    || rows < gap * .6f
                    || rows > gap * 2.5f
                    || count > columns * rows * .6f
                    || target.y >= top
                    || target.x - right < gap * .5f
                    || target.x - right > gap * 3) continue;
            float[] bins = new float[3];
            int[] binsN = new int[3];
            for (int x = 0; x < columns; x++) {
                int bin = Math.min(2, x * 3 / columns);
                bins[bin] += centers[x];
                binsN[bin]++;
            }
            if (binsN[0] == 0 || binsN[1] == 0 || binsN[2] == 0) continue;
            for (int j = 0; j < 3; j++) bins[j] /= binsN[j];
            if (bins[0] - bins[1] < gap * .08f
                    || bins[1] - bins[2] < gap * .25f
                    || bins[1] - (bins[0] + bins[2]) * .5f < gap * .08f) continue;
            int checked = 0, thin = 0;
            for (int x = 2; x < columns - 2; x++) {
                double tangent = (centers[x + 2] - centers[x - 2]) * .25;
                checked++;
                if (size[x] / Math.sqrt(1 + tangent * tangent) <= Math.max(3, gap * .35f)) thin++;
            }
            if (checked < gap * .7f || thin < checked * .85f) continue;
            if (!approachSector(target.x, target.y, right, centers)) {
                StaffPitchTrack track = curveTrack(gray, w, h, staffs, target.staff, tracks);
                if (track == null) continue;
                float actualGap = track.at(target.x)[1];
                if (!Float.isFinite(actualGap)
                        || actualGap < 3
                        || actualGap > 64
                        || !possibleSupportedSector(target.x, target.y, right, centers, actualGap))
                    continue;
            }
            // Topology must nominate one principal before raw-body queries or
            // foreign-head checks; neither may choose one of competing destinations.
            if (owner >= 0) return null;
            owner = n;
        }
        if (owner < 0) return null;
        Head target = heads.get(owner);
        float gap = target.gap;
        if (!approachSector(target.x, target.y, right, centers)
                && !supportedDestination(gray, w, h, staffs, heads, owner, right, centers, tracks))
            return null;
        for (int other = 0; other < heads.size(); other++)
            if (other != owner) {
                Head a = heads.get(other);
                // This negative box test cannot own or remove ink; exact pixel checks still
                // decide every physically overlapping head. Duplicate-destination checks follow.
                if (curveBoxContains(a, left, right, top, bottom, gap * .6f + 1))
                    for (int i = 0; i < count; i++)
                        if (Math.abs(a.x - pixels[i] % w) < gap * .6f
                                && Math.abs(a.y - pixels[i] / w) < gap * .6f) return null;
                if (a.staff == target.staff
                        && a.measure == target.measure
                        && Math.abs(a.x - target.x) <= gap * .35f
                        && Math.abs(a.y - target.y) <= gap * .35f) return null;
            }
        return new Stroke(left, centers[0], right, centers[columns - 1], 1, owner, false);
    }

    private static boolean curveBoxContains(
            Head head, int left, int right, int top, int bottom, float radius) {
        return !(head.x() < left - radius
                || head.x() > right + radius
                || head.y() < top - radius
                || head.y() > bottom + radius);
    }

    private static boolean approachSector(double x, double y, int right, float[] centers) {
        int columns = centers.length, terminal = Math.max(1, columns / 3);
        double dx = x - right, dy = y - centers[columns - 1];
        double chord = centers[columns - 1] - centers[0];
        double tail = centers[columns - 1] - centers[columns - 1 - terminal];
        return dx > 0 && dy * (columns - 1) <= chord * dx && dy * terminal >= tail * dx;
    }

    /** Cheap negative preflight only: every allowed issuer body lies in this envelope.
     * An overlapping envelope never owns a pixel; the actual raw issuer is still required. */
    private static boolean possibleSupportedSector(
            double x, double y, int right, float[] centers, float gap) {
        int columns = centers.length, terminal = Math.max(1, columns / 3);
        double lower = (centers[columns - 1] - centers[columns - 1 - terminal]) / (double) terminal;
        double upper = (centers[columns - 1] - centers[0]) / (double) (columns - 1);
        if (lower > upper) return false;
        // This is the actual raw-verified track gap used by the existing head issuer.
        double first = Math.max(1, x - .72 * gap - right), last = x + .72 * gap - right;
        double lowY = y - .55 * gap - centers[columns - 1],
                highY = y + .55 * gap - centers[columns - 1];
        if (lower > 0) last = Math.min(last, highY / lower);
        else if (lower < 0) first = Math.max(first, highY / lower);
        else if (highY < 0) return false;
        if (upper > 0) first = Math.max(first, lowY / upper);
        else if (upper < 0) last = Math.min(last, lowY / upper);
        else if (lowY > 0) return false;
        return first <= last;
    }

    /** Cache only within this exact gray/staff invocation; null retains a failed raw proof. */
    private static StaffPitchTrack curveTrack(
            byte[] gray,
            int w,
            int h,
            List<Staff> staffs,
            int index,
            Map<Integer, StaffPitchTrack> tracks) {
        if (index < 0 || index >= staffs.size()) return null;
        if (tracks.containsKey(index)) return tracks.get(index);
        Staff staff = staffs.get(index);
        if (!Float.isFinite(staff.top + staff.bottom + staff.gap)
                || staff.gap < 3
                || staff.gap > 64
                || staff.bottom <= staff.top) return null;
        StaffPitchTrack track =
                StaffPitchTrack.forPrintedSlurs(gray, w, h, staff.top, staff.bottom, staff.gap);
        tracks.put(index, track);
        return track;
    }

    /** Reuse the existing raw supported head issuer; exterior paper/ink is not head extent. */
    private static boolean supportedDestination(
            byte[] gray,
            int w,
            int h,
            List<Staff> staffs,
            List<Head> heads,
            int owner,
            int right,
            float[] centers,
            Map<Integer, StaffPitchTrack> tracks) {
        Head target = heads.get(owner);
        if (target.staff < 0
                || target.staff >= staffs.size()
                || target.x < 0
                || target.x >= w
                || target.y < 0
                || target.y >= h) return false;
        StaffPitchTrack track = curveTrack(gray, w, h, staffs, target.staff, tracks);
        if (track == null) return false;
        var evidence =
                PrintedSlurOwners.headEvidence(
                        List.of(
                                new PrintedSlurOwners.GeometryHead(
                                        owner, target.x, target.y, target.staff, staffs.size())),
                        gray,
                        w,
                        h,
                        track);
        float gap = track.at(target.x)[1];
        if (!Float.isFinite(gap) || gap < 3 || gap > 64) return false;
        for (int y = Math.max(0, Math.round(target.y - gap * .55f));
                y < h && y <= target.y + gap * .55f;
                y++)
            for (int x = Math.max(0, Math.round(target.x - gap * .72f));
                    x < w && x <= target.x + gap * .72f;
                    x++)
                if (evidence.contains(x, y) && approachSector(x, y, right, centers)) return true;
        return false;
    }

    private static void removeHeadLedgerLines(
            byte[] clean, byte[] gray, int w, int h, List<Staff> staffs, List<Head> heads) {
        for (var head : heads) {
            if (head.staff < 0 || head.staff >= staffs.size()) continue;
            var staff = staffs.get(head.staff);
            float gap = head.gap;
            int direction =
                    head.y < staff.top - gap * .25f
                            ? -1
                            : head.y > staff.bottom + gap * .25f ? 1 : 0;
            if (direction == 0) continue;
            float edge = direction < 0 ? staff.top : staff.bottom;
            int left = Math.max(0, Math.round(head.x - gap * 1.4f));
            int right = Math.min(w - 1, Math.round(head.x + gap * 1.4f));
            for (int line = 1; line <= 6; line++) {
                int center = Math.round(edge + line * gap * direction);
                if (Math.abs(center - head.y) > gap * 1.25f) continue;
                int top = h, bottom = -1;
                for (int y = Math.max(2, center - 3); y <= Math.min(h - 3, center + 3); y++) {
                    int count = 0;
                    for (int x = left; x <= right; x++) if ((gray[y * w + x] & 255) < 150) count++;
                    if (count >= gap * 1.7f) {
                        top = Math.min(top, y);
                        bottom = Math.max(bottom, y);
                    }
                }
                if (bottom < top || bottom - top + 1 > Math.max(2, gap * .25f)) continue;
                // Preserve the source shaft and diagonal crossings using original ink.
                for (int x = left; x <= right; x++) {
                    boolean above = false, below = false;
                    for (int dx = -2; dx <= 2; dx++)
                        if (x + dx >= 0 && x + dx < w)
                            for (int dy = 1; dy <= 2; dy++) {
                                above |= (gray[(top - dy) * w + x + dx] & 255) < 150;
                                below |= (gray[(bottom + dy) * w + x + dx] & 255) < 150;
                            }
                    if (!above || !below)
                        for (int y = top; y <= bottom; y++) clean[y * w + x] = (byte) 255;
                }
            }
        }
    }

    /** A printed two-note glide starts at its unique immediate attack, not a scale neighbor. */
    private static boolean immediateSource(
            List<Head> heads, Head target, float left, double leftY, double slope) {
        float gap = target.gap, latest = -Float.MAX_VALUE;
        for (var head : heads)
            if (head.staff == target.staff
                    && head.measure == target.measure
                    && head.x < target.x - gap * .35f) latest = Math.max(latest, head.x);
        Head source = null;
        for (var head : heads)
            if (head.staff == target.staff
                    && head.measure == target.measure
                    && head.x < target.x - gap * .35f
                    && latest - head.x <= gap * .35f) {
                // The existing wire stores "previous attack", not one selected head of a chord.
                if (source != null) return false;
                source = head;
            }
        if (source == null) return false;
        double distance = (left - source.x) / gap;
        return distance >= .5
                && distance <= 3
                && Math.abs(source.y - leftY) < gap
                && (target.y - source.y) * slope > 0;
    }

    /** Verify the connecting line before accepting an OCR portamento word (or a clipped p/t). */
    static boolean connection(byte[] gray, int w, int h, Head from, Head to) {
        float g = to.gap, span = to.x - from.x;
        if (span < g * 4 || span > g * 30 || Math.abs(to.y - from.y) > g * 4) return false;
        for (float li = 1; li <= 2; li += .5f)
            for (float ri = 1; ri <= 2; ri += .5f) {
                int left = Math.round(from.x + g * li),
                        right = Math.round(to.x - g * ri),
                        samples = right - left;
                if (samples < g * 2) continue;
                for (float a = -.6f; a <= .61f; a += .2f)
                    for (float b = -.6f; b <= .61f; b += .2f) {
                        int hits = 0;
                        for (int x = left; x < right; x++) {
                            float t = (x - from.x) / span;
                            int y = Math.round((from.y + g * a) * (1 - t) + (to.y + g * b) * t);
                            if (x < 0 || x >= w || y < 1 || y >= h - 1) continue;
                            if ((gray[(y - 1) * w + x] & 255) < 150
                                    || (gray[y * w + x] & 255) < 150
                                    || (gray[(y + 1) * w + x] & 255) < 150) hits++;
                        }
                        if (hits >= samples * .90f) return true;
                    }
            }
        return false;
    }
}

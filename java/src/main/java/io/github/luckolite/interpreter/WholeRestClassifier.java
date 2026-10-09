// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Conservative hanging-plate proof precedes full-measure silence. */
final class WholeRestClassifier {
    enum Status {
        PROVED_FULL,
        PROVED_LITERAL,
        UNRESOLVED
    }

    enum Support {
        SECOND_RULE,
        RAISED_LEDGER
    }

    record Decision(
            ScoreRestEvent event,
            Status status,
            String reason,
            Support support,
            List<SixteenthRestDetector.InkDot> dots) {}

    private record Plate(
            int left, int right, int rule, int first, int last, float gap, Support support) {}

    /**
     * This proves symbol identity, not performed duration or pickup span. Those remain the
     * caller's independently established context. Legacy records never seed a raw plate.
     */
    static List<Decision> detect(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<SixteenthRestDetector.Staff> staffs,
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> otherRests) {
        if (gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != gray.length
                || measures == null
                || staffs == null
                || notes == null
                || otherRests == null) return List.of();
        var printed = RawStaffLineDetector.detect(gray, width, height);
        var result = new ArrayList<Decision>();
        for (var staff : staffs) {
            if (staff == null
                    || staff.count() <= 0
                    || staff.index() < 0
                    || staff.index() >= staff.count()
                    || !Float.isFinite(staff.top())
                    || !Float.isFinite(staff.gap())
                    || staff.gap() < 6) continue;
            RawStaffLineDetector.StaffLines lines = null;
            for (int i = 0; i < printed.size(); i++) {
                var p = printed.get(i);
                if (i % staff.count() != staff.index()
                        || p.gap() < staff.gap() * .65f
                        || p.gap() > staff.gap() * 1.35f
                        || Math.abs(p.top() - staff.top()) > staff.gap() * 1.4f) continue;
                if (lines == null
                        || Math.abs(p.top() - staff.top()) < Math.abs(lines.top() - staff.top()))
                    lines = p;
            }
            if (lines == null) continue;
            float gap = lines.gap();
            for (int mi = 0; mi < measures.size(); mi++) {
                var m = measures.get(mi);
                if (!BeamedHeadQuarterRests.validMeasure(m)
                        || lines.top() < m.top() * height
                        || lines.bottom() > m.bottom() * height) continue;
                int lo = Math.max(1, (int) Math.ceil(m.left() * width));
                int hi = Math.min(width - 2, (int) Math.floor(m.right() * width));
                var plates = new ArrayList<Plate>();
                scanSecondRule(gray, width, height, lo, hi, lines.top(), gap, plates);
                scanRaised(gray, width, height, lo, hi, lines.top(), gap, plates);
                for (var plate : plates) {
                    if (ownedByHead(plate, mi, staff, m, width, height, notes)) continue;
                    var dotStaff =
                            new SixteenthRestDetector.Staff(
                                    plate.rule() - gap,
                                    plate.rule() + 3 * gap,
                                    gap,
                                    staff.index(),
                                    staff.count());
                    // Existing body/dot geometry is retained; its broad note-x veto is replaced
                    // by a dot/head/dotted-note ownership test at the actual vertical band.
                    var rawDots =
                            SixteenthRestDetector.augmentationDots(
                                    gray, width, height, dotStaff, plate.right(), m, List.of(), mi);
                    var dots = new ArrayList<SixteenthRestDetector.InkDot>();
                    boolean uncertainDot = false;
                    for (var dot : rawDots) {
                        int owner = dotOwner(dot, gap, mi, staff, m, width, height, notes);
                        if (owner == 0) dots.add(dot);
                        else if (owner < 0) uncertainDot = true;
                    }
                    // Failure to trace a dot is not proof of an undotted glyph. Faint, clipped,
                    // irregular or connected marks in the augmentation corridor stay ambiguous.
                    uncertainDot |= unaccountedDotInk(gray, width, height, plate, m, rawDots);
                    double duration = dots.isEmpty() ? 4 : dots.size() == 1 ? 6 : 7;
                    float cx = (plate.left() + plate.right()) * .5f / width;
                    float cy = (plate.first() + plate.last()) * .5f / height;
                    float pos = (cx - m.left()) / (m.right() - m.left());
                    var literal =
                            new ScoreRestEvent(
                                    mi,
                                    pos,
                                    cy,
                                    (plate.last() - plate.first() + 1f) / height,
                                    staff.index(),
                                    staff.count(),
                                    duration);
                    Status status = dots.isEmpty() ? Status.UNRESOLVED : Status.PROVED_LITERAL;
                    String reason =
                            dots.isEmpty()
                                    ? "noncentered whole glyph; full-bar versus literal semantics unresolved"
                                    : "owned augmentation dots";
                    if (uncertainDot) {
                        status = Status.UNRESOLVED;
                        reason = "unresolved nearby dot ownership or ink";
                    }
                    float tolerance =
                            Math.min(
                                    .085f,
                                    Math.max(.035f, gap * .65f / ((m.right() - m.left()) * width)));
                    if (dots.isEmpty() && Math.abs(pos - .5f) <= tolerance) {
                        String conflict =
                                conflictReason(
                                        plate,
                                        literal,
                                        mi,
                                        staff,
                                        m,
                                        width,
                                        height,
                                        notes,
                                        otherRests);
                        if (uncertainDot) {
                            status = Status.UNRESOLVED;
                            reason = "unresolved nearby dot ownership";
                        } else if (conflict != null) {
                            status = Status.UNRESOLVED;
                            reason = conflict;
                        } else {
                            status = Status.PROVED_FULL;
                            reason = "centered undotted complete plate and silent lane";
                        }
                    }
                    var event =
                            status == Status.PROVED_FULL
                                    ? ScoreRestEvent.fullMeasure(
                                            mi,
                                            pos,
                                            cy,
                                            literal.pageHeight(),
                                            staff.index(),
                                            staff.count())
                                    : literal;
                    result.add(
                            new Decision(
                                    event, status, reason, plate.support(), List.copyOf(dots)));
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean unaccountedDotInk(
            byte[] g,
            int w,
            int h,
            Plate p,
            MeasureRegion m,
            List<SixteenthRestDetector.InkDot> dots) {
        int l = p.right() + Math.max(2, Math.round(p.gap() * .12f));
        int r = Math.min(Math.round(m.right() * w) - 1, p.right() + Math.round(p.gap() * 2.4f));
        int top = Math.max(p.first() + 1, p.rule() + Math.max(2, Math.round(p.gap() * .15f)));
        int bottom = p.rule() + Math.round(p.gap() * .85f);
        if (l < 0 || r >= w || top < 0 || bottom >= h || l >= r || top >= bottom) return true;
        for (int y = top; y <= bottom; y++)
            for (int x = l; x <= r; x++) {
                if ((g[y * w + x] & 255) >= 225) continue;
                boolean accounted = false;
                for (var dot : dots)
                    if (Math.abs(dot.x() - x) <= p.gap() * .45f
                            && Math.abs(dot.y() - y) <= p.gap() * .45f) {
                        accounted = true;
                        break;
                    }
                if (!accounted) return true;
            }
        return false;
    }

    private static String conflictReason(
            Plate p,
            ScoreRestEvent candidate,
            int mi,
            SixteenthRestDetector.Staff staff,
            MeasureRegion m,
            int w,
            int h,
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> rests) {
        for (var r : rests) {
            if (r == null
                    || r.measureIndex() != mi
                    || r.staffIndex() != staff.index()
                    || r.staffCount() != staff.count()) continue;
            // A previously decoded copy of this exact plate is not independent fresh evidence.
            if (Math.abs(r.positionInMeasure() - candidate.positionInMeasure()) <= .02f
                    && Math.abs(r.pageY() - candidate.pageY()) <= p.gap() * .4f / h
                    && r.durationBeats() == candidate.durationBeats()) continue;
            return "other same-staff rest has no proved independent voice";
        }
        for (var n : notes) {
            if (n == null
                    || n.measureIndex() != mi
                    || n.staffIndex() != staff.index()
                    || n.staffCount() != staff.count()) continue;
            if (!Float.isFinite(n.pageY())
                    || !Float.isFinite(n.positionInMeasure())
                    || n.positionInMeasure() < 0
                    || n.positionInMeasure() > 1) return "invalid note geometry";
            // This limited optical assertion identifies an upper silent lane over an entirely
            // lower, down-stem lane. Mixed/unknown/up-stem/cross-staff evidence is ambiguous.
            if (n.stemDirection() != -1
                    || n.crossStaffBeam()
                    || n.pageY() * h < p.last() + p.gap() * 1.1f)
                return "sounding lane not proved independent and below";
        }
        return null;
    }

    /** 1 = owned printed note dot; -1 = proximity without reliable ownership; 0 = rest dot. */
    private static int dotOwner(
            SixteenthRestDetector.InkDot dot,
            float gap,
            int mi,
            SixteenthRestDetector.Staff staff,
            MeasureRegion m,
            int w,
            int h,
            List<ScoreNoteEvent> notes) {
        for (var n : notes) {
            if (n == null
                    || n.measureIndex() != mi
                    || n.staffIndex() != staff.index()
                    || n.staffCount() != staff.count()) continue;
            float nx = (m.left() + n.positionInMeasure() * (m.right() - m.left())) * w;
            float ny = n.pageY() * h;
            if (!Float.isFinite(nx) || !Float.isFinite(ny)) return -1;
            if (dot.x() - nx < gap * .25f
                    || dot.x() - nx > gap * 1.4f
                    || Math.abs(dot.y() - ny) > gap * .55f) continue;
            return n.augmentationDots() > 0 ? 1 : -1;
        }
        return 0;
    }

    private static boolean ownedByHead(
            Plate p,
            int mi,
            SixteenthRestDetector.Staff staff,
            MeasureRegion m,
            int w,
            int h,
            List<ScoreNoteEvent> notes) {
        for (var n : notes) {
            if (n == null
                    || n.measureIndex() != mi
                    || n.staffIndex() != staff.index()
                    || n.staffCount() != staff.count()) continue;
            float nx = (m.left() + n.positionInMeasure() * (m.right() - m.left())) * w;
            float ny = n.pageY() * h;
            if (!Float.isFinite(nx) || !Float.isFinite(ny)) return true;
            if (nx >= p.left() - p.gap() * .6f
                    && nx <= p.right() + p.gap() * .6f
                    && ny >= p.rule() - p.gap() * .7f
                    && ny <= p.last() + p.gap() * .7f) return true;
        }
        return false;
    }

    private static void scanSecondRule(
            byte[] g, int w, int h, int lo, int hi, float staffTop, float gap, List<Plate> out) {
        int rule = Math.round(staffTop + gap);
        if (rule < 2 || rule + gap >= h || ink(g, w, lo, hi, rule) < (hi - lo + 1) * .80f) return;
        int bottom = rule;
        while (bottom + 1 < h
                && bottom - rule < gap * .3f
                && ink(g, w, lo, hi, bottom + 1) >= (hi - lo + 1) * .80f) bottom++;
        int first = bottom + 1;
        for (int x = lo; x <= hi; x++) {
            if (!dark(g, w, x, first) || dark(g, w, x - 1, first)) continue;
            int right = x;
            while (right < hi && dark(g, w, right + 1, first)) right++;
            int left = x;
            x = right;
            Plate p =
                    completePlate(
                            g, w, h, lo, hi, left, right, rule, first, gap, Support.SECOND_RULE);
            if (p == null) continue;
            int arms = Math.round(gap * .5f);
            if (ink(g, w, left - arms, right + arms, rule) < (right - left + 1 + 2 * arms) * .90f)
                continue;
            if (ink(g, w, left - arms, right + arms, rule - Math.max(1, Math.round(gap * .2f))) > 1)
                continue;
            out.add(p);
        }
    }

    private static void scanRaised(
            byte[] g, int w, int h, int lo, int hi, float top, float gap, List<Plate> out) {
        for (int y = Math.max(2, Math.round(top - gap * 1.4f));
                y <= Math.min(h - 3, Math.round(top - gap * .65f));
                y++) {
            for (int x = lo; x <= hi; x++) {
                if (!dark(g, w, x, y) || dark(g, w, x - 1, y)) continue;
                int right = x;
                while (right < hi && dark(g, w, right + 1, y)) right++;
                int left = x;
                x = right;
                int runWidth = right - left + 1;
                if (runWidth < gap * 2 || runWidth > gap * 3.7f || left <= lo || right >= hi)
                    continue;
                int supportBottom = y;
                while (supportBottom + 1 < h
                        && supportBottom - y < gap * .25f
                        && ink(g, w, left, right, supportBottom + 1) >= runWidth * .90f)
                    supportBottom++;
                if (supportBottom - y + 1 > Math.max(2, Math.ceil(gap * .30f))) continue;
                int first = supportBottom + 1, seed = (left + right) / 2;
                if (!dark(g, w, seed, first)) continue;
                int l = seed, r = seed;
                while (l > lo && dark(g, w, l - 1, first)) l--;
                while (r < hi && dark(g, w, r + 1, first)) r++;
                if (l - left < gap * .25f
                        || right - r < gap * .25f
                        || Math.abs(l + r - left - right) > gap * .3f) continue;
                var p = completePlate(g, w, h, lo, hi, l, r, y, first, gap, Support.RAISED_LEDGER);
                if (p == null || p.last() >= top - gap * .2f) continue;
                boolean clear = ink(g, w, left - 1, right + 1, y - 1) <= 1;
                for (int row = first; row <= p.last(); row++)
                    clear &=
                            ink(g, w, left - 1, l - 1, row) <= 1
                                    && ink(g, w, r + 1, right + 1, row) <= 1;
                if (clear
                        && out.stream()
                                .noneMatch(
                                        a ->
                                                Math.abs(a.left() - p.left()) < gap * .2f
                                                        && Math.abs(a.first() - p.first())
                                                                < gap * .2f)) out.add(p);
            }
        }
    }

    private static Plate completePlate(
            byte[] g,
            int w,
            int h,
            int lo,
            int hi,
            int l,
            int r,
            int rule,
            int first,
            float gap,
            Support support) {
        int bodyWidth = r - l + 1;
        if (bodyWidth < gap * .8f || bodyWidth > gap * 1.9f) return null;
        int last = first;
        while (last + 1 < h
                && last - first < gap * .8f
                && ink(g, w, l, r, last + 1) >= bodyWidth * .85f) last++;
        int depth = last - first + 1, margin = Math.max(2, Math.round(gap * .25f));
        if (depth < Math.max(3, Math.round(gap * .23f))
                || depth > gap * .65f
                || l - margin <= lo
                || r + margin >= hi
                || last + margin >= h) return null;
        for (int row = first; row <= last; row++)
            if (ink(g, w, l - margin, l - 1, row) > 1
                    || ink(g, w, r + 1, r + margin, row) > 1
                    || ink(g, w, l, r, row) < bodyWidth * .85f) return null;
        for (int row = last + 1; row <= last + margin; row++)
            if (ink(g, w, l - margin, r + margin, row) > 1) return null;
        return new Plate(l, r, rule, first, last, gap, support);
    }

    private static boolean dark(byte[] g, int w, int x, int y) {
        return (g[y * w + x] & 255) < 170;
    }

    private static int ink(byte[] g, int w, int l, int r, int y) {
        if (l < 0 || r >= w || y < 0 || (long) (y + 1) * w > g.length) return Integer.MAX_VALUE;
        int n = 0;
        for (int x = l; x <= r; x++) if (dark(g, w, x, y)) n++;
        return n;
    }
}

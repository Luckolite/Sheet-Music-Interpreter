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
        return detect(gray, width, height, measures, staffs, notes, otherRests, java.util.Set.of());
    }

    static List<Decision> detect(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<SixteenthRestDetector.Staff> staffs,
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> otherRests,
            java.util.Set<Integer> shortBars) {
        return detect(
                gray, width, height, measures, staffs, notes, otherRests, shortBars, List.of());
    }

    /** Crops must come from accepted, conflict-free meter readings, never bare candidates. */
    static List<Decision> detect(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<SixteenthRestDetector.Staff> staffs,
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> otherRests,
            java.util.Set<Integer> shortBars,
            List<MeterChangeDetector.Crop> verifiedHeaders) {
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
                // Anchor the hanging body to the detected rule rather than averaged spacing.
                scanSecondRule(
                        gray, width, height, lo, hi, lines.rows()[1] - gap, gap, plates);
                scanRaised(gray, width, height, lo, hi, lines.top(), gap, plates);
                for (var plate : plates) {
                    float plateGap = plate.gap();
                    if (ownedByHead(plate, mi, staff, m, width, height, notes)) continue;
                    var dotStaff =
                            new SixteenthRestDetector.Staff(
                                    plate.rule() - plateGap,
                                    plate.rule() + 3 * plateGap,
                                    plateGap,
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
                        int owner = dotOwner(dot, plateGap, mi, staff, m, width, height, notes);
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
                    // Header ink can straddle the detected measure boundary. Keep event geometry
                    // unchanged; only the full-rest centering frame excludes accepted header ink.
                    float contentLeft = m.left();
                    int headerRight = -1;
                    boolean headerConflict = false;
                    for (var header : verifiedHeaders) {
                        if (header == null
                                || Math.abs(header.firstLine() - lines.top()) > gap * .4f
                                || header.gap() < gap * .8f
                                || header.gap() > gap * 1.2f
                                || header.left() > m.left() * width
                                || header.right() <= m.left() * width
                                || header.right() >= plate.left() - gap
                                || MeterChangeDetector.followingMeasure(
                                                header, width, height, measures)
                                        != mi
                                || !MeterChangeDetector.precedesNotes(
                                        header, gray, width, height, mi, measures, notes)) continue;
                        if (headerRight >= 0 && headerRight != header.right())
                            headerConflict = true;
                        headerRight = header.right();
                    }
                    if (headerRight >= 0 && !headerConflict)
                        contentLeft = headerRight / (float) width;
                    float contentRight =
                            doubleBarContentRight(
                                    gray,
                                    width,
                                    height,
                                    m.right(),
                                    lines.top(),
                                    lines.bottom(),
                                    gap);
                    float centerPosition = (cx - contentLeft) / (contentRight - contentLeft);
                    float tolerance =
                            Math.min(
                                    .085f,
                                    Math.max(
                                            .035f,
                                            plateGap
                                                    * .65f
                                                    / ((contentRight - contentLeft) * width)));
                    final int measureIndex = mi;
                    boolean shortSilentBar =
                            shortBars.contains(mi)
                                    && notes.stream()
                                            .noneMatch(
                                                    n ->
                                                            n.measureIndex() == measureIndex
                                                                    && n.staffIndex()
                                                                            == staff.index()
                                                                    && n.staffCount()
                                                                            == staff.count());
                    if (dots.isEmpty()
                            && (Math.abs(centerPosition - .5f) <= tolerance || shortSilentBar)) {
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
                            reason =
                                    shortSilentBar
                                            ? "complete undotted plate in proved short silent bar"
                                            : "centered undotted complete plate and silent lane";
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
        for (int y = top; y <= bottom; y++) {
            var fringe = ruleFringeEnvelope(g, w, h, p, m, y);
            for (int x = l; x <= r; x++) {
                if ((g[y * w + x] & 255) >= 225) continue;
                boolean accounted = false;
                for (var dot : dots)
                    if (Math.abs(dot.x() - x) <= p.gap() * .45f
                            && Math.abs(dot.y() - y) <= p.gap() * .45f) {
                        accounted = true;
                        break;
                    }
                if (!accounted && !ownedFringePixel(g, w, fringe, x, y)) return true;
            }
        }
        return false;
    }

    private record OpticalProfile(
            double minimumInner,
            double maximumInner,
            double meanInner,
            double meanOuter,
            double slope,
            double excess) {
        double upper(double inner) {
            return meanOuter + slope * (inner - meanInner) + excess;
        }
    }

    private record FringeEnvelope(int core, OpticalProfile profile) {}

    /** Learn a thin optical falloff only from two clear stripes outside the body/dot corridor. */
    private static FringeEnvelope ruleFringeEnvelope(
            byte[] g, int w, int h, Plate p, MeasureRegion m, int y) {
        if (p.support() != Support.SECOND_RULE
                || y != p.first() + 1
                || y <= p.rule() + 1
                || y + 1 >= h) return null;
        int[] strips = {
            p.left() - Math.round(p.gap() * 3),
            p.left() - Math.round(p.gap() * .75f),
            p.right() + Math.round(p.gap() * 3),
            p.right() + Math.round(p.gap() * 4.5f)
        };
        if (strips[0] <= Math.ceil(m.left() * w)
                || strips[3] >= Math.floor(m.right() * w)
                || strips[1] - strips[0] < p.gap()
                || strips[3] - strips[2] < p.gap()) return null;
        int core = -1;
        for (int row = y - 2; row >= p.rule(); row--) {
            boolean complete = true;
            for (int strip = 0; strip < 2; strip++) {
                int count = 0;
                for (int x = strips[strip * 2]; x <= strips[strip * 2 + 1]; x++)
                    if (dark(g, w, x, row)) count++;
                complete &= count >= (strips[strip * 2 + 1] - strips[strip * 2] + 1) * .9f;
            }
            // The independently observed core must be one uninterrupted horizontal owner
            // across both reference stripes and the intervening body/corridor.
            if (complete)
                for (int x = strips[0]; x <= strips[3]; x++)
                    if (!dark(g, w, x, row)) {
                        complete = false;
                        break;
                    }
            if (complete) {
                core = row;
                break;
            }
        }
        if (core < 0) return null;
        var samples = new ArrayList<double[]>();
        int flank = Math.max(2, Math.round(p.gap() * .35f));
        for (int strip = 0; strip < 2; strip++) {
            int lo = strips[strip * 2], hi = strips[strip * 2 + 1];
            for (int x = lo; x <= hi; x++) {
                int inner = g[(y - 1) * w + x] & 255, outer = g[y * w + x] & 255;
                int c = g[core * w + x] & 255;
                if (c >= 170
                        || inner <= c
                        || inner >= 225
                        || outer <= inner
                        || outer < 200
                        || outer >= 245
                        || (g[(y + 1) * w + x] & 255) < 245) return null;
                // Local added ink in a reference stripe cannot set a permissive envelope.
                if (x - flank >= lo
                        && x + flank <= hi
                        && outer + 3
                                < Math.min(g[y * w + x - flank] & 255, g[y * w + x + flank] & 255))
                    return null;
                if (x - flank >= lo && x + flank <= hi)
                    samples.add(
                            new double[] {
                                x, (255d - inner) / (255d - c), (255d - outer) / (255d - inner)
                            });
            }
        }
        if (samples.size() < Math.ceil(p.gap())) return null;
        var profile = opticalProfile(samples);
        return profile == null ? null : new FringeEnvelope(core, profile);
    }

    /** Reference attenuation must predict the observed outer falloff, without gray-level slack. */
    private static OpticalProfile opticalProfile(List<double[]> samples) {
        double minimumInner = Double.POSITIVE_INFINITY, maximumInner = 0;
        double minimumOuter = Double.POSITIVE_INFINITY, maximumOuter = 0;
        double meanInner = 0, meanOuter = 0;
        for (var v : samples) {
            minimumInner = Math.min(minimumInner, v[1]);
            maximumInner = Math.max(maximumInner, v[1]);
            minimumOuter = Math.min(minimumOuter, v[2]);
            maximumOuter = Math.max(maximumOuter, v[2]);
            meanInner += v[1];
            meanOuter += v[2];
        }
        // Equal inner phases cannot explain independently darkened outer reference ink.
        if (minimumInner == maximumInner) {
            if (minimumOuter != maximumOuter) return null;
            return new OpticalProfile(minimumInner, maximumInner, minimumInner, minimumOuter, 0, 0);
        }
        meanInner /= samples.size();
        meanOuter /= samples.size();
        double xy = 0, xx = 0;
        for (var v : samples) {
            xy += (v[1] - meanInner) * (v[2] - meanOuter);
            xx += (v[1] - meanInner) * (v[1] - meanInner);
        }
        double slope = xy / xx, excess = 0;
        if (!(slope > 0) || !Double.isFinite(slope)) return null;
        for (var v : samples)
            excess = Math.max(excess, v[2] - meanOuter - slope * (v[1] - meanInner));
        return new OpticalProfile(minimumInner, maximumInner, meanInner, meanOuter, slope, excess);
    }

    private static boolean ownedFringePixel(byte[] g, int w, FringeEnvelope profile, int x, int y) {
        if (profile == null) return false;
        int c = g[profile.core() * w + x] & 255;
        int inner = g[(y - 1) * w + x] & 255, outer = g[y * w + x] & 255;
        if (c >= 170
                || inner <= c
                || inner >= 225
                || outer <= inner
                || outer < 200
                || outer >= 245
                || (g[(y + 1) * w + x] & 255) < 245) return false;
        double innerRatio = (255d - inner) / (255d - c);
        double outerRatio = (255d - outer) / (255d - inner);
        var learned = profile.profile();
        return innerRatio >= learned.minimumInner()
                && innerRatio <= learned.maximumInner()
                && outerRatio <= learned.upper(innerRatio);
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

    /** Require two agreeing complete local staffs before correcting the page-wide phase. */
    static float[] localRules(byte[] g, int w, int h, int lo, int hi, float top, float gap) {
        var a =
                PrintedDirectionStaff.printedRules(
                        g, w, h, lo + (hi - lo) * .25f, top + 4 * gap, gap);
        var b =
                PrintedDirectionStaff.printedRules(
                        g, w, h, lo + (hi - lo) * .75f, top + 4 * gap, gap);
        if (a == null
                || b == null
                || Math.abs(a[0] - b[0]) > gap * .15f
                || Math.abs(a[1] - b[1]) > gap * .06f) return null;
        float localGap = (a[1] + b[1]) * .5f;
        float localTop = (a[0] + b[0]) * .5f - 4 * localGap;
        if (Math.abs(localTop - top) > gap * .5f
                || Math.abs(localGap - gap) > gap * .12f
                || localTop < 0
                || localTop + 4 * localGap >= h) return null;
        return new float[] {localTop, localGap};
    }

    private static void scanSecondRule(
            byte[] g, int w, int h, int lo, int hi, float staffTop, float gap, List<Plate> out) {
        int rule = Math.round(staffTop + gap);
        if (rule < 2 || rule + gap >= h) return;
        if (ink(g, w, lo, hi, rule) < (hi - lo + 1) * .80f) {
            var local = localRules(g, w, h, lo, hi, staffTop, gap);
            if (local == null) return;
            gap = local[1];
            rule = Math.round(local[0] + gap);
            if (rule < 2 || rule + gap >= h || ink(g, w, lo, hi, rule) < (hi - lo + 1) * .80f)
                return;
        }
        int bottom = rule;
        while (bottom + 1 < h
                && bottom - rule < gap * .3f
                && ink(g, w, lo, hi, bottom + 1) >= (hi - lo + 1) * .80f) bottom++;
        // A partial antialiased rule edge may extend past a hanging body. Search only
        // the thin support band, retaining every distinct plate in this measure.
        for (int first = bottom + 1; first <= bottom + 1 + Math.round(gap * .2f); first++) {
            if (first >= h) break;
            for (int x = lo; x <= hi; x++) {
                if (!dark(g, w, x, first) || dark(g, w, x - 1, first)) continue;
                int right = x;
                while (right < hi && dark(g, w, right + 1, first)) right++;
                int left = x;
                x = right;
                var p =
                        completePlate(
                                g,
                                w,
                                h,
                                lo,
                                hi,
                                left,
                                right,
                                rule,
                                first,
                                gap,
                                Support.SECOND_RULE);
                if (p == null || !attachedRuleEdge(g, w, lo, hi, left, right, bottom, first, gap))
                    continue;
                int arms = Math.round(gap * .5f);
                // A thin printed rule may start with a pale fringe. Require the same complete
                // arms and paper above one observed row within its already proved support band.
                boolean supported = false;
                for (int support = rule; support <= bottom; support++) {
                    if (ink(g, w, left - arms, right + arms, support)
                            < (right - left + 1 + 2 * arms) * .90f) continue;
                    if (ink(
                                    g,
                                    w,
                                    left - arms,
                                    right + arms,
                                    support - Math.max(1, Math.round(gap * .2f)))
                            > 1) continue;
                    supported = true;
                    break;
                }
                if (!supported) continue;
                boolean duplicate = false;
                for (var prior : out)
                    if (prior.support() == Support.SECOND_RULE
                            && prior.last() == p.last()
                            && prior.left() <= p.right()
                            && p.left() <= prior.right()) {
                        duplicate = true;
                        break;
                    }
                if (!duplicate) out.add(p);
            }
        }
    }

    private static boolean attachedRuleEdge(
            byte[] g, int w, int lo, int hi, int l, int r, int bottom, int first, float gap) {
        for (int y = bottom + 1; y < first; y++) {
            // Never skip ordinary body rows, a missing connection, or a taper.
            if (ink(g, w, l, r, y) != r - l + 1) return false;
            int a = l, b = r;
            while (a > lo && dark(g, w, a - 1, y)) a--;
            while (b < hi && dark(g, w, b + 1, y)) b++;
            if ((l - a) + (b - r) < gap) return false;
        }
        return true;
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
        // A rasterized rectangle may end with one inset corner row. The body above remains
        // rectangular, and the ordinary clear-space checks still apply below this edge.
        if (last - first + 1 >= Math.max(3, Math.round(gap * .23f)) && last + 1 < h) {
            int inset = Math.max(1, Math.round(gap * .15f));
            int edgeLeft = l, edgeRight = r;
            while (edgeLeft <= r && !dark(g, w, edgeLeft, last + 1)) edgeLeft++;
            while (edgeRight >= l && !dark(g, w, edgeRight, last + 1)) edgeRight--;
            if (edgeLeft - l <= inset
                    && r - edgeRight <= inset
                    && edgeRight >= edgeLeft
                    && ink(g, w, edgeLeft, edgeRight, last + 1) == edgeRight - edgeLeft + 1) last++;
        }
        int depth = last - first + 1, margin = Math.max(2, Math.round(gap * .25f));
        if (depth < Math.max(3, Math.round(gap * .23f))
                || depth > gap * .65f
                || l - margin <= lo
                || r + margin >= hi
                || last + margin >= h) return null;
        for (int row = first; row <= last; row++)
            if (ink(g, w, l - margin, l - 1, row) > 1
                    || ink(g, w, r + 1, r + margin, row) > 1
                    || row < last && ink(g, w, l, r, row) < bodyWidth * .85f) return null;
        for (int row = last + 1; row <= last + margin; row++)
            if (ink(g, w, l - margin, r + margin, row) > 1) return null;
        return new Plate(l, r, rule, first, last, gap, support);
    }

    /** The inner stroke owns the usable ending of an independently printed double bar. */
    private static float doubleBarContentRight(
            byte[] g, int w, int h, float right, float top, float bottom, float gap) {
        int end = Math.min(w - 1, Math.round(right * w)),
                start = Math.max(0, end - Math.round(gap * 2)),
                first = Math.max(0, Math.round(top) + 2),
                last = Math.min(h - 1, Math.round(bottom) - 2);
        if (last <= first) return right;
        var bands = new ArrayList<int[]>();
        int run = -1;
        for (int x = start; x <= end + 1; x++) {
            int count = 0;
            if (x <= end) for (int y = first; y <= last; y++) if (dark(g, w, x, y)) count++;
            boolean bar = x <= end && count >= (last - first + 1) * .9f;
            if (bar && run < 0) run = x;
            if (!bar && run >= 0) {
                bands.add(new int[] {run, x - 1});
                run = -1;
            }
        }
        if (bands.size() != 2) return right;
        int[] a = bands.get(0), b = bands.get(1);
        if (a[1] - a[0] + 1 > gap * .35f
                || b[1] - b[0] + 1 > gap * .9f
                || b[0] - a[1] < 2
                || b[0] - a[1] > gap
                || end - b[1] > gap * .3f) return right;
        int flank = Math.max(3, Math.round(gap * .3f));
        int upper = Math.round(top) - flank, lower = Math.round(bottom) + flank;
        if (upper < 0 || lower >= h) return right;
        for (int[] band : bands)
            for (int x = band[0]; x <= band[1]; x++)
                if (dark(g, w, x, upper) || dark(g, w, x, lower)) return right;
        return a[0] / (float) w;
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

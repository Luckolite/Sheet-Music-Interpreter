// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Positive whole-figure ownership for a quarter proposal cut from one finite single beam. */
final class FiniteBeamQuarterOwnership {
    private static final int DARK = 170; // The existing quarter-contour foreground grammar.

    private FiniteBeamQuarterOwnership() {}

    private record Stem(
            int left,
            int right,
            int end,
            int direction,
            float x,
            float y,
            int start,
            int[] columnEnds) {}

    private record Rules(
            int[] rows, int left, int right, int[][] stripeTop, int[][] stripeBottom) {}

    private record Rail(int first, int[] top, int[] bottom) {
        boolean contains(int x, int y) {
            return x >= first
                    && x < first + top.length
                    && y >= top[x - first]
                    && y <= bottom[x - first];
        }
    }

    static boolean owns(
            byte[] g,
            int w,
            int h,
            MeasureRegion region,
            List<ScoreNoteEvent> notes,
            int measure,
            int staffIndex,
            int staffCount,
            float printedCenter,
            float gap,
            int left,
            int right,
            int minY,
            int maxY) {
        if (g == null
                || w < 3
                || h < 3
                || g.length != (long) w * h
                || region == null
                || notes == null
                || !Float.isFinite(gap + printedCenter)
                || gap < 8
                || gap > Math.min(w, h) / 8f
                || left < 1
                || right >= w - 1
                || left > right
                || minY < 1
                || maxY >= h - 1
                || minY > maxY) return false;
        var owners = new ArrayList<ScoreNoteEvent>();
        for (var n : notes) {
            if (n == null
                    || n.measureIndex() != measure
                    || n.staffIndex() != staffIndex
                    || n.staffCount() != staffCount
                    || n.kind() != ScoreNoteEvent.Kind.PITCHED
                    || n.beamCount() != 1
                    || !Float.isFinite(n.pageY() + n.positionInMeasure())) continue;
            float x = x(region, n, w);
            if (x >= left - gap && x <= right + gap * 5) owners.add(n);
        }
        for (int i = 0; i < owners.size(); i++)
            for (int j = i + 1; j < owners.size(); j++) {
                var na = owners.get(i);
                var nb = owners.get(j);
                if (x(region, na, w) > x(region, nb, w)) {
                    var t = na;
                    na = nb;
                    nb = t;
                }
                float ax = x(region, na, w), bx = x(region, nb, w);
                if (bx - ax < gap * 2 || bx - ax > gap * 5) continue;
                Stem a = stem(g, w, h, ax, na.pageY() * h, gap),
                        b = stem(g, w, h, bx, nb.pageY() * h, gap);
                if (a == null
                        || b == null
                        || a.direction != 1
                        || b.direction != 1
                        || a.right >= b.left
                        || Math.abs(a.end - b.end) > gap * .75f
                        || left > a.right
                        || right < a.left) continue;
                Rules rules = rules(g, w, h, a, b, printedCenter * h - 2 * gap, gap);
                if (rules == null || !head(g, w, h, a, gap, rules) || !head(g, w, h, b, gap, rules))
                    continue;
                // A head itself is not a quarter-rest fragment in this additional route.
                if (a.direction > 0 ? minY <= a.y + gap * .65f : maxY >= a.y - gap * .65f) continue;
                Rail rail = rail(g, w, h, a, b, gap, left, right, rules);
                if (rail == null) continue;
                boolean residual = false, usedShaft = false, usedRail = false;
                for (int y = minY; y <= maxY && !residual; y++)
                    for (int xx = left; xx <= right; xx++) {
                        if (!dark(g, w, xx, y)) continue;
                        boolean shaft = shaftContains(a, xx, y) || shaftContains(b, xx, y);
                        boolean beam = rail.contains(xx, y);
                        if (!shaft && !beam && !rulePixel(g, w, h, xx, y, rules, gap)) {
                            residual = true;
                            break;
                        }
                        usedShaft |= shaft;
                        usedRail |= beam && !shaft;
                    }
                if (!residual && usedShaft && usedRail) return true;
            }
        return false;
    }

    enum ProposalResult {
        INVALID_PROJECTION,
        UNKNOWN_HEAD_ORIGIN,
        NO_FINITE_OWNER_PAIR,
        NO_OWN_SHAFT_CONTACT,
        UNPROVED_RULES,
        PROPOSAL_OVERLAPS_HEAD,
        UNPROVED_FINITE_BEAM,
        UNOWNED_PROPOSAL_INK,
        OWNED
    }

    /** Optional route: accepted semantic identity anchors the head; never its unseen AA cap. */
    static ProposalResult proposalOwnership(
            byte[] g,
            int w,
            int h,
            MeasureRegion region,
            List<ScoreNoteEvent> notes,
            int measure,
            int staffIndex,
            int staffCount,
            float printedCenter,
            float gap,
            int left,
            int right,
            int minY,
            int maxY,
            RestFigureOrigins.ProposalOrigin proposal) {
        if (proposal == null
                || !proposal.valid(g, w, h, left, right, minY, maxY)
                || region == null
                || notes == null
                || !Float.isFinite(gap + printedCenter)
                || gap < 8
                || gap > Math.min(w, h) / 8f) return ProposalResult.INVALID_PROJECTION;
        var owners = new ArrayList<ScoreNoteEvent>();
        for (var n : notes) {
            if (n == null
                    || n.measureIndex() != measure
                    || n.staffIndex() != staffIndex
                    || n.staffCount() != staffCount
                    || n.kind() != ScoreNoteEvent.Kind.PITCHED
                    || n.beamCount() != 1
                    || !Float.isFinite(n.pageY() + n.positionInMeasure())) continue;
            float xx = x(region, n, w);
            if (xx >= left - gap && xx <= right + gap * 5) owners.add(n);
        }
        ProposalResult failure = ProposalResult.NO_FINITE_OWNER_PAIR;
        for (int i = 0; i < owners.size(); i++)
            for (int j = i + 1; j < owners.size(); j++) {
                var na = owners.get(i);
                var nb = owners.get(j);
                if (x(region, na, w) > x(region, nb, w)) {
                    var t = na;
                    na = nb;
                    nb = t;
                }
                float ax = x(region, na, w), bx = x(region, nb, w);
                if (bx - ax < gap * 2 || bx - ax > gap * 5) continue;
                var ha = RestFigureOrigins.origin(notes, na, g, w, h);
                var hb = RestFigureOrigins.origin(notes, nb, g, w, h);
                if (ha == null || hb == null || ha.component == hb.component) {
                    failure = ProposalResult.UNKNOWN_HEAD_ORIGIN;
                    continue;
                }
                if (ha.originalGray != g || hb.originalGray != g) {
                    var original = RestFigureOrigins.originalPair(notes, na, ha, nb, hb, g, w, h);
                    if (original == null) {
                        failure = ProposalResult.UNKNOWN_HEAD_ORIGIN;
                        continue;
                    }
                    // Brightening may hide an original branch. Account EVERY original proposal
                    // row, including ink outside the derived first/last classified extent.
                    var originalProposal =
                            new RestFigureOrigins.ProposalOrigin(
                                    original.gray(),
                                    original.gray(),
                                    w,
                                    h,
                                    proposal.top,
                                    proposal.bottom,
                                    proposal.mask,
                                    left,
                                    right,
                                    proposal.top,
                                    proposal.bottom);
                    var originalDecision =
                            proposalOwnership(
                                    original.gray(),
                                    w,
                                    h,
                                    region,
                                    original.notes(),
                                    measure,
                                    staffIndex,
                                    staffCount,
                                    printedCenter,
                                    gap,
                                    left,
                                    right,
                                    minY,
                                    maxY,
                                    originalProposal);
                    if (originalDecision != ProposalResult.OWNED) {
                        failure = originalDecision;
                        continue;
                    }
                    // The canonical pair's active/original planes are identical: recursion stops
                    // after exactly one canonical proof. The derived proof below stays complete.
                }
                Stem a = stem(g, w, h, ax, na.pageY() * h, gap),
                        b = stem(g, w, h, bx, nb.pageY() * h, gap);
                if (a == null
                        || b == null
                        || a.direction != 1
                        || b.direction != 1
                        || a.right >= b.left
                        || Math.abs(a.end - b.end) > gap * .75f
                        || left > a.right
                        || right < a.left) continue;
                if (!semanticShaftPort(g, w, h, a, ha, gap)
                        || !semanticShaftPort(g, w, h, b, hb, gap)) {
                    failure = ProposalResult.NO_OWN_SHAFT_CONTACT;
                    continue;
                }
                Rules rules = rules(g, w, h, a, b, printedCenter * h - 2 * gap, gap);
                if (rules == null) {
                    failure = ProposalResult.UNPROVED_RULES;
                    continue;
                }
                if (minY <= a.y + gap * .65f) {
                    failure = ProposalResult.PROPOSAL_OVERLAPS_HEAD;
                    continue;
                }
                Rail beam = rail(g, w, h, a, b, gap, left, right, rules);
                ParallelFigure extra = null;
                if (beam == null) {
                    extra = parallelFigure(g, w, h, a, b, gap, left, right, rules);
                    if (extra != null) beam = extra.rail;
                }
                if (beam == null) {
                    failure = ProposalResult.UNPROVED_FINITE_BEAM;
                    continue;
                }
                boolean unknown = false, usedShaft = false, usedBeam = false;
                // Original bounds, not the classified/trimmed crop. Masked staff ink is NOT erased.
                for (int y = proposal.firstInk; y <= proposal.lastInk && !unknown; y++)
                    for (int xx = left; xx <= right; xx++) {
                        if (!dark(g, w, xx, y)) continue;
                        boolean shaft =
                                shaftContains(a, xx, y)
                                        || shaftContains(b, xx, y)
                                        || extra != null
                                                && (extra.a.contains(xx, y)
                                                        || extra.b.contains(xx, y));
                        boolean rail = beam.contains(xx, y);
                        if (!shaft && !rail && !rulePixel(g, w, h, xx, y, rules, gap)) {
                            unknown = true;
                            break;
                        }
                        usedShaft |= shaft;
                        usedBeam |= rail && !shaft;
                    }
                if (!unknown && usedShaft && usedBeam) return ProposalResult.OWNED;
                failure = ProposalResult.UNOWNED_PROPOSAL_INK;
            }
        return failure;
    }

    /** A connected 2-D accepted-semantic/raw port on the independently continuous shaft. */
    private static boolean semanticShaftPort(
            byte[] g, int w, int h, Stem stem, RestFigureOrigins.HeadOrigin origin, float gap) {
        int first = Math.max(1, stem.start - (int) Math.ceil(gap));
        int last = Math.min(h - 2, stem.start + (int) Math.ceil(gap));
        for (int xx = stem.left; xx < stem.right; xx++)
            for (int y = first; y < last; y++) {
                boolean[] contact = new boolean[4];
                for (int dx = 0; dx < 2; dx++)
                    for (int dy = 0; dy < 2; dy++)
                        contact[dx * 2 + dy] =
                                origin.contains(xx + dx, y + dy)
                                        && dark(g, w, xx + dx, y + dy)
                                        && shaftContains(stem, xx + dx, y + dy);
                // Any L or square has actual row AND column contact, never a lone tangent pixel.
                if (contact[0] && contact[1] && (contact[2] || contact[3])
                        || contact[2] && contact[3] && (contact[0] || contact[1])) return true;
            }
        return false;
    }

    private static float x(MeasureRegion r, ScoreNoteEvent n, int w) {
        return (r.left() + n.positionInMeasure() * (r.right() - r.left())) * w;
    }

    private static boolean dark(byte[] g, int w, int x, int y) {
        return (g[y * w + x] & 255) < DARK;
    }

    /** Discover the raw direction; zero event metadata never supplies a direction. */
    private static Stem stem(byte[] g, int w, int h, float cx, float cy, float gap) {
        if (!FilledSparseBeamHead.matches(g, w, h, cx, cy, gap)) return null;
        Stem found = null;
        for (int direction : new int[] {-1, 1}) {
            int start = Math.round(cy + direction * gap * .25f);
            int limit = Math.round(cy + direction * gap * 4.5f);
            if (start < 1 || start >= h - 1 || limit < 1 || limit >= h - 1) continue;
            int groupLeft = -1, groupRight = -1, end = direction > 0 ? 0 : h - 1;
            boolean separated = false;
            int lo = Math.max(1, Math.round(cx - gap * .85f)),
                    hi = Math.min(w - 2, Math.round(cx + gap * .85f));
            int[] observedEnds = new int[hi - lo + 1];
            Arrays.fill(observedEnds, Integer.MIN_VALUE);
            for (int xx = lo; xx <= hi; xx++) {
                int yy = start;
                while ((direction > 0 ? yy <= limit : yy >= limit) && dark(g, w, xx, yy))
                    yy += direction;
                int last = yy - direction;
                if (Math.abs(last - start) + 1 < Math.ceil(gap * 2)) continue;
                observedEnds[xx - lo] = last;
                if (groupRight >= 0 && xx > groupRight + 1) separated = true;
                if (groupLeft < 0) groupLeft = xx;
                groupRight = xx;
                end = direction > 0 ? Math.max(end, last) : Math.min(end, last);
            }
            if (groupLeft < 0) continue;
            if (separated || groupRight - groupLeft + 1 > gap * .35f) return null;
            if (found != null)
                return null; // Independently plausible opposite shafts are ambiguous.
            found =
                    new Stem(
                            groupLeft,
                            groupRight,
                            end,
                            direction,
                            cx,
                            cy,
                            start,
                            Arrays.copyOfRange(observedEnds, groupLeft - lo, groupRight - lo + 1));
        }
        return found;
    }

    /** Independent three-gap witnesses on BOTH sides register all five physical rules. */
    private static Rules rules(byte[] g, int w, int h, Stem a, Stem b, float top, float gap) {
        int span = Math.round(gap * 3), l = a.left - span, z = b.right + span;
        if (l < 1 || z >= w - 1) return null;
        int[] rows = new int[5];
        for (int k = 0; k < 5; k++) {
            float seed = top + k * gap;
            int chosen = -1;
            float distance = Float.POSITIVE_INFINITY;
            for (int y = Math.round(seed - gap * .25f); y <= Math.round(seed + gap * .25f); y++) {
                if (y < 1 || y >= h - 1) continue;
                boolean complete = true;
                for (int xx = l; xx < a.left; xx++)
                    if (!dark(g, w, xx, y)) {
                        complete = false;
                        break;
                    }
                if (complete)
                    for (int xx = b.right + 1; xx <= z; xx++)
                        if (!dark(g, w, xx, y)) {
                            complete = false;
                            break;
                        }
                if (complete)
                    for (int xx = a.left; xx <= b.right; xx++)
                        if (!dark(g, w, xx, y)) {
                            complete = false;
                            break;
                        }
                if (complete && Math.abs(y - seed) < distance) {
                    chosen = y;
                    distance = Math.abs(y - seed);
                }
            }
            if (chosen < 0
                    || k > 0 && Math.abs(chosen - rows[k - 1] - gap) > Math.max(1, gap * .15f))
                return null;
            rows[k] = chosen;
        }
        int n = z - l + 1;
        int[][] stripeTop = new int[5][n], stripeBottom = new int[5][n];
        for (int k = 0; k < 5; k++) {
            Arrays.fill(stripeTop[k], Integer.MIN_VALUE);
            Arrays.fill(stripeBottom[k], Integer.MIN_VALUE);
            traceStripe(g, w, h, rows[k], l, z, a, b, gap, stripeTop[k], stripeBottom[k]);
        }
        return new Rules(rows, l, z, stripeTop, stripeBottom);
    }

    /** A locally bounded transverse profile, connected to an independently long rule core. */
    private static boolean rulePixel(byte[] g, int w, int h, int x, int y, Rules rules, float gap) {
        if (x < rules.left || x > rules.right) return false;
        for (int core : rules.rows) {
            if (!dark(g, w, x, core)) continue;
            int t = core, z = core, reach = Math.max(2, Math.round(gap * .5f));
            while (t > Math.max(0, core - reach) && dark(g, w, x, t - 1)) t--;
            while (z < Math.min(h - 1, core + reach) && dark(g, w, x, z + 1)) z++;
            if (t < 1 || z >= h - 1 || z - t + 1 > gap * .35f || y < t || y > z) continue;
            int peak = t;
            for (int yy = t + 1; yy <= z; yy++)
                if ((g[yy * w + x] & 255) < (g[peak * w + x] & 255)) peak = yy;
            boolean profile = true;
            for (int yy = t; yy < peak; yy++)
                if ((g[yy * w + x] & 255) < (g[(yy + 1) * w + x] & 255)) profile = false;
            for (int yy = peak; yy < z; yy++)
                if ((g[yy * w + x] & 255) > (g[(yy + 1) * w + x] & 255)) profile = false;
            if (profile) return true;
        }
        return transversePixel(x, y, rules);
    }

    private record Profile(int top, int bottom) {}

    /** Whole local vertical run, including paper immediately above/below. */
    private static Profile profile(byte[] g, int w, int h, int x, int core, float gap) {
        if (!dark(g, w, x, core)) return null;
        int t = core, b = core, reach = Math.max(2, Math.round(gap * .5f));
        while (t > Math.max(0, core - reach) && dark(g, w, x, t - 1)) t--;
        while (b < Math.min(h - 1, core + reach) && dark(g, w, x, b + 1)) b++;
        if (t < 1
                || b >= h - 1
                || b - t + 1 > gap * .35f
                || dark(g, w, x, t - 1)
                || dark(g, w, x, b + 1)) return null;
        int peak = t;
        for (int y = t + 1; y <= b; y++)
            if ((g[y * w + x] & 255) < (g[peak * w + x] & 255)) peak = y;
        for (int y = t; y < peak; y++)
            if ((g[y * w + x] & 255) < (g[(y + 1) * w + x] & 255)) return null;
        for (int y = peak; y < b; y++)
            if ((g[y * w + x] & 255) > (g[(y + 1) * w + x] & 255)) return null;
        return new Profile(t, b);
    }

    private static boolean outsideHeads(int x, Stem a, Stem b, float gap) {
        return (x < Math.round(a.x - gap * .9f) || x > Math.round(a.x + gap * .9f))
                && (x < Math.round(b.x - gap * .9f) || x > Math.round(b.x + gap * .9f))
                && (x < a.left || x > a.right)
                && (x < b.left || x > b.right);
    }

    /** A separate finite strip of identical paper-bounded profiles; no head columns vote. */
    private static Profile flank(
            byte[] g,
            int w,
            int h,
            int core,
            int from,
            int direction,
            int l,
            int r,
            Stem a,
            Stem b,
            float gap,
            int[] endpoint) {
        int count = Math.max(2, (int) Math.ceil(gap * .25f));
        for (int x = from; x >= l && x <= r; x += direction) {
            Profile p = null;
            boolean complete = true;
            for (int j = 0; j < count; j++) {
                int xx = x + direction * j;
                if (xx < l || xx > r || !outsideHeads(xx, a, b, gap)) {
                    complete = false;
                    break;
                }
                Profile q = profile(g, w, h, xx, core, gap);
                if (q == null || p != null && !q.equals(p)) {
                    complete = false;
                    break;
                }
                p = q;
            }
            if (complete) {
                endpoint[0] = x;
                return p;
            }
        }
        return null;
    }

    /** Claim only actual raw pixels in a stripe joining TWO independently exposed flanks.
     * A head/rule merger never makes its entire thick vertical run a rule. */
    private static void traceStripe(
            byte[] g,
            int w,
            int h,
            int core,
            int l,
            int r,
            Stem a,
            Stem b,
            float gap,
            int[] tops,
            int[] bottoms) {
        for (int domain = 0; domain < 3; domain++) {
            Stem owner = domain == 0 ? a : b;
            int hl = domain == 2 ? Math.round(a.x - gap * .9f) : Math.round(owner.x - gap * .9f);
            int hr = domain == 2 ? Math.round(b.x + gap * .9f) : Math.round(owner.x + gap * .9f);
            int[] le = {0}, re = {0};
            Profile lp = flank(g, w, h, core, hl - 1, -1, l, r, a, b, gap, le);
            Profile rp = flank(g, w, h, core, hr + 1, 1, l, r, a, b, gap, re);
            if (lp == null
                    || rp == null
                    || le[0] >= re[0]
                    || Math.abs(lp.top - rp.top) > 1
                    || Math.abs(lp.bottom - rp.bottom) > 1) continue;
            int top = Math.min(lp.top, rp.top), bottom = Math.max(lp.bottom, rp.bottom);
            if (bottom - top + 1 > gap * .35f) continue;
            // Every column retains the independently registered core. All dark samples in
            // the bounded strip connect through that ACTUAL core; no paper gap is filled.
            boolean connected = true;
            for (int x = le[0]; x <= re[0]; x++)
                if (!dark(g, w, x, core)) {
                    connected = false;
                    break;
                }
            if (!connected) continue;
            for (int x = hl; x <= hr && x <= r; x++) {
                if (x < l) continue;
                if (domain == 2
                        && (x <= Math.round(a.x + gap * .9f) || x >= Math.round(b.x - gap * .9f)))
                    continue;
                int actualTop = core, actualBottom = core;
                while (actualTop > top && dark(g, w, x, actualTop - 1)) actualTop--;
                while (actualBottom < bottom && dark(g, w, x, actualBottom + 1)) actualBottom++;
                int at = x - l;
                if (tops[at] != Integer.MIN_VALUE
                        && (tops[at] != actualTop || bottoms[at] != actualBottom)) {
                    // Competing owner-local stripe envelopes are not merged into a wider band.
                    tops[at] = Integer.MAX_VALUE;
                    bottoms[at] = Integer.MIN_VALUE;
                } else if (tops[at] == Integer.MIN_VALUE) {
                    tops[at] = actualTop;
                    bottoms[at] = actualBottom;
                }
            }
        }
    }

    private static boolean transversePixel(int x, int y, Rules rules) {
        if (x < rules.left || x > rules.right) return false;
        int at = x - rules.left;
        for (int k = 0; k < rules.rows.length; k++) {
            int top = rules.stripeTop[k][at], bottom = rules.stripeBottom[k][at];
            if (top != Integer.MIN_VALUE && top != Integer.MAX_VALUE && y >= top && y <= bottom)
                return true;
        }
        return false;
    }

    /** A filled raw body with a complete external cap, except its own shaft and proved rules. */
    private static boolean head(byte[] g, int w, int h, Stem s, float gap, Rules rules) {
        int l = Math.round(s.x - gap * .9f), r = Math.round(s.x + gap * .9f);
        int t = Math.round(s.y - gap * .65f), z = Math.round(s.y + gap * .65f);
        if (l < 1 || r >= w - 1 || t < 1 || z >= h - 1) return false;
        for (int yy = t; yy <= z; yy++)
            for (int xx = l; xx <= r; xx++) {
                if (xx != l && xx != r && yy != t && yy != z || !dark(g, w, xx, yy)) continue;
                if (!shaftContains(s, xx, yy) && !rulePixel(g, w, h, xx, yy, rules, gap))
                    return false;
            }
        return true;
    }

    private static boolean shaftContains(Stem s, int x, int y) {
        if (x < s.left || x > s.right) return false;
        int end = s.columnEnds[x - s.left];
        return end != Integer.MIN_VALUE
                && (s.direction > 0 ? y >= s.start && y <= end : y <= s.start && y >= end);
    }

    /** Full measured rail columns; no ROI-fit envelope or staff-mask subtraction. */
    private static Rail rail(
            byte[] g,
            int w,
            int h,
            Stem a,
            Stem b,
            float gap,
            int proposalLeft,
            int proposalRight,
            Rules rules) {
        Rail old = railLegacy(g, w, h, a, b, gap, proposalLeft, proposalRight, rules);
        return old != null
                ? old
                : railUnion(g, w, h, a, b, gap, proposalLeft, proposalRight, rules);
    }

    private static Rail railLegacy(
            byte[] g,
            int w,
            int h,
            Stem a,
            Stem b,
            float gap,
            int proposalLeft,
            int proposalRight,
            Rules rules) {
        int first = a.right + 1, last = b.left - 1, n = last - first + 1;
        if (n < 1) return null;
        int[] tops = new int[n], bottoms = new int[n];
        int[] outside = new int[n];
        int witnesses = 0;
        for (int x = first; x <= last; x++) {
            float expected = a.end + (b.end - a.end) * (x - a.right) / (float) (b.left - a.right);
            int lo = Math.round(expected - gap), hi = Math.round(expected + gap);
            if (lo < 1 || hi >= h - 1) return null;
            int selectedTop = -1, selectedBottom = -1, start = -1;
            for (int y = lo; y <= hi + 1; y++) {
                if (y <= hi && dark(g, w, x, y)) {
                    if (start < 0) start = y;
                    continue;
                }
                if (start < 0) continue;
                int end = y - 1, thick = end - start + 1;
                if (start > lo
                        && end < hi
                        && thick >= Math.max(4, Math.ceil(gap * .25f))
                        && thick <= gap * .8f
                        && Math.abs(end - expected) <= gap * .5f) {
                    if (selectedTop >= 0) return null;
                    selectedTop = start;
                    selectedBottom = end;
                }
                start = -1;
            }
            if (selectedTop < 0) return null;
            int i = x - first;
            tops[i] = selectedTop;
            bottoms[i] = selectedBottom;
            if (i > 0
                    && (Math.abs(tops[i] - tops[i - 1]) > 1
                            || Math.abs(bottoms[i] - bottoms[i - 1]) > 1)) return null;
            if (x < proposalLeft || x > proposalRight)
                outside[witnesses++] = selectedBottom - selectedTop + 1;
        }
        if (witnesses < Math.ceil(gap)) return null;
        Arrays.sort(outside, 0, witnesses);
        int thickness = outside[witnesses / 2];
        for (int i = 0; i < n; i++)
            if (Math.abs(bottoms[i] - tops[i] + 1 - thickness) > 1) return null;
        // Endpoint support is measured at the interior edges, then projected across
        // each finite shaft width. The shaft's far edge need not have the same
        // rounded y as the last open-space column of a sloping beam.
        float slope = n == 1 ? 0 : (bottoms[n - 1] - bottoms[0]) / (float) (n - 1);
        for (int i = 0; i < n; i++)
            if (Math.abs(bottoms[i] - (bottoms[0] + slope * i)) > 1) return null;
        for (Stem s : new Stem[] {a, b}) {
            float y1 = bottoms[0] + slope * (s.left - first),
                    y2 = bottoms[0] + slope * (s.right - first);
            if (s.end < Math.min(y1, y2) - 1 || s.end > Math.max(y1, y2) + 1) return null;
        }
        // No continuation outside either measured shaft can be silently absorbed.
        for (int x : new int[] {a.left - 1, b.right + 1})
            for (int y = Math.min(a.end, b.end) - Math.round(gap);
                    y <= Math.max(a.end, b.end) + Math.round(gap);
                    y++) {
                if (x < 1 || x >= w - 1 || y < 1 || y >= h - 1) return null;
                if (dark(g, w, x, y) && !rulePixel(g, w, h, x, y, rules, gap)) return null;
            }
        return new Rail(first, tops, bottoms);
    }

    /** Additive overlap route. Exposed upper AND lower beam edges establish the same
     * original median thickness. A merged column is accepted only with ONE exact
     * interval at that thickness; excess must be certified transverse rule ink. */
    private static Rail railUnion(
            byte[] g,
            int w,
            int h,
            Stem a,
            Stem b,
            float gap,
            int proposalLeft,
            int proposalRight,
            Rules rules) {
        int first = a.right + 1, last = b.left - 1, n = last - first + 1;
        if (n < 1) return null;
        int[] rawTop = new int[n], rawBottom = new int[n], observed = new int[n];
        int count = 0, witnesses = 0;
        for (int x = first; x <= last; x++) {
            float expected = a.end + (b.end - a.end) * (x - a.right) / (float) (b.left - a.right);
            int lo = Math.round(expected - gap), hi = Math.round(expected + gap);
            if (lo < 1 || hi >= h - 1) return null;
            int chosenTop = -1, chosenBottom = -1, start = -1;
            for (int y = lo; y <= hi + 1; y++) {
                if (y <= hi && dark(g, w, x, y)) {
                    if (start < 0) start = y;
                    continue;
                }
                if (start < 0) continue;
                int end = y - 1;
                if (start > lo
                        && end < hi
                        && end - start + 1 >= Math.max(4, Math.ceil(gap * .25f))
                        && end - start + 1 <= gap * .8f
                        && Math.abs(end - expected) <= gap * .5f) {
                    if (chosenTop >= 0) return null;
                    chosenTop = start;
                    chosenBottom = end;
                }
                start = -1;
            }
            if (chosenTop < 0) return null;
            int i = x - first;
            rawTop[i] = chosenTop;
            rawBottom[i] = chosenBottom;
            if (x < proposalLeft || x > proposalRight) witnesses++;
            if ((x < proposalLeft || x > proposalRight)
                    && !transversePixel(x, chosenTop, rules)
                    && !transversePixel(x, chosenBottom, rules))
                observed[count++] = chosenBottom - chosenTop + 1;
        }
        if (witnesses < Math.ceil(gap) || count == 0) return null;
        Arrays.sort(observed, 0, count);
        int thickness = observed[count / 2];
        for (int i = 0; i < count; i++) if (Math.abs(observed[i] - thickness) > 1) return null;
        int[] top = new int[n], bottom = new int[n];
        for (int i = 0; i < n; i++) {
            int x = first + i;
            if (!transversePixel(x, rawTop[i], rules) && !transversePixel(x, rawBottom[i], rules)) {
                top[i] = rawTop[i];
                bottom[i] = rawBottom[i];
                if (Math.abs(bottom[i] - top[i] + 1 - thickness) > 1) return null;
            } else {
                int candidates = 0;
                for (int t = rawTop[i]; t + thickness - 1 <= rawBottom[i]; t++) {
                    int endBand = t + thickness - 1;
                    boolean extraOwned = true;
                    for (int y = rawTop[i]; y <= rawBottom[i]; y++)
                        if ((y < t || y > endBand) && !transversePixel(x, y, rules)) {
                            extraOwned = false;
                            break;
                        }
                    // A raw shaft end can include an independently witnessed staff stripe.
                    // Do not predict a beam edge from that merged endpoint. Uniqueness comes
                    // from the observed raw interval and separately owned rule excess; the
                    // original measured beam slope/shaft-end checks remain below.
                    if (extraOwned) {
                        top[i] = t;
                        bottom[i] = endBand;
                        candidates++;
                    }
                }
                if (candidates != 1) return null;
            }
            if (i > 0
                    && (Math.abs(top[i] - top[i - 1]) > 1
                            || Math.abs(bottom[i] - bottom[i - 1]) > 1)) return null;
        }
        float slope = n == 1 ? 0 : (bottom[n - 1] - bottom[0]) / (float) (n - 1);
        for (int i = 0; i < n; i++)
            if (Math.abs(bottom[i] - (bottom[0] + slope * i)) > 1) return null;
        for (Stem q : new Stem[] {a, b}) {
            int railIndex = q == a ? 0 : n - 1, edge = q == a ? q.right : q.left;
            int observedEnd = q.columnEnds[edge - q.left];
            // Direct raw adjacency at the actual interior shaft edge, not extrapolation.
            if (observedEnd == Integer.MIN_VALUE
                    || Math.max(q.start, top[railIndex]) > Math.min(observedEnd, bottom[railIndex]))
                return null;
            float y1 = bottom[0] + slope * (q.left - first),
                    y2 = bottom[0] + slope * (q.right - first);
            if (q.end < Math.min(y1, y2) - 1 || q.end > Math.max(y1, y2) + 1) {
                // The measured shaft may end on a registered rule beyond the finite beam.
                // Keep the old soft-end route above; this additional route owns EVERY
                // extra actual shaft sample separately, rather than extending the beam.
                for (int x = q.left; x <= q.right; x++) {
                    int end = q.columnEnds[x - q.left];
                    float beamEnd = bottom[0] + slope * (x - first);
                    int contact = (int) Math.floor(beamEnd);
                    if (end == Integer.MIN_VALUE || q.start > contact + 1 || end < contact - 1)
                        return null;
                    for (int y = (int) Math.ceil(beamEnd) + 1; y <= end; y++)
                        if (!transversePixel(x, y, rules)) return null;
                }
            }
        }
        for (int x : new int[] {a.left - 1, b.right + 1})
            for (int y = Math.min(a.end, b.end) - Math.round(gap);
                    y <= Math.max(a.end, b.end) + Math.round(gap);
                    y++) {
                if (x < 1 || x >= w - 1 || y < 1 || y >= h - 1) return null;
                if (dark(g, w, x, y) && !rulePixel(g, w, h, x, y, rules, gap)) return null;
            }
        return new Rail(first, top, bottom);
    }

    private record ParallelFigure(Rail rail, FiniteShaftFoot a, FiniteShaftFoot b) {}

    /** Additive quantized parallel-band proof; neither legacy geometry route is changed. */
    private static ParallelFigure parallelFigure(
            byte[] g,
            int w,
            int h,
            Stem a,
            Stem b,
            float gap,
            int proposalLeft,
            int proposalRight,
            Rules rules) {
        int first = a.right + 1, last = b.left - 1, n = last - first + 1;
        if (n < 1) return null;
        int[] rawTop = new int[n], rawBottom = new int[n], observed = new int[n];
        int count = 0, witnesses = 0;
        for (int x = first; x <= last; x++) {
            float expected = a.end + (b.end - a.end) * (x - a.right) / (float) (b.left - a.right);
            int lo = Math.round(expected - gap), hi = Math.round(expected + gap);
            if (lo < 1 || hi >= h - 1) return null;
            int chosenTop = -1, chosenBottom = -1, start = -1;
            for (int y = lo; y <= hi + 1; y++) {
                if (y <= hi && dark(g, w, x, y)) {
                    if (start < 0) start = y;
                    continue;
                }
                if (start < 0) continue;
                int end = y - 1;
                if (start > lo
                        && end < hi
                        && end - start + 1 >= Math.max(4, Math.ceil(gap * .25f))
                        && end - start + 1 <= gap * .8f
                        && Math.abs(end - expected) <= gap * .5f) {
                    if (chosenTop >= 0) return null;
                    chosenTop = start;
                    chosenBottom = end;
                }
                start = -1;
            }
            if (chosenTop < 0) return null;
            int i = x - first;
            rawTop[i] = chosenTop;
            rawBottom[i] = chosenBottom;
            if (x < proposalLeft || x > proposalRight) witnesses++;
            if ((x < proposalLeft || x > proposalRight)
                    && !transversePixel(x, chosenTop, rules)
                    && !transversePixel(x, chosenBottom, rules))
                observed[count++] = chosenBottom - chosenTop + 1;
        }
        if (witnesses < Math.ceil(gap) || count == 0) return null;
        Arrays.sort(observed, 0, count);
        int thickness = observed[count / 2];
        for (int i = 0; i < count; i++) if (Math.abs(observed[i] - thickness) > 1) return null;
        int[] top = new int[n], bottom = new int[n];
        for (int i = 0; i < n; i++) {
            int x = first + i;
            if (!transversePixel(x, rawTop[i], rules) && !transversePixel(x, rawBottom[i], rules)) {
                top[i] = rawTop[i];
                bottom[i] = rawBottom[i];
                if (Math.abs(bottom[i] - top[i] + 1 - thickness) > 1) return null;
            } else {
                int candidates = 0;
                for (int t = rawTop[i]; t + thickness - 1 <= rawBottom[i]; t++) {
                    int endBand = t + thickness - 1;
                    boolean extraOwned = true;
                    for (int y = rawTop[i]; y <= rawBottom[i]; y++)
                        if ((y < t || y > endBand) && !transversePixel(x, y, rules)) {
                            extraOwned = false;
                            break;
                        }
                    // A raw shaft end can include an independently witnessed staff stripe.
                    // Do not predict a beam edge from that merged endpoint. Uniqueness comes
                    // from the observed raw interval and separately owned rule excess; the
                    // original measured beam slope/shaft-end checks remain below.
                    if (extraOwned) {
                        top[i] = t;
                        bottom[i] = endBand;
                        candidates++;
                    }
                }
                if (candidates != 1) return null;
            }
            if (i > 0
                    && (Math.abs(top[i] - top[i - 1]) > 1
                            || Math.abs(bottom[i] - bottom[i - 1]) > 1)) return null;
        }
        FiniteParallelBeamBand band = FiniteParallelBeamBand.prove(top, bottom, thickness);
        if (band == null) return null;
        FiniteShaftFoot[] feet = new FiniteShaftFoot[2];
        int index = 0;
        for (Stem q : new Stem[] {a, b}) {
            // Endpoint authority must hold for every feasible line, not one selected fit.
            if (!band.supportsEnd(q.left - first, q.right - first, q.end)) return null;
            int[] ceilings = new int[q.columnEnds.length];
            for (int x = q.left; x <= q.right; x++)
                ceilings[x - q.left] = band.terminalCeiling(x - first);
            int railIndex = q == a ? 0 : n - 1, railX = q == a ? first : last;
            feet[index++] =
                    FiniteShaftFoot.prove(
                            g,
                            w,
                            h,
                            q.left,
                            q.right,
                            q.start,
                            q.columnEnds,
                            railX,
                            top[railIndex],
                            bottom[railIndex],
                            ceilings);
            if (feet[index - 1] == null) return null;
        }
        // Preserve original outer-continuation authority; no cropped branch is erased.
        for (int x : new int[] {a.left - 1, b.right + 1})
            for (int y = Math.min(a.end, b.end) - Math.round(gap);
                    y <= Math.max(a.end, b.end) + Math.round(gap);
                    y++) {
                if (x < 1 || x >= w - 1 || y < 1 || y >= h - 1) return null;
                if (dark(g, w, x, y) && !rulePixel(g, w, h, x, y, rules, gap)) return null;
            }
        return new ParallelFigure(new Rail(first, top, bottom), feet[0], feet[1]);
    }
}

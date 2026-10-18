// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.security.*;

/** Private evidence prototype: does not produce a dynamic event or modify source gray. */
final class ContextualMfBodyEvidence {
    record Band(int first, int last) {
        int width() {
            return last - first + 1;
        }
    }

    record Level(
            int shade,
            boolean supported,
            String reason,
            int top,
            int bottom,
            int firstFour,
            int lastFour,
            int[][] legs) {}

    record Evidence(
            boolean coreFeaturesSupported,
            String reason,
            List<Level> levels,
            int literalForeground,
            int exactContextSamples,
            int unknownSamples,
            List<Integer> unknownIndices,
            String scope,
            boolean completeForegroundOwnership) {
        Evidence {
            levels = List.copyOf(levels);
            unknownIndices = List.copyOf(unknownIndices);
        }

        Evidence(
                boolean supported,
                String reason,
                List<Level> levels,
                int fg,
                int notation,
                int unknown,
                List<Integer> indices) {
            this(
                    supported,
                    reason,
                    levels,
                    fg,
                    notation,
                    unknown,
                    indices,
                    "CORE_FEATURES_ONLY_UNKNOWN_RETAINED",
                    false);
        }

        /** Compatibility for frozen PRIVATE prototype controls only; never a complete foreground claim. */
        boolean supported() {
            return coreFeaturesSupported;
        }
    }

    private ContextualMfBodyEvidence() {}

    /** Identity/source-bound notation samples; callers cannot supply an arbitrary erasure map. */
    static final class NotationEvidence {
        private final Map<Integer, Integer> values;
        private final String digest;
        private final int w, h;
        private final boolean valid;

        private NotationEvidence(Map<Integer, Integer> v, String d, int w, int h, boolean valid) {
            values = Map.copyOf(v);
            digest = d;
            this.w = w;
            this.h = h;
            this.valid = valid;
        }
    }

    private static String lowerHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 255;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 15];
        }
        return new String(out);
    }

    private static String digest(byte[] g) {
        try {
            return lowerHex(MessageDigest.getInstance("SHA-256").digest(g));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    static NotationEvidence withoutNotation(byte[] g, int w, int h) {
        return new NotationEvidence(
                Map.of(), g == null ? "" : digest(g), w, h, g != null && g.length == (long) w * h);
    }

    static NotationEvidence captureNotation(
            DynamicNotationRawContext.Evidence curves,
            byte[] g,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            PlayingTechniqueDetector.Staff owner,
            int left,
            int right,
            float gap) {
        if (g == null || curves == null || staffs == null)
            return new NotationEvidence(Map.of(), "", w, h, false);
        String d = digest(g);
        boolean alias = false;
        for (var staff : staffs) if (staff == owner) alias = true;
        if (!alias
                || curves.width() != w
                || curves.height() != h
                || !d.equals(curves.sourceSha256()))
            return new NotationEvidence(Map.of(), d, w, h, false);
        var known = new HashMap<Integer, Integer>(curves.selectedContourPixelValues(g, w, h));
        int pad = Math.round(gap * 2), l = Math.max(0, left - pad), r = Math.min(w, right + pad);
        // Only original actual registered staff operands participate. Every one of the five
        // physical rules must be literally continuous across this complete local domain.
        for (var staff : staffs) {
            if (!Float.isFinite(staff.top() + staff.bottom() + staff.gap())
                    || staff.gap() < 5
                    || staff.gap() > 100
                    || Float.floatToIntBits(staff.bottom() - staff.top())
                            != Float.floatToIntBits(4 * staff.gap())) continue;
            int[] rows = new int[5];
            boolean proved = true;
            for (int i = 0; i < 5; i++) {
                rows[i] = Math.round(staff.top() + i * staff.gap());
                if (rows[i] < 0 || rows[i] >= h) {
                    proved = false;
                    break;
                }
                for (int x = l; x < r; x++)
                    if ((g[rows[i] * w + x] & 255) > 205) {
                        proved = false;
                        break;
                    }
                if (!proved) break;
            }
            if (proved)
                for (int y : rows)
                    for (int x = l; x < r; x++) known.put(y * w + x, g[y * w + x] & 255);
        }
        return new NotationEvidence(known, d, w, h, true);
    }

    /** Upper-bound work reservations count shared row scans and complete parser passes.
     * A reservation is conservative: unused allowance is never recycled into a first-winner result. */
    private static final class Work {
        long remaining = 32000000;

        void take(long n) {
            if (n < 0 || n > remaining) throw new Limit();
            remaining -= n;
        }
    }

    private static final class Limit extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    record LocalizedEvidence(
            Evidence body,
            List<Integer> originalRead,
            List<Integer> localizedRead,
            int componentSamples,
            long workReserved,
            String localizationScope) {
        LocalizedEvidence {
            originalRead = List.copyOf(originalRead);
            localizedRead = List.copyOf(localizedRead);
        }
    }

    static Evidence inspect(
            String literal,
            byte[] gray,
            int w,
            int h,
            int left,
            int top,
            int right,
            int bottom,
            float gap,
            NotationEvidence notation) {
        var work = new Work();
        try {
            return inspectBody(
                    literal, gray, w, h, left, top, right, bottom, gap, notation, left, top, right,
                    bottom, work);
        } catch (Limit limit) {
            return new Evidence(false, "resource-limit", List.of(), 0, 0, 0, List.of());
        }
    }

    /** Separate additive localization route. Original gray and ORIGINAL halo counterevidence
     * remain unchanged. A complete observed core component supplies bounds, never padded OCR. */
    static LocalizedEvidence inspectLocalized(
            String literal,
            byte[] gray,
            int w,
            int h,
            int left,
            int top,
            int right,
            int bottom,
            float gap,
            NotationEvidence notation) {
        var work = new Work();
        var original = List.of(left, top, right, bottom);
        var empty = new Evidence(false, "localization-unproved", List.of(), 0, 0, 0, List.of());
        if (!"mf".equalsIgnoreCase(literal)
                || gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || left < 0
                || top < 0
                || right > w
                || bottom > h
                || left >= right
                || top >= bottom
                || !Float.isFinite(gap)
                || gap < 8
                || gap > 100
                || notation == null
                || !notation.valid
                || notation.w != w
                || notation.h != h
                || !notation.digest.equals(digest(gray)))
            return new LocalizedEvidence(
                    empty, original, List.of(), 0, 0, "INVALID_OR_UNPROVED_SOURCE");
        int pad = Math.round(gap * 2),
                ll = Math.max(0, left - pad),
                rr = Math.min(w, right + pad),
                tt = Math.max(0, top - pad),
                bb = Math.min(h, bottom + pad);
        long area = (long) (rr - ll) * (bb - tt);
        if (area > 200000)
            return new LocalizedEvidence(
                    new Evidence(false, "resource-limit", List.of(), 0, 0, 0, List.of()),
                    original,
                    List.of(),
                    0,
                    0,
                    "BOUNDED_LOCALIZATION");
        try {
            work.take(16 * area);
            var seen = new HashSet<Integer>();
            var todo = new ArrayDeque<Integer>();
            int count = 0, total = 0, ml = rr, mr = ll, mt = bb, mb = tt;
            for (int y = tt; y < bb; y++)
                for (int x = ll; x < rr; x++) {
                    int start = y * w + x;
                    if (seen.contains(start)
                            || notation.values.containsKey(start)
                            || (gray[start] & 255) > 205) continue;
                    seen.add(start);
                    todo.add(start);
                    boolean intersects = false, touches = false;
                    int n = 0, al = rr, ar = ll, at = bb, ab = tt;
                    while (!todo.isEmpty()) {
                        int z = todo.remove(), xx = z % w, yy = z / w;
                        n++;
                        intersects |= xx >= left && xx < right && yy >= top && yy < bottom;
                        touches |= xx == ll || xx == rr - 1 || yy == tt || yy == bb - 1;
                        al = Math.min(al, xx);
                        ar = Math.max(ar, xx);
                        at = Math.min(at, yy);
                        ab = Math.max(ab, yy);
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                int nx = xx + dx, ny = yy + dy;
                                if (nx < ll || nx >= rr || ny < tt || ny >= bb) continue;
                                int q = ny * w + nx;
                                if (!seen.contains(q)
                                        && !notation.values.containsKey(q)
                                        && (gray[q] & 255) <= 205) {
                                    seen.add(q);
                                    todo.add(q);
                                }
                            }
                    }
                    if (intersects) {
                        if (touches)
                            return new LocalizedEvidence(
                                    new Evidence(
                                            false,
                                            "core-component-clipped-by-original-halo",
                                            List.of(),
                                            0,
                                            0,
                                            0,
                                            List.of()),
                                    original,
                                    List.of(),
                                    n,
                                    32000000 - work.remaining,
                                    "UNPROVED_CORE_CLOSURE");
                        count++;
                        total = n;
                        ml = al;
                        mr = ar;
                        mt = at;
                        mb = ab;
                    }
                }
            if (count != 1)
                return new LocalizedEvidence(
                        new Evidence(
                                false,
                                "unique-ocr-intersecting-core-missing",
                                List.of(),
                                0,
                                0,
                                0,
                                List.of()),
                        original,
                        List.of(),
                        total,
                        32000000 - work.remaining,
                        "NO_COMPONENT_SELECTION");
            var body =
                    inspectBody(
                            literal, gray, w, h, ml, mt, mr + 1, mb + 1, gap, notation, left, top,
                            right, bottom, work);
            return new LocalizedEvidence(
                    body,
                    original,
                    List.of(ml, mt, mr + 1, mb + 1),
                    total,
                    32000000 - work.remaining,
                    "COMPLETE_OBSERVED_CORE_ORIGINAL_HALO_UNKNOWN_RETAINED");
        } catch (Limit limit) {
            return new LocalizedEvidence(
                    new Evidence(false, "resource-limit", List.of(), 0, 0, 0, List.of()),
                    original,
                    List.of(),
                    0,
                    32000000 - work.remaining,
                    "INCOMPLETE_RESOURCE_ABSTENTION");
        }
    }

    private static Evidence inspectBody(
            String literal,
            byte[] gray,
            int w,
            int h,
            int left,
            int top,
            int right,
            int bottom,
            float gap,
            NotationEvidence notation,
            int counterLeft,
            int counterTop,
            int counterRight,
            int counterBottom,
            Work work) {
        if (!"mf".equalsIgnoreCase(literal)
                || gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || left < 0
                || top < 0
                || right > w
                || bottom > h
                || left >= right
                || top >= bottom
                || !Float.isFinite(gap)
                || gap < 8
                || gap > 100
                || notation == null)
            return new Evidence(false, "invalid-input", List.of(), 0, 0, 0, List.of());
        if (!notation.valid
                || notation.w != w
                || notation.h != h
                || !notation.digest.equals(digest(gray)))
            return new Evidence(
                    false, "notation-source-or-identity-mismatch", List.of(), 0, 0, 0, List.of());
        Map<Integer, Integer> exactNotation = notation.values;
        if ((long) (right - left) * (bottom - top) > 200000)
            return new Evidence(false, "resource-limit", List.of(), 0, 0, 0, List.of());
        int foreground = 0, context = 0;
        var unknown = new ArrayList<Integer>();
        for (int y = top; y < bottom; y++)
            for (int x = left; x < right; x++) {
                int at = y * w + x, value = gray[at] & 255;
                if (value == 255) continue;
                foreground++;
                Integer owned = exactNotation.get(at);
                if (owned != null) {
                    if (owned != value)
                        return new Evidence(
                                false,
                                "context-source-mismatch",
                                List.of(),
                                foreground,
                                context,
                                unknown.size(),
                                unknown);
                    context++;
                } else if (value > 205) unknown.add(at);
            }
        // All pre-existing dark-core levels participate, never a chosen favorable binarization.
        work.take((long) (right - left) * (bottom - top));
        var levels = new ArrayList<Level>();
        boolean supported = true;
        String reason = "complete-persistent-body";
        for (int shade = 145; shade <= 205; shade++) {
            var level = level(gray, w, left, top, right, bottom, gap, exactNotation, shade, work);
            levels.add(level);
            if (!level.supported()) {
                supported = false;
                if (reason.equals("complete-persistent-body")) reason = level.reason();
            }
        }
        // UNKNOWN remains explicit. Only an independently separated extra raw component or literal
        // thin unassigned connection between two owners is counterevidence; no blanket unknown
        // veto.
        if (supported) {
            String counter =
                    foregroundCounter(
                            gray,
                            w,
                            h,
                            counterLeft,
                            counterTop,
                            counterRight,
                            counterBottom,
                            gap,
                            exactNotation,
                            levels.get(0),
                            work);
            if (counter != null) {
                supported = false;
                reason = counter;
            }
        }
        return new Evidence(
                supported, reason, levels, foreground, context, unknown.size(), unknown);
    }

    private static String foregroundCounter(
            byte[] g,
            int w,
            int h,
            int l,
            int t,
            int r,
            int b,
            float gap,
            Map<Integer, Integer> context,
            Level body,
            Work work) {
        int pad = Math.round(gap * 2),
                ll = Math.max(0, l - pad),
                rr = Math.min(w, r + pad),
                tt = Math.max(0, t - pad),
                bb = Math.min(h, b + pad);
        long area = (long) (rr - ll) * (bb - tt);
        if (area > 200000) return "resource-limit";
        work.take(32 * area);
        var seeds = new HashSet<Integer>();
        for (var leg : body.legs())
            for (int x = leg[0]; x <= leg[1]; x++) seeds.add(body.firstFour() * w + x);
        var seen = new HashSet<Integer>();
        var todo = new ArrayDeque<Integer>();
        for (int y = tt; y < bb; y++)
            for (int x = ll; x < rr; x++) {
                int start = y * w + x;
                if ((g[start] & 255) == 255 || !seen.add(start)) continue;
                todo.add(start);
                boolean anchor = false, notation = false;
                while (!todo.isEmpty()) {
                    int at = todo.remove(), xx = at % w, yy = at / w;
                    anchor |= seeds.contains(at);
                    notation |= context.containsKey(at);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = xx + dx, ny = yy + dy;
                            if (nx < ll || nx >= rr || ny < tt || ny >= bb) continue;
                            int z = ny * w + nx;
                            if ((g[z] & 255) < 255 && seen.add(z)) todo.add(z);
                        }
                }
                if (!anchor && !notation) return "separate-unassigned-foreground";
            }
        seen.clear();
        for (int y = tt; y < bb; y++)
            for (int x = ll; x < rr; x++) {
                int start = y * w + x, shade = g[start] & 255;
                if (shade <= 205 || shade == 255 || context.containsKey(start) || !seen.add(start))
                    continue;
                todo.add(start);
                boolean bodyContact = false, notationContact = false, path = true;
                int endpoints = 0, freeEnds = 0;
                while (!todo.isEmpty()) {
                    int at = todo.remove(), xx = at % w, yy = at / w, degree = 0;
                    boolean endpointAnchored = false;
                    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = xx + d[0], ny = yy + d[1];
                        if (nx < ll || nx >= rr || ny < tt || ny >= bb) continue;
                        int z = ny * w + nx, v = g[z] & 255;
                        if (context.containsKey(z)) {
                            notationContact = true;
                            endpointAnchored = true;
                            continue;
                        }
                        if (v <= 205) {
                            bodyContact = true;
                            endpointAnchored = true;
                            continue;
                        }
                        if (v < 255) {
                            degree++;
                            if (seen.add(z)) todo.add(z);
                        }
                    }
                    if (degree > 2) path = false;
                    if (degree <= 1) {
                        endpoints++;
                        if (!endpointAnchored) freeEnds++;
                    }
                }
                if (path && endpoints == 2 && bodyContact && (notationContact || freeEnds > 0))
                    return "unassigned-foreign-connection-or-free-cap";
            }
        return null;
    }

    private static Level fail(int shade, String reason, int top, int bottom, int first, int last) {
        return new Level(shade, false, reason, top, bottom, first, last, new int[0][]);
    }

    private static List<Band> runs(
            byte[] g, int w, int y, int l, int r, int shade, Map<Integer, Integer> context) {
        var out = new ArrayList<Band>();
        int first = -1;
        for (int x = l; x <= r; x++) {
            int at = y * w + x;
            boolean core = x < r && (g[at] & 255) <= shade && !context.containsKey(at);
            if (core && first < 0) first = x;
            if (!core && first >= 0) {
                out.add(new Band(first, x - 1));
                first = -1;
            }
        }
        return out;
    }

    /** Literal transverse joint contacts. Context samples are never visited as glyph nodes.
     * Each step changes y exactly once and x by at most one: no horizontal rail walk,
     * no guessed ink/paper bridge and no interpolation from a bounding envelope. */
    private static Set<Integer> transverseContacts(
            byte[] g,
            int w,
            Map<Integer, Integer> context,
            int x,
            int y,
            int direction,
            int shade,
            int l,
            int r,
            int top,
            int bottom,
            int[] work,
            Work global) {
        var frontier = new HashSet<Integer>();
        frontier.add(x);
        var exits = new HashSet<Integer>();
        for (int yy = y + direction;
                yy >= top && yy <= bottom && !frontier.isEmpty();
                yy += direction) {
            var next = new HashSet<Integer>();
            for (int prior : frontier)
                for (int dx = -1; dx <= 1; dx++) {
                    global.take(1);
                    if (++work[0] > 200000) return null;
                    int xx = prior + dx;
                    if (xx < l || xx >= r) continue;
                    int at = yy * w + xx;
                    if (context.containsKey(at)) {
                        if ((g[at] & 255) != context.get(at)) return Set.of();
                        next.add(xx);
                    } else if ((g[at] & 255) <= shade) exits.add(at);
                }
            frontier = next;
        }
        return exits;
    }

    /** Complete literal lower contour anchored to the actual preceding f frontier.
     * Returning hooks may connect through later raw rows; no enclosing run rectangle is used. */
    private static Set<Integer> lowerReachable(
            byte[] g,
            int w,
            Map<Integer, Integer> context,
            List<Band> previous,
            int previousY,
            int shade,
            int l,
            int r,
            int bottom,
            int[] work,
            Work global) {
        var seen = new HashSet<Integer>();
        var todo = new ArrayDeque<Integer>();
        for (var p : previous)
            for (int x = p.first(); x <= p.last(); x++) {
                int at = previousY * w + x;
                if (!context.containsKey(at) && (g[at] & 255) <= shade) {
                    seen.add(at);
                    todo.add(at);
                }
            }
        while (!todo.isEmpty()) {
            int at = todo.remove(), x = at % w, y = at / w;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    global.take(1);
                    if (++work[0] > 200000) return null;
                    int xx = x + dx, yy = y + dy;
                    if (xx < l || xx >= r || yy < previousY || yy > bottom) continue;
                    int z = yy * w + xx;
                    if (context.containsKey(z)) {
                        if (dy == 0) continue;
                        var exits =
                                transverseContacts(
                                        g, w, context, x, y, dy, shade, l, r, previousY, bottom,
                                        work, global);
                        if (exits == null) return null;
                        for (int exit : exits) if (seen.add(exit)) todo.add(exit);
                    } else if ((g[z] & 255) <= shade && seen.add(z)) todo.add(z);
                }
        }
        return seen;
    }

    private static boolean wholeKnownRow(
            Map<Integer, Integer> context, int w, int y, int l, int r) {
        for (int x = l; x < r; x++) if (!context.containsKey(y * w + x)) return false;
        return true;
    }

    /** All maximal narrow four-run seed intervals are examined. An observed common junction,
     * not the first accidental four-run row, supplies anatomical identity. Every successful
     * interpretation must agree on that junction; failed or competing interpretations are not selected. */
    private static Level level(
            byte[] g,
            int w,
            int l,
            int t,
            int r,
            int b,
            float gap,
            Map<Integer, Integer> context,
            int shade,
            Work work) {
        work.take((long) (r - l) * (b - t));
        var rows = new ArrayList<List<Band>>();
        int min = b, max = -1;
        for (int y = t; y < b; y++) {
            var a = runs(g, w, y, l, r, shade, context);
            rows.add(a);
            if (!a.isEmpty()) {
                min = Math.min(min, y);
                max = y;
            }
            if (a.size() > 4) return fail(shade, "additional-raw-core-branch", min, max, -1, -1);
        }
        Level accepted = null, firstFailure = null;
        int acceptedJunction = -1;
        for (int y = t; y < b; ) {
            var a = rows.get(y - t);
            boolean seed = a.size() == 4;
            for (var q : a) seed &= q.width() <= gap * .5f;
            if (!seed) {
                y++;
                continue;
            }
            int first = y;
            while (++y < b) {
                var z = rows.get(y - t);
                boolean good = z.size() == 4;
                for (var q : z) good &= q.width() <= gap * .5f;
                if (!good) break;
            }
            int last = y - 1;
            if (last <= first) continue;
            work.take(32L * (b - t));
            var parsed =
                    interval(
                            g, w, l, t, r, b, gap, context, shade, first, last, rows, min, max,
                            work);
            if (!parsed.supported()) {
                if (firstFailure == null) firstFailure = parsed;
                continue;
            }
            int junction = commonJunction(rows, t, first, min);
            if (accepted != null && junction != acceptedJunction)
                return fail(shade, "competing-qualified-body-intervals", min, max, first, last);
            if (accepted == null) {
                accepted = parsed;
                acceptedJunction = junction;
            }
        }
        return accepted != null
                ? accepted
                : firstFailure != null
                        ? firstFailure
                        : fail(
                                shade,
                                "four-independent-leg-trajectories-missing",
                                min,
                                max,
                                -1,
                                -1);
    }

    /** Trace all observed ancestral runs, allowing only literal adjacent-row mergers.
     * Every run on both rows must participate: no discarded side branch or inferred bridge. */
    private static int commonJunction(List<List<Band>> rows, int t, int first, int min) {
        var lower = rows.get(first - t);
        for (int y = first - 1; y >= min; y--) {
            var upper = rows.get(y - t);
            if (upper.isEmpty() || upper.size() > 4) return -1;
            for (var a : upper) {
                boolean contact = false;
                for (var z : lower)
                    contact |= a.first() <= z.last() + 1 && z.first() <= a.last() + 1;
                if (!contact) return -1;
            }
            for (var z : lower) {
                boolean contact = false;
                for (var a : upper)
                    contact |= a.first() <= z.last() + 1 && z.first() <= a.last() + 1;
                if (!contact) return -1;
            }
            if (upper.size() == 1) return y;
            lower = upper;
        }
        return -1;
    }

    private static Level interval(
            byte[] g,
            int w,
            int l,
            int t,
            int r,
            int b,
            float gap,
            Map<Integer, Integer> context,
            int shade,
            int seedFirst,
            int seedLast,
            List<List<Band>> rows,
            int min,
            int max,
            Work work) {
        work.take(16L * (b - t + r - l));
        int first = seedFirst, last = seedLast;
        int[] contactWork = {0};
        if (first < 0 || last <= first)
            return fail(shade, "four-independent-leg-trajectories-missing", min, max, first, last);
        // Touching the read bounds is allowed only when literal original neighbours show no clipped
        // dark continuation.
        for (int y = t; y < b; y++) {
            if (l > 0
                    && (g[y * w + l] & 255) <= shade
                    && (g[y * w + l - 1] & 255) <= shade
                    && !context.containsKey(y * w + l))
                return fail(shade, "read-boundary-clips-core", min, max, first, last);
            if (r < w
                    && (g[y * w + r - 1] & 255) <= shade
                    && (g[y * w + r] & 255) <= shade
                    && !context.containsKey(y * w + r - 1))
                return fail(shade, "read-boundary-clips-core", min, max, first, last);
        }
        for (int x = l; x < r; x++) {
            if (t > 0
                    && (g[t * w + x] & 255) <= shade
                    && (g[(t - 1) * w + x] & 255) <= shade
                    && !context.containsKey(t * w + x))
                return fail(shade, "read-boundary-clips-core", min, max, first, last);
            if ((long) b * w + x < g.length
                    && (g[(b - 1) * w + x] & 255) <= shade
                    && (g[b * w + x] & 255) <= shade
                    && !context.containsKey((b - 1) * w + x))
                return fail(shade, "read-boundary-clips-core", min, max, first, last);
        }
        var initial = rows.get(first - t);
        var previous = initial;
        for (int y = first + 1; y <= last; y++) {
            var a = rows.get(y - t);
            if (a.isEmpty() && wholeKnownRow(context, w, y, l, r)) continue;
            if (a.size() != 4)
                return fail(shade, "interrupted-four-leg-body", min, max, first, last);
            for (int i = 0; i < 4; i++)
                if (a.get(i).first() > previous.get(i).last() + 1
                        || previous.get(i).first() > a.get(i).last() + 1)
                    return fail(shade, "disconnected-leg-step", min, max, first, last);
            previous = a;
        }
        // A real common raw junction is observed above the four stems; no guessed white bridge.
        int junction = commonJunction(rows, t, first, min);
        if (junction < min) return fail(shade, "common-junction-missing", min, max, first, last);
        var join =
                rows.get(junction - t)
                        .get(
                                0); // All four seed paths reach this actual junction through
                                    // commonJunction; no envelope shortcut.
        // The independent f ascender occupies every exposed row above the common junction.
        int topCapEnd = min + Math.round((max - min + 1) * .2f);
        for (int y = min; y < junction; y++) {
            var a = rows.get(y - t);
            if (a.isEmpty() && wholeKnownRow(context, w, y, l, r)) continue;
            if (a.isEmpty()) return fail(shade, "disconnected-upper-body", min, max, first, last);
            if (y <= topCapEnd && (a.size() > 2 || a.get(0).first() <= initial.get(2).last()))
                return fail(shade, "extra-upper-cap", min, max, first, last);
        }
        // All exposed upper body must have literal raw continuation down to the common junction.
        var seen = new HashSet<Integer>();
        var todo = new ArrayDeque<Integer>();
        for (int x = join.first(); x <= join.last(); x++) {
            seen.add(junction * w + x);
            todo.add(junction * w + x);
        }
        while (!todo.isEmpty()) {
            int at = todo.remove(), x = at % w, y = at / w;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    work.take(1);
                    int xx = x + dx, yy = y + dy;
                    if (xx < l || xx >= r || yy < min || yy > junction) continue;
                    int z = yy * w + xx;
                    if (context.containsKey(z)) {
                        if (dy == 0)
                            continue; // No lateral walk through a staff/curve may manufacture a
                                      // glyph connection.
                        var exits =
                                transverseContacts(
                                        g,
                                        w,
                                        context,
                                        x,
                                        y,
                                        dy,
                                        shade,
                                        l,
                                        r,
                                        min,
                                        junction,
                                        contactWork,
                                        work);
                        if (exits == null)
                            return fail(
                                    shade,
                                    "transverse-contact-resource-limit",
                                    min,
                                    max,
                                    first,
                                    last);
                        for (int exit : exits) if (seen.add(exit)) todo.add(exit);
                    } else if ((g[z] & 255) <= shade && seen.add(z)) todo.add(z);
                }
        }
        for (int y = min; y < junction; y++)
            for (var q : rows.get(y - t))
                for (int x = q.first(); x <= q.last(); x++) {
                    work.take(1);
                    if (!seen.contains(y * w + x))
                        return fail(shade, "detached-upper-core", min, max, first, last);
                }
        // Three legs end together; fourth continues as the independent f descender.
        int[] end = {-1, -1, -1, -1};
        var active = new Band[4];
        for (int i = 0; i < 4; i++) active[i] = previous.get(i);
        List<Band> fFrontier = List.of(previous.get(3));
        int fFrontierY = last;
        Set<Integer> fReachable = null;
        for (int y = last + 1; y <= max; y++) {
            var next = rows.get(y - t);
            if (next.isEmpty() && wholeKnownRow(context, w, y, l, r)) continue;
            if (active[0] == null && active[1] == null && active[2] == null) {
                if (next.isEmpty() || next.size() > 2)
                    return fail(shade, "unowned-f-descender-branch", min, max, first, last);
                for (var q : next) {
                    if (q.first() < initial.get(0).first() || q.last() > initial.get(3).last())
                        return fail(
                                shade, "f-descender-outside-observed-body", min, max, first, last);
                    if (fReachable == null) {
                        fReachable =
                                lowerReachable(
                                        g,
                                        w,
                                        context,
                                        fFrontier,
                                        fFrontierY,
                                        shade,
                                        l,
                                        r,
                                        max,
                                        contactWork,
                                        work);
                        if (fReachable == null)
                            return fail(
                                    shade,
                                    "transverse-contact-resource-limit",
                                    min,
                                    max,
                                    first,
                                    last);
                    }
                    for (int x = q.first(); x <= q.last(); x++) {
                        work.take(1);
                        if (!fReachable.contains(y * w + x))
                            return fail(shade, "disconnected-f-descender", min, max, first, last);
                    }
                }
                fFrontier = List.copyOf(next);
                fFrontierY = y;
                active[3] = new Band(next.get(0).first(), next.get(next.size() - 1).last());
                continue;
            }
            int[] claims = new int[next.size()];
            for (int i = 0; i < 4; i++) {
                if (active[i] == null) continue;
                int found = -1;
                for (int j = 0; j < next.size(); j++)
                    if (next.get(j).first() <= active[i].last() + 1
                            && active[i].first() <= next.get(j).last() + 1) {
                        if (found >= 0) return fail(shade, "descender-fork", min, max, first, last);
                        found = j;
                    }
                if (found < 0) {
                    end[i] = y - 1;
                    active[i] = null;
                } else {
                    claims[found]++;
                    active[i] = next.get(found);
                }
            }
            for (int n : claims)
                if (n != 1)
                    return fail(shade, "unowned-lower-core-or-merge", min, max, first, last);
            if (active[3] != null) {
                fFrontier = List.of(active[3]);
                fFrontierY = y;
            }
        }
        if (end[0] < last || end[1] < last || end[2] < last || active[3] == null)
            return fail(shade, "mezzo-end-or-f-continuation-missing", min, max, first, last);
        int maxWidth = 0;
        for (int i = 0; i < 3; i++) maxWidth = Math.max(maxWidth, previous.get(i).width());
        if (Math.max(end[0], Math.max(end[1], end[2])) - Math.min(end[0], Math.min(end[1], end[2]))
                > maxWidth)
            return fail(shade, "unequal-independent-mezzo-ends", min, max, first, last);
        if (active[3].last() >= rows.get(min - t).get(0).first())
            return fail(shade, "f-descender-return-missing", min, max, first, last);
        int[][] legs = new int[4][2];
        for (int i = 0; i < 4; i++)
            legs[i] = new int[] {initial.get(i).first(), initial.get(i).last()};
        return new Level(shade, true, "complete-observed-body", min, max, first, last, legs);
    }
}

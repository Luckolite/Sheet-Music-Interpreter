// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.function.IntPredicate;

/** Preservation only. One-level254/255 contrast is sufficient; this never creates a note.
 * Semantic ellipses alone are not physical head evidence. They additionally need a
 * contrasted filled core, distributed faint body evidence, or an independently visible
 * physical silhouette. Tiny/partial, hollow and attached-curve heads use the same bounds.
 */
final class FaintHeadBodyPreserver {
    // Ordinary notehead families follow the independently established staff axis;
    // explicit cross/diamond/unpitched/recovery admissions are separate KEEP guards.
    private static final int[][] BASES = {
        {1, 0}, {3, 1}, {2, 1}, {1, 1}, {3, -1}, {2, -1}, {1, -1}, {0, 1}
    };

    static boolean supported(
            byte[] gray,
            int width,
            int height,
            int[] completeMembers,
            float gap,
            IntPredicate observedCarrier,
            SlurEvidenceBudget budget) {
        return supported(gray, width, height, completeMembers, gap, observedCarrier, budget, null);
    }

    static boolean supported(
            byte[] gray,
            int width,
            int height,
            int[] completeMembers,
            float gap,
            IntPredicate observedCarrier,
            SlurEvidenceBudget budget,
            StringBuilder trace) {
        if (gray == null
                || completeMembers == null
                || completeMembers.length < 3
                || width <= 0
                || height <= 0
                || (long) width * height != gray.length
                || !Float.isFinite(gap)
                || gap < 6
                || budget == null)
            throw new SlurEvidenceBudget.Unknown("FAINT_HEAD_SOURCE_UNKNOWN");
        budget.use(completeMembers.length, 64L * completeMembers.length + 4096);
        var source = new HashSet<Integer>();
        int l = width, r = -1, t = height, b = -1, totalSourceAA = 0, totalSourceWhite = 0;
        for (int p : completeMembers) {
            if (p < 0 || p >= gray.length || !source.add(p))
                throw new SlurEvidenceBudget.Unknown("FAINT_HEAD_MEMBERS_UNKNOWN");
            l = Math.min(l, p % width);
            r = Math.max(r, p % width);
            t = Math.min(t, p / width);
            b = Math.max(b, p / width);
            if ((gray[p] & 255) >= 165 && (gray[p] & 255) < 255) totalSourceAA++;
            if ((gray[p] & 255) == 255) totalSourceWhite++;
        }
        int minW = (int) Math.ceil(Math.max(2f, gap * .38f)),
                minH = (int) Math.ceil(Math.max(2f, gap * .30f));
        int maxW = (int) Math.floor(gap * 3.2f), maxH = (int) Math.floor(gap * 3.2f);
        int stroke = Math.max(2, Math.round(gap * .42f));
        // Three exact ten-bit counters share each cache cell. Larger native
        // domains retain KEEP/UNKNOWN instead of overflowing that representation.
        if ((2L * Math.round(maxW * .2f) + 1) * (2L * Math.round(maxH * .25f) + 1) > 1023)
            throw new SlurEvidenceBudget.Unknown("FAINT_CORE_COUNTER_STORAGE_UNKNOWN");
        budget.use(1, 1024L * maxW * maxH + 65536);
        // This additive hollow route needs source WHITE evidence. Unrelated
        // receiving-head pockets are not evidence for this candidate. Existing
        // whole-pocket/raw/open admissions remain independent caller KEEP guards.
        boolean possibleWhite =
                totalSourceWhite > 0
                        && possibleWhiteCounter(gray, width, height, l, r, t, b, maxW, budget);
        int centersX = 2 * (r - l) + 1,
                centersY = 2 * (b - t) + 1,
                coreYs = Math.round(maxH * .25f) + 1;
        long cacheSize =
                (long) (Math.round(maxW * .2f) + 1) * coreYs * BASES.length * centersX * centersY;
        if (cacheSize > Integer.MAX_VALUE)
            throw new SlurEvidenceBudget.Unknown("FAINT_CORE_CACHE_UNKNOWN");
        budget.use(0, 4 * cacheSize);
        int[] coreCache = new int[(int) cacheSize];
        // No rank/tone/paper threshold of25 is imposed. The native ordinary-head
        // bounds are preservation proposals, not a negative physical-head theorem.
        for (int bw = minW; bw <= maxW; bw++)
            for (int bh = minH; bh <= Math.min(maxH, bw); bh++)
                for (int bi = 0; bi < BASES.length; bi++)
                    for (int cy2 = 2 * t; cy2 <= 2 * b; cy2++)
                        for (int cx2 = 2 * l; cx2 <= 2 * r; cx2++) {
                            budget.use(1, 0);
                            int[] basis = BASES[bi];
                            int a = basis[0], d = basis[1], norm = a * a + d * d;
                            int rx =
                                    1
                                            + (int)
                                                    Math.ceil(
                                                            Math.sqrt(
                                                                            (double) a * a * bw * bw
                                                                                    + (double) d * d
                                                                                            * bh
                                                                                            * bh)
                                                                    / (2 * Math.sqrt(norm)));
                            int ry =
                                    1
                                            + (int)
                                                    Math.ceil(
                                                            Math.sqrt(
                                                                            (double) d * d * bw * bw
                                                                                    + (double) a * a
                                                                                            * bh
                                                                                            * bh)
                                                                    / (2 * Math.sqrt(norm)));
                            int left = Math.max(0, cx2 / 2 - rx),
                                    right = Math.min(width - 1, cx2 / 2 + rx);
                            int top = Math.max(0, cy2 / 2 - ry),
                                    bottom = Math.min(height - 1, cy2 / 2 + ry);
                            boolean possiblePartialAA = totalSourceAA >= Math.ceil(bw * bh * .4);
                            // A complete source-backed ellipse must fit its source bounds.
                            // These four axis extrema are literal lattice members, rather
                            // than a font-size cutoff or an assumed head contrast.
                            boolean canBeComplete = true;
                            for (int[] point :
                                    new int[][] {
                                        {l - 1, cy2 / 2},
                                        {l - 1, (cy2 + 1) / 2},
                                        {r + 1, cy2 / 2},
                                        {r + 1, (cy2 + 1) / 2},
                                        {cx2 / 2, t - 1},
                                        {(cx2 + 1) / 2, t - 1},
                                        {cx2 / 2, b + 1},
                                        {(cx2 + 1) / 2, b + 1}
                                    }) {
                                int x = point[0], y = point[1];
                                budget.use(1, 0);
                                long dx = 2L * x - cx2,
                                        dy = 2L * y - cy2,
                                        u = a * dx + d * dy,
                                        v = -d * dx + a * dy;
                                if (u * u * bh * bh + v * v * bw * bw
                                        <= (long) norm * bw * bw * bh * bh) {
                                    canBeComplete = false;
                                    break;
                                }
                            }
                            // Cheap preservation-core test, including the original9x7 cases.
                            int count = 0, filled = 0, pale = 0;
                            float root = (float) Math.sqrt(norm), cx = cx2 * .5f, cy = cy2 * .5f;
                            int coreX = Math.round(bw * .2f), coreY = Math.round(bh * .25f);
                            int expected = (2 * coreX + 1) * (2 * coreY + 1), misses = 0;
                            boolean corePossible = true;
                            int cacheAt =
                                    ((((coreX * coreYs + coreY) * BASES.length + bi) * centersY
                                                            + cy2
                                                            - 2 * t)
                                                    * centersX
                                            + cx2
                                            - 2 * l);
                            int cached = coreCache[cacheAt];
                            if (cached != 0) {
                                corePossible = cached != -1;
                                if (corePossible) {
                                    count = cached & 1023;
                                    filled = (cached >>> 10) & 1023;
                                    pale = (cached >>> 20) & 1023;
                                }
                            } else {
                                coreScan:
                                for (int yy = -coreY; yy <= coreY; yy++)
                                    for (int xx = -coreX; xx <= coreX; xx++) {
                                        int x = Math.round(cx + (a * xx - d * yy) / root),
                                                y = Math.round(cy + (d * xx + a * yy) / root);
                                        budget.use(1, 0);
                                        if (x < 0 || y < 0 || x >= width || y >= height) continue;
                                        int shade = gray[y * width + x] & 255;
                                        count++;
                                        if (shade < 255) filled++;
                                        else misses++;
                                        if (shade >= 145 && shade < 255) pale++;
                                        if (misses > Math.floor(expected * .05)) {
                                            corePossible = false;
                                            break coreScan;
                                        }
                                    }
                                coreCache[cacheAt] =
                                        corePossible ? (count | (filled << 10) | (pale << 20)) : -1;
                            }
                            boolean solidCore =
                                    corePossible && count >= 12 && filled >= Math.ceil(count * .95);
                            boolean filledCore = solidCore && pale >= Math.ceil(count * .5);
                            // Without a filled core, a source-WHITE counter may still preserve
                            // an original tiny hollow head. No filled/default value is inferred.
                            if ((!solidCore || !canBeComplete)
                                    && !filledCore
                                    && !possibleWhite
                                    && !possiblePartialAA) continue;
                            var body = new HashSet<Integer>();
                            int anchors = 0, foreground = 0, quadrants = 0, sourceAA = 0;
                            int[] aaQuadrants = new int[4];
                            boolean completeSemantic = true;
                            long rhs = (long) norm * bw * bw * bh * bh;
                            for (int y = top; y <= bottom; y++)
                                for (int x = left; x <= right; x++) {
                                    long dx = 2L * x - cx2,
                                            dy = 2L * y - cy2,
                                            u = a * dx + d * dy,
                                            v = -d * dx + a * dy;
                                    budget.use(1, 0);
                                    if (u * u * bh * bh + v * v * bw * bw > rhs) continue;
                                    int p = y * width + x;
                                    body.add(p);
                                    boolean member = source.contains(p);
                                    if (member) anchors++;
                                    else completeSemantic = false;
                                    int shade = gray[p] & 255;
                                    if (shade < 255) foreground++;
                                    if (member && shade >= 165 && shade < 255) {
                                        int q = (dx >= 0 ? 1 : 0) + (dy >= 0 ? 2 : 0);
                                        quadrants |= 1 << q;
                                        aaQuadrants[q]++;
                                        sourceAA++;
                                    }
                                }
                            if (anchors < 3 || body.size() < 3) continue;
                            // A small ellipse can be inscribed in a staff/slur blob. A core
                            // alone does not establish its independent outer head body.
                            if (completeSemantic
                                    && quadrants == 15
                                    && foreground >= Math.ceil(body.size() * .95)) {
                                detail(
                                        trace,
                                        "SEMANTIC_FAINT_FOUR_QUADRANTS",
                                        bw,
                                        bh,
                                        cx2,
                                        cy2,
                                        a,
                                        d,
                                        body.size(),
                                        pale,
                                        count);
                                return true;
                            }
                            int supportedQuadrants = 0;
                            for (int n : aaQuadrants) if (n >= 2) supportedQuadrants++;
                            if (sourceAA >= Math.ceil(bw * bh * .4) && supportedQuadrants >= 3) {
                                detail(
                                        trace,
                                        "SOURCE_DISTRIBUTED_FAINT_PARTIAL_BODY",
                                        bw,
                                        bh,
                                        cx2,
                                        cy2,
                                        a,
                                        d,
                                        body.size(),
                                        pale,
                                        count);
                                return true;
                            }
                            boolean whiteCounter =
                                    possibleWhite && closedWhite(gray, width, body, budget);
                            if (completeSemantic && whiteCounter) {
                                detail(
                                        trace,
                                        "SOURCE_WHITE_CLOSED_COUNTER",
                                        bw,
                                        bh,
                                        cx2,
                                        cy2,
                                        a,
                                        d,
                                        body.size(),
                                        pale,
                                        count);
                                return true;
                            }
                            boolean relativeCounter =
                                    solidCore && relativeCounter(gray, width, body, budget);
                            // Do not invent a new arbitrary dark partial-ellipse admission.
                            // Existing raw/recovery/subset admissions remain separate KEEP
                            // guards. This additive route preserves a complete source-backed
                            // body, or independently visible low-contrast partial engraving.
                            // A complete filled-body claim cannot turn literal paper into
                            // ink. Partial faint semantic evidence and closed WHITE
                            // counters have separate preservation routes above.
                            if ((solidCore
                                                    && (completeSemantic || filledCore)
                                                    && !relativeCounter
                                                    && foreground == body.size()
                                            || whiteCounter)
                                    && visibleSilhouette(
                                            gray,
                                            width,
                                            height,
                                            body,
                                            stroke,
                                            observedCarrier,
                                            budget,
                                            trace)) {
                                if (trace != null)
                                    trace.append("BODY_SEMANTIC_ANCHORS=")
                                            .append(anchors)
                                            .append('\n');
                                detail(
                                        trace,
                                        "INDEPENDENT_VISIBLE_BODY",
                                        bw,
                                        bh,
                                        cx2,
                                        cy2,
                                        a,
                                        d,
                                        body.size(),
                                        pale,
                                        count);
                                return true;
                            }
                        }
        return false;
    }

    private static void detail(
            StringBuilder trace,
            String kind,
            int w,
            int h,
            int cx2,
            int cy2,
            int a,
            int b,
            int body,
            int pale,
            int core) {
        if (trace != null)
            trace.append(kind)
                    .append(" w=")
                    .append(w)
                    .append(" h=")
                    .append(h)
                    .append(" center2=")
                    .append(cx2)
                    .append(',')
                    .append(cy2)
                    .append(" basis=")
                    .append(a)
                    .append(',')
                    .append(b)
                    .append(" body=")
                    .append(body)
                    .append(" pale/core=")
                    .append(pale)
                    .append('/')
                    .append(core)
                    .append('\n');
    }

    private static boolean possibleWhiteCounter(
            byte[] gray,
            int width,
            int height,
            int l,
            int r,
            int t,
            int b,
            int maximumAxis,
            SlurEvidenceBudget budget) {
        int left = Math.max(0, l - maximumAxis - 2),
                right = Math.min(width - 1, r + maximumAxis + 2),
                top = Math.max(0, t - maximumAxis - 2),
                bottom = Math.min(height - 1, b + maximumAxis + 2),
                sw = right - left + 1,
                sh = bottom - top + 1;
        long area = (long) sw * sh;
        if (area > Integer.MAX_VALUE)
            throw new SlurEvidenceBudget.Unknown("WHITE_FIELD_DOMAIN_UNKNOWN");
        budget.use(area, 5 * area + 1024);
        boolean[] seen = new boolean[(int) area];
        int[] queue = new int[(int) area];
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                int at = (y - top) * sw + x - left;
                if (seen[at] || (gray[y * width + x] & 255) != 255) continue;
                int first = 0, last = 1;
                queue[0] = at;
                seen[at] = true;
                boolean edge = false, within = true;
                while (first < last) {
                    int p = queue[first++], px = left + p % sw, py = top + p / sw;
                    budget.use(4, 0);
                    edge |= px == left || px == right || py == top || py == bottom;
                    long dx = Math.max(l - px, Math.max(0, px - r)),
                            dy = Math.max(t - py, Math.max(0, py - b));
                    if (4 * (dx * dx + dy * dy) > (long) maximumAxis * maximumAxis) within = false;
                    for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                        int xx = px + d[0], yy = py + d[1];
                        if (xx < left || xx > right || yy < top || yy > bottom) continue;
                        int q = (yy - top) * sw + xx - left;
                        if (!seen[q] && (gray[yy * width + xx] & 255) == 255) {
                            seen[q] = true;
                            queue[last++] = q;
                        }
                    }
                }
                if (!edge && within) return true;
            }
        return false;
    }

    private static boolean closedWhite(
            byte[] gray, int width, Set<Integer> body, SlurEvidenceBudget budget) {
        var pending = new HashSet<Integer>();
        for (int p : body) if ((gray[p] & 255) == 255) pending.add(p);
        while (!pending.isEmpty()) {
            var queue = new ArrayDeque<Integer>();
            int first = pending.iterator().next();
            pending.remove(first);
            queue.add(first);
            boolean edge = false;
            while (!queue.isEmpty()) {
                int p = queue.remove();
                budget.use(4, 0);
                for (int q : new int[] {p - 1, p + 1, p - width, p + width}) {
                    if (!body.contains(q)) edge = true;
                    if (pending.remove(q)) queue.add(q);
                }
            }
            if (!edge) return true;
        }
        return false;
    }

    /** Same relative-counter veto as the existing compact filled-body law. */
    private static boolean relativeCounter(
            byte[] gray, int width, Set<Integer> body, SlurEvidenceBudget budget) {
        int l = width, r = -1, t = Integer.MAX_VALUE, b = -1;
        for (int p : body) {
            l = Math.min(l, p % width);
            r = Math.max(r, p % width);
            t = Math.min(t, p / width);
            b = Math.max(b, p / width);
        }
        int sw = r - l + 1, sh = b - t + 1, n = sw * sh;
        int[] histogram = new int[256];
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                budget.use(1, 0);
                histogram[gray[y * width + x] & 255]++;
            }
        int dark = 0, total = 0;
        for (; dark < 255; dark++) if ((total += histogram[dark]) >= n * .1f) break;
        int threshold = Math.round((dark + 255) * .5f), enclosed = 0, central = 0;
        boolean[] rows = new boolean[sh], seen = new boolean[n];
        int[] queue = new int[n];
        for (int seed = 0; seed < n; seed++) {
            if (seen[seed] || (gray[(t + seed / sw) * width + l + seed % sw] & 255) < threshold)
                continue;
            int first = 0, last = 1;
            queue[0] = seed;
            seen[seed] = true;
            boolean edge = false;
            while (first < last) {
                int p = queue[first++], x = p % sw, y = p / sw;
                edge |= x == 0 || y == 0 || x == sw - 1 || y == sh - 1;
                budget.use(8, 0);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= sw || yy < 0 || yy >= sh) continue;
                        int q = yy * sw + xx;
                        if (!seen[q] && (gray[(t + yy) * width + l + xx] & 255) >= threshold) {
                            seen[q] = true;
                            queue[last++] = q;
                        }
                    }
            }
            if (edge || last < 2) continue;
            enclosed += last;
            for (int i = 0; i < last; i++) {
                int x = queue[i] % sw, y = queue[i] / sw;
                if (Math.abs(x - (sw - 1) * .5f) <= sw * .3f
                        && Math.abs(y - (sh - 1) * .5f) <= sh * .35f) {
                    central++;
                    rows[y] = true;
                }
            }
        }
        int rowCount = 0;
        for (boolean row : rows) if (row) rowCount++;
        return enclosed >= Math.max(4, Math.round(n * .03f)) && central >= 3 && rowCount >= 2;
    }

    private static boolean visibleSilhouette(
            byte[] gray,
            int width,
            int height,
            Set<Integer> body,
            int stroke,
            IntPredicate carrier,
            SlurEvidenceBudget budget,
            StringBuilder trace) {
        var contacts = new HashSet<Integer>();
        int l = width, r = -1, t = height, b = -1;
        for (int p : body) {
            l = Math.min(l, p % width);
            r = Math.max(r, p % width);
            t = Math.min(t, p / width);
            b = Math.max(b, p / width);
        }
        int paperQuadrants = 0;
        for (int p : body)
            for (int q : new int[] {p - 1, p + 1, p - width, p + width}) {
                budget.use(1, 0);
                if (q < 0 || q >= gray.length) return false;
                if (body.contains(q)) continue;
                if ((gray[q] & 255) == 255) {
                    paperQuadrants |=
                            1
                                    << ((2 * (q % width) >= l + r ? 1 : 0)
                                            + (2 * (q / width) >= t + b ? 2 : 0));
                    continue;
                }
                if (carrier != null && carrier.test(q)) continue;
                contacts.add(q);
            }
        while (!contacts.isEmpty()) {
            var queue = new ArrayDeque<Integer>();
            var component = new ArrayList<Integer>();
            int first = contacts.iterator().next();
            contacts.remove(first);
            queue.add(first);
            while (!queue.isEmpty()) {
                int p = queue.remove();
                component.add(p);
                budget.use(8, 0);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int q = p + dy * width + dx;
                        if (contacts.remove(q)) queue.add(q);
                    }
            }
            for (int p : component)
                for (int q : component) {
                    budget.use(1, 0);
                    long dx = p % width - q % width, dy = p / width - q / width;
                    if (dx * dx + dy * dy > (long) stroke * stroke) return false;
                }
        }
        if (trace != null && trace.length() < 8192)
            trace.append("VISIBLE_BOUNDARY_PAPER_QUADRANTS=").append(paperQuadrants).append('\n');
        return paperQuadrants == 15;
    }
}

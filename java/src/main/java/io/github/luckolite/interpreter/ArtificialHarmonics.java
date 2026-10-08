// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A stopped note plus a hollow diamond a fourth above sounds two octaves higher. */
final class ArtificialHarmonics {
    private ArtificialHarmonics() {}

    private static final class ProbeSigns {
        private static final int[] VALUES = {-1, 1};
    }

    private static final class DiamondSizes {
        private static final float[] VALUES = {.5f, .6f, .7f, .8f};
    }

    static List<ScoreNoteEvent> apply(
            byte[] gray,
            int w,
            int h,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            List<PlayingTechniqueDetector.Staff> staffs) {
        return apply(null, gray, w, h, measures, notes, staffs);
    }

    static List<ScoreNoteEvent> apply(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            List<PlayingTechniqueDetector.Staff> staffs) {
        if (gray == null || notes.isEmpty()) return notes;
        var replacements = new HashMap<ScoreNoteEvent, ScoreNoteEvent>();
        var remove = new HashSet<ScoreNoteEvent>();
        for (var n : notes) {
            if (n.kind() != ScoreNoteEvent.Kind.PITCHED
                    || n.octaveShift() != 0
                    || n.measureIndex() < 0
                    || n.measureIndex() >= measures.size()) continue;
            var m = measures.get(n.measureIndex());
            float x = (m.left() + n.positionInMeasure() * (m.right() - m.left())) * w,
                    y = n.pageY() * h;
            PlayingTechniqueDetector.Staff staff = null;
            for (var s : staffs)
                if (s.index() == n.staffIndex()
                        && s.top() / h >= m.top() - .02f
                        && s.bottom() / h <= m.bottom() + .02f) {
                    staff = s;
                    break;
                }
            if (staff == null) continue;
            float gap = staff.gap();

            boolean unpitchedTouch = false;
            for (var upper : notes)
                if (upper.kind() == ScoreNoteEvent.Kind.UNPITCHED
                        && upper.measureIndex() == n.measureIndex()
                        && upper.staffIndex() == n.staffIndex()
                        && upper.staffStep() - n.staffStep() == 3
                        && Math.abs(upper.pageY() * h - (y - 1.5f * gap)) < gap * .35f
                        && Math.abs(
                                        (upper.positionInMeasure() - n.positionInMeasure())
                                                * (m.right() - m.left())
                                                * w)
                                < gap * .55f) unpitchedTouch = true;
            if (unpitchedTouch) continue;
            ScoreNoteEvent pairedTouch = null;
            for (var upper : notes)
                if (upper.kind() == ScoreNoteEvent.Kind.PITCHED
                        && upper != n
                        && upper.measureIndex() == n.measureIndex()
                        && upper.staffIndex() == n.staffIndex()
                        && upper.staffStep() - n.staffStep() == 3
                        && Math.abs(upper.pageY() * h - (y - 1.5f * gap)) < gap * .35f
                        && Math.abs(
                                        (upper.positionInMeasure() - n.positionInMeasure())
                                                * (m.right() - m.left())
                                                * w)
                                < gap * .55f) {
                    pairedTouch = upper;
                    break;
                }
            boolean strictDiamond = diamond(gray, w, h, x, y - 1.5f * gap, gap);
            boolean symbolTouch =
                    pairedTouch == null
                            && n.beamCount() >= 2
                            && n.unbeamedDurationBeats() < ScoreNoteEvent.DURATION_HALF
                            && symbolDiamond(labels, gray, w, h, x, y - 1.5f * gap, gap);
            if (!strictDiamond
                    && !symbolTouch
                    && !(pairedTouch != null
                            && n.beamCount() >= 2
                            && ((pairedTouch.beamCount() == 0
                                            && pairedTouch.unbeamedDurationBeats()
                                                    >= ScoreNoteEvent.DURATION_HALF)
                                    || pairedTouch.beamCount() >= 2)
                            && diamond(gray, w, h, x, y - 1.5f * gap, gap, true))) continue;
            // Require the stopped stem, or a paired beamed lower note when a crowded touch
            // diamond interrupts that stem and the decoded upper fourth is a spurious half note.
            if (!stem(
                            gray,
                            w,
                            h,
                            x,
                            y,
                            gap,
                            n.unbeamedDurationBeats() < ScoreNoteEvent.DURATION_HALF)
                    && !(symbolTouch && paleJoinedStem(gray, w, h, x, y, gap))
                    && !(pairedTouch != null
                            && n.beamCount() >= 2
                            && !strictDiamond
                            && pairedTouch.beamCount() == 0
                            && pairedTouch.unbeamedDurationBeats() >= ScoreNoteEvent.DURATION_HALF))
                continue;
            replacements.put(n, n.withOctaveShift(2));
            for (var upper : notes)
                if (upper.kind() == ScoreNoteEvent.Kind.PITCHED
                        && upper != n
                        && upper.measureIndex() == n.measureIndex()
                        && upper.staffIndex() == n.staffIndex()
                        && upper.staffStep() - n.staffStep() == 3
                        && Math.abs(upper.pageY() * h - (y - 1.5f * gap)) < gap * .35f
                        && Math.abs(
                                        (upper.positionInMeasure() - n.positionInMeasure())
                                                * (m.right() - m.left())
                                                * w)
                                < gap * .55f) remove.add(upper);
        }
        if (replacements.isEmpty()) return notes;
        var result = new ArrayList<ScoreNoteEvent>();
        for (var n : notes) if (!remove.contains(n)) result.add(replacements.getOrDefault(n, n));
        return List.copyOf(result);
    }

    private static boolean symbolDiamond(
            byte[] labels, byte[] gray, int w, int h, float x, float y, float gap) {
        return labels != null
                && labels.length == (long) w * h
                && diamondEvidence(gray, labels, w, h, x, y, gap, true);
    }

    private static boolean paleJoinedStem(byte[] gray, int w, int h, float x, float y, float gap) {
        int[] paper = new int[256];
        int samples = 0;
        for (int yy = Math.max(0, Math.round(y - gap * 1.1f));
                yy <= Math.min(h - 1, Math.round(y + gap * 1.2f));
                yy++)
            for (int sign : ProbeSigns.VALUES) {
                int xx = Math.round(x + sign * gap * 1.2f);
                if (xx >= 0 && xx < w) {
                    paper[gray[yy * w + xx] & 255]++;
                    samples++;
                }
            }
        int background = 0, count = 0;
        for (; background < 255; background++) {
            count += paper[background];
            if (count >= Math.max(1, (samples * 3 + 3) / 4)) break;
        }
        int limit = Math.min(210, background - 45);
        if (samples < 8 || limit <= 165) return false;
        for (int direction : ProbeSigns.VALUES)
            for (int offset = Math.round(gap * .3f); offset <= Math.round(gap * .95f); offset++) {
                int xx = Math.round(x) - direction * offset, countStem = 0, total = 0;
                for (int distance = Math.round(gap * .2f);
                        distance <= Math.round(gap * 1.2f);
                        distance++) {
                    int yy = Math.round(y) + direction * distance;
                    total++;
                    if (ink(gray, w, h, xx, yy, limit)) countStem++;
                }
                if (total < 4 || countStem < total * .9f) continue;
                int joined = 0, span = 0, missing = 0, longestGap = 0;
                // A lower stem and touch diamond must share the same actual ink column.
                for (int yy = Math.round(y - gap * 1.05f); yy <= Math.round(y - gap * .3f); yy++) {
                    span++;
                    if (ink(gray, w, h, xx, yy, limit)) {
                        joined++;
                        missing = 0;
                    } else {
                        missing++;
                        longestGap = Math.max(longestGap, missing);
                    }
                }
                if (span >= 4
                        && joined >= span * .7f
                        && longestGap <= Math.max(1, Math.round(gap * .25f))) return true;
            }
        return false;
    }

    private static boolean ink(byte[] gray, int w, int h, int x, int y, int limit) {
        return x >= 0 && x < w && y >= 0 && y < h && (gray[y * w + x] & 255) < limit;
    }

    private static boolean stem(
            byte[] g, int w, int h, float x, float y, float gap, boolean filledStoppedHead) {
        // Both conventional stem directions occur in artificial harmonics.
        for (int direction : ProbeSigns.VALUES) {
            for (int offset = Math.round(gap * .3f); offset <= Math.round(gap * .95f); offset++) {
                // Hollow stopped heads need stronger shape evidence: ordinary open
                // fourths can otherwise resemble a diamond pair in low-resolution scans.
                if (direction > 0 && !filledStoppedHead) continue;
                int xx = Math.round(x) - direction * offset;
                int count = 0, total = 0;
                for (int distance = Math.round(gap * .2f);
                        distance <= Math.round(gap * 1.2f);
                        distance++) {
                    int yy = Math.round(y) + direction * distance;
                    total++;
                    if (dark(g, w, h, xx, yy)) count++;
                }
                if (total > 0 && count >= total * .9) {
                    if (direction < 0) return true;
                    // A down-stem also joins the touch diamond above the stopped head.
                    // A detached fingering zero must not become an artificial harmonic.
                    int joined = 0, span = 0;
                    for (int yy = Math.round(y - gap * 1.05f);
                            yy <= Math.round(y - gap * .3f);
                            yy++) {
                        span++;
                        if (dark(g, w, h, xx, yy)) joined++;
                    }
                    if (span > 0 && joined >= span * .85f) return true;
                }
            }
        }
        return false;
    }

    static boolean diamond(byte[] g, int w, int h, float x, float y, float gap) {
        return diamond(g, w, h, x, y, gap, false);
    }

    private static boolean diamond(
            byte[] g, int w, int h, float x, float y, float gap, boolean crowded) {
        return diamondEvidence(g, null, w, h, x, y, gap, crowded);
    }

    private static boolean diamondEvidence(
            byte[] g, byte[] labels, int w, int h, float x, float y, float gap, boolean crowded) {
        if (gap < 8) return false;
        int horizontal = Math.round(gap * .4f), vertical = Math.round(gap * .25f);
        for (int dx = -horizontal; dx <= horizontal; dx++)
            for (int dy = -vertical; dy <= vertical; dy++)
                for (float size : DiamondSizes.VALUES) {
                    float cx = x + dx, cy = y + dy, r = gap * size;
                    int hit = 0, total = 0, symbolHits = 0;
                    for (int side = 0; side < 4; side++)
                        for (int i = 1; i <= 4; i++) {
                            float t = i / 5f, px = (1 - t) * r, py = t * r;
                            if (side == 1) {
                                px = -px;
                            }
                            if (side == 2) {
                                px = -px;
                                py = -py;
                            }
                            if (side == 3) py = -py;
                            int xx = Math.round(cx + px), yy = Math.round(cy + py);
                            total++;
                            if (dark(g, w, h, xx, yy)
                                    || dark(g, w, h, xx - 1, yy)
                                    || dark(g, w, h, xx + 1, yy)) {
                                hit++;
                                if (labels != null
                                        && (symbol(labels, w, h, xx, yy)
                                                || symbol(labels, w, h, xx - 1, yy)
                                                || symbol(labels, w, h, xx + 1, yy))) symbolHits++;
                            }
                        }
                    if (hit < total * (crowded ? .9f : .94f)) continue;
                    // Model support belongs to the four diamond sides, not neighboring staff/stem
                    // ink.
                    if (labels != null && symbolHits < Math.max(8, (hit + 1) / 2)) continue;
                    // Side samples alone also fit a tilted oval at small staff sizes.
                    // A touch diamond has four actual vertices, including its high and low tips.
                    boolean vertices = true;
                    for (int sign : ProbeSigns.VALUES) {
                        int vx = Math.round(cx + sign * r), vy = Math.round(cy + sign * r);
                        vertices &=
                                dark(g, w, h, vx, Math.round(cy))
                                        || dark(g, w, h, vx, Math.round(cy) - 1)
                                        || dark(g, w, h, vx, Math.round(cy) + 1);
                        vertices &=
                                dark(g, w, h, Math.round(cx), vy)
                                        || dark(g, w, h, Math.round(cx) - 1, vy)
                                        || dark(g, w, h, Math.round(cx) + 1, vy);
                    }
                    if (!vertices) continue;
                    // An enclosed paper counter proves a thick hollow outline even when
                    // its small opening occupies less of a fixed center sample rectangle.
                    if (labels != null
                            && hit == total
                            && enclosedCounter(g, w, h, cx, cy, r)
                            && diamondExterior(g, w, h, cx, cy, r, gap, y + 1.5f * gap))
                        return true;
                    int clear = 0, inside = 0;
                    for (int yy = Math.round(cy - r * .3f); yy <= Math.round(cy + r * .3f); yy++) {
                        // Ignore a staff or ledger line crossing the hollow center.
                        if (dark(g, w, h, Math.round(cx - r - 3), yy)
                                && dark(g, w, h, Math.round(cx + r + 3), yy)) continue;
                        for (int xx = Math.round(cx - r * .25f);
                                xx <= Math.round(cx + r * .25f);
                                xx++) {
                            inside++;
                            if (!dark(g, w, h, xx, yy)) clear++;
                        }
                    }
                    if (inside < 4 || (!crowded && clear < inside * .65)) continue;
                    int outside = 0;
                    for (int a : ProbeSigns.VALUES)
                        for (int b : ProbeSigns.VALUES)
                            if (!dark(
                                    g,
                                    w,
                                    h,
                                    Math.round(cx + a * r * .75f),
                                    Math.round(cy + b * r * .75f))) outside++;
                    if (crowded) {
                        if ((hit == total && clear >= inside * .8)
                                || (hit >= total * .9f && clear >= inside * .6 && outside >= 3))
                            return true;
                    } else if (outside == 4) return true;
                }
        return false;
    }

    private static boolean diamondExterior(
            byte[] gray, int w, int h, float cx, float cy, float r, float gap, float stoppedY) {
        int paper = 0, explained = 0, quadrants = 0, nearQuadrants = 0;
        for (int a : ProbeSigns.VALUES)
            for (int b : ProbeSigns.VALUES) {
                int localPaper = 0, localOwned = 0;
                for (float t : new float[] {.4f, .6f}) {
                    int x = Math.round(cx + a * r * (1 - t) * 1.25f);
                    int y = Math.round(cy + b * r * t * 1.25f);
                    if (!dark(gray, w, h, x, y)) {
                        paper++;
                        localPaper++;
                    } else if (horizontalRule(gray, w, h, y, cx, r)
                            || outwardStemBeforeHead(gray, w, h, x, y, cx, cy, r, gap, stoppedY)) {
                        explained++;
                        localOwned++;
                    }
                }
                if (localPaper > 0) {
                    quadrants++;
                    nearQuadrants++;
                } else if (localOwned == 2
                        && !dark(
                                gray,
                                w,
                                h,
                                Math.round(cx + a * r * 1.1f),
                                Math.round(cy + b * r * 1.1f))) {
                    quadrants++;
                }
            }
        return paper >= 3 && paper + explained >= 6 && quadrants == 4 && nearQuadrants >= 3;
    }

    private static boolean horizontalRule(byte[] gray, int w, int h, int y, float cx, float r) {
        int from = Math.round(cx - r * 1.5f);
        int to = Math.round(cx + r * 1.5f);
        if (!dark(gray, w, h, from, y) || !dark(gray, w, h, to, y)) return false;
        int hits = 0;
        for (int p = from; p <= to; p++) if (dark(gray, w, h, p, y)) hits++;
        if (hits < (to - from + 1) * .9f) return false;
        int limit = Math.max(1, Math.round(r * .5f));
        for (int endpoint : new int[] {from, to}) {
            int thickness = 1;
            for (int direction : ProbeSigns.VALUES)
                for (int d = 1; d <= limit; d++) {
                    if (!dark(gray, w, h, endpoint, y + direction * d)) break;
                    thickness++;
                }
            if (thickness > limit) return false;
        }
        return true;
    }

    private static boolean outwardStemBeforeHead(
            byte[] gray,
            int w,
            int h,
            int x,
            int y,
            float cx,
            float cy,
            float r,
            float gap,
            float stoppedY) {
        int direction = y >= cy ? 1 : -1;
        int endpoint = y + direction * Math.round(r * 2);
        // Measure the stem in the gap between the heads, before its filled-head attachment.
        if (direction > 0) endpoint = Math.min(endpoint, Math.round(stoppedY - gap * .5f));
        int distance = direction * (endpoint - y);
        if (distance < 2) return false;
        if (direction > 0) {
            int from = Math.round(stoppedY + gap * .2f), to = Math.round(stoppedY + gap * 1.2f);
            int stemHits = 0;
            for (int yy = from; yy <= to; yy++) if (dark(gray, w, h, x, yy)) stemHits++;
            if (stemHits < (to - from + 1) * .9f) return false;
        } else if (distance < Math.round(r * .5f)) return false;
        int hits = 0;
        for (int d = 0; d <= distance; d++) if (dark(gray, w, h, x, y + direction * d)) hits++;
        if (hits < (distance + 1) * .9f) return false;
        int limit = Math.max(1, Math.round(r * .5f)), outward = x < cx ? -1 : 1;
        int nearThickness = 1;
        for (int d = 1; d <= limit; d++) {
            if (!dark(gray, w, h, x + outward * d, y)) break;
            nearThickness++;
        }
        if (nearThickness > limit) return false;
        int farThickness = 1;
        for (int side : ProbeSigns.VALUES)
            for (int d = 1; d <= limit; d++) {
                if (!dark(gray, w, h, x + side * d, endpoint)) break;
                farThickness++;
            }
        return farThickness <= limit;
    }

    private static boolean enclosedCounter(byte[] gray, int w, int h, float cx, float cy, float r) {
        int left = (int) Math.floor(cx - r), top = (int) Math.floor(cy - r);
        int right = (int) Math.ceil(cx + r), bottom = (int) Math.ceil(cy + r);
        if (left < 0 || top < 0 || right >= w || bottom >= h) return false;
        int width = right - left + 1, height = bottom - top + 1;
        boolean[] visited = new boolean[width * height];
        int[] queue = new int[visited.length];
        for (int iy = 0; iy < height; iy++)
            for (int ix = 0; ix < width; ix++) {
                int first = iy * width + ix;
                if (visited[first] || dark(gray, w, h, left + ix, top + iy)) continue;
                int head = 0, tail = 1, area = 0, minX = ix, maxX = ix, minY = iy, maxY = iy;
                long sumX = 0, sumY = 0;
                boolean boundary = false;
                queue[0] = first;
                visited[first] = true;
                while (head < tail) {
                    int value = queue[head++], x = value % width, y = value / width;
                    area++;
                    sumX += x;
                    sumY += y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    if (x == 0 || y == 0 || x == width - 1 || y == height - 1) boundary = true;
                    for (int direction = 0; direction < 4; direction++) {
                        int nx = x + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
                        int ny = y + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!visited[next] && !dark(gray, w, h, left + nx, top + ny)) {
                            visited[next] = true;
                            queue[tail++] = next;
                        }
                    }
                }
                if (!boundary
                        && area >= 4
                        && maxX > minX
                        && maxY > minY
                        && Math.abs(left + sumX / (float) area - cx) <= r * .4f
                        && Math.abs(top + sumY / (float) area - cy) <= r * .4f) return true;
            }
        return false;
    }

    private static boolean symbol(byte[] labels, int w, int h, int x, int y) {
        return x >= 0
                && x < w
                && y >= 0
                && y < h
                && labels[y * w + x] == OmrMeasurePostProcessor.SYMBOL;
    }

    private static boolean dark(byte[] g, int w, int h, int x, int y) {
        return x >= 0 && x < w && y >= 0 && y < h && (g[y * w + x] & 255) < 165;
    }
}

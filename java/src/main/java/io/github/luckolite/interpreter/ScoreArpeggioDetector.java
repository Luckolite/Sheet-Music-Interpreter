// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Conservative raw-ink wave recognition immediately left of a complete written chord. */
public final class ScoreArpeggioDetector {
    private ScoreArpeggioDetector() {}

    static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            int sourcePage) {
        if (gray == null || gray.length != width * height || width <= 0 || height <= 0)
            return score;
        var done = new HashSet<String>();
        for (int i = 0; i < score.notes().size(); i++) {
            var n = score.notes().get(i);
            if (n.kind() != ScoreNoteEvent.Kind.PITCHED
                    || (n.articulations() & 32768) != 0
                    || n.measureIndex() < 0
                    || n.measureIndex() >= score.measures().size()) continue;
            String key =
                    n.measureIndex()
                            + ":"
                            + n.staffIndex()
                            + ":"
                            + n.staffCount()
                            + ":"
                            + n.positionInMeasure();
            if (!done.add(key)) continue;
            var chord = new ArrayList<Integer>();
            float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < score.notes().size(); j++) {
                var other = score.notes().get(j);
                if (other.kind() == ScoreNoteEvent.Kind.PITCHED
                        && (other.articulations() & 32768) == 0
                        && other.measureIndex() == n.measureIndex()
                        && other.staffIndex() == n.staffIndex()
                        && other.staffCount() == n.staffCount()
                        && Math.abs(other.positionInMeasure() - n.positionInMeasure()) <= .018f) {
                    chord.add(j);
                    done.add(
                            other.measureIndex()
                                    + ":"
                                    + other.staffIndex()
                                    + ":"
                                    + other.staffCount()
                                    + ":"
                                    + other.positionInMeasure());
                    top = Math.min(top, other.pageY() * height);
                    bottom = Math.max(bottom, other.pageY() * height);
                }
            }
            if (chord.size() < 2) continue;
            PlayingTechniqueDetector.Staff chosen = null;
            float distance = Float.POSITIVE_INFINITY;
            for (var staff : staffs)
                if (staff.index() == n.staffIndex() && staff.count() == n.staffCount()) {
                    float d = Math.abs((top + bottom) * .5f - (staff.top() + staff.bottom()) * .5f);
                    if (d < distance) {
                        chosen = staff;
                        distance = d;
                    }
                }
            if (chosen == null || chosen.gap() < 4 || distance > chosen.gap() * 4) continue;
            float gap = chosen.gap();
            if (bottom - top < gap * 1.5f) continue;
            var m = score.measures().get(n.measureIndex());
            float x = (m.left() + n.positionInMeasure() * (m.right() - m.left())) * width;
            var owner =
                    new ColumnOwner(
                            x,
                            m.left() * width,
                            m.right() * width,
                            chosen,
                            chord.stream().map(score.notes()::get).toList());
            // A long wave must span this chord, not just an adjacent accidental or rest hook.
            for (int left = Math.max(0, Math.round(x - gap * 3)); left <= x - gap * 1.1f; left++) {
                int right = Math.round(left + gap * .8f);
                if (wave(
                        gray,
                        width,
                        height,
                        left,
                        right,
                        Math.round(top - gap * .8f),
                        Math.round(bottom + gap * .8f),
                        gap)) {
                    var extent =
                            finiteExtent(
                                    gray,
                                    width,
                                    height,
                                    left,
                                    right,
                                    Math.round(top - gap * .8f),
                                    Math.round(bottom + gap * .8f),
                                    gap,
                                    owner);
                    if (extent == null || !ownedExtent(extent, top, bottom, chosen, staffs))
                        continue;
                    score =
                            ScoreArpeggioNotation.capture(
                                    score,
                                    chord,
                                    new ScoreExpressiveEvent.Evidence(
                                            "printed-arpeggio-wave",
                                            sourcePage,
                                            (extent.left + extent.right) * .5f / width,
                                            n.staffIndex(),
                                            n.staffCount(),
                                            "arpeggio"));
                    break;
                }
            }
        }
        return score;
    }

    private record ColumnOwner(
            float headX,
            float measureLeft,
            float measureRight,
            PlayingTechniqueDetector.Staff staff,
            List<ScoreNoteEvent> heads) {}

    private record Extent(int left, int right, int top, int bottom) {}

    private record InkRow(int left, int right, boolean rule, boolean valid) {
        boolean empty() {
            return left < 0 && !rule;
        }
    }

    /** The wave window supplies a seed, never a physical component boundary. */
    private static Extent finiteExtent(
            byte[] ink,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            ColumnOwner owner) {
        int seed = -1;
        InkRow seedRow = null;
        int middle = (top + bottom) / 2;
        for (int distance = 0; distance <= bottom - top && seed < 0; distance++)
            for (int sign : new int[] {1, -1}) {
                int y = middle + sign * distance;
                if (y < top || y > bottom) continue;
                var row = inkRow(ink, width, height, left, right, y, gap);
                if (row.valid && !row.rule && !row.empty()) {
                    seed = y;
                    seedRow = row;
                    break;
                }
            }
        if (seed < 0) return null;
        int minX = seedRow.left, maxX = seedRow.right, first = seed, last = seed;
        for (int direction : new int[] {-1, 1}) {
            int y = seed;
            InkRow previous = seedRow;
            while (true) {
                int next = y + direction;
                if (next < 0 || next >= height) return null;
                int ruleCount = 0, lastRule = -1;
                var row =
                        inkRow(
                                ink,
                                width,
                                height,
                                previous.left - 1,
                                previous.right + 1,
                                next,
                                gap);
                while (row.rule) {
                    if (++ruleCount > Math.max(1, Math.ceil(gap * .25f))) return null;
                    lastRule = next;
                    next += direction;
                    if (next < 0 || next >= height) return null;
                    // A monotone 8-neighbor path can move one pixel per row. The rail itself
                    // cannot connect arbitrary distant contours or establish their identity.
                    int reach = ruleCount + 1;
                    row =
                            inkRow(
                                    ink,
                                    width,
                                    height,
                                    previous.left - reach,
                                    previous.right + reach,
                                    next,
                                    gap);
                }
                if (ruleCount > 0) {
                    // Uniform staff ink cannot prove hidden path displacement. Inspect the whole
                    // declared ownership strip on both visible faces, excluding only anchored
                    // objects.
                    var before = bandFace(ink, width, height, y, gap, owner);
                    var after = bandFace(ink, width, height, next, gap, owner);
                    if (!before.valid
                            || before.rule
                            || before.empty()
                            || before.left != previous.left
                            || before.right != previous.right) return null;
                    row = after;
                }
                if (!row.valid) return null;
                if (row.empty()) {
                    // No outgoing contour may exist anywhere in the reachable exit cone or quiet
                    // cap.
                    // The full rail band bounds a possible terminal tip conservatively.
                    if (ruleCount > 0) {
                        int needed = Math.max(2, (int) Math.ceil(gap * .25f));
                        for (int d = 0; d < needed; d++) {
                            var cap =
                                    bandFace(ink, width, height, next + d * direction, gap, owner);
                            if (!cap.valid || cap.rule || !cap.empty()) return null;
                        }
                    } else if (!quietCap(ink, width, height, previous, next, direction, gap, 0))
                        return null;
                    if (lastRule >= 0) {
                        if (direction < 0) first = lastRule;
                        else last = lastRule;
                    }
                    break;
                }
                y = next;
                previous = row;
                minX = Math.min(minX, row.left);
                maxX = Math.max(maxX, row.right);
                if (maxX - minX > gap * 1.5f) return null;
                if (direction < 0) first = y;
                else last = y;
            }
        }
        if (last - first < gap * 3.5f) return null;
        return new Extent(minX, maxX, first, last);
    }

    private static boolean quietCap(
            byte[] ink,
            int width,
            int height,
            InkRow previous,
            int first,
            int direction,
            float gap,
            int crossedRows) {
        int needed = Math.max(2, (int) Math.ceil(gap * .25f));
        for (int distance = 1; distance <= needed; distance++) {
            int y = first + (distance - 1) * direction;
            var row =
                    inkRow(
                            ink,
                            width,
                            height,
                            previous.left - distance - crossedRows,
                            previous.right + distance + crossedRows,
                            y,
                            gap);
            if (!row.valid || row.rule || !row.empty()) return false;
        }
        return true;
    }

    /** Find every complete row component intersecting the reachable interval. */
    private static InkRow inkRow(
            byte[] ink, int width, int height, int left, int right, int y, float gap) {
        if (y < 0 || y >= height || left < 0 || right >= width)
            return new InkRow(-1, -1, false, false);
        int outsideLeft = Math.round(left - gap), outsideRight = Math.round(right + gap);
        if (outsideLeft >= 0
                && outsideRight < width
                && horizontalRule(ink, width, outsideLeft, outsideRight, y, gap))
            return new InkRow(-1, -1, true, true);
        int lo = -1, hi = -1, runs = 0;
        for (int x = left; x <= right; x++)
            if (traceDark(ink, width, x, y)) {
                int a = x, b = x;
                while (a > 0 && traceDark(ink, width, a - 1, y)) a--;
                while (b + 1 < width && traceDark(ink, width, b + 1, y)) b++;
                if (++runs > 1) return new InkRow(a, b, false, false);
                lo = a;
                hi = b;
                x = b;
            }
        if (lo < 0) return new InkRow(-1, -1, false, true);
        boolean valid = lo > 0 && hi < width - 1 && hi - lo <= gap * .8f;
        return new InkRow(lo, hi, false, valid);
    }

    /** Conservative spatial glyph class: all unowned ink in the complete chord strip is ambiguous.
     * This is observable ownership, not universal component identity through an opaque rail. */
    private static InkRow bandFace(
            byte[] ink, int width, int height, int y, float gap, ColumnOwner o) {
        int left = (int) Math.floor(o.headX - 3 * gap),
                right = (int) Math.ceil(o.headX + .8f * gap);
        if (y < 0 || y >= height || left < 0 || right >= width)
            return new InkRow(-1, -1, false, false);
        if (horizontalRule(ink, width, left, right, y, gap)) return new InkRow(-1, -1, true, true);
        int lo = -1, hi = -1, count = 0;
        for (int x = left; x <= right; x++)
            if (traceDark(ink, width, x, y)) {
                int a = x, b = x;
                while (a > 0 && traceDark(ink, width, a - 1, y)) a--;
                while (b + 1 < width && traceDark(ink, width, b + 1, y)) b++;
                x = b;
                if (a <= left || b >= right) return new InkRow(a, b, false, false);
                if (ownedBarline(ink, width, height, a, b, y, gap, o)
                        || ownedChordInk(ink, width, height, a, b, y, gap, o)) continue;
                if (++count > 1 || b - a > gap * .8f) return new InkRow(a, b, false, false);
                lo = a;
                hi = b;
            }
        return new InkRow(lo, hi, false, true);
    }

    private static boolean ownedBarline(
            byte[] ink,
            int width,
            int height,
            int left,
            int right,
            int y,
            float gap,
            ColumnOwner o) {
        if (right - left > gap * .3f) return false;
        int top = Math.round(o.staff.top()), bottom = Math.round(o.staff.bottom() + gap);
        if (y < top || top < 0 || bottom >= height) return false;
        for (int x = left; x <= right; x++) {
            if (Math.min(Math.abs(x - o.measureLeft), Math.abs(x - o.measureRight)) > gap * .8f)
                continue;
            if (straightShaft(ink, width, x, top, Math.max(bottom, y))) return true;
        }
        return false;
    }

    private static boolean ownedChordInk(
            byte[] ink,
            int width,
            int height,
            int left,
            int right,
            int y,
            float gap,
            ColumnOwner o) {
        int stem = 0;
        float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (var n : o.heads) {
            if (n.stemDirection() == 0 || stem != 0 && stem != n.stemDirection()) return false;
            stem = n.stemDirection();
            top = Math.min(top, n.pageY() * height);
            bottom = Math.max(bottom, n.pageY() * height);
        }
        if (left < o.headX - gap * .9f || right > o.headX + gap * .8f) return false;
        int shaftLeft = Math.round(o.headX + (stem < 0 ? -gap * .9f : gap * .15f));
        int shaftRight = Math.round(o.headX + (stem < 0 ? -gap * .15f : gap * .9f));
        int first = Math.round(stem < 0 ? top : Math.min(top - gap, y));
        int last = Math.round(stem < 0 ? Math.max(bottom + gap, y) : bottom);
        if (first < 0 || last >= height) return false;
        for (int sx = shaftLeft; sx <= shaftRight; sx++) {
            if (sx < 0 || sx >= width || !straightShaft(ink, width, sx, first, last)) continue;
            boolean contacts = true;
            for (var head : o.heads)
                if (!headContact(ink, width, height, sx, o.headX, head.pageY() * height, gap)) {
                    contacts = false;
                    break;
                }
            if (!contacts) continue;
            if (left <= sx
                    && right >= sx
                    && right - left <= gap * .3f
                    && y >= 0
                    && y < height
                    && straightShaft(ink, width, sx, Math.min(first, y), Math.max(last, y)))
                return true;
            for (var head : o.heads)
                if (Math.abs(y - head.pageY() * height) <= gap * .7f
                        && headComponentOwned(
                                ink,
                                width,
                                height,
                                left,
                                right,
                                y,
                                sx,
                                head.pageY() * height,
                                gap,
                                o)) return true;
        }
        return false;
    }

    /** Membership must belong to the CURRENT component, never another nearby genuine head. */
    private static boolean headComponentOwned(
            byte[] ink,
            int width,
            int height,
            int left,
            int right,
            int y,
            int shaft,
            float headY,
            float gap,
            ColumnOwner o) {
        int minX = (int) Math.ceil(o.headX - gap * .9f),
                maxX = (int) Math.floor(o.headX + gap * .8f);
        int minY = (int) Math.ceil(headY - gap * .7f), maxY = (int) Math.floor(headY + gap * .7f);
        if (minX < 0
                || maxX >= width
                || minY < 0
                || maxY >= height
                || left < minX
                || right > maxX
                || y < minY
                || y > maxY
                || shaft < minX
                || shaft > maxX) return false;
        int rw = maxX - minX + 1, rh = maxY - minY + 1;
        boolean[] rail = new boolean[rh], seen = new boolean[rw * rh];
        int stripLeft = (int) Math.floor(o.headX - 3 * gap),
                stripRight = (int) Math.ceil(o.headX + .8f * gap);
        if (stripLeft < 0 || stripRight >= width) return false;
        for (int yy = minY; yy <= maxY; yy++) {
            boolean grid = false;
            for (int k = 0; k < 5; k++)
                if (Math.abs(yy - (o.staff.top() + k * o.staff.gap()))
                        <= Math.max(1, Math.ceil(gap * .15f))) {
                    grid = true;
                    break;
                }
            // A head bowl cannot declare itself a rail: grid AND long full-strip ink required.
            rail[yy - minY] = grid && horizontalRule(ink, width, stripLeft, stripRight, yy, gap);
        }
        if (rail[y - minY]) return false;
        for (int x = left; x <= right; x++) if (!traceDark(ink, width, x, y)) return false;
        var pending = new ArrayDeque<Integer>();
        int seed = (y - minY) * rw + left - minX;
        seen[seed] = true;
        pending.add(seed);
        boolean reachesShaft = false;
        while (!pending.isEmpty()) {
            int at = pending.removeFirst(), xx = minX + at % rw, yy = minY + at / rw;
            if (xx == shaft) reachesShaft = true;
            // Rail pixels are never vertices. Only a fully witnessed same-column raw path
            // may connect its two nonrail faces; no lateral travel inside a rail is possible.
            for (int dy : new int[] {-1, 1}) {
                int ny = yy + dy;
                if (ny < minY || ny > maxY || !rail[ny - minY]) continue;
                boolean inkPath = true;
                int bandRows = 0;
                while (ny >= minY && ny <= maxY && rail[ny - minY]) {
                    if (++bandRows > Math.max(1, Math.ceil(gap * .25f))
                            || !traceDark(ink, width, xx, ny)) {
                        inkPath = false;
                        break;
                    }
                    ny += dy;
                }
                if (!inkPath || ny < minY || ny > maxY || !traceDark(ink, width, xx, ny)) continue;
                int index = (ny - minY) * rw + xx - minX;
                if (!seen[index]) {
                    seen[index] = true;
                    pending.add(index);
                }
            }
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = xx + dx, ny = yy + dy;
                    if (nx < minX || nx > maxX || ny < minY || ny > maxY || rail[ny - minY])
                        continue;
                    int index = (ny - minY) * rw + nx - minX;
                    if (!seen[index] && traceDark(ink, width, nx, ny)) {
                        seen[index] = true;
                        pending.add(index);
                    }
                }
        }
        if (!reachesShaft) return false;
        for (int x = left; x <= right; x++) if (!seen[(y - minY) * rw + x - minX]) return false;
        return true;
    }

    private static boolean straightShaft(byte[] ink, int width, int x, int top, int bottom) {
        for (int y = top; y <= bottom; y++) if (!traceDark(ink, width, x, y)) return false;
        return true;
    }

    private static boolean headContact(
            byte[] ink, int width, int height, int sx, float hx, float hy, float gap) {
        for (int y = Math.max(0, Math.round(hy - gap * .5f));
                y <= Math.min(height - 1, Math.round(hy + gap * .5f));
                y++) {
            if (!traceDark(ink, width, sx, y)) continue;
            int a = sx, b = sx;
            while (a > 0 && traceDark(ink, width, a - 1, y)) a--;
            while (b + 1 < width && traceDark(ink, width, b + 1, y)) b++;
            if (a < hx - gap * .9f || b > hx + gap * .9f) continue;
            if (sx < hx && b >= hx - gap * .2f || sx > hx && a <= hx + gap * .2f) return true;
        }
        return false;
    }

    private static boolean horizontalRule(
            byte[] ink, int width, int left, int right, int y, float gap) {
        int white = 0, run = 0;
        // A long mostly-ink rail may contain short pale antialiasing segments.
        int paleRun = Math.max(2, (int) Math.floor(gap * .18f));
        for (int x = left; x <= right; x++) {
            if (traceDark(ink, width, x, y)) run = 0;
            else {
                white++;
                if (++run > paleRun) return false;
            }
        }
        return white <= (right - left + 1) * .3f;
    }

    private static boolean traceDark(byte[] ink, int width, int x, int y) {
        return (ink[y * width + x] & 255) < 185;
    }

    private static boolean ownedExtent(
            Extent extent,
            float chordTop,
            float chordBottom,
            PlayingTechniqueDetector.Staff staff,
            List<PlayingTechniqueDetector.Staff> staffs) {
        float gap = staff.gap();
        if (extent.top < Math.min(chordTop, staff.top()) - gap * 1.5f
                || extent.bottom > Math.max(chordBottom, staff.bottom()) + gap * 1.5f
                || extent.top > chordTop + gap * .4f
                || extent.bottom < chordBottom - gap * .4f) return false;
        for (var other : staffs) {
            if (other.equals(staff)) continue;
            if (other.bottom() < staff.top() && extent.top <= (other.bottom() + staff.top()) * .5f)
                return false;
            if (other.top() > staff.bottom()
                    && extent.bottom >= (other.top() + staff.bottom()) * .5f) return false;
        }
        return true;
    }

    static boolean wave(
            byte[] ink,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap) {
        if (ink == null
                || width <= 0
                || height <= 0
                || ink.length != width * height
                || gap < 4
                || left < 0
                || right >= width
                || top < 0
                || bottom >= height
                || right <= left
                || bottom - top < gap * 3.5f) return false;
        int rows = 0, turns = 0, direction = 0, blank = 0, first = -1, last = -1;
        var leftTurns = new EdgeTurns(Math.max(.75, gap * .12));
        var rightTurns = new EdgeTurns(Math.max(.75, gap * .12));
        double extreme = Double.NaN, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;

        for (int y = top; y <= bottom; y++) {
            boolean rule =
                    left >= gap
                            && right + gap < width
                            && dark(ink, width, Math.round(left - gap), y)
                            && dark(ink, width, Math.round(right + gap), y);
            if (rule) continue;
            int lo = -1, hi = -1, count = 0;
            for (int x = left; x <= right; x++)
                if (dark(ink, width, x, y)) {
                    if (lo < 0) lo = x;
                    hi = x;
                    count++;
                }
            if (lo < 0) {
                if (++blank > gap * .3f) return false;
                continue;
            }
            blank = 0;
            rows++;
            if (first < 0) first = y;
            last = y;
            if (count > gap * .65f || hi - lo > gap * .75f) return false;
            // A separate shaft crossed by a wave yields disconnected runs away from crossings.
            for (int x = lo; x <= hi; x++) if (!dark(ink, width, x, y)) return false;
            leftTurns.add(lo);
            rightTurns.add(hi);
            double center = (lo + hi) * .5;
            min = Math.min(min, center);
            max = Math.max(max, center);
            if (Double.isNaN(extreme)) extreme = center;
            else if (direction == 0) {
                if (max - min >= gap * .15) {
                    direction = center >= (min + max) * .5 ? 1 : -1;
                    extreme = center;
                }
            } else if (direction * (center - extreme) >= 0) extreme = center;
            else if (Math.abs(center - extreme) >= gap * .15) {
                turns++;
                direction = -direction;
                extreme = center;
            }
        }
        return first >= 0
                && first <= top + gap * .4f
                && last >= bottom - gap * .4f
                && last - first >= gap * 3.5f
                && rows > (bottom - top) * .65f
                && turns >= 6
                && max - min >= gap * .15f
                && leftTurns.turns >= 6
                && rightTurns.turns >= 6;
    }

    /** Both lateral edges must reverse; a straight shaft with periodic flags cannot qualify. */
    private static final class EdgeTurns {
        final double excursion;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, extreme = Double.NaN;
        int direction, turns;

        EdgeTurns(double excursion) {
            this.excursion = excursion;
        }

        void add(double x) {
            min = Math.min(min, x);
            max = Math.max(max, x);
            if (Double.isNaN(extreme)) extreme = x;
            else if (direction == 0) {
                if (max - min >= excursion) {
                    direction = x >= (min + max) * .5 ? 1 : -1;
                    extreme = x;
                }
            } else if (direction * (x - extreme) >= 0) extreme = x;
            else if (Math.abs(x - extreme) >= excursion) {
                turns++;
                direction = -direction;
                extreme = x;
            }
        }
    }

    private static boolean dark(byte[] ink, int width, int x, int y) {
        return (ink[y * width + x] & 255) < 150;
    }
}

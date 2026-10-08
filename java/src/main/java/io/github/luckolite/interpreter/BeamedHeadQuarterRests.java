// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reads visible quarter-rest strokes beside a physically proved beamed head. */
final class BeamedHeadQuarterRests {
    record Candidate(
            int noteIndex,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            int staffIndex,
            int staffCount,
            int measureIndex,
            float position,
            float pageY) {}

    static List<Candidate> find(
            byte[] labels, byte[] gray, int w, int h, ScorePageInterpretation score)
            throws Exception {
        return find(
                gray,
                w,
                h,
                score,
                OmrScoreInterpreter.techniqueStaffs(labels, gray, w, h, score.measures()));
    }

    static List<ScoreRestEvent> detect(
            byte[] gray,
            int w,
            int h,
            List<MeasureRegion> measures,
            List<SixteenthRestDetector.Staff> sourceStaffs,
            List<ScoreNoteEvent> notes) {
        return detectWithDots(gray, w, h, measures, sourceStaffs, notes).rests();
    }

    static SixteenthRestDetector.Detection detectWithDots(
            byte[] gray,
            int w,
            int h,
            List<MeasureRegion> measures,
            List<SixteenthRestDetector.Staff> sourceStaffs,
            List<ScoreNoteEvent> notes) {
        if (gray == null
                || w <= 0
                || h <= 0
                || (long) w * h > gray.length
                || measures == null
                || sourceStaffs == null
                || notes == null) return new SixteenthRestDetector.Detection(List.of(), List.of());
        var staffs =
                sourceStaffs.stream()
                        .filter(s -> s.pitchTrack() == null)
                        .map(
                                s ->
                                        new PlayingTechniqueDetector.Staff(
                                                s.top(), s.bottom(), s.gap(), s.index(), s.count()))
                        .toList();
        var score = new ScorePageInterpretation(measures, notes);
        var result = new ArrayList<ScoreRestEvent>();
        var dots = new ArrayList<SixteenthRestDetector.RestDot>();
        for (var c : find(gray, w, h, score, staffs)) {
            var staff =
                    sourceStaffs.stream()
                            .filter(
                                    s ->
                                            s.pitchTrack() == null
                                                    && s.index() == c.staffIndex()
                                                    && s.count() == c.staffCount())
                            .findFirst()
                            .orElseThrow();
            var inkDots =
                    SixteenthRestDetector.augmentationDots(
                            gray,
                            w,
                            h,
                            staff,
                            c.right(),
                            measures.get(c.measureIndex()),
                            notes,
                            c.measureIndex());
            var rest =
                    new ScoreRestEvent(
                            c.measureIndex(),
                            c.position(),
                            c.pageY(),
                            (c.bottom() - c.top() + 1f) / h,
                            c.staffIndex(),
                            c.staffCount(),
                            inkDots.size() == 2 ? 1.75 : inkDots.size() == 1 ? 1.5 : 1);
            result.add(rest);
            for (var dot : inkDots)
                dots.add(new SixteenthRestDetector.RestDot(dot.x(), dot.y(), rest));
        }
        return new SixteenthRestDetector.Detection(List.copyOf(result), List.copyOf(dots));
    }

    static List<Candidate> find(
            byte[] gray,
            int w,
            int h,
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs) {
        var results = new ArrayList<Candidate>();
        if (gray == null
                || w <= 0
                || h <= 0
                || (long) w * h > gray.length
                || score == null
                || staffs == null) return results;
        for (int index = 0; index < score.notes().size(); index++) {
            var note = score.notes().get(index);
            if (note.kind() != ScoreNoteEvent.Kind.PITCHED
                    || note.beamCount() < 1
                    || note.measureIndex() < 0
                    || note.measureIndex() >= score.measures().size()) continue;
            var m = score.measures().get(note.measureIndex());
            if (!validMeasure(m)
                    || !Float.isFinite(note.positionInMeasure())
                    || !Float.isFinite(note.pageY())) continue;
            float hx = (m.left() + note.positionInMeasure() * (m.right() - m.left())) * w,
                    hy = note.pageY() * h;
            for (var staff : staffs) {
                float g = staff.gap();
                if (staff.index() != note.staffIndex()
                        || staff.count() != note.staffCount()
                        || !Float.isFinite(g)
                        || !Float.isFinite(staff.top())
                        || !Float.isFinite(staff.bottom())
                        || g < 6
                        || hy < staff.top() - 2 * g
                        || hy > staff.bottom() + 3 * g) continue;
                int direction = PrintedStemDirection.detect(gray, w, h, hx, hy, g);
                if (direction == 0) continue;
                int stem = -1;
                int sa = Math.round(direction == 1 ? hy - 2.4f * g : hy + .65f * g),
                        sb = Math.round(direction == 1 ? hy - .65f * g : hy + 2.4f * g);
                if (sa < 0 || sb >= h) continue;
                for (int x = Math.max(0, Math.round(hx + (direction == 1 ? .4f : -.9f) * g));
                        x <= Math.min(w - 1, Math.round(hx + (direction == 1 ? .9f : -.4f) * g));
                        x++) {
                    int hits = 0;
                    for (int y = sa; y <= sb; y++) if ((gray[y * w + x] & 255) < 170) hits++;
                    if (hits >= (sb - sa + 1) * .95f) {
                        stem = x;
                        break;
                    }
                }
                if (stem < 0) continue;
                int stemEnd = shaftEnd(gray, w, stem, sa, sb, g);
                int l = Math.max(0, Math.round(hx - 1.15f * g));
                int r =
                        direction == 1
                                ? stem - Math.max(2, Math.round(.1f * g))
                                : Math.min(w - 1, Math.round(hx + g));
                int t = Math.max(0, Math.round(hy - 1.55f * g)),
                        b = Math.min(h - 1, Math.round(hy + 3.3f * g));
                t = Math.max(t, (int) Math.ceil(m.top() * h));
                b = Math.min(b, (int) Math.floor(m.bottom() * h));
                for (var other : score.notes()) {
                    if (other == note
                            || other.measureIndex() != note.measureIndex()
                            || other.staffIndex() != note.staffIndex()
                            || other.staffCount() != note.staffCount()) continue;
                    float ox = (m.left() + other.positionInMeasure() * (m.right() - m.left())) * w,
                            oy = other.pageY() * h;
                    if (Math.abs(ox - hx) < .35f * g && oy > hy + .8f * g)
                        b = Math.min(b, Math.round(oy - .55f * g));
                }
                if (l >= r
                        || t >= b
                        || l < m.left() * w
                        || r > m.right() * w
                        || t < m.top() * h
                        || b > m.bottom() * h) continue;
                boolean[] line = new boolean[b - t + 1];
                for (int y = t; y <= b; y++) {
                    float rule = staff.top() + Math.round((y - staff.top()) / g) * g;
                    if (Math.abs(y - rule) > Math.max(1.5f, g * .15f)) continue;
                    int a = Math.max(0, Math.round(hx - .98f * g)),
                            z = Math.min(w - 1, Math.round(hx + .98f * g));
                    int hits = 0;
                    for (int x = a; x <= z; x++) if ((gray[y * w + x] & 255) < 170) hits++;
                    if (hits >= (z - a + 1) * .90f) line[y - t] = true;
                }
                int il = r + 1, ir = l - 1, it = b + 1, ib = t - 1;
                int blank = 0, lastBody = -1;
                for (int y = t; y <= b; y++) {
                    if (line[y - t]) continue;
                    boolean dark = false;
                    for (int x = l; x <= r; x++)
                        if ((gray[y * w + x] & 255) < 170) {
                            dark = true;
                            break;
                        }
                    if (dark) {
                        blank = 0;
                        lastBody = y;
                    } else if (y > hy + 1.5f * g && lastBody >= t && ++blank >= 2) {
                        b = y;
                        break;
                    }
                }
                for (int y = t; y <= b; y++)
                    if (!line[y - t])
                        for (int x = l; x <= r; x++)
                            if ((gray[y * w + x] & 255) < 170) {
                                il = Math.min(il, x);
                                ir = Math.max(ir, x);
                                it = Math.min(it, y);
                                ib = Math.max(ib, y);
                            }
                if (il > ir) continue;
                for (var contour :
                        ownedContours(
                                gray,
                                w,
                                l,
                                r,
                                t,
                                b,
                                line,
                                g,
                                true,
                                hy,
                                direction == -1 ? stemEnd + 1 : l)) {
                    il = contour[0];
                    ir = contour[1];
                    it = contour[2];
                    ib = contour[3];
                    if (it <= t || ib >= b || ir - il + 1 < g * .7f || ir - il + 1 > g * 1.85f)
                        continue;
                    results.add(
                            new Candidate(
                                    index,
                                    il,
                                    ir,
                                    it,
                                    ib,
                                    g,
                                    staff.index(),
                                    staff.count(),
                                    note.measureIndex(),
                                    (((il + ir) * .5f / w) - m.left()) / (m.right() - m.left()),
                                    (it + ib) * .5f / h));
                }
            }
        }
        results.addAll(coveredFoot(gray, w, h, score, staffs));
        return results;
    }

    // A beamed head can cover the lower hook while the quarter rest retains
    // an independent upper tapered diagonal and broad returning wing.
    static List<Candidate> coveredFoot(
            byte[] gray,
            int w,
            int h,
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs) {
        var found = new ArrayList<Candidate>();
        for (int index = 0; index < score.notes().size(); index++) {
            var n = score.notes().get(index);
            if (n.kind() != ScoreNoteEvent.Kind.PITCHED
                    || n.beamCount() < 1
                    || n.measureIndex() < 0
                    || n.measureIndex() >= score.measures().size()) continue;
            var m = score.measures().get(n.measureIndex());
            if (!validMeasure(m)
                    || !Float.isFinite(n.positionInMeasure())
                    || !Float.isFinite(n.pageY())) continue;
            float hx = (m.left() + n.positionInMeasure() * (m.right() - m.left())) * w,
                    hy = n.pageY() * h;
            for (var s : staffs) {
                float g = s.gap();
                if (s.index() != n.staffIndex()
                        || s.count() != n.staffCount()
                        || !Float.isFinite(g)
                        || !Float.isFinite(s.top())
                        || !Float.isFinite(s.bottom())
                        || g < 6
                        || hy < s.top() - 2 * g
                        || hy > s.bottom() + 3 * g) continue;
                int direction = PrintedStemDirection.detect(gray, w, h, hx, hy, g);
                if (direction == 0) continue;
                int sa = Math.round(direction == 1 ? hy - 2.4f * g : hy + .65f * g),
                        sb = Math.round(direction == 1 ? hy - .65f * g : hy + 2.4f * g);
                if (sa < 0 || sb >= h) continue;
                int stem = -1;
                for (int x = Math.max(0, Math.round(hx + (direction == 1 ? .4f : -.9f) * g));
                        x <= Math.min(w - 1, Math.round(hx + (direction == 1 ? .9f : -.4f) * g));
                        x++) {
                    int hits = 0;
                    for (int y = sa; y <= sb; y++) if ((gray[y * w + x] & 255) < 170) hits++;
                    if (hits >= (sb - sa + 1) * .95f) {
                        stem = x;
                        break;
                    }
                }
                if (stem < 0) continue;
                int l = Math.max(0, Math.round(hx - 1.15f * g));
                int r =
                        direction == 1
                                ? stem - Math.max(2, Math.round(.1f * g))
                                : Math.min(w - 1, Math.round(hx + g));
                int t = Math.max(0, Math.round(hy - 3.6f * g)),
                        b = Math.min(h - 1, Math.round(hy - .55f * g));
                t = Math.max(t, (int) Math.ceil(m.top() * h));
                b = Math.min(b, (int) Math.floor(m.bottom() * h));
                boolean upperHeadClip = false;
                for (var other : score.notes()) {
                    if (other == n
                            || other.measureIndex() != n.measureIndex()
                            || other.staffIndex() != n.staffIndex()
                            || other.staffCount() != n.staffCount()) continue;
                    float ox = (m.left() + other.positionInMeasure() * (m.right() - m.left())) * w,
                            oy = other.pageY() * h;
                    if (Math.abs(ox - hx) < g * .35f && oy < hy - .8f * g && oy + .6f * g > t) {
                        t = Math.round(oy + .6f * g);
                        upperHeadClip = true;
                    }
                }
                if (l >= r
                        || t >= b
                        || l < m.left() * w
                        || r > m.right() * w
                        || t < m.top() * h
                        || b > m.bottom() * h) continue;
                boolean[] line = new boolean[b - t + 1];
                for (int y = t; y <= b; y++) {
                    float rule = s.top() + Math.round((y - s.top()) / g) * g;
                    if (Math.abs(y - rule) > Math.max(1.5f, g * .15f)) continue;
                    int a = Math.max(0, Math.round(hx - .98f * g)),
                            z = Math.min(w - 1, Math.round(hx + .98f * g));
                    int hits = 0;
                    for (int x = a; x <= z; x++) if ((gray[y * w + x] & 255) < 170) hits++;
                    if (hits >= (z - a + 1) * .90f) line[y - t] = true;
                }
                for (var contour : ownedContours(gray, w, l, r, t, b, line, g, false, hy, l)) {
                    int il = contour[0], ir = contour[1], it = contour[2], ib = contour[3];
                    if (it == t && !upperHeadClip
                            || ir - il + 1 < g * .6f
                            || ir - il + 1 > g * 1.85f) continue;
                    int end = Math.round(it + 3 * g) - 1;
                    if (end >= h || end > m.bottom() * h) continue;
                    found.add(
                            new Candidate(
                                    index,
                                    il,
                                    ir,
                                    it,
                                    end,
                                    g,
                                    s.index(),
                                    s.count(),
                                    n.measureIndex(),
                                    (((il + ir) * .5f / w) - m.left()) / (m.right() - m.left()),
                                    (it + end) * .5f / h));
                }
            }
        }
        return found;
    }

    // Read each connected ink owner separately. Only independently proved ruled bands
    // can connect two visible rows; an ordinary paper gap never supplies missing ink.
    static List<int[]> ownedContours(
            byte[] gray,
            int w,
            int l,
            int r,
            int t,
            int b,
            boolean[] line,
            float g,
            boolean lower,
            float hy,
            int lowerLeft) {
        int cw = r - l + 1, ch = b - t + 1;
        boolean[] seen = new boolean[cw * ch];
        var accepted = new ArrayList<int[]>();
        for (int sy = 0; sy < ch; sy++)
            for (int sx = 0; sx < cw; sx++) {
                int seed = sy * cw + sx;
                if (seen[seed]
                        || line[sy]
                        || sy + t >= hy - .55f * g && sx + l < lowerLeft
                        || (gray[(sy + t) * w + sx + l] & 255) >= 170) continue;
                var queue = new ArrayDeque<Integer>();
                var pixels = new ArrayList<Integer>();
                queue.add(seed);
                seen[seed] = true;
                int il = cw, ir = -1, it = ch, ib = -1;
                while (!queue.isEmpty()) {
                    int p = queue.removeFirst(), x = p % cw, y = p / cw;
                    pixels.add(p);
                    il = Math.min(il, x);
                    ir = Math.max(ir, x);
                    it = Math.min(it, y);
                    ib = Math.max(ib, y);
                    for (int direction = -1; direction <= 1; direction++) {
                        int yy = y + direction;
                        if (direction != 0) while (yy >= 0 && yy < ch && line[yy]) yy += direction;
                        if (yy < 0 || yy >= ch || Math.abs(yy - y) > g * .35f + 1) continue;
                        int reach = Math.max(1, Math.abs(yy - y));
                        for (int xx = Math.max(0, x - reach);
                                xx <= Math.min(cw - 1, x + reach);
                                xx++) {
                            int next = yy * cw + xx;
                            if (seen[next]
                                    || line[yy]
                                    || yy + t >= hy - .55f * g && xx + l < lowerLeft
                                    || (gray[(yy + t) * w + xx + l] & 255) >= 170) continue;
                            seen[next] = true;
                            queue.add(next);
                        }
                    }
                }
                if (ib - it + 1 < g * (lower ? 2.1f : 1.15f)
                        || ib - it + 1 > g * (lower ? 3.6f : 2.8f)) continue;
                byte[] component = new byte[cw * ch];
                Arrays.fill(component, (byte) 240);
                for (int p : pixels) component[p] = gray[(p / cw + t) * w + p % cw + l];
                if (lower
                        ? occluded(
                                component,
                                cw,
                                0,
                                cw - 1,
                                0,
                                line,
                                it,
                                ib,
                                hy - t,
                                g,
                                lowerLeft > l ? lowerLeft - l : Float.NEGATIVE_INFINITY)
                        : upperQuarter(component, cw, 0, cw - 1, 0, line, it, ib, g))
                    accepted.add(new int[] {il + l, ir + l, it + t, ib + t});
            }
        return accepted;
    }

    static boolean upperQuarter(
            byte[] gray,
            int w,
            int l,
            int r,
            int top,
            boolean[] line,
            int first,
            int last,
            float g) {
        int n = last - first + 1;
        if (n < g * 1.15f || n > g * 2.8f) return false;
        double[] left = new double[n], right = new double[n];
        Arrays.fill(left, Double.NaN);
        Arrays.fill(right, Double.NaN);
        for (int y = first; y <= last; y++)
            if (!line[y - top]) {
                for (int x = l; x <= r; x++)
                    if ((gray[y * w + x] & 255) < 170) {
                        if (!Double.isFinite(left[y - first])) left[y - first] = x;
                        right[y - first] = x;
                    }
                if (!Double.isFinite(left[y - first])) return false;
            }
        for (int i = 0; i < n; i++)
            if (!Double.isFinite(left[i])) {
                int a = i - 1, b = i + 1;
                while (b < n && !Double.isFinite(left[b])) b++;
                if (a < 0 || b >= n || b - a > g * .35f + 1) return false;
                left[i] = left[a] + (left[b] - left[a]) * (i - a) / (b - a);
                right[i] = right[a] + (right[b] - right[a]) * (i - a) / (b - a);
            }
        if (right[0] - left[0] + 1 > g * .3f) return false;
        for (int peak = Math.max(2, Math.round(g * .4f)); peak < Math.min(n, g * 1.05f); peak++) {
            if (right[peak] - right[0] + .5 < g * .35f
                    || left[peak] - left[0] + .5 < g * .14f
                    || right[peak] - left[peak] + 1 < g * .22f) continue;
            for (int valley = peak + Math.max(2, Math.round(g * .2f));
                    valley < Math.min(n, g * 1.65f);
                    valley++)
                if (left[peak] - left[valley] + .5 >= g * .16f
                        && right[peak] - right[valley] + .5 >= g * .10f) return true;
        }
        return false;
    }

    // A beamed owner's oval can cover the upper zigzag, but the lower elbow and
    // returning hook must remain in original ink below the oval's measured edge.
    static boolean occluded(
            byte[] gray,
            int w,
            int l,
            int r,
            int top,
            boolean[] line,
            int first,
            int last,
            float hy,
            float g,
            float shaftBoundary) {
        if (last - first + 1 < 2.1f * g
                || last - first + 1 > 3.6f * g
                || first < hy - 1.55f * g
                || first > hy + .1f * g) return false;
        boolean cap = visibleCap(gray, w, l, r, top, line, first, Math.round(hy - .58f * g), g);
        int a = Math.max(first, Math.round(hy + .55f * g));
        int n = last - a + 1;
        if (n < (cap ? .75f : 1.45f) * g || n > 2.8f * g) return false;
        double[] left = new double[n], right = new double[n];
        Arrays.fill(left, Double.NaN);
        Arrays.fill(right, Double.NaN);
        for (int y = a; y <= last; y++)
            if (!line[y - top]) {
                for (int x = l; x <= r; x++)
                    if ((gray[y * w + x] & 255) < 170) {
                        if (!Double.isFinite(left[y - a])) left[y - a] = x;
                        right[y - a] = x;
                    }
                if (!Double.isFinite(left[y - a])) return false;
            }
        for (int i = 0; i < n; i++)
            if (!Double.isFinite(left[i])) {
                int before = i - 1, after = i + 1;
                while (after < n && !Double.isFinite(left[after])) after++;
                if (before < 0 || after >= n || after - before > g * .35f + 1) return false;
                left[i] =
                        left[before]
                                + (left[after] - left[before]) * (i - before) / (after - before);
                right[i] =
                        right[before]
                                + (right[after] - right[before]) * (i - before) / (after - before);
            }
        if (cap && returningHook(left, right, g)) return true;
        for (int elbow = 1; elbow < n * .45; elbow++) {
            if (!cap && right[0] - right[elbow] + .5 < g * .10f) continue;
            for (int shoulder = elbow + 2; shoulder < n * .78; shoulder++) {
                if (!cap && right[shoulder] - right[elbow] + .5 < g * .25f) continue;
                double leftShoulder = left[elbow];
                for (int i = elbow; i <= shoulder; i++)
                    leftShoulder = Math.max(leftShoulder, left[i]);
                for (int hook = shoulder + 1; hook < n * .9; hook++) {
                    if (right[shoulder] - right[hook] < g * .4f
                            || leftShoulder - left[hook] < g * .12f) continue;
                    if (right[n - 1] - right[hook] + .5 < g * .07f) continue;
                    if (left[n - 1] - left[hook] + .5 < g * .25f) {
                        if (!Float.isFinite(shaftBoundary)
                                || left[hook] > shaftBoundary + .5
                                || left[n - 1] - left[hook] + .5 < g * .07f) continue;
                    }
                    if (right[n - 1] - left[n - 1] > g * .22f) return false;
                    return true;
                }
            }
        }
        return false;
    }

    // A separately visible tapered upper stroke proves the first quarter zigzag.
    // Its remaining lower hook still needs a broad left turn and a thin returning foot.
    static boolean returningHook(double[] left, double[] right, float g) {
        int n = left.length;
        for (int shoulder = 0; shoulder < n * .65; shoulder++)
            for (int hook = shoulder + Math.max(1, Math.round(g * .1f)); hook < n * .85; hook++) {
                if (right[shoulder] - right[hook] + .5 < g * .4f) continue;
                if (left[shoulder] - left[hook] + .5 < g * .12f) {
                    double broad = right[shoulder] - left[shoulder] + 1,
                            narrow = right[hook] - left[hook] + 1;
                    if (broad < g * .6f || narrow > g * .25f + .5 || broad - narrow < g * .4f)
                        continue;
                }
                if (left[n - 1] - left[hook] + .5 < g * .25f
                        || right[n - 1] - right[hook] + .5 < g * .07f) continue;
                if (right[n - 1] - left[n - 1] > g * .22f) continue;
                return true;
            }
        return false;
    }

    static boolean visibleCap(
            byte[] gray,
            int w,
            int l,
            int r,
            int top,
            boolean[] line,
            int first,
            int last,
            float g) {
        if (last - first + 1 < g * .35f || last - first + 1 > g * 1.1f) return false;
        int firstLeft = -1, firstRight = -1, lastLeft = -1, lastRight = -1, rows = 0;
        for (int y = first; y <= last; y++)
            if (!line[y - top]) {
                int a = -1, b = -1;
                for (int x = l; x <= r; x++)
                    if ((gray[y * w + x] & 255) < 170) {
                        if (a < 0) a = x;
                        b = x;
                    }
                if (a < 0) return false;
                if (b - a + 1 > g * .65f) break;
                if (rows == 0) {
                    firstLeft = a;
                    firstRight = b;
                    if (b - a + 1 > g * .3f) return false;
                }
                lastLeft = a;
                lastRight = b;
                rows++;
            }
        return rows >= g * .25f
                && lastRight - firstRight + .5 >= g * .23f
                && lastLeft - firstLeft + .5 >= g * .16f;
    }

    static boolean validMeasure(MeasureRegion m) {
        return m != null
                && Float.isFinite(m.left())
                && Float.isFinite(m.right())
                && Float.isFinite(m.top())
                && Float.isFinite(m.bottom())
                && m.left() < 1
                && m.right() > 0
                && m.top() < 1
                && m.bottom() > 0
                && m.left() < m.right()
                && m.top() < m.bottom();
    }

    static int shaftEnd(byte[] gray, int w, int first, int top, int bottom, float g) {
        int last = first;
        for (int x = first + 1;
                x <= Math.min(w - 1, first + Math.max(1, (int) Math.ceil(g * .3f)));
                x++) {
            int hits = 0;
            for (int y = top; y <= bottom; y++) if ((gray[y * w + x] & 255) < 170) hits++;
            if (hits < (bottom - top + 1) * .95f) break;
            last = x;
        }
        return last;
    }
}

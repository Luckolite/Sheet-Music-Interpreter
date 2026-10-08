// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Reads independently visible half-rest rectangles beside a physically proved note shaft. */
final class PolyphonicHalfRests {
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

    record Shaft(int left, int right, int direction) {}

    static SixteenthRestDetector.Detection detectWithDots(
            byte[] gray,
            int w,
            int h,
            List<MeasureRegion> measures,
            List<SixteenthRestDetector.Staff> staffs,
            List<ScoreNoteEvent> notes) {
        var result = new ArrayList<ScoreRestEvent>();
        var dots = new ArrayList<SixteenthRestDetector.RestDot>();
        if (gray == null
                || w <= 0
                || h <= 0
                || (long) w * h != gray.length
                || measures == null
                || staffs == null
                || notes == null) return new SixteenthRestDetector.Detection(List.of(), List.of());
        var plain =
                staffs.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(
                                s ->
                                        new PlayingTechniqueDetector.Staff(
                                                s.top(), s.bottom(), s.gap(), s.index(), s.count()))
                        .toList();
        for (var c : find(gray, w, h, new ScorePageInterpretation(measures, notes), plain)) {
            // The dot sits in the space above the supporting rule, independent of plate thickness.
            float virtualTop = c.bottom() + 1 - c.gap() * 2;
            var dotStaff =
                    new SixteenthRestDetector.Staff(
                            virtualTop,
                            virtualTop + 4 * c.gap(),
                            c.gap(),
                            c.staffIndex(),
                            c.staffCount());
            var inkDots =
                    SixteenthRestDetector.augmentationDots(
                            gray,
                            w,
                            h,
                            dotStaff,
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
                            inkDots.size() == 2 ? 3.5 : inkDots.size() == 1 ? 3 : 2);
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
        var result = new ArrayList<Candidate>();
        if (gray == null
                || w <= 0
                || h <= 0
                || (long) w * h != gray.length
                || score == null
                || staffs == null) return result;
        for (int ni = 0; ni < score.notes().size(); ni++) {
            var n = score.notes().get(ni);
            if (n == null
                    || n.kind() != ScoreNoteEvent.Kind.PITCHED
                    || n.staffIndex() < 0
                    || n.staffCount() <= 0
                    || n.staffIndex() >= n.staffCount()
                    || n.measureIndex() < 0
                    || n.measureIndex() >= score.measures().size()
                    || !Float.isFinite(n.positionInMeasure())
                    || !Float.isFinite(n.pageY())) continue;
            var m = score.measures().get(n.measureIndex());
            if (!BeamedHeadQuarterRests.validMeasure(m)) continue;
            float hx = (m.left() + n.positionInMeasure() * (m.right() - m.left())) * w,
                    hy = n.pageY() * h;
            if (hx < 0 || hx >= w || hy < 0 || hy >= h) continue;
            for (var s : staffs) {
                if (s == null) continue;
                float g = s.gap();
                if (s.count() <= 0
                        || s.index() < 0
                        || s.index() >= s.count()
                        || !(s.bottom() > s.top())
                        || s.index() != n.staffIndex()
                        || s.count() != n.staffCount()
                        || !Float.isFinite(g)
                        || g < 6
                        || !Float.isFinite(s.top())
                        || !Float.isFinite(s.bottom())
                        || hy < s.top() - 3 * g
                        || hy > s.bottom() + 4 * g) continue;
                Shaft shaft = shaft(gray, w, h, hx, hy, g);
                if (shaft == null || !head(gray, w, h, hx, hy, g)) continue;
                for (int offset = -5; offset <= 9; offset++) {
                    float expected = s.top() + offset * g;
                    if (Math.abs(expected - hy) > g * 2.2f) continue;
                    for (int rule = Math.max(2, Math.round(expected - g * .3f));
                            rule <= Math.min(h - 3, Math.round(expected + g * .3f));
                            rule++) {
                        for (int width = Math.max(5, Math.round(g * .85f));
                                width <= Math.round(g * 1.65f);
                                width++) {
                            for (int left = Math.max(1, Math.round(hx - width * .5f - g * .25f));
                                    left
                                            <= Math.min(
                                                    w - width - 2,
                                                    Math.round(hx - width * .5f + g * .25f));
                                    left++) {
                                int right = left + width - 1;
                                if (left < m.left() * w || right > m.right() * w) continue;
                                int[] bounds =
                                        rectangle(gray, w, h, left, right, rule, g, shaft, hx, hy);
                                if (bounds == null
                                        || hy >= bounds[0] - g * .2f && hy <= bounds[1] + g * .2f
                                        || bounds[0] < m.top() * h
                                        || bounds[1] > m.bottom() * h
                                        || beamlet(gray, w, h, left, right, bounds[0], g, shaft))
                                    continue;
                                boolean otherHead = false;
                                for (var peer : score.notes()) {
                                    if (peer == n
                                            || peer.measureIndex() != n.measureIndex()
                                            || !Float.isFinite(peer.positionInMeasure())
                                            || !Float.isFinite(peer.pageY())) continue;
                                    float
                                            px =
                                                    (m.left()
                                                                    + peer.positionInMeasure()
                                                                            * (m.right()
                                                                                    - m.left()))
                                                            * w,
                                            py = peer.pageY() * h;
                                    if (px >= left - g * .25f
                                            && px <= right + g * .25f
                                            && py >= bounds[0] - g * .6f
                                            && py <= bounds[1] + g * .6f) {
                                        otherHead = true;
                                        break;
                                    }
                                }
                                if (otherHead) continue;
                                float cy = (bounds[0] + bounds[1]) * .5f / h,
                                        cx = (left + right) * .5f / w;
                                float position = (cx - m.left()) / (m.right() - m.left());
                                Candidate c =
                                        new Candidate(
                                                ni,
                                                left,
                                                right,
                                                bounds[0],
                                                bounds[1],
                                                g,
                                                s.index(),
                                                s.count(),
                                                n.measureIndex(),
                                                position,
                                                cy);
                                boolean duplicate =
                                        result.stream()
                                                .anyMatch(
                                                        old ->
                                                                old.staffIndex() == c.staffIndex()
                                                                        && old.staffCount()
                                                                                == c.staffCount()
                                                                        && old.measureIndex()
                                                                                == c.measureIndex()
                                                                        && Math.abs(
                                                                                        old
                                                                                                        .position()
                                                                                                - position)
                                                                                < .025f
                                                                        && Math.abs(
                                                                                        old.pageY()
                                                                                                - cy)
                                                                                < g / h);
                                if (!duplicate) result.add(c);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    static Shaft shaft(byte[] gray, int w, int h, float x, float y, float g) {
        int direction = PrintedStemDirection.detect(gray, w, h, x, y, g);
        if (direction == 0) return null;
        int top = Math.round(direction == 1 ? y - 2.4f * g : y + .65f * g),
                bottom = Math.round(direction == 1 ? y - .65f * g : y + 2.4f * g);
        if (top < 0 || bottom >= h) return null;
        int a = Math.max(0, Math.round(x + (direction == 1 ? .4f : -.9f) * g)),
                b = Math.min(w - 1, Math.round(x + (direction == 1 ? .9f : -.4f) * g));
        int first = -1, last = -1;
        for (int col = a; col <= b; col++) {
            int hits = 0;
            for (int row = top; row <= bottom; row++) if (dark(gray, w, col, row)) hits++;
            if (hits >= (bottom - top + 1) * .95f) {
                if (first < 0) first = col;
                last = col;
            } else if (first >= 0) break;
        }
        return first < 0 || last - first + 1 > Math.max(3, g * .22f)
                ? null
                : new Shaft(first, last, direction);
    }

    static boolean head(byte[] gray, int w, int h, float x, float y, float g) {
        int l = Math.round(x - .4f * g),
                r = Math.round(x + .4f * g),
                t = Math.round(y - .22f * g),
                b = Math.round(y + .22f * g);
        if (l < 0 || r >= w || t < 0 || b >= h) return false;
        int filled = 0;
        for (int row = t; row <= b; row++)
            if (ink(gray, w, l, r, row) >= (r - l + 1) * .8f) filled++;
        return filled >= (b - t + 1) * .8f;
    }

    static int[] rectangle(
            byte[] gray,
            int w,
            int h,
            int left,
            int right,
            int rule,
            float g,
            Shaft shaft,
            float hx,
            float hy) {
        int width = right - left + 1, span = Math.max(4, Math.round(g * 1.25f));
        if (left - span < 0 || right + span >= w || !support(gray, w, left, right, rule, span))
            return null;
        int last = rule - 1;
        while (last > rule - g * .3f && support(gray, w, left, right, last, span)) last--;
        int first = last;
        while (first > 0
                && last - first < g * .8f
                && ink(gray, w, left, right, first) >= width * .85f) first--;
        first++;
        int rows = last - first + 1;
        if (rows < Math.max(3, Math.ceil(g * .3f)) || rows > g * .65f || first < 1) return null;
        if (inkWithoutOwner(gray, w, left, right, first - 1, shaft, hx, hy, g) > width * .25f)
            return null;
        int margin = Math.max(2, Math.round(g * .22f));
        if (left - margin < 0 || right + margin >= w) return null;
        for (int row = first; row <= last; row++) {
            if (inkWithoutShaft(gray, w, left - margin, left - 1, row, shaft) > 1
                    || inkWithoutShaft(gray, w, right + 1, right + margin, row, shaft) > 1)
                return null;
            if (!dark(gray, w, left, row) && !dark(gray, w, left + 1, row)
                    || !dark(gray, w, right, row) && !dark(gray, w, right - 1, row)) return null;
        }
        return new int[] {first, last};
    }

    static boolean beamlet(
            byte[] gray, int w, int h, int left, int right, int top, float g, Shaft shaft) {
        if (shaft.direction() != 1) return false;
        int extension = Math.max(4, Math.round(g));
        int a = left - extension, b = shaft.right();
        if (a < 0 || b >= w || shaft.left() < right - g * .35f) return false;
        int run = 0;
        for (int row = Math.max(0, Math.round(top - g * 1.5f));
                row < Math.max(0, Math.round(top - g * .3f));
                row++) {
            if (ink(gray, w, a, b, row) >= (b - a + 1) * .95f) run++;
            else run = 0;
            if (run >= Math.max(3, Math.round(g * .22f))) return true;
        }
        return false;
    }

    static boolean support(byte[] gray, int w, int l, int r, int y, int span) {
        return ink(gray, w, l - span, l - 1, y) >= span * .90f
                && ink(gray, w, r + 1, r + span, y) >= span * .90f
                && ink(gray, w, l, r, y) >= (r - l + 1) * .9f;
    }

    static int ink(byte[] gray, int w, int l, int r, int y) {
        int count = 0;
        for (int x = l; x <= r; x++) if (dark(gray, w, x, y)) count++;
        return count;
    }

    static int inkWithoutShaft(byte[] gray, int w, int l, int r, int y, Shaft shaft) {
        int count = 0;
        for (int x = l; x <= r; x++)
            if ((x < shaft.left() || x > shaft.right()) && dark(gray, w, x, y)) count++;
        return count;
    }

    static int inkWithoutOwner(
            byte[] gray, int w, int l, int r, int y, Shaft shaft, float hx, float hy, float gap) {
        int count = 0;
        for (int x = l; x <= r; x++)
            if ((x < shaft.left() || x > shaft.right())
                    && !(Math.abs(y - hy) < gap * .5f && Math.abs(x - hx) < gap * .8f)
                    && dark(gray, w, x, y)) count++;
        return count;
    }

    static boolean dark(byte[] gray, int w, int x, int y) {
        return (gray[y * w + x] & 255) < 170;
    }
}

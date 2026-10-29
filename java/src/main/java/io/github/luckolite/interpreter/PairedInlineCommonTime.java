// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** A repeated inline C needs its own shared boundary and complete, agreeing staff copies. */
final class PairedInlineCommonTime {
    record Proof(int measureIndex, List<MeterChangeDetector.Crop> copies) {
        Proof {
            copies = List.copyOf(copies);
        }
    }

    private record Column(int left, int right) {}

    private record Glyph(MeterChangeDetector.Crop crop, boolean[] silhouette, boolean[] visible) {}

    private record StaffFrame(RawStaffLineDetector.StaffLines lines, boolean[] rules) {
        int top() {
            return lines.top();
        }

        int bottom() {
            return lines.bottom();
        }

        float gap() {
            return lines.gap();
        }

        float center() {
            return lines.center();
        }
    }

    private PairedInlineCommonTime() {}

    static List<Proof> prove(
            byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
        if (labels == null
                || gray == null
                || width < 1
                || height < 1
                || (long) width * height != labels.length
                || labels.length != gray.length
                || measures == null
                || measures.isEmpty()) return List.of();
        var rawStaffs = RawStaffLineDetector.detect(gray, width, height);
        if (rawStaffs.size() > 128) return List.of();
        var staffs = new ArrayList<StaffFrame>();
        for (var raw : rawStaffs) staffs.add(frame(raw, gray, width, height));
        var result = new ArrayList<Proof>();
        for (int s = 0; s + 1 < staffs.size(); s++) {
            var upper = staffs.get(s);
            var lower = staffs.get(s + 1);
            float gap = upper.gap();
            if (gap < 6
                    || Math.abs(gap - lower.gap()) > gap * .12f
                    || lower.top() - upper.top() < gap * 6
                    || lower.top() - upper.top() > gap * 24) continue;
            var columns = boundaryColumns(gray, width, height, upper.top(), lower.bottom(), gap);
            for (int b = 0; b + 1 < columns.size(); b++) {
                var first = columns.get(b);
                var second = columns.get(b + 1);
                int separation = second.left() - first.right();
                if (separation < 2
                        || separation > gap * .9f
                        || b > 0 && first.left() - columns.get(b - 1).right() < gap
                        || b + 2 < columns.size()
                                && columns.get(b + 2).left() - second.right() < gap) continue;
                var a = glyph(gray, labels, width, height, second.right(), upper);
                var c = glyph(gray, labels, width, height, second.right(), lower);
                if (a == null || c == null || !agree(a, c, gap)) continue;
                int measure =
                        MeterChangeDetector.followingMeasure(a.crop(), width, height, measures);
                if (measure < 0
                        || MeterChangeDetector.followingMeasure(c.crop(), width, height, measures)
                                != measure
                        || Math.abs(measures.get(measure).left() * width - second.right()) > gap
                        || a.crop().right() > measures.get(measure).right() * width) continue;
                var proof = new Proof(measure, List.of(a.crop(), c.crop()));
                if (result.stream().noneMatch(old -> old.measureIndex() == measure))
                    result.add(proof);
            }
        }
        return List.copyOf(result);
    }

    /** Remove only head labels owned by those exact printed C crops; retain the original frame. */
    static byte[] normalize(
            byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
        byte[] result = labels;
        for (var proof : prove(labels, gray, width, height, measures))
            for (var crop : proof.copies())
                for (int y = crop.top(); y < crop.bottom(); y++)
                    for (int x = crop.left(); x < crop.right(); x++) {
                        int at = y * width + x;
                        if (labels[at] != OmrMeasurePostProcessor.NOTEHEAD) continue;
                        if (result == labels) result = labels.clone();
                        result[at] = 0;
                    }
        return result;
    }

    private static List<Column> boundaryColumns(
            byte[] gray, int width, int height, int top, int bottom, float gap) {
        var result = new ArrayList<Column>();
        int start = -1;
        for (int x = 0; x <= width; x++) {
            boolean continuous = x < width && continuous(gray, width, height, x, top, bottom);
            if (continuous && start < 0) start = x;
            if (!continuous && start >= 0) {
                if (x - start <= Math.max(2, Math.round(gap * .4f)))
                    result.add(new Column(start, x - 1));
                start = -1;
            }
        }
        return result;
    }

    private static boolean continuous(
            byte[] gray, int width, int height, int x, int top, int bottom) {
        if (top < 0 || bottom >= height) return false;
        for (int i = 0; i < 5; i++)
            if ((gray[(top + (bottom - top) * i / 4) * width + x] & 255) > 185) return false;
        int missing = 0, allowed = Math.max(1, (bottom - top + 1) / 25);
        for (int y = top; y <= bottom; y++)
            if ((gray[y * width + x] & 255) > 185 && ++missing > allowed) return false;
        return true;
    }

    private static Glyph glyph(
            byte[] gray, byte[] labels, int width, int height, int boundary, StaffFrame staff) {
        float gap = staff.gap(), mid = staff.center();
        int left = Math.max(0, boundary + Math.round(gap * .25f));
        int right = Math.min(width - 1, boundary + Math.round(gap * 4));
        if (right - left + 1 < gap * .9f) return null;
        int top = Math.max(0, Math.round(mid - gap * 1.65f));
        int bottom = Math.min(height - 1, Math.round(mid + gap * 1.65f));
        int[] columns = new int[right - left + 1];
        for (int x = left; x <= right; x++)
            for (int y = top; y <= bottom; y++)
                if (!rule(y, staff) && ink(gray, width, x, y)) columns[x - left]++;
        int l = right + 1, r = -1;
        for (int x = left; x <= right; x++) {
            if (columns[x - left] < 2) continue;
            l = Math.min(l, x);
            r = x;
        }
        // Include both sides of an arc hidden by a staff rule. Extra nearby notation makes
        // this entire bounded column ambiguous; it is not silently discarded as another word.
        if (r - l + 1 < gap * .9f || r - l + 1 > gap * 2.7f) return null;
        return shape(gray, labels, width, height, l, r, staff);
    }

    private static Glyph shape(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int left,
            int right,
            StaffFrame staff) {
        float gap = staff.gap(), mid = staff.center();
        int top = height, bottom = -1, count = 0, span = right - left + 1;
        for (int y = Math.max(0, Math.round(mid - gap * 1.65f));
                y <= Math.min(height - 1, Math.round(mid + gap * 1.65f));
                y++)
            for (int x = left; x <= right; x++)
                if (!rule(y, staff) && ink(gray, width, x, y)) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    count++;
                }
        if (top > mid - gap * .75f
                || bottom < mid + gap * .75f
                || bottom - top > gap * 3.25f
                || count < gap * gap * .35f) return null;
        if (band(gray, width, left, right, mid - gap * .75f, mid - gap * .18f, staff, true)
                        < Math.floor(gap * .65f)
                || band(gray, width, left, right, mid + gap * .18f, mid + gap * .75f, staff, true)
                        < Math.floor(gap * .65f)
                || band(gray, width, left, right, mid - gap * 1.35f, mid - gap * .35f, staff, false)
                        < Math.floor(gap * .65f)
                || band(gray, width, left, right, mid + gap * .35f, mid + gap * 1.35f, staff, false)
                        < Math.floor(gap * .65f)) return null;
        // A middle shaft is a cut, sharp or stem, not an agreeing pair of plain C signs.
        for (int x = Math.round(left + span * .32f); x <= Math.round(left + span * .68f); x++) {
            int rows = 0, dark = 0;
            for (int y = Math.round(mid - gap * 1.15f); y <= Math.round(mid + gap * 1.15f); y++) {
                if (y < 0 || y >= height || rule(y, staff)) continue;
                rows++;
                if (ink(gray, width, x, y)) dark++;
            }
            if (rows > 0 && dark >= rows * .9f && separatedShaft(gray, width, left, x, staff))
                return null;
        }
        int openRows = 0, consecutive = 0, bestRun = 0;
        for (int y = Math.round(mid - gap * .55f); y <= Math.round(mid + gap * .55f); y++) {
            if (y < 0 || y >= height || rule(y, staff)) continue;
            int arc = -1;
            for (int x = left; x <= Math.round(left + span * .55f); x++)
                if (ink(gray, width, x, y)) arc = x;
            boolean open = arc >= 0 && right - arc >= span * .32f;
            for (int x = arc + 1; open && x <= right; x++) if (ink(gray, width, x, y)) open = false;
            if (open) {
                openRows++;
                bestRun = Math.max(bestRun, ++consecutive);
            } else consecutive = 0;
        }
        int needed = Math.max(2, (int) Math.ceil(gap * .12f));
        if (openRows < needed || bestRun < needed) return null;
        // An adjacent sounding-note stem must not be absorbed into the printed sign.
        for (int x = left; x <= right; x++)
            for (int y = Math.max(0, staff.top() - Math.round(gap));
                    y <= Math.min(height - 1, staff.bottom() + Math.round(gap));
                    y++)
                if ((y < staff.top() - gap * .25f || y > staff.bottom() + gap * .25f)
                        && labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST)
                    return null;
        if (!connected(
                gray,
                width,
                left,
                right,
                Math.max(0, Math.round(mid - gap * 1.65f)),
                Math.min(height - 1, Math.round(mid + gap * 1.65f)),
                staff)) return null;
        boolean[] silhouette = new boolean[24 * 32];
        boolean[] visible = new boolean[silhouette.length];
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 24; x++) {
                int xx = left + Math.round(x * (right - left) / 23f);
                int yy = Math.round(mid - gap * 1.65f + y * gap * 3.3f / 31f);
                visible[y * 24 + x] = yy >= 0 && yy < height && !rule(yy, staff);
                silhouette[y * 24 + x] = visible[y * 24 + x] && ink(gray, width, xx, yy);
            }
        return new Glyph(
                new MeterChangeDetector.Crop(left, top, right + 1, bottom + 1, staff.top(), gap),
                silhouette,
                visible);
    }

    private static int band(
            byte[] gray,
            int width,
            int left,
            int right,
            float top,
            float bottom,
            StaffFrame staff,
            boolean shoulder) {
        int count = 0, span = right - left + 1;
        int l = shoulder ? left : Math.round(left + span * .55f);
        int r = shoulder ? Math.round(left + span * .35f) : right;
        for (int y = Math.max(0, Math.round(top)); y <= Math.round(bottom); y++)
            if (!rule(y, staff)) for (int x = l; x <= r; x++) if (ink(gray, width, x, y)) count++;
        return count;
    }

    private static boolean connected(
            byte[] gray, int width, int left, int right, int top, int bottom, StaffFrame staff) {
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int totalDark = 0, largest = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++)
                if (!rule(y, staff) && ink(gray, width, x, y)) totalDark++;
        for (int seed = 0; seed < seen.length; seed++) {
            int sx = seed % w, sy = seed / w;
            if (seen[seed] || (gray[(top + sy) * width + left + sx] & 255) > 185) continue;
            int read = 0, write = 1, dark = 0;
            queue[0] = seed;
            seen[seed] = true;
            while (read < write) {
                int at = queue[read++], x = at % w, y = at / w;
                if (!rule(top + y, staff) && ink(gray, width, left + x, top + y)) dark++;
                // The upper arc can coincide with a printed staff rule. Follow actual ink in
                // this bounded glyph box; never bridge a white gap merely because it is a rule.
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                        int next = yy * w + xx;
                        if (seen[next] || (gray[(top + yy) * width + left + xx] & 255) > 185)
                            continue;
                        seen[next] = true;
                        queue[write++] = next;
                    }
            }
            largest = Math.max(largest, dark);
        }
        return totalDark > 0 && largest >= totalDark * .9f;
    }

    private static boolean separatedShaft(
            byte[] gray, int width, int left, int shaft, StaffFrame staff) {
        int[] supported = new int[2], rows = new int[2];
        float mid = staff.center(), gap = staff.gap();
        for (int y = Math.round(mid - gap * .7f); y <= Math.round(mid + gap * .7f); y++) {
            if (rule(y, staff)) continue;
            int half = y < mid ? 0 : 1;
            rows[half]++;
            if (!ink(gray, width, shaft, y)) continue;
            boolean arc = false, channel = false;
            for (int x = left; x < shaft - Math.max(1, Math.round(gap * .1f)); x++) {
                if (ink(gray, width, x, y)) arc = true;
                else if (arc) channel = true;
            }
            if (channel) supported[half]++;
        }
        return rows[0] > 0
                && rows[1] > 0
                && supported[0] >= rows[0] * .65f
                && supported[1] >= rows[1] * .65f;
    }

    private static boolean agree(Glyph a, Glyph b, float gap) {
        var x = a.crop();
        var y = b.crop();
        if (Math.abs(x.left() - y.left()) > gap * .2f
                || Math.abs(x.right() - y.right()) > gap * .2f
                || Math.abs((x.bottom() - x.top()) - (y.bottom() - y.top())) > gap * .25f)
            return false;
        int union = 0, intersection = 0;
        for (int i = 0; i < a.silhouette().length; i++) {
            if (!a.visible()[i] || !b.visible()[i]) continue;
            if (a.silhouette()[i] || b.silhouette()[i]) union++;
            if (a.silhouette()[i] && b.silhouette()[i]) intersection++;
        }
        return union > 0 && intersection >= union * .68f;
    }

    private static StaffFrame frame(
            RawStaffLineDetector.StaffLines staff, byte[] gray, int width, int height) {
        boolean[] rules = new boolean[height];
        int radius = Math.max(1, Math.round(staff.gap() * .12f));
        for (int row : staff.rows())
            for (int y = Math.max(0, row - radius); y <= Math.min(height - 1, row + radius); y++) {
                int dark = 0;
                for (int x = 0; x < width; x++) if (ink(gray, width, x, y)) dark++;
                // Suppress actual long rules, not adjacent paper containing a C's small opening.
                rules[y] = dark >= Math.max(24, width * .35f);
            }
        return new StaffFrame(staff, rules);
    }

    private static boolean rule(int y, StaffFrame staff) {
        return y >= 0 && y < staff.rules().length && staff.rules()[y];
    }

    private static boolean ink(byte[] gray, int width, int x, int y) {
        return (gray[y * width + x] & 255) < 155;
    }
}

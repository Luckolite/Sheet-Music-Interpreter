// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Private global proof: adjacent complete physical systems and a short owned returning bowl. */
final class WideSystemIncomingTieProof {
    record Endpoint(
            ScoreNoteEvent note,
            int left,
            int right,
            int top,
            int bottom,
            float x,
            float y,
            float gap) {}

    record Bowl(int left, int right, int top, int bottom, float curvature) {}

    record Proof(
            boolean sameWrittenPitch,
            boolean adjacentPhysicalSystems,
            boolean outgoingProved,
            boolean incomingProved,
            int side,
            Bowl incoming) {
        boolean proved() {
            return sameWrittenPitch && adjacentPhysicalSystems && outgoingProved && incomingProved;
        }
    }

    static Proof prove(
            byte[] gray,
            int w,
            int h,
            Endpoint before,
            Endpoint after,
            boolean outgoingProved,
            int side) {
        boolean pitch = matchingPitch(before, after);
        boolean systems = pitch && adjacentSystems(gray, w, h, before, after);
        Bowl bowl = systems && outgoingProved ? incoming(gray, w, h, after, side) : null;
        return new Proof(pitch, systems, outgoingProved, bowl != null, side, bowl);
    }

    private static boolean matchingPitch(Endpoint a, Endpoint b) {
        if (a == null || b == null || a.note() == null || b.note() == null) return false;
        var x = a.note();
        var y = b.note();
        return x.kind() == ScoreNoteEvent.Kind.PITCHED
                && y.kind() == ScoreNoteEvent.Kind.PITCHED
                && x.staffCount() == y.staffCount()
                && ScoreTiePitchGuard.sameContinuingStaff(x, y)
                && x.clefBottomDiatonic() != ScoreNoteEvent.CLEF_UNKNOWN
                && x.clefBottomDiatonic() == y.clefBottomDiatonic()
                && x.diatonicPitchIdentity() == y.diatonicPitchIdentity()
                && x.octaveShift() == y.octaveShift()
                && (x.writtenAccidental() == y.writtenAccidental()
                        || x.writtenAccidental() == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                        || y.writtenAccidental() == ScoreNoteEvent.ACCIDENTAL_FROM_KEY)
                && y.measureIndex() == x.measureIndex() + 1
                && y.positionInMeasure() < .58f
                && x.followingRestBeats() == 0
                && y.leadingRestBeats() == 0;
    }

    static boolean adjacentSystems(byte[] gray, int w, int h, Endpoint a, Endpoint b) {
        if (gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || a == null
                || b == null) return false;
        int count = a.note().staffCount(), rank = a.note().staffIndex();
        if (count < 1
                || count > 8
                || rank < 0
                || rank >= count
                || b.note().staffCount() != count
                || b.note().staffIndex() != rank
                || !Float.isFinite(a.x() + a.y() + b.x() + b.y() + a.gap() + b.gap())
                || a.gap() < 5
                || b.gap() < 5
                || a.x()
                        <= w
                                * (ScoreNoteTiming.hasIndependentSustain(a.note())
                                                && ScoreNoteTiming.hasIndependentSustain(b.note())
                                        ? .5f
                                        : .65f)
                || b.x() >= w * .35f
                || !headInk(gray, w, h, a)
                || !headInk(gray, w, h, b)) return false;
        var staffs = RawStaffLineDetector.detect(gray, w, h);
        if (staffs.size() < count * 2 || staffs.size() % count != 0) return false;
        int first = frame(staffs, a, count, rank), last = frame(staffs, b, count, rank);
        if (first < 0 || last < 0 || last / count != first / count + 1) return false;
        int startA = first - rank, startB = last - rank;
        for (int i = 0; i < count; i++) {
            var one = staffs.get(startA + i);
            var two = staffs.get(startB + i);
            float gap = (one.gap() + two.gap()) * .5f;
            if (one.gap() < 5 || two.gap() < 5 || Math.abs(one.gap() - two.gap()) > gap * .2f)
                return false;
            if (i > 0) {
                float da = one.top() - staffs.get(startA + i - 1).bottom(),
                        db = two.top() - staffs.get(startB + i - 1).bottom();
                if (da < gap || db < gap || Math.abs(da - db) > gap * 1.5f) return false;
            }
        }
        return true;
    }

    private static int frame(
            List<RawStaffLineDetector.StaffLines> staffs, Endpoint n, int count, int rank) {
        int found = -1;
        float error = Float.POSITIVE_INFINITY;
        for (int i = rank; i < staffs.size(); i += count) {
            var s = staffs.get(i);
            float predicted = s.bottom() - n.note().staffStep() * s.gap() * .5f;
            float e = Math.abs(n.y() - predicted);
            if (s.gap() < n.gap() * .65f || s.gap() > n.gap() * 1.55f || e > s.gap() * .65f)
                continue;
            if (e < error) {
                error = e;
                found = i;
            }
        }
        return found;
    }

    static Bowl incoming(byte[] gray, int w, int h, Endpoint note, int side) {
        if (gray == null
                || gray.length != (long) w * h
                || note == null
                || (side != -1 && side != 1)
                || note.gap() < 5) return null;
        float gap = note.gap();
        int lo = Math.max(1, note.left() - Math.round(gap * 2.8f));
        int hi = Math.min(w - 2, note.left() - Math.max(2, Math.round(gap * .12f)));
        int top = Math.max(1, Math.round(note.y() + (side < 0 ? -1.45f : .15f) * gap));
        int bottom = Math.min(h - 2, Math.round(note.y() + (side < 0 ? -.15f : 1.45f) * gap));
        if (lo >= hi || top >= bottom) return null;
        int cw = hi - lo + 1, ch = bottom - top + 1;
        var seen = new boolean[cw * ch];
        var stack = new int[cw * ch];
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || !dark(gray, w, lo + seed % cw, top + seed / cw)) continue;
            int size = 0;
            stack[size++] = seed;
            seen[seed] = true;
            int minX = cw, maxX = -1, minY = ch, maxY = -1, area = 0;
            var pixels = new ArrayList<Integer>();
            while (size > 0) {
                int at = stack[--size], x = at % cw, y = at / cw;
                pixels.add(at);
                area++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= cw || ny < 0 || ny >= ch) continue;
                        int next = ny * cw + nx;
                        if (!seen[next] && dark(gray, w, lo + nx, top + ny)) {
                            seen[next] = true;
                            stack[size++] = next;
                        }
                    }
            }
            int span = maxX - minX + 1, depth = maxY - minY + 1;
            if (minX == 0
                    || minY == 0
                    || maxY == ch - 1
                    || span < gap * .95f
                    || span > gap * 2.6f
                    || depth < Math.max(3, gap * .16f)
                    || depth > gap * .8f
                    || area < span * 1.25f
                    || note.left() - (lo + maxX) > gap * .5f) continue;
            // The search crop ends just before this head. A faint last shoulder pixel may
            // touch it; inspect the original next column to prove the contour itself ended.
            if (maxX == cw - 1) {
                boolean detached = true;
                for (int y = top + minY - 1; y <= top + maxY + 1; y++)
                    if ((gray[y * w + hi + 1] & 255) < 225) {
                        detached = false;
                        break;
                    }
                if (!detached) continue;
            }
            float[] centers = new float[span];
            int[] first = new int[span], last = new int[span], hits = new int[span];
            Arrays.fill(first, Integer.MAX_VALUE);
            Arrays.fill(last, Integer.MIN_VALUE);
            for (int at : pixels) {
                int x = at % cw - minX, y = top + at / cw;
                first[x] = Math.min(first[x], y);
                last[x] = Math.max(last[x], y);
                hits[x]++;
            }
            boolean valid = true;
            for (int x = 0; x < span; x++) {
                if (hits[x] == 0
                        || last[x] - first[x] + 1 > gap * .4f
                        || hits[x] < last[x] - first[x]) {
                    valid = false;
                    break;
                }
                centers[x] = (first[x] + last[x]) * .5f;
            }
            if (!valid) continue;
            float left = mean(centers, 0, .15f),
                    mid = mean(centers, .4f, .6f),
                    right = mean(centers, .85f, 1);
            float curvature = side * (mid - (left + right) * .5f);
            if (curvature < Math.max(1.5f, gap * .12f)
                    || curvature > gap * .75f
                    || side * (mid - left) < gap * .10f
                    || side * (mid - right) < gap * .10f) continue;
            float a = mean(centers, .05f, .2f),
                    b = mean(centers, .2f, .35f),
                    c = mean(centers, .4f, .6f),
                    d = mean(centers, .65f, .8f),
                    e = mean(centers, .8f, .95f);
            float rise = side * (b - a),
                    flatten = side * (c - b),
                    fall = side * (d - e),
                    flatRight = side * (c - d);
            if (rise < gap * .05f
                    || fall < gap * .05f
                    || flatten > rise * .9f + .15f
                    || flatRight > fall * .9f + .15f) continue;
            // Actual stroke centers must fit a smooth returning quadratic; an angular V,
            // straight rule, double contour, broken fragment or nearby shaft cannot supply it.
            double[] fit = quadratic(centers);
            if (fit == null || -side * fit[2] < gap * .55f) continue;
            double error = 0;
            for (int x = 0; x < span; x++) {
                double t = x / (double) (span - 1),
                        predicted = fit[0] + fit[1] * t + fit[2] * t * t;
                error += Math.pow(centers[x] - predicted, 2);
            }
            if (Math.sqrt(error / span) > Math.max(.65f, gap * .075f)) continue;
            return new Bowl(lo + minX, lo + maxX, top + minY, top + maxY, curvature);
        }
        return null;
    }

    private static double[] quadratic(float[] y) {
        double[][] a = new double[3][4];
        for (int x = 0; x < y.length; x++) {
            double t = x / (double) (y.length - 1);
            double[] v = {1, t, t * t};
            for (int i = 0; i < 3; i++) {
                a[i][3] += v[i] * y[x];
                for (int j = 0; j < 3; j++) a[i][j] += v[i] * v[j];
            }
        }
        for (int i = 0; i < 3; i++) {
            double p = a[i][i];
            if (Math.abs(p) < 1e-8) return null;
            for (int j = i; j < 4; j++) a[i][j] /= p;
            for (int k = 0; k < 3; k++)
                if (k != i) {
                    double q = a[k][i];
                    for (int j = i; j < 4; j++) a[k][j] -= q * a[i][j];
                }
        }
        return new double[] {a[0][3], a[1][3], a[2][3]};
    }

    private static float mean(float[] x, float from, float to) {
        int a = Math.round(from * (x.length - 1)), b = Math.round(to * (x.length - 1));
        float sum = 0;
        for (int i = a; i <= b; i++) sum += x[i];
        return sum / (b - a + 1);
    }

    private static boolean headInk(byte[] g, int w, int h, Endpoint n) {
        if (n.left() < 0
                || n.right() >= w
                || n.top() < 0
                || n.bottom() >= h
                || n.left() > n.right()
                || n.top() > n.bottom()) return false;
        int count = 0, broadRows = 0;
        for (int y = n.top(); y <= n.bottom(); y++) {
            int left = n.right() + 1, right = n.left() - 1, hits = 0;
            for (int x = n.left(); x <= n.right(); x++)
                if ((g[y * w + x] & 255) < 205) {
                    count++;
                    hits++;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            // A rule or shaft alone cannot supply a head. Separate sides of a hollow
            // head still span the required width on several original-pixel rows.
            if (hits >= 2 && right - left + 1 >= n.gap() * .45f) broadRows++;
        }
        return count >= Math.max(3, n.gap() * n.gap() * .10f)
                && broadRows >= Math.max(5, Math.ceil(n.gap() * .4f));
    }

    private static boolean dark(byte[] g, int w, int x, int y) {
        return (g[y * w + x] & 255) < 205;
    }
}

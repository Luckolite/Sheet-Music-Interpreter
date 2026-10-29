// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Adjudicates only isolated observed rule-edge rows; never declares complete written values. */
final class ObservedStaffRuleFringe {
    record Evidence(byte[] labels, int[] rows, int protectedRails) {}

    static final class Observation {
        int stemX, stemEnd, top, bottom, left, right, minimum;
        boolean upward, attached, valid;
        final java.util.List<java.util.List<Integer>> bands = new java.util.ArrayList<>();

        void frame(
                int x,
                int end,
                boolean up,
                int a,
                int b,
                int l,
                int r,
                int min,
                boolean hasAttached) {
            stemX = x;
            stemEnd = end;
            upward = up;
            top = a;
            bottom = b;
            left = l;
            right = r;
            minimum = min;
            attached = hasAttached;
            valid = true;
        }

        void row(int y, boolean starting) {
            if (starting) bands.add(new java.util.ArrayList<>());
            bands.get(bands.size() - 1).add(y);
        }
    }

    private record Rule(StaffPitchTrack track, float phase, int width, int mass) {}

    private record Run(int first, int last) {}

    private ObservedStaffRuleFringe() {}

    static Evidence adjudicate(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            ShortStemSingleBeamSupport.Head own,
            int[] shaft,
            ShortStemSingleBeamSupport.Head peer,
            int[] fullPeer,
            float gap,
            StaffPitchTrack track,
            Observation observation) {
        if (labels == null
                || gray == null
                || labels.length != gray.length
                || gray.length != (long) w * h
                || w < 3
                || h < 3
                || !Float.isFinite(gap)
                || gap < 6
                || gap > 64
                || own == null
                || shaft == null
                || shaft.length != 3
                || Math.abs(shaft[2]) != 1
                || peer == null
                || fullPeer == null
                || fullPeer.length != 3
                || shaft[2] != fullPeer[2]
                || track == null
                || !track.verified()
                || !track.verifiedAt(shaft[0])) return null;
        if (observation == null || !observation.valid || observation.attached) return null;
        int left = observation.left, right = observation.right, minimum = observation.minimum;
        int searchLeft = observation.stemX, searchRight = searchLeft;
        int commonLeft = left, commonRight = right;
        Rule rule = references(labels, gray, w, h, left, right, gap, track);
        if (rule == null) return null;
        int protectedRails =
                visibleRails(labels, gray, w, h, own, shaft, peer, fullPeer, gap, rule);
        if (protectedRails < 0) return null;
        java.util.List<Integer> rows = new java.util.ArrayList<>();
        byte[] corrected = null;
        for (java.util.List<Integer> nativeBand : observation.bands) {
            if (nativeBand.size() != 1) continue;
            int y = nativeBand.get(0);
            if (y < 1 || y >= h - 1) continue;
            if (staff(labels, w, y, left, right) >= minimum) continue;
            boolean above = staff(labels, w, y - 1, commonLeft, commonRight) >= minimum;
            boolean below = staff(labels, w, y + 1, commonLeft, commonRight) >= minimum;
            if (!above && !below) continue;
            if (!above && longRun(gray, w, y - 1, left, right, searchLeft, searchRight) >= minimum)
                continue;
            if (!below && longRun(gray, w, y + 1, left, right, searchLeft, searchRight) >= minimum)
                continue;
            if (longRun(gray, w, y, left, right, searchLeft, searchRight) < minimum) continue;
            boolean beam = false;
            for (int x = Math.max(left, shaft[0] - Math.round(gap));
                    x <= Math.min(right, shaft[0] + Math.round(gap));
                    x++)
                if (Math.abs(x - shaft[0]) > 2
                        && Math.abs(x - observation.stemX) > 2
                        && Math.abs(x - fullPeer[0]) > 2
                        && !ownedHead(labels, w, own, peer, x, y)) {
                    if (labels[y * w + x] == 5) {
                        int horizontal = 0;
                        for (int xx = x; xx <= Math.min(right, x + minimum); xx++) {
                            if (labels[y * w + xx] != 5) break;
                            horizontal++;
                        }
                        if (horizontal >= minimum) beam = true;
                        for (int adjacent : new int[] {y - 1, y + 1}) {
                            boolean area = true;
                            for (int dx = 0; dx < 3; dx++)
                                if (x + dx > right
                                        || labels[y * w + x + dx] != 5
                                        || labels[adjacent * w + x + dx] != 5) area = false;
                            if (area) beam = true;
                        }
                    }
                    if (labels[y * w + x] == 1) {
                        int horizontal = 0;
                        for (int xx = Math.max(left, x - 2); xx <= Math.min(right, x + 2); xx++)
                            if (Math.abs(xx - shaft[0]) > 2
                                    && Math.abs(xx - observation.stemX) > 2
                                    && Math.abs(xx - fullPeer[0]) > 2
                                    && !ownedHead(labels, w, own, peer, xx, y)
                                    && labels[y * w + xx] == 1) horizontal++;
                        if (horizontal >= 3) beam = true;
                    }
                }
            if (beam) continue;
            int clean = 0;
            boolean excess = false;
            for (int x = Math.max(left, shaft[0] - Math.round(gap));
                    x <= Math.min(right, shaft[0] + Math.round(gap));
                    x++) {
                if (Math.abs(x - shaft[0]) <= 2
                        || Math.abs(x - observation.stemX) <= 2
                        || Math.abs(x - fullPeer[0]) <= 2) continue;
                if ((gray[y * w + x] & 255) > 165) continue;
                int a = y, b = y;
                while (a > 0
                        && (gray[(a - 1) * w + x] & 255) < 205
                        && !ownedHead(labels, w, own, peer, x, a - 1)) a--;
                while (b < h - 1
                        && (gray[(b + 1) * w + x] & 255) < 205
                        && !ownedHead(labels, w, own, peer, x, b + 1)) b++;
                if (!ordinary(gray, w, h, x, a, b, gap, rule)) {
                    boolean explained = false;
                    for (java.util.List<Integer> oldBand : observation.bands)
                        if (oldBand.size() > 1) {
                            int supported = 0;
                            for (int yy = a; yy <= b; yy++)
                                if (labels[yy * w + x] == 5 && (gray[yy * w + x] & 255) < 205)
                                    supported++;
                            if (a <= oldBand.get(oldBand.size() - 1) + 1
                                    && b >= oldBand.get(0) - 1
                                    && supported >= 2) explained = true;
                        }
                    int firstSymbol = -1, lastSymbol = -1;
                    for (int yy = a; yy <= b; yy++)
                        if (labels[yy * w + x] == 5) {
                            if (firstSymbol < 0) firstSymbol = yy;
                            lastSymbol = yy;
                        }
                    if (!explained
                            || firstSymbol < 0
                            || b - a + 1 > rule.width + lastSymbol - firstSymbol + 1) {
                        excess = true;
                        break;
                    }
                }
                clean++;
            }
            if (excess || clean < minimum) continue;
            java.util.List<Integer> marked = new java.util.ArrayList<>();
            for (int x = left; x <= right; x++)
                if (labels[y * w + x] == 0
                        && (gray[y * w + x] & 255) <= 165
                        && (labels[(y - 1) * w + x] == 4 || labels[(y + 1) * w + x] == 4))
                    marked.add(y * w + x);
            if (marked.size() >= minimum) {
                if (corrected == null) corrected = labels.clone();
                for (int index : marked) corrected[index] = 4;
                rows.add(y);
            }
        }
        if (rows.isEmpty()) return null;
        return new Evidence(
                corrected,
                rows.stream().mapToInt(Integer::intValue).toArray(),
                Math.max(1, protectedRails));
    }

    private static int staff(byte[] l, int w, int y, int a, int b) {
        int n = 0;
        for (int x = a; x <= b; x++) if (l[y * w + x] == 4) n++;
        return n;
    }

    private static boolean horizontalOne(byte[] l, int w, int x, int y, int a, int b) {
        int n = 0;
        for (int xx = Math.max(a, x - 2); xx <= Math.min(b, x + 2); xx++)
            if (l[y * w + xx] == 1) n++;
        return n >= 3;
    }

    private static int longRun(
            byte[] g, int w, int y, int left, int right, int stemLeft, int stemRight) {
        int best = 0, first = -1, last = -1, previous = -1;
        for (int x = left; x <= right; x++)
            if ((g[y * w + x] & 255) <= 165) {
                if (first < 0 || x - previous > 2) {
                    if (first >= 0 && first <= stemRight + 4 && last >= stemLeft - 4)
                        best = Math.max(best, last - first + 1);
                    first = x;
                }
                last = x;
                previous = x;
            }
        if (first >= 0 && first <= stemRight + 4 && last >= stemLeft - 4)
            best = Math.max(best, last - first + 1);
        return best;
    }

    private static boolean ownedHead(
            byte[] l,
            int w,
            ShortStemSingleBeamSupport.Head a,
            ShortStemSingleBeamSupport.Head b,
            int x,
            int y) {
        return nearHead(l, w, a, x, y) || nearHead(l, w, b, x, y);
    }

    private static boolean nearHead(
            byte[] l, int w, ShortStemSingleBeamSupport.Head a, int x, int y) {
        if (x < a.left() - 2 || x > a.right() + 2 || y < a.top() - 2 || y > a.bottom() + 2)
            return false;
        for (int yy = Math.max(a.top(), y - 2); yy <= Math.min(a.bottom(), y + 2); yy++)
            for (int xx = Math.max(a.left(), x - 2); xx <= Math.min(a.right(), x + 2); xx++)
                if (l[yy * w + xx] == 2) return true;
        return false;
    }

    private static Rule references(
            byte[] l,
            byte[] g,
            int w,
            int h,
            int left,
            int right,
            float gap,
            StaffPitchTrack track) {
        java.util.List<Float> phases = new java.util.ArrayList<>();
        java.util.List<Integer> widths = new java.util.ArrayList<>();
        int lineMask = 0, mass = 0;
        for (float fraction : new float[] {.2f, .5f, .8f}) {
            int x = Math.round(left + (right - left) * fraction);
            float[] f = track.at(x);
            for (int line = 0; line < 5; line++) {
                float seed = f[0] - (4 - line) * f[1];
                Run run = sample(g, w, h, x, seed, gap);
                if (run == null) continue;
                boolean clean = true;
                for (int y = run.first; y <= run.last; y++)
                    if (l[y * w + x] == 1 || l[y * w + x] == 2 || l[y * w + x] == 5) clean = false;
                if (!clean) continue;
                phases.add((run.first + run.last) * .5f - seed);
                widths.add(run.last - run.first + 1);
                lineMask |= 1 << line;
                int m = 0;
                for (int y = run.first; y <= run.last; y++) m += 255 - (g[y * w + x] & 255);
                mass = Math.max(mass, m);
            }
        }
        if (phases.size() < 6 || Integer.bitCount(lineMask) < 3) return null;
        java.util.Collections.sort(phases);
        java.util.Collections.sort(widths);
        return new Rule(track, phases.get(phases.size() / 2), widths.get(widths.size() / 2), mass);
    }

    private static Run sample(byte[] g, int w, int h, int x, float seed, float gap) {
        int lo = Math.max(1, Math.round(seed - gap * .4f)),
                hi = Math.min(h - 2, Math.round(seed + gap * .4f));
        Run best = null;
        float distance = gap;
        int first = -1;
        for (int y = lo; y <= hi + 1; y++) {
            boolean dark = y <= hi && (g[y * w + x] & 255) < 205;
            if (dark && first < 0) first = y;
            if (!dark && first >= 0) {
                float d = Math.abs((first + y - 1) * .5f - seed);
                if (first > lo
                        && y <= hi
                        && y - first <= Math.ceil(gap * .4f)
                        && d < distance
                        && d <= gap * .2f) {
                    best = new Run(first, y - 1);
                    distance = d;
                }
                first = -1;
            }
        }
        return best;
    }

    private static boolean ordinary(
            byte[] g, int w, int h, int x, int a, int b, float gap, Rule rule) {
        if (b - a + 1 > rule.width || a < 1 || b >= h - 1) return false;
        float[] f = rule.track.at(x);
        float top = f[0] - 4 * f[1] + rule.phase;
        int line = Math.round(((a + b) * .5f - top) / f[1]);
        if (line < 0 || line > 4) return false;
        float center = top + line * f[1];
        int first = Math.round(center - (rule.width - 1) * .5f);
        if (a < first - 1 || b > first + rule.width) return false;
        int mass = 0;
        for (int y = a; y <= b; y++) mass += 255 - (g[y * w + x] & 255);
        return mass <= rule.mass;
    }

    private static int visibleRails(
            byte[] l,
            byte[] g,
            int w,
            int h,
            ShortStemSingleBeamSupport.Head own,
            int[] shaft,
            ShortStemSingleBeamSupport.Head peer,
            int[] fullPeer,
            float gap,
            Rule rule) {
        int maximum = 0;
        int lo = Math.max(1, Math.round(shaft[1] - (shaft[2] > 0 ? gap * 1.85f : gap * .5f)));
        int hi = Math.min(h - 2, Math.round(shaft[1] + (shaft[2] < 0 ? gap * 1.85f : gap * .5f)));
        if (shaft[2] < 0) hi = Math.min(hi, own.top() - 2);
        else lo = Math.max(lo, own.bottom() + 2);
        for (float distance : new float[] {.35f, .5f, .75f, 1f, 1.25f, 1.5f})
            for (int side : new int[] {-1, 1}) {
                int x = shaft[0] + side * Math.round(gap * distance);
                if (x < 1 || x >= w - 1) continue;
                if (Math.abs(x - fullPeer[0]) <= 1) continue;
                int count = 0, first = -1;
                for (int y = lo; y <= hi + 1; y++) {
                    boolean dark =
                            y <= hi
                                    && (g[y * w + x] & 255) < 205
                                    && !ownedHead(l, w, own, peer, x, y);
                    if (dark && first < 0) first = y;
                    if (!dark && first >= 0) {
                        int last = y - 1;
                        if (!ordinary(g, w, h, x, first, last, gap, rule)) {
                            boolean support = false;
                            for (int yy = first; yy <= last; yy++)
                                if (l[yy * w + x] == 5
                                        || horizontalOne(
                                                l,
                                                w,
                                                x,
                                                yy,
                                                Math.max(0, x - 3),
                                                Math.min(w - 1, x + 3))) support = true;
                            if (!support) return -1;
                            if (last - first + 1 >= 2) count++;
                        }
                        first = -1;
                    }
                }
                maximum = Math.max(maximum, count);
            }
        return Math.min(3, maximum);
    }
}

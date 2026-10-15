// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Private candidate: three floating strokes between two independently proved stems. */
final class PairedTremoloInk {
    private PairedTremoloInk() {}

    record Stem(int x, int headY, int endY) {}

    record Head(int id, int measure, int staff, double x, double y, double gap, int direction) {}

    record Pair(int firstId, int secondId, List<Integer> railHeadIds, List<Mark> marks) {
        Pair {
            railHeadIds = List.copyOf(railHeadIds);
            marks = List.copyOf(marks);
        }
    }

    record Band(double center, int thickness) {}

    record Mark(int left, int right, double slope, List<Band> bands) {
        Mark {
            bands = List.copyOf(bands);
        }

        boolean contains(double x, double y, double gap) {
            if (x < left || x > right) return false;
            double center = (left + right) * .5;
            for (Band band : bands)
                if (Math.abs(y - band.center - slope * (x - center))
                        <= band.thickness * .5 + gap * .1) return true;
            return false;
        }
    }

    private static int rounded(double value) {
        return (int) Math.rint(value);
    }

    private static boolean valid(byte[] gray, int w, int h, double gap) {
        return gray != null
                && w > 0
                && h > 0
                && gray.length == (long) w * h
                && Double.isFinite(gap)
                && gap >= 5;
    }

    private static boolean dark(byte[] gray, int w, int x, int y) {
        return (gray[y * w + x] & 255) < 185;
    }

    /** Returns evidence only. Callers must retain the pair's semantics before any head removal. */
    static List<Pair> pairs(byte[] gray, int w, int h, List<Head> input) {
        List<Head> heads = input.stream().sorted(Comparator.comparingDouble(Head::x)).toList();
        java.util.Map<Integer, Stem> stems = new java.util.HashMap<>();
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (Head head : heads) {
            if (!ids.add(head.id)) throw new IllegalArgumentException("Duplicate head identity");
            Stem anchor = stem(gray, w, h, head.x, head.y, head.gap, head.direction);
            // Unclassified heads still need an independent-stem check before rejection.
            if (head.direction == 0) {
                anchor = stem(gray, w, h, head.x, head.y, head.gap, 1);
                if (anchor == null) anchor = stem(gray, w, h, head.x, head.y, head.gap, -1);
            }
            if (anchor != null) stems.put(head.id, anchor);
        }
        List<Pair> result = new ArrayList<>();
        for (int i = 0; i < heads.size(); i++) {
            Head a = heads.get(i);
            if (a.direction == 0 || !stems.containsKey(a.id)) continue;
            for (int j = i + 1; j < heads.size(); j++) {
                Head b = heads.get(j);
                if (b.x - a.x > 5 * a.gap) break;
                if (a.measure != b.measure
                        || a.staff != b.staff
                        || a.direction != b.direction
                        || Math.abs(a.gap - b.gap) > .1 * a.gap
                        || !stems.containsKey(b.id)) continue;
                List<Mark> marks = find(gray, w, h, stems.get(a.id), stems.get(b.id), a.gap);
                if (marks.isEmpty()) continue;
                List<Integer> rejected = new ArrayList<>();
                boolean safe = true;
                for (Head other : heads) {
                    if (other.measure != a.measure
                            || other.staff != a.staff
                            || other.x <= a.x
                            || other.x >= b.x) continue;
                    if (stems.containsKey(other.id)
                            || marks.stream().noneMatch(m -> m.contains(other.x, other.y, a.gap))) {
                        safe = false;
                        break;
                    }
                    rejected.add(other.id);
                }
                if (safe) result.add(new Pair(a.id, b.id, rejected, marks));
            }
        }
        java.util.Map<Integer, Integer> owners = new java.util.HashMap<>();
        for (Pair pair : result) {
            owners.merge(pair.firstId, 1, Integer::sum);
            owners.merge(pair.secondId, 1, Integer::sum);
        }
        return result.stream()
                .filter(p -> owners.get(p.firstId) == 1 && owners.get(p.secondId) == 1)
                .toList();
    }

    /** direction is the note's stem direction: +1 up, -1 down. */
    static Stem stem(byte[] gray, int w, int h, double cx, double cy, double gap, int direction) {
        if (!valid(gray, w, h, gap)
                || !Double.isFinite(cx)
                || !Double.isFinite(cy)
                || Math.abs(direction) != 1
                || cx < 0
                || cx >= w
                || cy < 0
                || cy >= h) return null;
        List<Stem> candidates = new ArrayList<>();
        int start = rounded(cy),
                first = rounded(cx + direction * .25 * gap),
                last = rounded(cx + direction * .95 * gap);
        if (start < 0 || start >= h) return null;
        int longest = 0;
        for (int x = first; direction * (last - x) >= 0; x += direction) {
            if (x < 1 || x >= w - 1) continue;
            boolean attached = false;
            for (int y = Math.max(0, start - rounded(.2 * gap));
                    y <= Math.min(h - 1, start + rounded(.2 * gap));
                    y++) if (dark(gray, w, x, y)) attached = true;
            if (!attached) continue;
            int misses = 0, end = start, ink = 0;
            for (int distance = 0; distance < rounded(9 * gap); distance++) {
                int y = start - direction * distance;
                if (y < 0 || y >= h) break;
                if (dark(gray, w, x, y)) {
                    ink++;
                    end = y;
                    misses = 0;
                } else if (++misses > Math.max(1, rounded(.15 * gap))) break;
            }
            int length = Math.abs(end - start);
            if (length < 2.5 * gap || ink / (double) (length + 1) < .85) continue;
            candidates.add(new Stem(x, start, end));
            longest = Math.max(longest, length);
        }
        final double minimum = longest - .15 * gap;
        List<Stem> shaft =
                candidates.stream()
                        .filter(s -> Math.abs(s.endY - s.headY) >= minimum)
                        .sorted(Comparator.comparingInt(Stem::x))
                        .toList();
        if (shaft.isEmpty()) return null;
        // Center the entire shaft so a one-column tie cannot change flank clearance.
        return shaft.get((shaft.size() - 1) / 2);
    }

    static List<Mark> find(byte[] gray, int w, int h, Stem left, Stem right, double gap) {
        if (!valid(gray, w, h, gap) || left == null || right == null) return List.of();
        int distance = right.x - left.x;
        if (distance < 1.4 * gap || distance > 5 * gap) return List.of();
        int direction = left.endY > left.headY ? 1 : -1;
        for (Stem stem : List.of(left, right)) {
            if ((stem.endY - stem.headY) * direction < 2.5 * gap
                    || stem.x < 1
                    || stem.x >= w - 1
                    || Math.min(stem.headY, stem.endY) < 0
                    || Math.max(stem.headY, stem.endY) >= h) return List.of();
            int ink = 0, total = Math.abs(stem.endY - stem.headY) + 1;
            for (int y = Math.min(stem.headY, stem.endY); y <= Math.max(stem.headY, stem.endY); y++)
                if (dark(gray, w, stem.x - 1, y)
                        || dark(gray, w, stem.x, y)
                        || dark(gray, w, stem.x + 1, y)) ink++;
            if (ink / (double) total < .85) return List.of();
        }
        int x0 = rounded(left.x + distance * .30), x1 = rounded(left.x + distance * .75);
        double center = (x0 + x1) * .5;
        int first = Math.max(1, rounded(Math.min(left.endY, right.endY) - .4 * gap));
        int last = Math.min(h - 2, rounded(Math.min(left.headY, right.headY) - .7 * gap));
        if (direction > 0) {
            first = Math.max(1, rounded(Math.max(left.headY, right.headY) + .7 * gap));
            last = Math.min(h - 2, rounded(Math.max(left.endY, right.endY) + .4 * gap));
        }
        List<Mark> marks = new ArrayList<>();
        for (int sign : new int[] {-1, 1})
            for (int tick = 2; tick <= 13; tick++) {
                double slope = sign * tick * .05;
                List<Band> bands = new ArrayList<>();
                int runStart = -1;
                for (int y = first; y <= last + 1; y++) {
                    int ink = 0;
                    if (y <= last)
                        for (int x = x0; x <= x1; x++) {
                            int yy = rounded(y + slope * (x - center));
                            if (yy >= 0 && yy < h && dark(gray, w, x, yy)) ink++;
                        }
                    boolean covered = y <= last && ink / (double) (x1 - x0 + 1) >= .85;
                    if (covered && runStart < 0) runStart = y;
                    if (!covered && runStart >= 0) {
                        int thickness = y - runStart;
                        if (thickness >= .20 * gap && thickness <= .75 * gap)
                            bands.add(new Band((runStart + y - 1) * .5, thickness));
                        runStart = -1;
                    }
                }
                for (int i = 0; i + 2 < bands.size(); i++) {
                    List<Band> three = bands.subList(i, i + 3);
                    double d1 = three.get(1).center - three.get(0).center,
                            d2 = three.get(2).center - three.get(1).center;
                    if (d1 < .5 * gap
                            || d1 > 1.1 * gap
                            || d2 < .5 * gap
                            || d2 > 1.1 * gap
                            || Math.abs(d1 - d2) > .2 * gap) continue;
                    boolean bounded = true;
                    for (int x :
                            new int[] {rounded(left.x + .18 * gap), rounded(right.x - .18 * gap)}) {
                        if (x < 0 || x >= w) {
                            bounded = false;
                            break;
                        }
                        for (Band band : three) {
                            int yy = rounded(band.center + slope * (x - center)), run = 0;
                            for (int y = Math.max(0, yy - rounded(.45 * gap));
                                    y <= Math.min(h - 1, yy + rounded(.45 * gap));
                                    y++) {
                                run = dark(gray, w, x, y) ? run + 1 : 0;
                                if (run >= Math.max(4, rounded(.24 * gap))) bounded = false;
                            }
                        }
                    }
                    if (bounded) marks.add(new Mark(x0, x1, slope, three));
                }
            }
        return List.copyOf(marks);
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Optional complete opposed pairs; an already accepted ordinary long wedge supplies the role. */
final class CompleteOpposedHairpinPair {
    record Shape(PlayingTechniqueDetector.Staff staff, int left, int right, int direction) {}

    static final class Work {
        long work, bytes;
        boolean failed;

        Work(long work, long bytes) {
            this.work = work;
            this.bytes = bytes;
        }

        boolean take(long operations, long allocation) {
            if (failed
                    || operations < 0
                    || allocation < 0
                    || operations > work
                    || allocation > bytes) {
                failed = true;
                return false;
            }
            work -= operations;
            bytes -= allocation;
            return true;
        }
    }

    private record Body(
            PlayingTechniqueDetector.Staff staff,
            int left,
            int right,
            int top,
            int bottom,
            int[] upper,
            int[] lower,
            int direction,
            double axis) {}

    static final class Collector {
        private final byte[] raw;
        private final int width, height;
        private final Work budget;
        private final List<Body> shorts, acceptedLongs;

        Collector(byte[] raw, int width, int height) {
            this(raw, width, height, new Work(2_000_000, 1_000_000));
        }

        Collector(byte[] raw, int width, int height, Work budget) {
            this.raw = raw;
            this.width = width;
            this.height = height;
            this.budget = budget;
            if (raw == null
                    || width < 1
                    || height < 1
                    || raw.length != (long) width * height
                    || budget == null) throw new IllegalArgumentException("Invalid pair source");
            if (budget.take(128, 128)) {
                shorts = new ArrayList<>();
                acceptedLongs = new ArrayList<>();
            } else {
                shorts = List.of();
                acceptedLongs = List.of();
            }
        }

        void candidate(
                PlayingTechniqueDetector.Staff staff,
                int left,
                int right,
                int top,
                int bottom,
                int count,
                int[] upper,
                int[] lower) {
            Body body = capture(staff, left, right, top, bottom, count, upper, lower, false);
            if (body != null) shorts.add(body);
        }

        /** Caller invokes only after the existing ordinary long-wedge timing route accepted it. */
        void acceptedLong(
                PlayingTechniqueDetector.Staff staff,
                int left,
                int right,
                int top,
                int bottom,
                int count,
                int[] upper,
                int[] lower) {
            Body body = capture(staff, left, right, top, bottom, count, upper, lower, true);
            if (body != null) acceptedLongs.add(body);
        }

        private Body capture(
                PlayingTechniqueDetector.Staff staff,
                int left,
                int right,
                int top,
                int bottom,
                int count,
                int[] upper,
                int[] lower,
                boolean ordinaryLong) {
            if (!budget.take(64, 0)) return null;
            if (staff == null
                    || !Float.isFinite(staff.gap())
                    || staff.gap() < 3
                    || staff.gap() > 64
                    || left < 0
                    || right >= width
                    || top < 0
                    || bottom >= height
                    || right < left
                    || bottom < top) return null;
            float gap = staff.gap();
            int w = right - left + 1, h = bottom - top + 1;
            if (upper == null
                    || lower == null
                    || upper.length != w
                    || lower.length != w
                    || count < 1
                    || h < gap * .4f
                    || h > gap * 3
                    || count > w * Math.max(8, gap)) return null;
            if (ordinaryLong
                    ? w < gap * 4 || w < h * 4
                    : w < gap * 3 || w > gap * 6 || h > gap * 1.75f) return null;
            if (!budget.take(w * 32L + (long) w * h * 10 + 128, 0)) return null;
            int direction = ScoreDynamicsDetector.hairpinDirection(upper, lower, gap);
            if (direction == 0
                    || !ScoreDynamicsDetector.openHairpinInterior(
                            raw, width, left, upper, lower, gap)) return null;
            int found = 0;
            double axis = 0;
            // Every literal core pixel is accounted for. Open columns have exactly two rails;
            // foreign interior islands, extra arms, solid triangles and broken rails refuse.
            for (int x = left; x <= right; x++) {
                int first = height, last = -1, runs = 0;
                boolean in = false;
                for (int y = top; y <= bottom; y++) {
                    boolean ink = (raw[y * width + x] & 255) < 145;
                    if (ink) {
                        found++;
                        first = Math.min(first, y);
                        last = y;
                        if (!in) runs++;
                    }
                    in = ink;
                }
                int at = x - left;
                if (first != upper[at]
                        || last != lower[at]
                        || runs < 1
                        || runs > 2
                        || last - first >= gap * .5f && runs != 2) return null;
                // Each arm itself follows the existing one-column/two-row raw adjacency;
                // a surviving rail cannot hide a missing opposite arm inside an average bin.
                if (at > 0
                        && (Math.abs(first - upper[at - 1]) > 2
                                || Math.abs(last - lower[at - 1]) > 2)) return null;
                axis += (first + last) * .5;
            }
            if (found != count) return null;
            if (shorts.size() + acceptedLongs.size() >= 256) {
                budget.failed = true;
                return null;
            }
            if (!budget.take(w * 2L + 32, w * 8L + 160)) return null;
            return new Body(
                    staff,
                    left,
                    right,
                    top,
                    bottom,
                    upper.clone(),
                    lower.clone(),
                    direction,
                    axis / w);
        }

        List<Shape> finish() {
            if (budget.failed) return List.of();
            if (!budget.take(
                    (long) shorts.size() * acceptedLongs.size() * 32 + 32,
                    shorts.size() * 48L + 32)) return List.of();
            List<Shape> proposed = new ArrayList<>();
            for (Body shortBody : shorts) {
                Body selected = null;
                int nearest = Integer.MAX_VALUE;
                boolean ambiguous = false;
                for (Body longBody : acceptedLongs) {
                    if (!shortBody.staff.equals(longBody.staff)
                            || shortBody.direction == longBody.direction) continue;
                    Body a = shortBody.left < longBody.left ? shortBody : longBody;
                    Body b = a == shortBody ? longBody : shortBody;
                    int separation = b.left - a.right - 1;
                    float gap = a.staff.gap();
                    // The existing compact window bounds the optional role search, too.
                    if (separation < 1 || separation > gap * 6) continue;
                    double tolerance = Math.max(1.4, gap * .14);
                    if (Math.abs(a.axis - b.axis) > tolerance) continue;
                    int aa = Math.max(0, a.upper.length - 1 - a.upper.length / 20);
                    int bb = Math.min(b.upper.length - 1, b.upper.length / 20);
                    if (Math.abs((a.lower[aa] - a.upper[aa]) - (b.lower[bb] - b.upper[bb]))
                            > tolerance * 2) continue;
                    if (!gapProof(a, b, (int) Math.ceil(tolerance))) continue;
                    if (separation < nearest) {
                        selected = longBody;
                        nearest = separation;
                        ambiguous = false;
                    } else if (separation == nearest) ambiguous = true;
                }
                if (selected != null && !ambiguous)
                    proposed.add(
                            new Shape(
                                    shortBody.staff,
                                    shortBody.left,
                                    shortBody.right,
                                    shortBody.direction));
            }
            // Optional funding failure never exposes a partially proved new set.
            return budget.failed ? List.of() : List.copyOf(proposed);
        }

        private boolean gapProof(Body a, Body b, int fringe) {
            int left = a.right + 1, right = b.left - 1;
            int top = Math.min(a.top, b.top), bottom = Math.max(a.bottom, b.bottom);
            int w = right - left + 1, h = bottom - top + 1, area = w * h;
            if (!budget.take(area * 32L + h * 16L + 128, area * 2L + 64)) return false;
            boolean[] fromLeft = new boolean[area], fromRight = new boolean[area];
            // A fringe path advances one literal column outward per step. It cannot walk
            // vertically along an arbitrary attached branch or cross a brighter paper cut.
            for (int side = 0; side < 2; side++) {
                boolean[] reached = side == 0 ? fromLeft : fromRight;
                int edge = side == 0 ? a.right : b.left;
                Body body = side == 0 ? a : b;
                for (int d = 1; d <= Math.min(fringe, w); d++) {
                    int x = edge + (side == 0 ? d : -d);
                    for (int y = top; y <= bottom; y++) {
                        int value = raw[y * width + x] & 255;
                        if (value >= 225) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            int py = y + dy;
                            if (py < top || py > bottom) continue;
                            int px = x + (side == 0 ? -1 : 1);
                            boolean connected;
                            if (d == 1) {
                                int k = edge - body.left;
                                connected =
                                        py >= body.upper[k]
                                                && py <= body.lower[k]
                                                && (raw[py * width + edge] & 255) < 145;
                            } else connected = reached[(py - top) * w + px - left];
                            if (connected && (raw[py * width + px] & 255) <= value) {
                                reached[(y - top) * w + x - left] = true;
                                break;
                            }
                        }
                    }
                }
            }
            boolean separator = false;
            for (int x = left; x <= right; x++) {
                boolean paper = true;
                for (int y = top; y <= bottom; y++) {
                    if ((raw[y * width + x] & 255) >= 225) continue;
                    paper = false;
                    int at = (y - top) * w + x - left;
                    if (fromLeft[at] == fromRight[at]) return false;
                }
                separator |= paper;
            }
            return separator;
        }
    }
}

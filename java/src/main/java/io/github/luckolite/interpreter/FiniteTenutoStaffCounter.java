// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Conservative physical-staff counter to recovery of an already classified finite mark.
 * No inferred rule pixels are added to an image. A clear result does not recognize a mark. */
final class FiniteTenutoStaffCounter {
    enum Decision {
        CLEAR,
        PHYSICAL_STAFF,
        AMBIGUOUS
    }

    // Resource bounds, frozen before execution. Exhaustion preserves the old veto.
    private static final int MAX_CELLS = 262144,
            MAX_COLUMNS = 512,
            MAX_RUNS = 32768,
            MAX_FAMILIES = 32768,
            MAX_STATES = 8192,
            MAX_OPERATIONS = 50000000;

    private FiniteTenutoStaffCounter() {}

    static Decision inspect(
            byte[] gray,
            int width,
            int height,
            int[] originalCore,
            int bodyLeft,
            int bodyTop,
            int bodyRight,
            int bodyBottom,
            float gap,
            List<NoteArticulationDetector.Anchor> anchors) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || originalCore == null
                || originalCore.length == 0
                || !Float.isFinite(gap)
                || gap < 6
                || gap > Math.min(width, height)
                || bodyLeft < 1
                || bodyTop < 1
                || bodyRight >= width - 1
                || bodyBottom >= height - 1
                || bodyRight < bodyLeft
                || bodyBottom < bodyTop
                || anchors == null) return Decision.AMBIGUOUS;
        try {
            return new Engine(
                            gray,
                            width,
                            height,
                            originalCore,
                            bodyLeft,
                            bodyTop,
                            bodyRight,
                            bodyBottom,
                            gap,
                            anchors)
                    .inspect();
        } catch (Limit exhausted) {
            return Decision.AMBIGUOUS;
        }
    }

    private static final class Limit extends RuntimeException {}

    private static final class CompactBudget {
        static void checkLive(int keys, int intervals, int knownCells) {
            if (keys < 0
                    || intervals < 0
                    || knownCells < 0
                    || keys > MAX_STATES
                    || intervals > MAX_STATES
                    || knownCells > MAX_CELLS) throw new Limit();
        }

        static void checkTotals(int families, int operations) {
            if (families < 0
                    || operations < 0
                    || families > MAX_FAMILIES
                    || operations > MAX_OPERATIONS) throw new Limit();
        }
    }

    private record CompactKey(
            int a,
            int b,
            int c,
            int d,
            int distinct,
            boolean ambiguous,
            List<Integer> known,
            int fifth,
            boolean coreWitness) {
        int run(int n) {
            return switch (n) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> c;
                case 3 -> d;
                default -> throw new IllegalArgumentException("four paths");
            };
        }
    }

    /** Exact closed-interval union, ONLY for one identical full CompactKey. */
    private static List<Range> compactUnion(List<Range> old, Range addition) {
        List<Range> result = new ArrayList<>();
        boolean placed = false;
        for (Range at : old) {
            if (at.high < addition.low) result.add(at);
            else if (addition.high < at.low) {
                if (!placed) {
                    result.add(addition);
                    placed = true;
                }
                result.add(at);
            } else
                addition =
                        new Range(Math.min(at.low, addition.low), Math.max(at.high, addition.high));
        }
        if (!placed) result.add(addition);
        return result;
    }

    private record Range(double low, double high) {
        Range intersect(Range b) {
            double l = Math.max(low, b.low), h = Math.min(high, b.high);
            return l <= h ? new Range(l, h) : null;
        }
    }

    private static final class Run {
        final int id, column, top, bottom;
        final boolean clipped;
        final List<Integer> next = new ArrayList<>(), previous = new ArrayList<>();

        Run(int id, int column, int top, int bottom, boolean clipped) {
            this.id = id;
            this.column = column;
            this.top = top;
            this.bottom = bottom;
            this.clipped = clipped;
        }
    }

    private record KnownFive(StaffPitchTrack track) {}

    private static final class Engine {
        final byte[] gray;
        final int width,
                height,
                bodyLeft,
                bodyTop,
                bodyRight,
                bodyBottom,
                left,
                right,
                top,
                bottom,
                center,
                near,
                columns;
        final float gap;
        final int[] core;
        final List<Run> runs = new ArrayList<>();
        final List<List<Integer>> byColumn = new ArrayList<>();
        final List<KnownFive> known = new ArrayList<>();
        int operations, families;
        int frontierKeys, frontierIntervals, nextIntervals;
        int[] ordinals;
        int missing;

        Engine(
                byte[] gray,
                int width,
                int height,
                int[] core,
                int l,
                int t,
                int r,
                int b,
                float gap,
                List<NoteArticulationDetector.Anchor> anchors) {
            this.gray = gray;
            this.width = width;
            this.height = height;
            this.core = core;
            bodyLeft = l;
            bodyTop = t;
            bodyRight = r;
            bodyBottom = b;
            this.gap = gap;
            center = Math.round((l + r) * .5f);
            near = Math.max(3, Math.round(gap * 1.5f));
            int far = Math.round(gap * 4f), reach = Math.max(1, Math.round(gap * .15f)) + 1;
            left = center - far;
            right = center + far;
            // The four-rail gap domain is the existing tracked-frame compatibility range.
            // Its largest admissible spacing, not a candidate-selected phase, bounds the ROI.
            int extent = (int) Math.ceil(4d * gap * 1.2d) + reach;
            top = (int) Math.floor((t + b) * .5d) - extent;
            bottom = (int) Math.ceil((t + b) * .5d) + extent;
            columns = right - left + 1;
            if (left < 0
                    || right >= width
                    || top < 1
                    || bottom >= height - 1
                    || columns > MAX_COLUMNS
                    || (long) columns * (bottom - top + 1) > MAX_CELLS) throw new Limit();
            for (int p : core)
                if (p < 0
                        || p >= gray.length
                        || p % width < l
                        || p % width > r
                        || p / width < t
                        || p / width > b) throw new Limit();
            IdentityHashMap<StaffPitchTrack, Boolean> seen = new IdentityHashMap<>();
            for (var anchor : anchors) {
                StaffPitchTrack track = anchor.track();
                if (track == null
                        || seen.put(track, Boolean.TRUE) != null
                        || !track.verified()
                        || !track.verifiedAt(left)
                        || !track.verifiedAt(right)) continue;
                if (registeredFiveRawRules(gray, width, height, track, center, gap))
                    known.add(new KnownFive(track));
            }
        }

        void spend() {
            CompactBudget.checkTotals(families, ++operations);
        }

        Decision inspect() {
            buildRuns();
            for (missing = 0; missing < 5; missing++) {
                ordinals = new int[4];
                for (int n = 0, k = 0; n < 5; n++) if (n != missing) ordinals[k++] = n;
                Map<CompactKey, List<Range>> current = new HashMap<>();
                frontierKeys = 0;
                frontierIntervals = 0;
                nextIntervals = 0;
                compactFour(
                        0, 0, new int[4], List.of(new Range(gap * .8d, gap * 1.2d)), null, current);
                for (int column = 1; column < columns && !current.isEmpty(); column++) {
                    Map<CompactKey, List<Range>> next = new HashMap<>();
                    frontierKeys = current.size();
                    frontierIntervals = nextIntervals;
                    nextIntervals = 0;
                    for (var entry : current.entrySet())
                        compactFour(column, 0, new int[4], entry.getValue(), entry.getKey(), next);
                    current = next;
                }
                for (var entry : current.entrySet()) {
                    CompactKey k = entry.getKey();
                    if (k.distinct == 63 && k.coreWitness)
                        return compactAmbiguous(k) ? Decision.AMBIGUOUS : Decision.PHYSICAL_STAFF;
                }
            }
            return partialCounter() ? Decision.AMBIGUOUS : Decision.CLEAR;
        }

        /** Incomplete raw continuations are not absence proof. Four independently
         * observed central rows with bilateral raw support keep uncertainty explicit.
         * A complete confirmed owner-five cannot be relabeled by such a subset. */
        boolean partialCounter() {
            Map<Integer, List<Integer>> coreByColumn = new HashMap<>();
            for (int p : core)
                coreByColumn
                        .computeIfAbsent(p % width - left, k -> new ArrayList<>())
                        .add(p / width);
            for (var entry : coreByColumn.entrySet()) {
                int column = entry.getKey();
                List<Integer> available = byColumn.get(column);
                for (int a = 0; a < available.size(); a++)
                    for (int b = a + 1; b < available.size(); b++)
                        for (int c = b + 1; c < available.size(); c++)
                            for (int d = c + 1; d < available.size(); d++) {
                                spend();
                                Run[] four = {
                                    runs.get(available.get(a)),
                                    runs.get(available.get(b)),
                                    runs.get(available.get(c)),
                                    runs.get(available.get(d))
                                };
                                boolean body = false;
                                for (Run r : four)
                                    if (r.top <= bodyBottom && r.bottom >= bodyTop) body = true;
                                if (body || knownSubset(four, left + column)) continue;
                                for (int absent = 0; absent < 5; absent++) {
                                    Range g = new Range(gap * .8d, gap * 1.2d);
                                    int[] ordinal = new int[4];
                                    for (int k = 0, n = 0; k < 5; k++)
                                        if (k != absent) ordinal[n++] = k;
                                    for (int i = 0; i < 4 && g != null; i++)
                                        for (int j = i + 1; j < 4 && g != null; j++)
                                            g =
                                                    pairGap(
                                                            g,
                                                            four[i].top,
                                                            four[i].bottom,
                                                            ordinal[i],
                                                            four[j].top,
                                                            four[j].bottom,
                                                            ordinal[j]);
                                    if (g == null) continue;
                                    boolean corePossible = false;
                                    for (int y : entry.getValue()) {
                                        Range at = g;
                                        for (int k = 0; k < 4 && at != null; k++)
                                            at =
                                                    pairGap(
                                                            at,
                                                            four[k].top,
                                                            four[k].bottom,
                                                            ordinal[k],
                                                            y,
                                                            y,
                                                            absent);
                                        if (at != null) {
                                            corePossible = true;
                                            break;
                                        }
                                    }
                                    if (!corePossible) continue;
                                    boolean supported = true;
                                    for (Run r : four)
                                        if (!uncertainBilateralSupport((r.top + r.bottom) / 2)) {
                                            supported = false;
                                            break;
                                        }
                                    if (supported) return true;
                                }
                            }
            }
            return false;
        }

        boolean knownSubset(Run[] four, int x) {
            for (KnownFive five : known) {
                float[] frame = five.track.at(x);
                int used = 0;
                boolean complete = true;
                for (Run r : four) {
                    int mask = 0;
                    for (int k = 0; k < 5; k++) {
                        int y = Math.round(frame[0] - (4 - k) * frame[1]);
                        if (y >= r.top && y <= r.bottom) mask |= 1 << k;
                    }
                    if (Integer.bitCount(mask) != 1 || (used & mask) != 0) {
                        complete = false;
                        break;
                    }
                    used |= mask;
                }
                if (complete) return true;
            }
            return false;
        }

        boolean uncertainBilateralSupport(int row) {
            int far = (columns - 1) / 2, reach = Math.max(1, Math.round(gap * .15f)) + 1;
            if (row - reach < 0 || row + reach >= height) return true;
            for (int side : new int[] {-1, 1}) {
                int hit = 0, samples = 0;
                for (int d = near; d <= far; d++) {
                    spend();
                    int x = center + side * d;
                    boolean ink = false;
                    samples++;
                    for (int y = row - reach; y <= row + reach; y++)
                        if ((gray[y * width + x] & 255) < 235) {
                            ink = true;
                            break;
                        }
                    if (ink) hit++;
                }
                if (samples == 0 || hit < samples * .50f) return false;
            }
            return true;
        }

        void buildRuns() {
            for (int column = 0; column < columns; column++) {
                List<Integer> ids = new ArrayList<>();
                byColumn.add(ids);
                int x = left + column;
                for (int y = top; y <= bottom; ) {
                    spend();
                    if ((gray[y * width + x] & 255) >= 235) {
                        y++;
                        continue;
                    }
                    int first = y;
                    while (y < bottom && (gray[(y + 1) * width + x] & 255) < 235) {
                        y++;
                        spend();
                    }
                    boolean clipped =
                            first == top && (gray[(top - 1) * width + x] & 255) < 235
                                    || y == bottom && (gray[(bottom + 1) * width + x] & 255) < 235;
                    if (runs.size() >= MAX_RUNS) throw new Limit();
                    Run run = new Run(runs.size(), column, first, y, clipped);
                    runs.add(run);
                    ids.add(run.id);
                    y++;
                }
                if (column == 0) continue;
                for (int old : byColumn.get(column - 1))
                    for (int now : ids) {
                        spend();
                        Run a = runs.get(old), b = runs.get(now);
                        if (a.top <= b.bottom + 1 && b.top <= a.bottom + 1) {
                            a.next.add(now);
                            b.previous.add(old);
                        }
                    }
            }
        }

        /** Extend every compatible four-run history; merged paths retain distinctness history. */
        void compactFour(
                int column,
                int depth,
                int[] four,
                List<Range> feasible,
                CompactKey previous,
                Map<CompactKey, List<Range>> next) {
            if (depth == 4) {
                CompactBudget.checkTotals(++families, operations);
                compactWitnesses(column, four, feasible, previous, next);
                return;
            }
            List<Integer> candidates =
                    previous == null ? byColumn.get(column) : runs.get(previous.run(depth)).next;
            for (int id : candidates) {
                spend();
                Run run = runs.get(id);
                if (depth > 0 && runs.get(four[depth - 1]).top > run.top) continue;
                List<Range> possible = new ArrayList<>();
                for (Range before : feasible) {
                    Range at = before;
                    for (int n = 0; n < depth && at != null; n++) {
                        spend();
                        Run other = runs.get(four[n]);
                        at =
                                pairGap(
                                        at,
                                        other.top,
                                        other.bottom,
                                        ordinals[n],
                                        run.top,
                                        run.bottom,
                                        ordinals[depth]);
                    }
                    if (at != null) possible.add(at);
                }
                if (possible.isEmpty()) continue;
                four[depth] = id;
                compactFour(column, depth + 1, four, possible, previous, next);
            }
        }

        void compactWitnesses(
                int column,
                int[] four,
                List<Range> feasible,
                CompactKey previous,
                Map<CompactKey, List<Range>> next) {
            int distinct = previous == null ? 0 : previous.distinct, bit = 0;
            boolean ambiguous = previous != null && previous.ambiguous;
            for (int a = 0; a < 4; a++) {
                Run run = runs.get(four[a]);
                if (run.clipped || run.bottom - run.top + 1 > Math.floor(gap * .4f + 1))
                    ambiguous = true;
                for (int b = 0; b < a; b++, bit++) {
                    spend();
                    if (four[a] != four[b]) distinct |= 1 << bit;
                    else ambiguous = true;
                }
            }
            List<Integer> memberMasks = new ArrayList<>();
            for (int t = 0; t < known.size(); t++) {
                float[] frame = known.get(t).track.at(left + column);
                for (int a = 0; a < 4; a++) {
                    Run run = runs.get(four[a]);
                    int here = 0;
                    for (int member = 0; member < 5; member++) {
                        spend();
                        int row = Math.round(frame[0] - (4 - member) * frame[1]);
                        if (row >= run.top && row <= run.bottom) here |= 1 << member;
                    }
                    memberMasks.add(here & (previous == null ? 31 : previous.known.get(t * 4 + a)));
                }
            }
            List<Integer> masks = List.copyOf(memberMasks);
            int leftEnd = center - near - left, rightStart = center + near - left;
            boolean exterior = column <= leftEnd || column >= rightStart;
            List<Integer> fifths;
            if (!exterior) fifths = List.of(-1);
            else if (column == 0 || column == rightStart) fifths = byColumn.get(column);
            else fifths = runs.get(previous.fifth).next;
            for (int fifth : fifths)
                for (Range independent : feasible) {
                    spend();
                    Range common = independent;
                    if (fifth != -1) {
                        Run r = runs.get(fifth);
                        common = compactWithFifth(common, four, r.top, r.bottom);
                    }
                    if (common == null) continue;
                    boolean witnessed = previous != null && previous.coreWitness;
                    CompactKey key =
                            new CompactKey(
                                    four[0], four[1], four[2], four[3], distinct, ambiguous, masks,
                                    fifth, witnessed);
                    compactInsert(next, key, common);
                    if (witnessed) continue;
                    for (int p : core) {
                        spend();
                        if (p % width - left != column) continue;
                        Range seen = compactWithFifth(common, four, p / width, p / width);
                        if (seen != null)
                            compactInsert(
                                    next,
                                    new CompactKey(
                                            four[0], four[1], four[2], four[3], distinct, ambiguous,
                                            masks, fifth, true),
                                    seen);
                    }
                }
        }

        Range compactWithFifth(Range g, int[] four, int low, int high) {
            for (int a = 0; a < 4 && g != null; a++) {
                spend();
                Run run = runs.get(four[a]);
                g = pairGap(g, run.top, run.bottom, ordinals[a], low, high, missing);
            }
            return g;
        }

        void compactInsert(Map<CompactKey, List<Range>> next, CompactKey key, Range gapRange) {
            spend();
            List<Range> old = next.get(key);
            if (old == null) old = List.of();
            // Precharge the worst-case two comparisons per old interval plus insertion.
            // This conservatively accounts for compactUnion without duplicating predicates.
            for (int n = 0; n < old.size() * 2 + 1; n++) spend();
            List<Range> union = compactUnion(old, gapRange);
            int keys = frontierKeys + next.size() + (next.containsKey(key) ? 0 : 1),
                    intervals = frontierIntervals + nextIntervals - old.size() + union.size();
            long cells = (long) keys * known.size() * 4;
            if (cells > MAX_CELLS) throw new Limit();
            CompactBudget.checkLive(keys, intervals, (int) cells);
            nextIntervals += union.size() - old.size();
            next.put(key, union);
        }

        boolean compactAmbiguous(CompactKey key) {
            if (key.ambiguous) return true;
            for (int t = 0; t < known.size(); t++) {
                int used = 0, offset = 0;
                boolean subset = true;
                for (int a = 0; a < 4; a++) {
                    spend();
                    int common = key.known.get(t * 4 + a);
                    if (Integer.bitCount(common) != 1 || (used & common) != 0) {
                        subset = false;
                        break;
                    }
                    used |= common;
                }
                if (!subset) continue;
                for (int a = 0; a < 4; a++) {
                    int member = Integer.numberOfTrailingZeros(key.known.get(t * 4 + a));
                    if (a == 0) offset = member - ordinals[a];
                    else if (member - ordinals[a] != offset) return true;
                }
                if (missing + offset < 0 || missing + offset > 4) return true;
            }
            return false;
        }
    }

    private static Range pairGap(
            Range domain, int aLo, int aHi, int aOrdinal, int bLo, int bHi, int bOrdinal) {
        if (aOrdinal > bOrdinal) return pairGap(domain, bLo, bHi, bOrdinal, aLo, aHi, aOrdinal);
        int distance = bOrdinal - aOrdinal;
        if (distance <= 0) throw new IllegalArgumentException("Distinct rail ordinals required");
        return domain.intersect(
                new Range((bLo - aHi) / (double) distance, (bHi - aLo) / (double) distance));
    }

    static boolean registeredFiveRawRules(
            byte[] gray, int width, int height, StaffPitchTrack track, int x, float gap) {
        int near = Math.max(3, Math.round(gap * 1.5f)),
                far = Math.round(gap * 4f),
                flank = Math.max(2, Math.round(gap * .3f));
        for (int line = 0; line < 5; line++)
            for (int direction : new int[] {-1, 1}) {
                int hits = 0, samples = 0;
                for (int d = near; d <= far; d++) {
                    int xx = x + direction * d;
                    if (xx < 0 || xx >= width || !track.verifiedAt(xx)) return false;
                    float[] frame = track.at(xx);
                    if (!Float.isFinite(frame[0] + frame[1])
                            || frame[1] < 8
                            || Math.abs(frame[1] - gap) > gap * .2f) return false;
                    int row = Math.round(frame[0] - (4 - line) * frame[1]);
                    if (row - 1 - flank < 0 || row + 1 + flank >= height) return false;
                    boolean ink = false;
                    samples++;
                    for (int yy = row - 1; yy <= row + 1; yy++) {
                        int tone = gray[yy * width + xx] & 255;
                        int paper =
                                Math.max(
                                        gray[(yy - flank) * width + xx] & 255,
                                        gray[(yy + flank) * width + xx] & 255);
                        if (tone < 235 && paper >= tone + 12) ink = true;
                    }
                    if (ink) hits++;
                }
                if (samples == 0 || hits < samples * .50f) return false;
            }
        return true;
    }
}

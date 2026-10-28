// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.security.MessageDigest;
import java.util.*;

/** Optional whole-frame raw proof when the existing printed-slur track is unavailable. */
final class WholeStaffSlurRules {
    private record Domain(int top, int bottom, int gap) {}

    private record Peak(int row, int strength) {}

    static final class Page {
        final byte[] raw;
        final int width, height;
        final Thread thread = Thread.currentThread();
        final long workLimit, byteLimit;
        long workUsed, bytesUsed;
        int projections;
        boolean failed;
        byte[] digest;
        final Map<Domain, Optional<StaffPitchTrack>> cache = new HashMap<>();

        Page(byte[] raw, int width, int height) {
            this(raw, width, height, 128L * 1024 * 1024, 8L * 1024 * 1024);
        }

        Page(byte[] raw, int width, int height, long work, long bytes) {
            if (raw == null
                    || width <= 0
                    || height <= 0
                    || raw.length != (long) width * height
                    || work < 0
                    || bytes < 0) throw new IllegalArgumentException();
            this.raw = raw;
            this.width = width;
            this.height = height;
            workLimit = work;
            byteLimit = bytes;
            // Optional capacity is not a restriction on the existing page API.
            failed = raw.length > 20_000_000;
        }

        void take(long work, long bytes) {
            if (work < 0
                    || bytes < 0
                    || failed
                    || work > workLimit - workUsed
                    || bytes > byteLimit - bytesUsed) {
                failed = true;
                throw new Refusal();
            }
            workUsed += work;
            bytesUsed += bytes;
        }

        byte[] hash() {
            take(raw.length, 4096);
            try {
                return MessageDigest.getInstance("SHA-256").digest(raw);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new AssertionError(e);
            }
        }

        boolean finish() {
            if (failed) return false;
            if (digest == null) return true;
            try {
                if (thread == Thread.currentThread() && Arrays.equals(digest, hash())) return true;
                failed = true;
                cache.clear();
                return false;
            } catch (Refusal e) {
                return false;
            }
        }

        StaffPitchTrack prove(byte[] gray, int w, int h, PlayingTechniqueDetector.Staff staff) {
            if (failed) return null;
            try {
                if (thread != Thread.currentThread() || raw != gray || width != w || height != h) {
                    failed = true;
                    return null;
                }
                if (!Float.isFinite(staff.top() + staff.bottom() + staff.gap())
                        || staff.gap() < 3
                        || staff.bottom() <= staff.top()) return null;
                if (digest == null) digest = hash();
                take(1, 64);
                var key =
                        new Domain(
                                Float.floatToIntBits(staff.top()),
                                Float.floatToIntBits(staff.bottom()),
                                Float.floatToIntBits(staff.gap()));
                var prior = cache.get(key);
                if (prior != null) return prior.orElse(null);
                var result = scan(this, staff);
                take(1, 128);
                cache.put(key, Optional.ofNullable(result));
                return result;
            } catch (Refusal e) {
                cache.clear();
                return null;
            }
        }
    }

    private static final class Refusal extends RuntimeException {}

    private WholeStaffSlurRules() {}

    record Supplement(
            PrintedSlurPageCapture.Result captured,
            Set<Integer> unboundOwners,
            int unprovedStaffs) {}

    static <T extends PrintedSlurOwners.Location> Supplement append(
            int page,
            ScorePageInterpretation score,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<T> heads,
            PrintedSlurFrames.Result<T> legacy,
            PrintedSlurPageCapture.Result captured,
            Set<PrintedSlurOwners.Tie> ties,
            boolean geometryOnly) {
        var unchanged = new Supplement(captured, legacy.unboundOwners(), legacy.unprovedStaffs());
        if (captured.limitReached()
                || legacy.unprovedStaffSlots().isEmpty()
                || legacy.unboundOwners().isEmpty()) return unchanged;
        var additional =
                PrintedSlurFrames.prepareSupplemental(
                        gray, width, height, staffs, score.notes(), heads, legacy);
        if (additional.frames().isEmpty()) return unchanged;
        var observed =
                geometryOnly
                        ? PrintedSlurPageCapture.captureGeometry(
                                page,
                                score.notes().size(),
                                gray,
                                labels,
                                width,
                                height,
                                additional.frames(),
                                score.notes())
                        : PrintedSlurPageCapture.capture(
                                page,
                                score.notes().size(),
                                gray,
                                labels,
                                width,
                                height,
                                additional.frames(),
                                ties,
                                score.notes());
        if (observed.limitReached()) return unchanged;
        var spans = new ArrayList<>(captured.source().spans());
        var candidates = new ArrayList<>(captured.source().candidates());
        for (var s : observed.source().spans())
            spans.add(
                    new ScoreSlurPages.Span(
                            "whole-staff:" + s.id(),
                            s.start(),
                            s.end(),
                            s.placement(),
                            s.lineType(),
                            s.evidence()));
        for (var s : observed.source().candidates())
            candidates.add(
                    new ScoreSlurPages.Span(
                            "whole-staff:" + s.id(),
                            s.start(),
                            s.end(),
                            s.placement(),
                            s.lineType(),
                            s.evidence()));
        var statuses =
                new EnumMap<PrintedSlurOwners.Status, Integer>(PrintedSlurOwners.Status.class);
        statuses.putAll(captured.statuses());
        observed.statuses().forEach((k, v) -> statuses.merge(k, v, Integer::sum));
        var source =
                ScoreSlurSource.capture(
                        spans,
                        candidates,
                        List.of(new ScoreSlurPages.Page(page, score.notes().size())));
        var unbound = new HashSet<>(legacy.unboundOwners());
        for (var frame : additional.frames())
            for (var head : frame.heads()) unbound.remove(head.owner());
        return new Supplement(
                new PrintedSlurPageCapture.Result(source, statuses, false),
                Set.copyOf(unbound),
                legacy.unprovedStaffs() - (staffs.size() - additional.unprovedStaffs()));
    }

    private static StaffPitchTrack scan(Page p, PlayingTechniqueDetector.Staff seed) {
        int first = Math.max(0, Math.round(seed.top() - seed.gap() * 6)),
                last = Math.min(p.height, Math.round(seed.bottom() + seed.gap() * 6));
        int flank = Math.max(2, Math.round(seed.gap() * .32f)), height = last - first;
        if (height <= flank * 2) return null;
        p.take(height + 1, 32L + 4L * height);
        int[] strength = new int[height];
        p.projections++;
        // Same contrasted pixels and projection admission used by the existing subtle-track route.
        p.take((long) (height - 2 * flank) * p.width * 3, 0);
        for (int y = first + flank; y < last - flank; y++)
            for (int x = 0; x < p.width; x++) {
                int at = y * p.width + x, ink = p.raw[at] & 255;
                if (ink <= 225
                        && (p.raw[at - flank * p.width] & 255) >= ink + 12
                        && (p.raw[at + flank * p.width] & 255) >= ink + 12) strength[y - first]++;
            }
        int minimum = Math.max(24, Math.round(p.width * .25f));
        var peaks = new ArrayList<Peak>();
        p.take(1, 64);
        for (int row = 0; row < height; row++) {
            p.take(1, 0);
            if (strength[row] < minimum) continue;
            boolean maximum = true, prominent = false;
            for (int check = Math.max(0, row - 2);
                    check <= Math.min(height - 1, row + 2);
                    check++) {
                p.take(1, 0);
                if (strength[check] > strength[row]) maximum = false;
                if (strength[check] < strength[row]) prominent = true;
            }
            if (!maximum || !prominent) continue;
            // Contiguous equal maxima are the same printed stroke, not competing five-rule
            // families.
            int end = row;
            while (end + 1 < height && strength[end + 1] == strength[row]) {
                p.take(1, 0);
                end++;
            }
            if (end - row > Math.max(2, seed.gap() * .25f)) return null;
            p.take(1, 64);
            peaks.add(new Peak(first + (row + end) / 2, strength[row]));
            row = end;
        }
        int[] unique = null;
        float uniqueGap = 0;
        for (int a = 0; a + 4 < peaks.size(); a++)
            for (int z = a + 4; z < peaks.size(); z++) {
                p.take(1, 0);
                float gap = (peaks.get(z).row - peaks.get(a).row) / 4f;
                if (gap < 2.5f
                        || gap > Math.max(40f, height * .035f)
                        || gap < seed.gap() * .88f
                        || gap > seed.gap() * 1.12f
                        || Math.abs(peaks.get(z).row - seed.bottom()) > seed.gap() * .65f) continue;
                p.take(6, 64);
                int[] rows = {peaks.get(a).row, -1, -1, -1, peaks.get(z).row};
                int previous = a, total = peaks.get(a).strength + peaks.get(z).strength;
                float error = 0;
                boolean complete = true;
                for (int line = 1; line < 4; line++) {
                    int selected = -1;
                    float expected = rows[0] + line * gap;
                    for (int at = previous + 1; at < z; at++) {
                        p.take(1, 0);
                        if (Math.abs(peaks.get(at).row - expected) > Math.max(2, gap * .24f))
                            continue;
                        // No nearest/strongest fit may hide a competing observed rule.
                        if (selected >= 0) return null;
                        selected = at;
                    }
                    if (selected < 0) {
                        complete = false;
                        break;
                    }
                    rows[line] = peaks.get(selected).row;
                    previous = selected;
                    total += peaks.get(selected).strength;
                    error += Math.max(0, Math.abs(rows[line] - expected) - .5f);
                }
                if (!complete || total - error * minimum * .75f < minimum * 4.5f) continue;
                // Transport one flat phase only when its observed rows meet the existing broad-rule
                // law.
                boolean regular = true;
                for (int i = 0; i < 5; i++) {
                    p.take(1, 0);
                    if (Math.abs(rows[i] - (rows[4] - (4 - i) * gap)) > gap * .12f) regular = false;
                }
                if (!regular) continue;
                int radius = Math.max(1, Math.round(gap * .2f));
                p.take(7L * p.width * (2L * radius + 1) * 3, 64);
                if (!StaffPitchTrack.completePrintedSlurRules(
                        p.raw, p.width, p.height, new RawStaffLineDetector.StaffLines(rows, gap)))
                    continue;
                if (unique != null && !Arrays.equals(unique, rows)) return null;
                unique = rows;
                uniqueGap = gap;
            }
        if (unique == null) return null;
        p.take(16, 256);
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(
                        new float[] {0, unique[4], uniqueGap},
                        new float[] {p.width - 1, unique[4], uniqueGap}));
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/**
 * Locates a printed separator between inferred single-staff bars. Balanced written voices are
 * preferred. Otherwise require a unique full-height, staff-terminated separator clear of every
 * recognized head and between notes.
 */
final class PrintedVoiceMeasureCut {
    static Float resolve(
            MeasureRegion region,
            List<ScoreNoteEvent> notes,
            int measure,
            byte[] gray,
            int w,
            int h) {
        Float balanced = resolve(region, notes, measure, gray, w, h, false);
        return balanced != null ? balanced : resolve(region, notes, measure, gray, w, h, true);
    }

    private static Float resolve(
            MeasureRegion region,
            List<ScoreNoteEvent> notes,
            int measure,
            byte[] gray,
            int w,
            int h,
            boolean physicalOnly) {
        var voice =
                notes.stream()
                        .filter(n -> n.measureIndex() == measure)
                        .sorted(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure))
                        .toList();
        if (voice.size() < 4
                || voice.stream()
                        .anyMatch(
                                n ->
                                        n.staffCount() != 1
                                                || n.staffIndex() != 0
                                                || (!physicalOnly && n.leadingRestBeats() != 0)
                                                || (!physicalOnly && n.followingRestBeats() != 0)
                                                || (n.articulations() & NoteOrnament.GRACE) != 0))
            return null;
        int at = -1;
        if (!physicalOnly) {
            double total = 0;
            float previous = -1;
            for (var n : voice) {
                if (n.positionInMeasure() - previous <= .018f) return null;
                double d = ScoreNoteTiming.writtenDurationBeats(n);
                if (!Double.isFinite(d) || d <= 0) return null;
                total += d;
                previous = n.positionInMeasure();
            }
            if (total < 2 || total > 32) return null;
            double sum = 0;
            for (int i = 0; i + 1 < voice.size(); i++) {
                sum += ScoreNoteTiming.writtenDurationBeats(voice.get(i));
                if (Math.abs(sum - total * .5) < .001) {
                    at = i;
                    break;
                }
            }
            if (at < 0) return null;
        }
        int left = Math.max(0, Math.round(region.left() * w)),
                right = Math.min(w - 1, Math.round(region.right() * w)),
                top = Math.max(0, Math.round(region.top() * h)),
                bottom = Math.min(h - 1, Math.round(region.bottom() * h));
        int cw = right - left + 1, ch = bottom - top + 1;
        if (cw < 24 || ch < 20) return null;
        byte[] crop = new byte[cw * ch];
        for (int y = 0; y < ch; y++) System.arraycopy(gray, (top + y) * w + left, crop, y * cw, cw);
        int limit = BeamInkThreshold.at(crop, cw, ch, cw / 2, 0, ch - 1, Math.max(8, ch / 8f));
        List<RawStaffLineDetector.StaffLines> staffs;
        if (limit < 170) {
            int[] projection = new int[ch];
            for (int y = 0; y < ch; y++)
                for (int x = 0; x < cw; x++) if ((crop[y * cw + x] & 255) <= limit) projection[y]++;
            staffs =
                    RawStaffLineDetector.detectFromStrength(
                            projection, Math.max(24, Math.round(cw * .25f)), ch);
        } else staffs = RawStaffLineDetector.detect(crop, cw, ch);
        PaleFrame pale = staffs.isEmpty() ? paleFrame(crop, cw, ch) : null;
        if (pale != null) staffs = List.of(pale.staff());
        if (staffs.size() != 1) return null;
        var staff = staffs.get(0);
        float gap = staff.gap();
        float span = (region.right() - region.left()) * w;
        int from =
                physicalOnly
                        ? Math.round(left + gap * 2)
                        : Math.max(
                                left,
                                Math.round(
                                        left
                                                + voice.get(at).positionInMeasure() * span
                                                + gap * .55f));
        int to =
                physicalOnly
                        ? Math.round(right - gap * 2)
                        : Math.min(
                                right,
                                Math.round(
                                        left
                                                + voice.get(at + 1).positionInMeasure() * span
                                                - gap * .55f));
        if (from > to) return null;
        List<Integer> hits = new ArrayList<>();
        StaffPitchTrack curve =
                pale != null
                        ? pale.track()
                        : StaffPitchTrack.detectForSymbols(
                                crop, cw, ch, staff.top(), staff.bottom(), gap);
        for (int x = from; x <= to; x++) {
            if (pale != null && !pale.track().verifiedAt(x - left)) continue;
            if (physicalOnly) {
                final int column = x;
                if (voice.stream()
                                .anyMatch(
                                        n ->
                                                Math.abs(
                                                                left
                                                                        + n.positionInMeasure()
                                                                                * span
                                                                        - column)
                                                        < gap * (pale != null ? 1f : .65f))
                        || voice.stream()
                                .noneMatch(n -> left + n.positionInMeasure() * span < column)
                        || voice.stream()
                                .noneMatch(n -> left + n.positionInMeasure() * span > column))
                    continue;
            }

            float[] local = curve == null ? new float[] {staff.bottom(), gap} : curve.at(x - left);
            float localTop = top + local[0] - 4 * local[1], localGap = local[1];
            int columnLimit =
                    Math.min(
                            180,
                            BeamInkThreshold.at(
                                    gray, w, h, x, top + staff.top(), top + staff.bottom(), gap));
            if (pale != null) columnLimit = pale.limit();
            boolean proved = false;
            for (float lean :
                    new float[] {
                        0, -.02f, .02f, -.04f, .04f, -.06f, .06f, -.08f, .08f, -.10f, .10f, -.12f,
                        .12f
                    }) {
                boolean full = true;
                float middle = localTop + 2 * localGap;
                for (int line = 0; line < 4; line++) {
                    int y0 = Math.round(localTop + line * localGap + localGap * .15f) + 1,
                            y1 = Math.round(localTop + (line + 1) * localGap - localGap * .15f) - 1;
                    int samples = 0, ink = 0, blank = 0, maxBlank = 0;
                    for (int y = Math.max(0, y0); y <= Math.min(h - 1, y1); y++) {
                        int xx = Math.round(x + lean * (y - middle));
                        samples++;
                        if (xx >= 0 && xx < w && (gray[y * w + xx] & 255) < columnLimit) {
                            ink++;
                            blank = 0;
                        } else {
                            blank++;
                            maxBlank = Math.max(maxBlank, blank);
                        }
                    }
                    if (samples < 3 || ink < samples * .9f || maxBlank > 1) {
                        full = false;
                        break;
                    }
                }
                // A stem can cross all five rules but continues beyond the staff.
                // A separator terminates at the outer rules; inspect the same lean.
                if (full
                        && extendsBeyondStaff(gray, w, h, x, localTop, localGap, lean, columnLimit))
                    full = false;
                if (full) {
                    proved = true;
                    break;
                }
            }
            if (proved) hits.add(x);
    }

        if (hits.isEmpty() || hits.get(hits.size() - 1) - hits.get(0) > gap * .65f) return null;
        return hits.get(hits.size() / 2) / (float) w;
    }

    private record PaleFrame(
            RawStaffLineDetector.StaffLines staff, StaffPitchTrack track, int limit) {}

    /** Recover faint, tilted rules only from independently validated local five-rule strips. */
    private static PaleFrame paleFrame(byte[] crop, int w, int h) {
        int[] histogram = new int[256];
        for (byte b : crop) histogram[b & 255]++;
        int paper = 255, count = 0, target = crop.length * 3 / 4;
        for (int i = 0; i < 256; i++) {
            count += histogram[i];
            if (count >= target) {
                paper = i;
                break;
            }
        }
        int limit = Math.min(205, paper - 32);
        if (limit <= 170) return null;
        int strip = Math.min(w, Math.max(96, Math.round(h * 1.6f)));
        var samples = new ArrayList<float[]>();
        RawStaffLineDetector.StaffLines seed = null;
        int step = Math.max(Math.max(1, strip / 2), (int) Math.ceil((w - strip) / 12.0));
        for (int left = 0; left + strip <= w; left += step) {
            byte[] local = new byte[strip * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < strip; x++)
                    local[y * strip + x] =
                            (byte) ((crop[y * w + left + x] & 255) <= limit ? 0 : 255);
            var found = RawStaffLineDetector.detect(local, strip, h);
            if (found.size() != 1) continue;
            var staff = found.get(0);
            if (seed == null) seed = staff;
            if (Math.abs(staff.gap() - seed.gap()) > seed.gap() * .15f
                    || Math.abs(staff.center() - seed.center()) > seed.gap() * 1.25f) return null;
            samples.add(new float[] {left + (strip - 1) * .5f, staff.bottom(), staff.gap()});
    }

        if (samples.size() < 3 || samples.get(samples.size() - 1)[0] - samples.get(0)[0] < w * .5f)
            return null;
        return new PaleFrame(seed, StaffPitchTrack.fromVerifiedSamples(samples), limit);
    }

    private static boolean extendsBeyondStaff(
            byte[] gray, int w, int h, int x, float top, float gap, float lean, int limit) {
        float middle = top + 2 * gap;
        int start = Math.max(2, Math.round(gap * .3f));
        int length = Math.max(3, Math.round(gap * .3f));
        for (int side : new int[] {-1, 1}) {
            float edge = side < 0 ? top : top + 4 * gap;
            int dark = 0;
            for (int distance = start; distance < start + length; distance++) {
                int y = Math.round(edge + side * distance);
                int xx = Math.round(x + lean * (y - middle));
                if (y < 0 || y >= h || xx < 0 || xx >= w) return true;
                if ((gray[y * w + xx] & 255) < limit) dark++;
            }
            if (dark >= length * .8f) return true;
    }
        return false;
    }
}

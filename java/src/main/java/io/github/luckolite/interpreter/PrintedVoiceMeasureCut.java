// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Locates a printed separator between two complete, inferred single-staff bars. */
final class PrintedVoiceMeasureCut {
    static Float resolve(
            MeasureRegion region,
            List<ScoreNoteEvent> notes,
            int measure,
            byte[] gray,
            int w,
            int h) {
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
                                                || n.leadingRestBeats() != 0
                                                || n.followingRestBeats() != 0
                                                || (n.articulations() & NoteOrnament.GRACE) != 0))
            return null;
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
        int at = -1;
        for (int i = 0; i + 1 < voice.size(); i++) {
            sum += ScoreNoteTiming.writtenDurationBeats(voice.get(i));
            if (Math.abs(sum - total * .5) < .001) {
                at = i;
                break;
            }
        }
        if (at < 0) return null;
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
        if (staffs.size() != 1) return null;
        var staff = staffs.get(0);
        float gap = staff.gap();
        float span = (region.right() - region.left()) * w;
        int from =
                Math.max(
                        left,
                        Math.round(left + voice.get(at).positionInMeasure() * span + gap * .55f));
        int to =
                Math.min(
                        right,
                        Math.round(
                                left + voice.get(at + 1).positionInMeasure() * span - gap * .55f));
        if (from > to) return null;
        List<Integer> hits = new ArrayList<>();
        StaffPitchTrack curve =
                StaffPitchTrack.detectForSymbols(crop, cw, ch, staff.top(), staff.bottom(), gap);
        for (int x = from; x <= to; x++) {
            float[] local = curve == null ? new float[] {staff.bottom(), gap} : curve.at(x - left);
            float localTop = top + local[0] - 4 * local[1], localGap = local[1];
            int columnLimit =
                    Math.min(
                            180,
                            BeamInkThreshold.at(
                                    gray, w, h, x, top + staff.top(), top + staff.bottom(), gap));
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
}

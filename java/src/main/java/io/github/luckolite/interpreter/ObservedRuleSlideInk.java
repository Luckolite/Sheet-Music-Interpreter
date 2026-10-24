// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Independently corroborated rule contrast supplies an additional connecting-line proposal. */
final class ObservedRuleSlideInk {
    private ObservedRuleSlideInk() {}

    record Prepared(byte[] clean, StaffPitchTrack[] tracks) {}

    static Prepared prepare(
            byte[] gray,
            int width,
            int height,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads) {
        if (staffs.isEmpty() || heads.size() < 2) return null;
        byte[] clean = null;
        StaffPitchTrack[] tracks = new StaffPitchTrack[staffs.size()];
        for (int index = 0; index < staffs.size(); index++) {
            var staff = staffs.get(index);
            StaffPitchTrack track =
                    StaffPitchTrack.forPrintedSlurs(
                            gray, width, height, staff.top(), staff.bottom(), staff.gap());
            if (track == null) continue;
            tracks[index] = track;
            PrintedCurveRuleInk rule =
                    new PrintedCurveRuleInk(null, gray, width, height, 150, track);
            for (int x = 0; x < width; x++) {
                if (!track.verifiedAt(x)) continue;
                float[] frame = track.at(x);
                float top = frame[0] - 4 * frame[1];
                for (int line = 0; line < 5; line++) {
                    float center = top + line * frame[1];
                    int lo = Math.max(0, (int) Math.ceil(center - frame[1] * .45f));
                    int hi = Math.min(height - 1, (int) Math.floor(center + frame[1] * .45f));
                    for (int y = lo; y <= hi; y++) {
                        int at = y * width + x, value = rule.sample(x, y);
                        int previous = (clean == null ? gray[at] : clean[at]) & 255;
                        if (value > previous) {
                            if (clean == null) clean = gray.clone();
                            clean[at] = (byte) value;
                        }
                    }
                }
            }
        }
        return clean == null ? null : new Prepared(clean, tracks);
    }

    /** A shorter surviving piece cannot skip separate collinear ink toward either printed head. */
    static boolean completeSpan(
            byte[] clean,
            int width,
            int height,
            List<NoteSlideDetector.Head> heads,
            NoteSlideDetector.Stroke stroke) {
        var target = heads.get(stroke.noteIndex());
        float latest = -Float.MAX_VALUE;
        for (var head : heads)
            if (head.staff() == target.staff()
                    && head.measure() == target.measure()
                    && head.x() < target.x() - target.gap() * .35f)
                latest = Math.max(latest, head.x());
        NoteSlideDetector.Head source = null;
        for (var head : heads)
            if (head.staff() == target.staff()
                    && head.measure() == target.measure()
                    && head.x() < target.x() - target.gap() * .35f
                    && latest - head.x() <= target.gap() * .35f) {
                if (source != null) return false;
                source = head;
            }
        return completeSpan(clean, width, height, source, target, stroke);
    }

    /** The caller supplies an independently closed exact physical endpoint pair. */
    static boolean completeSpan(
            byte[] clean,
            int width,
            int height,
            NoteSlideDetector.Head source,
            NoteSlideDetector.Head target,
            NoteSlideDetector.Stroke stroke) {
        if (source == null
                || target == null
                || stroke.right() <= stroke.left()
                || source.staff() != target.staff()
                || source.x() >= target.x()) return false;
        // One-gap head collars retain their existing endpoint policy. Outside those collars,
        // even an otherwise unclassified short run is contrary evidence to a complete span.
        return !abandoned(
                        clean,
                        width,
                        height,
                        stroke,
                        (int) Math.ceil(source.x() + target.gap()),
                        (int) stroke.left() - 1)
                && !abandoned(
                        clean,
                        width,
                        height,
                        stroke,
                        (int) stroke.right() + 1,
                        (int) Math.floor(target.x() - target.gap()));
    }

    private static boolean abandoned(
            byte[] clean,
            int width,
            int height,
            NoteSlideDetector.Stroke stroke,
            int left,
            int right) {
        left = Math.max(0, left);
        right = Math.min(width - 1, right);
        if (right - left < 3) return false;
        double slope = (stroke.rightY() - stroke.leftY()) / (stroke.right() - stroke.left());
        int pixels = 0, columns = 0;
        for (int x = left; x <= right; x++) {
            double center = stroke.leftY() + (x - stroke.left()) * slope;
            boolean ink = false;
            for (int y = Math.max(0, (int) Math.ceil(center - 1.3));
                    y <= Math.min(height - 1, (int) Math.floor(center + 1.3));
                    y++)
                if ((clean[y * width + x] & 255) < 150) {
                    pixels++;
                    ink = true;
                }
            if (ink) columns++;
        }
        return pixels >= 8 && columns >= 4;
    }
}

// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Reuses complete raw-bow and unique endpoint proofs; proximity alone owns no slide pixels. */
final class PhraseOwnedSlideComponents {
    private PhraseOwnedSlideComponents() {}

    static boolean owned(
            byte[] gray,
            int width,
            int height,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            int target,
            int[] pixels,
            int count) {
        if (target < 0 || target >= heads.size() || count < 1) return false;
        var owner = heads.get(target);
        if (owner.staff() < 0 || owner.staff() >= staffs.size()) return false;
        var staff = staffs.get(owner.staff());
        if (!Float.isFinite(staff.top() + staff.bottom() + staff.gap())
                || staff.gap() < 3
                || staff.bottom() <= staff.top()) return false;
        var track =
                StaffPitchTrack.forPrintedSlurs(
                        gray, width, height, staff.top(), staff.bottom(), staff.gap());
        if (track == null) return false;
        var locations = new ArrayList<PrintedSlurOwners.GeometryHead>();
        float top = staff.top() - staff.gap() * 6, bottom = staff.bottom() + staff.gap() * 6;
        for (int i = 0; i < heads.size(); i++) {
            var h = heads.get(i);
            if (h.staff() < 0
                    || h.staff() >= staffs.size()
                    || !Float.isFinite(h.x() + h.y())
                    || h.x() < 0
                    || h.y() < 0
                    || h.x() >= width
                    || h.y() >= height) return false;
            // Keep foreign-lane competitors rather than letting a local lane borrow their cap.
            locations.add(
                    new PrintedSlurOwners.GeometryHead(i, h.x(), h.y(), h.staff(), staffs.size()));
            if (h.staff() == owner.staff()) {
                float gap = track.at(h.x())[1];
                top = Math.min(top, h.y() - gap * 8);
                bottom = Math.max(bottom, h.y() + gap * 8);
            }
        }
        var raw =
                PrintedSlurContours.findWithRawEvidence(
                        gray,
                        null,
                        width,
                        height,
                        0,
                        Math.max(0, (int) Math.floor(top)),
                        width - 1,
                        Math.min(height - 1, (int) Math.ceil(bottom)),
                        track,
                        null,
                        PrintedSlurOwners.headEvidence(locations, gray, width, height, track));
        if (raw.result().limitReached()) return false;
        var complete = new HashSet<Integer>();
        for (int i = 0; i < count; i++) if (!complete.add(pixels[i])) return false;
        int matches = 0;
        for (var bow : raw.result().bows()) {
            var columns = raw.witnesses().get(bow);
            if (columns == null) continue;
            var core = new HashSet<Integer>();
            for (var column : columns)
                for (var band : column.members())
                    for (int y = band.first(); y <= band.last(); y++) {
                        int p = y * width + column.x();
                        if ((gray[p] & 255) < 150) core.add(p);
                    }
            // Exact whole detector component, never merely its bounds or a favourable selected
            // contour.
            if (!core.equals(complete)) continue;
            var bound =
                    PrintedSlurOwners.bind(
                            "finite-slide-role", 0, bow, locations, gray, width, height, track);
            if (bound.span().isEmpty()) continue;
            var span = bound.span().orElseThrow();
            int ai = span.start().noteIndex(), bi = span.end().noteIndex();
            var a = heads.get(ai);
            var b = heads.get(bi);
            if (ai == target
                    || bi == target
                    || a.staff() != owner.staff()
                    || b.staff() != owner.staff()
                    || a.measure() != owner.measure()
                    || b.measure() != owner.measure()
                    || a.x() >= b.x()
                    || b.x() >= owner.x()) continue;
            // The endpoint contract alone permits a distant typed head. Require its own raw body,
            // using one identity at a time so an overlapping foreign head cannot supply this proof.
            var ga = new PrintedSlurOwners.GeometryHead(ai, a.x(), a.y(), 0, 1);
            var gb = new PrintedSlurOwners.GeometryHead(bi, b.x(), b.y(), 0, 1);
            if (!PrintedSlurOwners.headEvidence(List.of(ga), gray, width, height, track)
                            .contains(Math.round(a.x()), Math.round(a.y()))
                    || !PrintedSlurOwners.headEvidence(List.of(gb), gray, width, height, track)
                            .contains(Math.round(b.x()), Math.round(b.y()))) continue;
            matches++;
        }
        return matches == 1;
    }
}

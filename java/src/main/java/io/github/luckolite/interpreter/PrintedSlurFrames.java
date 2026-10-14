// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Builds page-local arc frames from independently proved raw staff tracks and final note slots. */
final class PrintedSlurFrames {
    record Result(
            List<PrintedSlurPageCapture.Frame> frames,
            Set<Integer> unboundOwners,
            int unprovedStaffs) {
        Result {
            frames = List.copyOf(frames);
            unboundOwners = Set.copyOf(unboundOwners);
        }
    }

    private PrintedSlurFrames() {}

    static Result prepare(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<ScoreNoteEvent> notes,
            List<PrintedSlurOwners.Head> heads) {
        if (width <= 0 || height <= 0 || gray == null || gray.length != (long) width * height)
            throw new IllegalArgumentException("Invalid printed frame raster");
        Objects.requireNonNull(staffs);
        Objects.requireNonNull(notes);
        Objects.requireNonNull(heads);
        if (staffs.size() > 512)
            throw new IllegalArgumentException("Too many printed staff frames");
        var tracks = new ArrayList<StaffPitchTrack>();
        var groups = new ArrayList<List<PrintedSlurOwners.Head>>();
        int unproved = 0;
        for (var staff : staffs) {
            if (staff.index() < 0 || staff.count() < 1 || staff.index() >= staff.count())
                throw new IllegalArgumentException("Invalid physical staff identity");
            var track =
                    StaffPitchTrack.forPrintedSlurs(
                            gray, width, height, staff.top(), staff.bottom(), staff.gap());
            tracks.add(track);
            groups.add(new ArrayList<>());
            if (track == null) unproved++;
        }
        var seen = new HashSet<Integer>();
        var unbound = new HashSet<Integer>();
        for (var head : heads) {
            if (head.owner() >= notes.size()
                    || !seen.add(head.owner())
                    || head.x() >= width
                    || head.y() >= height)
                throw new IllegalArgumentException("Stale or duplicate printed head ownership");
            var note = notes.get(head.owner());
            if (note.staffIndex() != head.staffIndex() || note.staffCount() != head.staffCount())
                throw new IllegalArgumentException(
                        "Printed head differs from its retained staff identity");
            int owner = -1;
            for (int i = 0; i < staffs.size(); i++) {
                var staff = staffs.get(i);
                var track = tracks.get(i);
                if (track == null
                        || staff.index() != head.staffIndex()
                        || staff.count() != head.staffCount()) continue;
                float[] local = track.at(head.x());
                float predicted = local[0] - note.staffStep() * local[1] * .5f;
                if (Math.abs(head.y() - predicted) > local[1] * .45f) continue;
                if (owner >= 0) {
                    owner = -2;
                    break;
                }
                owner = i;
            }
            if (owner < 0) unbound.add(head.owner());
            else groups.get(owner).add(head);
        }
        var frames = new ArrayList<PrintedSlurPageCapture.Frame>();
        for (int i = 0; i < staffs.size(); i++) {
            var group = groups.get(i);
            if (group.isEmpty()) continue;
            var staff = staffs.get(i);
            float top = staff.top() - staff.gap() * 6, bottom = staff.bottom() + staff.gap() * 6;
            for (var head : group) {
                float gap = tracks.get(i).at(head.x())[1];
                top = Math.min(top, head.y() - gap * 8);
                bottom = Math.max(bottom, head.y() + gap * 8);
            }
            int first = Math.max(0, (int) Math.floor(top)),
                    last = Math.min(height - 1, (int) Math.ceil(bottom));
            if (last <= first) throw new IllegalArgumentException("Empty printed staff scan frame");
            frames.add(
                    new PrintedSlurPageCapture.Frame(
                            0, first, width - 1, last, tracks.get(i), group));
        }
        return new Result(frames, unbound, unproved);
    }
}

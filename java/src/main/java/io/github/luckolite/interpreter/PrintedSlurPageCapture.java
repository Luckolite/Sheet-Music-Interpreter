// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Captures raw printed bows into explicit physical-page source identities. */
final class PrintedSlurPageCapture {
    private static final int MAX_FRAMES = 512;
    private static final long MAX_SCAN_PIXELS = 128L * 1024 * 1024;

    record Frame(
            int left,
            int top,
            int right,
            int bottom,
            StaffPitchTrack track,
            List<PrintedSlurOwners.Head> heads) {
        Frame {
            Objects.requireNonNull(track);
            heads = List.copyOf(heads);
            if (left < 0 || top < 0 || right <= left || bottom <= top || !track.verified())
                throw new IllegalArgumentException(
                        "Printed arc needs a verified bounded staff frame");
        }
    }

    record Result(
            ScoreSlurSource source,
            Map<PrintedSlurOwners.Status, Integer> statuses,
            boolean limitReached) {
        Result {
            Objects.requireNonNull(source);
            statuses = Map.copyOf(statuses);
        }
    }

    private PrintedSlurPageCapture() {}

    static Result capture(
            int physicalPage,
            int noteCount,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<Frame> frames,
            Set<PrintedSlurOwners.Tie> retainedTies) {
        if (physicalPage < 0
                || noteCount < 0
                || noteCount > ScoreSlurSource.MAX_NOTES
                || width <= 0
                || height <= 0
                || gray == null
                || gray.length != (long) width * height
                || labels != null && labels.length != gray.length)
            throw new IllegalArgumentException("Invalid printed page source");
        Objects.requireNonNull(frames);
        Objects.requireNonNull(retainedTies);
        var seen = new HashSet<Integer>();
        long pixels = 0;
        // Validate all ownership before doing any scanning or publishing a partial source.
        for (var frame : frames) {
            if (frame.right() >= width || frame.bottom() >= height)
                throw new IllegalArgumentException("Staff frame lies outside source image");
            pixels =
                    Math.addExact(
                            pixels,
                            (long) (frame.right() - frame.left() + 1)
                                    * (frame.bottom() - frame.top() + 1));
            for (var head : frame.heads()) {
                if (head.owner() >= noteCount
                        || head.x() >= width
                        || head.y() >= height
                        || !seen.add(head.owner()))
                    throw new IllegalArgumentException(
                            "Printed head lacks unique page-local ownership");
            }
        }
        for (var tie : retainedTies)
            if (tie.startOwner() >= noteCount || tie.endOwner() >= noteCount)
                throw new IllegalArgumentException("Retained tie lies outside source page");
        if (frames.size() > MAX_FRAMES || pixels > MAX_SCAN_PIXELS)
            return new Result(ScoreSlurSource.empty(noteCount), Map.of(), true);

        var spans = new ArrayList<ScoreSlurPages.Span>();
        var statuses =
                new EnumMap<PrintedSlurOwners.Status, Integer>(PrintedSlurOwners.Status.class);
        int ordinal = 0;
        for (var frame : frames) {
            var hints =
                    PrintedSlurOwners.headEvidence(
                            frame.heads(), gray, width, height, frame.track());
            var raw =
                    PrintedSlurContours.find(
                            gray,
                            labels,
                            width,
                            height,
                            frame.left(),
                            frame.top(),
                            frame.right(),
                            frame.bottom(),
                            frame.track(),
                            null,
                            hints);
            if (raw.limitReached())
                return new Result(ScoreSlurSource.empty(noteCount), statuses, true);
            for (var bow : raw.bows()) {
                String id = "printed-page-" + physicalPage + "-arc-" + ordinal++;
                var owned =
                        PrintedSlurOwners.resolve(
                                id,
                                physicalPage,
                                bow,
                                frame.heads(),
                                retainedTies,
                                gray,
                                width,
                                height,
                                frame.track());
                statuses.merge(owned.status(), 1, Integer::sum);
                owned.span().ifPresent(spans::add);
            }
        }
        return new Result(
                ScoreSlurSource.capture(
                        spans, List.of(new ScoreSlurPages.Page(physicalPage, noteCount))),
                statuses,
                false);
    }
}

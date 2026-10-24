// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Captures raw printed bows into explicit physical-page source identities. */
final class PrintedSlurPageCapture {
    private static final int MAX_FRAMES = 512;
    private static final long MAX_SCAN_PIXELS = 128L * 1024 * 1024;

    record Frame<T extends PrintedSlurOwners.Location>(
            int left, int top, int right, int bottom, StaffPitchTrack track, List<T> heads) {
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
            List<? extends Frame<? extends PrintedSlurOwners.Location>> frames,
            Set<PrintedSlurOwners.Tie> retainedTies) {
        return captureInternal(
                physicalPage,
                noteCount,
                gray,
                labels,
                width,
                height,
                frames,
                retainedTies,
                false,
                null);
    }

    static Result captureGeometry(
            int physicalPage,
            int noteCount,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<? extends Frame<? extends PrintedSlurOwners.Location>> frames) {
        return captureInternal(
                physicalPage, noteCount, gray, labels, width, height, frames, Set.of(), true, null);
    }

    static Result capture(
            int physicalPage,
            int noteCount,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<? extends Frame<? extends PrintedSlurOwners.Location>> frames,
            Set<PrintedSlurOwners.Tie> retainedTies,
            List<ScoreNoteEvent> sourceNotes) {
        return captureInternal(
                physicalPage,
                noteCount,
                gray,
                labels,
                width,
                height,
                frames,
                retainedTies,
                false,
                sourceNotes);
    }

    static Result captureGeometry(
            int physicalPage,
            int noteCount,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<? extends Frame<? extends PrintedSlurOwners.Location>> frames,
            List<ScoreNoteEvent> sourceNotes) {
        return captureInternal(
                physicalPage,
                noteCount,
                gray,
                labels,
                width,
                height,
                frames,
                Set.of(),
                true,
                sourceNotes);
    }

    private static Result captureInternal(
            int physicalPage,
            int noteCount,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<? extends Frame<? extends PrintedSlurOwners.Location>> frames,
            Set<PrintedSlurOwners.Tie> retainedTies,
            boolean geometryOnly,
            List<ScoreNoteEvent> sourceNotes) {
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
                if (!geometryOnly && !(head instanceof PrintedSlurOwners.Head))
                    throw new IllegalArgumentException(
                            "Musical classification requires explicit timed heads");
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

        if (sourceNotes != null && sourceNotes.size() != noteCount)
            throw new IllegalArgumentException("Beam ownership source count disagrees");
        var beam =
                sourceNotes == null ? null : new ObservedStemBeam(sourceNotes, gray, width, height);
        var marks =
                sourceNotes == null
                        ? null
                        : new PrintedCurveMarkRoles(sourceNotes, gray, width, height);
        var joints =
                sourceNotes == null
                        ? null
                        : new PrintedCurveJointRoles(sourceNotes, gray, width, height);
        var spans = new ArrayList<ScoreSlurPages.Span>();
        var candidates = new ArrayList<ScoreSlurPages.Span>();
        var statuses =
                new EnumMap<PrintedSlurOwners.Status, Integer>(PrintedSlurOwners.Status.class);
        int ordinal = 0;
        int markOrdinal = 0;
        int shortRailOrdinal = 0;
        int jointOrdinal = 0;
        var pieces = new ArrayList<PrintedSystemSlurJoins.Observed>();
        boolean admitJoins = sourceNotes != null;
        for (var frame : frames) {
            var hints =
                    PrintedSlurOwners.headEvidence(
                            frame.heads(), gray, width, height, frame.track());
            if (marks != null) hints = marks.wrap(hints, frame.heads(), frame.track());
            if (joints != null) hints = joints.wrap(hints, frame.heads(), frame.track());
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
                if (admitJoins) {
                    if (pieces.size() == 4096) {
                        pieces.clear();
                        admitJoins = false;
                    } else pieces.add(new PrintedSystemSlurJoins.Observed(frame, bow));
                }
                String id =
                        raw.jointBows().contains(bow)
                                ? "printed-page-" + physicalPage + "-joint-arc-" + jointOrdinal++
                                : raw.shortRailBows().contains(bow)
                                        ? "printed-page-"
                                                + physicalPage
                                                + "-short-rail-arc-"
                                                + shortRailOrdinal++
                                        : marks != null && marks.ownsRivalCurve(bow)
                                                ? "printed-page-"
                                                        + physicalPage
                                                        + "-mark-arc-"
                                                        + markOrdinal++
                                                : "printed-page-"
                                                        + physicalPage
                                                        + "-arc-"
                                                        + ordinal++;
                if (geometryOnly) {
                    var bound =
                            PrintedSlurOwners.bind(
                                    id,
                                    physicalPage,
                                    bow,
                                    frame.heads(),
                                    gray,
                                    width,
                                    height,
                                    frame.track());
                    if (beam != null
                            && bound.span().isPresent()
                            && beam.owns(
                                    bow, bound.span().orElseThrow(), frame.heads(), frame.track()))
                        continue;
                    statuses.merge(bound.status(), 1, Integer::sum);
                    bound.span().ifPresent(candidates::add);
                    continue;
                }
                var owned =
                        PrintedSlurOwners.resolve(
                                id,
                                physicalPage,
                                bow,
                                frame.heads().stream()
                                        .map(h -> (PrintedSlurOwners.Head) h)
                                        .toList(),
                                retainedTies,
                                gray,
                                width,
                                height,
                                frame.track());
                if (beam != null
                        && owned.span().isPresent()
                        && beam.owns(bow, owned.span().orElseThrow(), frame.heads(), frame.track()))
                    continue;
                statuses.merge(owned.status(), 1, Integer::sum);
                owned.span().ifPresent(spans::add);
                if (owned.status() == PrintedSlurOwners.Status.UNPITCHED_UNRESOLVED
                        || owned.status() == PrintedSlurOwners.Status.SAME_PITCH_UNRESOLVED
                        || owned.status() == PrintedSlurOwners.Status.TIE_PITCH_CONFLICT
                        || owned.status() == PrintedSlurOwners.Status.REVERSED_CLOCK) {
                    PrintedSlurOwners.bind(
                                    id,
                                    physicalPage,
                                    bow,
                                    frame.heads(),
                                    gray,
                                    width,
                                    height,
                                    frame.track())
                            .span()
                            .filter(
                                    span ->
                                            beam == null
                                                    || !beam.owns(
                                                            bow,
                                                            span,
                                                            frame.heads(),
                                                            frame.track()))
                            .ifPresent(candidates::add);
                }
            }
        }
        if (admitJoins) {
            var joined =
                    PrintedSystemSlurJoins.join(
                            physicalPage, pieces, frames, sourceNotes, gray, width, height);
            candidates.addAll(joined);
            if (!joined.isEmpty())
                statuses.merge(
                        PrintedSlurOwners.Status.GEOMETRY_OWNED, joined.size(), Integer::sum);
        }
        if (sourceNotes != null) {
            var graceRoles = new CalibratedGraceSlurs(sourceNotes, gray, width, height);
            for (var observed : graceRoles.discover(frames)) {
                var frame = observed.frame();
                var bow = observed.bow();
                String id =
                        "printed-page-"
                                + physicalPage
                                + "-grace-cell-"
                                + observed.graceOwner()
                                + ":"
                                + observed.principalOwner();
                boolean duplicate = false;
                for (var old : spans)
                    if (old.start().noteIndex() == observed.graceOwner()
                            && old.end().noteIndex() == observed.principalOwner()
                            && old.placement()
                                    == (bow.above()
                                            ? ScoreSlurSpan.Placement.ABOVE
                                            : ScoreSlurSpan.Placement.BELOW)) duplicate = true;
                for (var old : candidates)
                    if (old.start().noteIndex() == observed.graceOwner()
                            && old.end().noteIndex() == observed.principalOwner()
                            && old.placement()
                                    == (bow.above()
                                            ? ScoreSlurSpan.Placement.ABOVE
                                            : ScoreSlurSpan.Placement.BELOW)) duplicate = true;
                if (duplicate) continue;
                if (geometryOnly) {
                    var bound =
                            PrintedSlurOwners.bindPixelCells(
                                    id,
                                    physicalPage,
                                    bow,
                                    frame.heads(),
                                    gray,
                                    width,
                                    height,
                                    frame.track());
                    if (beam != null
                            && bound.span().isPresent()
                            && beam.owns(
                                    bow, bound.span().orElseThrow(), frame.heads(), frame.track()))
                        continue;
                    statuses.merge(bound.status(), 1, Integer::sum);
                    bound.span().ifPresent(candidates::add);
                } else {
                    var owned =
                            PrintedSlurOwners.resolvePixelCells(
                                    id,
                                    physicalPage,
                                    bow,
                                    frame.heads().stream()
                                            .map(h -> (PrintedSlurOwners.Head) h)
                                            .toList(),
                                    retainedTies,
                                    gray,
                                    width,
                                    height,
                                    frame.track());
                    if (beam != null
                            && owned.span().isPresent()
                            && beam.owns(
                                    bow, owned.span().orElseThrow(), frame.heads(), frame.track()))
                        continue;
                    statuses.merge(owned.status(), 1, Integer::sum);
                    owned.span().ifPresent(spans::add);
                    if (owned.status() == PrintedSlurOwners.Status.UNPITCHED_UNRESOLVED
                            || owned.status() == PrintedSlurOwners.Status.SAME_PITCH_UNRESOLVED
                            || owned.status() == PrintedSlurOwners.Status.TIE_PITCH_CONFLICT
                            || owned.status() == PrintedSlurOwners.Status.REVERSED_CLOCK)
                        PrintedSlurOwners.bindPixelCells(
                                        id,
                                        physicalPage,
                                        bow,
                                        frame.heads(),
                                        gray,
                                        width,
                                        height,
                                        frame.track())
                                .span()
                                .ifPresent(candidates::add);
                }
            }
        }
        return new Result(
                ScoreSlurSource.capture(
                        spans,
                        candidates,
                        List.of(new ScoreSlurPages.Page(physicalPage, noteCount))),
                statuses,
                false);
    }
}
